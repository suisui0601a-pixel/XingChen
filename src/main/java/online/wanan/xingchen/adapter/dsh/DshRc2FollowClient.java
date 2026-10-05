package online.wanan.xingchen.adapter.dsh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import online.wanan.xingchen.core.agent.CancellationToken;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** RC.2 remote.mux client. A physical socket owns one expected session/stream binding. */
final class DshRc2FollowClient implements AutoCloseable {
    private final HttpClient http;private final ObjectMapper mapper;private final DshRc2AuthClient auth;private final URI base;private final int timeoutMillis;
    private final Set<MuxListener> active=ConcurrentHashMap.newKeySet();
    DshRc2FollowClient(HttpClient http,ObjectMapper mapper,DshRc2AuthClient auth,URI base,int timeoutMillis){this.http=http;this.mapper=mapper;this.auth=auth;this.base=base;this.timeoutMillis=timeoutMillis;}

    DshFollowSubscription follow(String sessionId,Consumer<DshFollowEvent> events,Consumer<Throwable> failure){
        requireSession(sessionId);var listener=new MuxListener(sessionId,"session/follow",events,failure,null);open(listener,sessionId);return listener;
    }

    List<String> readQueuedItemIds(String sessionId,CancellationToken cancellation){
        requireSession(sessionId);var ready=new CompletableFuture<List<String>>();var listener=new MuxListener(sessionId,"session/control",e->{},e->{ready.completeExceptionally(e);},ready);open(listener,sessionId);
        try(AutoCloseable hook=cancellation.onCancel(()->ready.cancel(true))){return ready.get(timeoutMillis,TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){Thread.currentThread().interrupt();listener.close();throw new DshRc2Exception("transport/interrupted","DSH control stream interrupted");}
        catch(TimeoutException e){listener.close();throw new DshRc2Exception("transport/timeout","DSH control baseline timed out");}
        catch(CancellationException e){listener.close();throw e;}
        catch(ExecutionException e){listener.close();Throwable cause=e.getCause();if(cause instanceof RuntimeException re)throw re;throw new DshRc2Exception("transport/unavailable","DSH control stream unavailable");}
        catch(Exception e){listener.close();throw new DshRc2Exception("transport/unavailable","DSH control stream unavailable");}
        finally{listener.close();}
    }

    private void open(MuxListener listener,String sessionId){
        active.add(listener);
        try{
            String scheme="https".equalsIgnoreCase(base.getScheme())?"wss":"ws";URI ws=URI.create(scheme+"://"+base.getRawAuthority()+"/api/remote.mux");
            http.newWebSocketBuilder().connectTimeout(java.time.Duration.ofMillis(timeoutMillis)).header("Cookie",auth.cookie()).buildAsync(ws,listener)
                    .orTimeout(timeoutMillis,TimeUnit.MILLISECONDS).whenComplete((socket,error)->{if(error!=null){int status=handshakeStatus(error);boolean rejected=status==401||status==403;listener.fail(new DshRc2Exception(rejected?"auth/rejected":"transport/unavailable",rejected?"DSH rejected remote.mux authentication":"DSH remote.mux connection failed"));}else listener.attach(socket,sessionId);});
        }catch(RuntimeException e){listener.fail(e instanceof DshRc2Exception?e:new DshRc2Exception("transport/unavailable","DSH remote.mux connection failed"));}
    }
    private static int handshakeStatus(Throwable error){for(Throwable t=error;t!=null;t=t.getCause())if(t instanceof WebSocketHandshakeException h)return h.getResponse().statusCode();return 0;}
    private static void requireSession(String id){if(id==null||id.isBlank())throw new IllegalArgumentException("DSH session id is required");}

    private final class MuxListener implements WebSocket.Listener,DshFollowSubscription {
        private final String sessionId,endpoint,streamId=UUID.randomUUID().toString();private final Consumer<DshFollowEvent> events;private final Consumer<Throwable> failure;private final CompletableFuture<List<String>> control;
        private final StringBuilder fragments=new StringBuilder();private final CompletableFuture<Void> completion=new CompletableFuture<>();private volatile WebSocket socket;private volatile boolean open=true,terminal;
        MuxListener(String sessionId,String endpoint,Consumer<DshFollowEvent> events,Consumer<Throwable> failure,CompletableFuture<List<String>> control){this.sessionId=sessionId;this.endpoint=endpoint;this.events=events==null?e->{}:events;this.failure=failure==null?e->{}:failure;this.control=control;}
        void attach(WebSocket socket,String ignored){this.socket=socket;if(!open){socket.abort();return;} }
        @Override public void onOpen(WebSocket webSocket){this.socket=webSocket;ObjectNode frame=mapper.createObjectNode().put("type","open").put("streamId",streamId).put("endpoint",endpoint);ObjectNode payload=mapper.createObjectNode(),args=mapper.createObjectNode();if(endpoint.equals("session/follow")){ObjectNode request=mapper.createObjectNode();request.put("maxMessages",200);request.putObject("address").put("kind","session").put("sessionId",sessionId);args.set("request",request);}payload.set("args",args);frame.set("payload",payload);webSocket.sendText(frame.toString(),true);webSocket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket webSocket,CharSequence data,boolean last){if(fragments.length()+data.length()>1_048_576){fail(new DshRc2Exception("protocol/frame-too-large","DSH remote.mux frame exceeds limit"));return CompletableFuture.completedFuture(null);}fragments.append(data);if(last){String raw=fragments.toString();fragments.setLength(0);try{handle(mapper.readTree(raw));}catch(Throwable e){fail(e instanceof DshRc2Exception?e:new DshRc2Exception("protocol/malformed-frame","DSH remote.mux frame is malformed"));}}if(open)webSocket.request(1);return CompletableFuture.completedFuture(null);}
        private void handle(JsonNode frame){if(frame==null||!frame.isObject()||!streamId.equals(frame.path("streamId").asText()))throw new DshRc2Exception("protocol/foreign-stream","DSH remote.mux returned a foreign stream id");String type=frame.path("type").asText();switch(type){case "item"->item(frame.path("value"));case "end"->{terminal=true;open=false;active.remove(this);if(control!=null&&!control.isDone()){var error=new DshRc2Exception("protocol/missing-baseline","DSH control stream ended before baseline");control.completeExceptionally(error);completion.completeExceptionally(error);}else completion.complete(null);closeSocket();}case "error"->{JsonNode error=frame.path("error");String code=error.path("code").asText();fail(new DshRc2Exception(code.isBlank()?"remote/error":code,"DSH remote.mux operation failed"));}default->throw new DshRc2Exception("protocol/malformed-frame","DSH remote.mux frame type is unknown");}}
        private void item(JsonNode value){if(value==null||!value.isObject())throw new DshRc2Exception("protocol/malformed-frame","DSH remote.mux item is malformed");String kind=value.path("type").asText();if(endpoint.equals("session/control")){if(!"baseline".equals(kind))return;JsonNode queues=value.path("value").path("queues");if(!queues.isObject())throw new DshRc2Exception("protocol/malformed-frame","DSH control baseline is malformed");JsonNode items=queues.path(sessionId);if(items.isMissingNode())items=mapper.createArrayNode();if(!items.isArray())throw new DshRc2Exception("protocol/malformed-frame","DSH control queue is malformed");List<String> ids=new ArrayList<>();items.forEach(item->{String id=item.path("id").asText();if(id.isBlank())throw new DshRc2Exception("protocol/malformed-frame","DSH control queue item is malformed");ids.add(id);});control.complete(List.copyOf(ids));return;}
            switch(kind){case "snapshot"->{String actual=value.path("header").path("id").asText();if(!sessionId.equals(actual))throw new DshRc2Exception("protocol/foreign-session","DSH follow snapshot belongs to another session");events.accept(new DshFollowEvent(sessionId,DshFollowEvent.Kind.SNAPSHOT,"snapshot",value.path("cursor").asLong(),null,Map.of()));}case "event"->{JsonNode event=value.path("event");if(!event.isObject()||!event.path("seq").canConvertToLong()||!event.path("type").isTextual())throw new DshRc2Exception("protocol/malformed-frame","DSH durable follow event is malformed");JsonNode data=event.path("data");if(data.isObject()&&data.has("sessionId")&&!sessionId.equals(data.path("sessionId").asText()))throw new DshRc2Exception("protocol/foreign-session","DSH follow event belongs to another session");Map<String,Object> attrs=data.isObject()?mapper.convertValue(data,Map.class):Map.of("value",mapper.convertValue(data,Object.class));events.accept(new DshFollowEvent(sessionId,DshFollowEvent.Kind.DURABLE_EVENT,event.path("type").asText(),event.path("seq").asLong(),Instant.ofEpochMilli(event.path("time").asLong()),attrs));}case "assistant-stream"->{events.accept(new DshFollowEvent(sessionId,DshFollowEvent.Kind.ASSISTANT_STREAM,"assistant-stream",0,null,Map.of("frameType",value.path("frame").path("type").asText("unknown"))));}default->throw new DshRc2Exception("protocol/malformed-frame","DSH follow item type is unknown");}}
        void fail(Throwable error){if(!open)return;open=false;terminal=true;active.remove(this);completion.completeExceptionally(error);if(control!=null)control.completeExceptionally(error);try{failure.accept(error);}catch(RuntimeException ignored){}closeSocket();}
        @Override public void onError(WebSocket ws,Throwable error){fail(new DshRc2Exception("transport/disconnected","DSH remote.mux disconnected"));}
        @Override public CompletionStage<?> onClose(WebSocket ws,int statusCode,String reason){if(open)fail(new DshRc2Exception("transport/disconnected","DSH remote.mux closed"));return CompletableFuture.completedFuture(null);}
        @Override public boolean isOpen(){return open;}
        @Override public CompletionStage<Void> completion(){return completion;}
        @Override public void close(){if(!open)return;open=false;terminal=true;active.remove(this);completion.cancel(false);WebSocket ws=socket;if(ws!=null){ObjectNode cancel=mapper.createObjectNode().put("type","cancel").put("streamId",streamId);try{ws.sendText(cancel.toString(),true);}catch(RuntimeException ignored){}closeSocket();}}
        private void closeSocket(){WebSocket ws=socket;if(ws!=null){try{ws.sendClose(WebSocket.NORMAL_CLOSURE,"stream complete");}catch(RuntimeException ignored){ws.abort();}}}
    }
    @Override public void close(){for(MuxListener subscription:List.copyOf(active))subscription.close();active.clear();}
}
