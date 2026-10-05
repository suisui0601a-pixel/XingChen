package online.wanan.xingchen.adapter.dsh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Independent RC.2 $events stream; it never dispatches items into session/follow. */
final class DshRc2EventClient implements DshInteractionSubscription {
    private final HttpClient http; private final ObjectMapper mapper; private final DshRc2AuthClient auth;
    private final URI base; private final int timeoutMillis; private final Consumer<DshInteractionEvent> events;
    private final Consumer<Throwable> failures; private final ScheduledExecutorService retry;
    private final AtomicBoolean open=new AtomicBoolean(true); private final CompletableFuture<Void> completion=new CompletableFuture<>();
    private volatile WebSocket socket; private volatile String clientId; private volatile long generation; private volatile long clientGeneration;
    DshRc2EventClient(HttpClient http,ObjectMapper mapper,DshRc2AuthClient auth,URI base,int timeoutMillis,
                      Consumer<DshInteractionEvent> events,Consumer<Throwable> failures){
        this.http=http;this.mapper=mapper;this.auth=auth;this.base=base;this.timeoutMillis=timeoutMillis;
        this.events=events==null?e->{}:events;this.failures=failures==null?e->{}:failures;
        this.retry=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"dsh-events-reconnect");t.setDaemon(true);return t;});connect();
    }
    private void connect(){if(!open.get())return;long attempt=++generation;clientId=null;
        try{String scheme="https".equalsIgnoreCase(base.getScheme())?"wss":"ws";URI uri=URI.create(scheme+"://"+base.getRawAuthority()+"/api/remote.mux");
            http.newWebSocketBuilder().connectTimeout(java.time.Duration.ofMillis(timeoutMillis)).header("Cookie",auth.cookie()).buildAsync(uri,new Listener(attempt))
                    .orTimeout(timeoutMillis,TimeUnit.MILLISECONDS).whenComplete((ws,error)->{if(error!=null&&open.get()&&generation==attempt)reconnect();});
        }catch(RuntimeException e){reconnect();}
    }
    private void reconnect(){clientId=null;socket=null;if(open.get())try{retry.schedule(this::connect,250,TimeUnit.MILLISECONDS);}catch(RejectedExecutionException ignored){}}
    private void fail(Throwable error){if(!open.compareAndSet(true,false))return;clientId=null;completion.completeExceptionally(error);retry.shutdownNow();try{failures.accept(error);}catch(RuntimeException ignored){}WebSocket ws=socket;if(ws!=null)ws.abort();}
    @Override public boolean isOpen(){return open.get();}
    @Override public String currentClientId(){return clientId;}
    long currentClientGeneration(){return clientGeneration;}
    @Override public CompletionStage<Void> completion(){return completion;}
    @Override public void close(){if(!open.compareAndSet(true,false))return;clientId=null;retry.shutdownNow();completion.cancel(false);WebSocket ws=socket;if(ws!=null){try{ws.sendClose(WebSocket.NORMAL_CLOSURE,"closed");}catch(RuntimeException ignored){ws.abort();}}}

    private final class Listener implements WebSocket.Listener {
        private final long myGeneration;private final String streamId=UUID.randomUUID().toString();private final StringBuilder fragments=new StringBuilder();private boolean ready;
        Listener(long generation){myGeneration=generation;}
        @Override public void onOpen(WebSocket ws){if(!open.get()||generation!=myGeneration){ws.abort();return;}socket=ws;
            ObjectNode frame=mapper.createObjectNode().put("type","open").put("streamId",streamId).put("endpoint","$events");frame.set("payload",mapper.createObjectNode().set("args",mapper.createObjectNode()));
            ws.sendText(frame.toString(),true);ws.request(1);}
        @Override public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){if(!open.get()||generation!=myGeneration)return CompletableFuture.completedFuture(null);
            if(fragments.length()+data.length()>1_048_576){fail(protocol("event frame exceeds limit"));return CompletableFuture.completedFuture(null);}fragments.append(data);
            if(last){String raw=fragments.toString();fragments.setLength(0);try{handle(mapper.readTree(raw));}catch(Throwable e){fail(e instanceof DshRc2Exception?e:protocol("malformed event frame"));}}
            if(open.get())ws.request(1);return CompletableFuture.completedFuture(null);}
        private void handle(JsonNode frame){if(frame==null||!frame.isObject()||!streamId.equals(frame.path("streamId").asText()))throw protocol("foreign event stream frame");
            String type=frame.path("type").asText();if(!"item".equals(type))throw protocol("unexpected event stream terminal frame");JsonNode value=frame.path("value");
            if(!value.isObject())throw protocol("event stream item is not an object");String kind=value.path("type").asText();
            if("ready".equals(kind)){if(ready)throw protocol("duplicate ready frame");String id=value.path("clientId").asText();if(id.isBlank())throw protocol("ready frame has no clientId");clientId=id;clientGeneration++;ready=true;return;}
            if(!ready||clientId==null)throw protocol("event arrived before ready");
            if("cancel".equals(kind)){String id=value.path("eventId").asText();if(id.isBlank())throw protocol("cancel frame has no eventId");events.accept(new DshInteractionCancelled("",clientId,clientGeneration,id,Instant.now()));return;}
            if(!"waterfall".equals(kind))return;
            String eventId=value.path("eventId").asText(),agentId=value.path("agentId").asText(),event=value.path("event").asText();JsonNode request=value.path("request");
            if(eventId.isBlank()||agentId.isBlank()||!request.isObject())throw protocol("waterfall correlation or request is malformed");
            Instant at=Instant.now();
            if("approval/request".equals(event)){String tool=request.path("toolName").asText();if(tool.isBlank())throw protocol("approval request has no toolName");events.accept(new DshApprovalRequest(agentId,clientId,clientGeneration,eventId,at,tool,optional(request,"callId"),optional(request,"reason")));}
            else if("user-questions/request".equals(event)){JsonNode questions=request.path("questions");if(!questions.isArray()||questions.isEmpty())throw protocol("question request has no questions");List<DshInteractionEvent.Question> parsed=new ArrayList<>();for(JsonNode q:questions){String id=q.path("id").asText(),prompt=q.path("question").asText();if(id.isBlank()||prompt.isBlank())throw protocol("question item is malformed");List<String> options=new ArrayList<>();JsonNode choices=q.path("options");if(!choices.isMissingNode()&&!choices.isArray())throw protocol("question options are malformed");if(choices.isArray())for(JsonNode choice:choices){String label=choice.path("label").asText();if(label.isBlank())throw protocol("question option is malformed");options.add(label);}parsed.add(new DshInteractionEvent.Question(id,prompt,options));}events.accept(new DshUserQuestionRequest(agentId,clientId,clientGeneration,eventId,at,parsed));}
        }
        @Override public CompletionStage<?> onClose(WebSocket ws,int code,String reason){if(open.get()&&generation==myGeneration)reconnect();return CompletableFuture.completedFuture(null);}
        @Override public void onError(WebSocket ws,Throwable error){if(open.get()&&generation==myGeneration)reconnect();}
        private DshRc2Exception protocol(String detail){return new DshRc2Exception("protocol/malformed-event", "DSH interaction event is malformed: "+detail);}
    }
    private static String optional(JsonNode node,String name){JsonNode value=node.get(name);return value==null||value.isNull()?null:value.isTextual()?value.asText():null;}
}
