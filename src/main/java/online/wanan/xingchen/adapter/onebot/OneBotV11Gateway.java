package online.wanan.xingchen.adapter.onebot;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import online.wanan.xingchen.core.model.PlatformEvent;
import online.wanan.xingchen.core.model.ConversationIdentity;
import online.wanan.xingchen.core.model.ConversationType;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** OneBot v11 forward-WebSocket client + HTTP API adapter. Transport JSON is normalized at this boundary. */
public final class OneBotV11Gateway implements OneBotGateway,AutoCloseable {
    private static final int MAX_EVENT_TEXT_CHARS = 1_048_576;
    private volatile OneBotV11Configuration config;private final ObjectMapper mapper;private final OneBotEventNormalizer normalizer;private volatile Map<String,String> env;private final HttpClient http;private volatile BooleanSupplier enabled;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"xingchen-onebot-reconnect");t.setDaemon(true);return t;});
    private final Deque<PlatformEvent> events=new ArrayDeque<>();private final Map<String,String> cursors=new ConcurrentHashMap<>();private final AtomicInteger reconnectCount=new AtomicInteger();private final AtomicBoolean manuallyClosed=new AtomicBoolean(true);
    private final java.util.concurrent.locks.ReentrantLock eventLock=new java.util.concurrent.locks.ReentrantLock();private final java.util.concurrent.locks.Condition eventArrived=eventLock.newCondition();
    private volatile WebSocket socket;private volatile Consumer<PlatformEvent> eventListener=e->{};private volatile boolean connected;private volatile String detail="disconnected";private volatile Instant lastHeartbeat;private volatile ScheduledFuture<?> heartbeatTask;
    public OneBotV11Gateway(OneBotV11Configuration config,ObjectMapper mapper,OneBotEventNormalizer normalizer){this(config,mapper,normalizer,System.getenv(),()->true);}
    public OneBotV11Gateway(OneBotV11Configuration config,ObjectMapper mapper,OneBotEventNormalizer normalizer,Map<String,String> environment){this(config,mapper,normalizer,environment,()->true);}
    public OneBotV11Gateway(OneBotV11Configuration config,ObjectMapper mapper,OneBotEventNormalizer normalizer,Map<String,String> environment,BooleanSupplier enabled){this.config=Objects.requireNonNull(config);this.mapper=Objects.requireNonNull(mapper);this.normalizer=Objects.requireNonNull(normalizer);this.env=Map.copyOf(environment);this.enabled=Objects.requireNonNull(enabled);validateEndpoint(config.httpBaseUri());validateEndpoint(config.websocketUri());http=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(config.connectTimeoutMillis())).followRedirects(HttpClient.Redirect.NEVER).build();}
    public synchronized GatewayStatus reconfigure(OneBotV11Configuration next,Map<String,String> environment,BooleanSupplier enabledSwitch){Objects.requireNonNull(next);validateEndpoint(next,next.httpBaseUri());validateEndpoint(next,next.websocketUri());disconnect();config=next;env=Map.copyOf(environment);enabled=Objects.requireNonNull(enabledSwitch);return enabled.getAsBoolean()?connect():status();}
    @Override public synchronized GatewayStatus connect(){if(!enabled.getAsBoolean()){manuallyClosed.set(true);connected=false;detail="disabled";return status();}manuallyClosed.set(false);try{connectNow();}catch(RuntimeException e){detail="OneBot connection unavailable";connected=false;scheduleReconnect();}return status();}
    private void connectNow(){if(!enabled.getAsBoolean())throw new IllegalStateException("OneBot integration is disabled");String token=token(config.websocketTokenEnvironmentVariable());WebSocket.Builder ws=http.newWebSocketBuilder().connectTimeout(Duration.ofMillis(config.connectTimeoutMillis()));if(!token.isBlank())ws.header("Authorization","Bearer "+token);try{socket=ws.buildAsync(config.websocketUri(),new Listener()).get(config.connectTimeoutMillis(),TimeUnit.MILLISECONDS);connected=true;detail="connected";lastHeartbeat=Instant.now();startHeartbeatMonitor();}catch(Exception e){connected=false;throw new IllegalStateException("OneBot WebSocket connection failed",e);}}
    private synchronized void startHeartbeatMonitor(){ScheduledFuture<?> old=heartbeatTask;if(old!=null)old.cancel(false);long period=Math.max(10,config.heartbeatTimeoutMillis()/3);heartbeatTask=scheduler.scheduleWithFixedDelay(()->{Instant beat=lastHeartbeat;if(connected&&beat!=null&&Duration.between(beat,Instant.now()).toMillis()>config.heartbeatTimeoutMillis()){connected=false;detail="heartbeat timeout";WebSocket current=socket;if(current!=null)current.abort();scheduleReconnect();}},period,period,TimeUnit.MILLISECONDS);}
    @Override public synchronized GatewayStatus disconnect(){manuallyClosed.set(true);connected=false;ScheduledFuture<?> task=reconnectTask;if(task!=null)task.cancel(false);ScheduledFuture<?> heartbeat=heartbeatTask;if(heartbeat!=null)heartbeat.cancel(false);WebSocket prior=socket;socket=null;if(prior!=null)prior.sendClose(WebSocket.NORMAL_CLOSURE,"client disconnect");detail="disconnected";return status();}
    private volatile ScheduledFuture<?> reconnectTask;
    @Override public GatewayStatus status(){return new GatewayStatus(connected,config.selfId(),detail,lastHeartbeat,reconnectCount.get());}
    @Override public Optional<OneBotAccountInfo> getLoginInfo(){try{JsonNode data=post("get_login_info",Map.of()).path("data");String id=data.path("user_id").asText("");if(id.isBlank())return Optional.empty();return Optional.of(new OneBotAccountInfo(id,data.path("nickname").asText("")));}catch(RuntimeException e){return Optional.empty();}}
    @Override public String probeHttpConnection(){try{JsonNode data=post("get_login_info",Map.of()).path("data");return data.isMissingNode()||data.isNull()?"UNAVAILABLE":"CONNECTED";}catch(OneBotApiException e){return e.statusCode()==401||e.statusCode()==403?"CREDENTIAL_REJECTED":e.statusCode()==0?"UNAVAILABLE":"SERVICE_ERROR";}catch(RuntimeException e){return "UNAVAILABLE";}}
    @Override public GatewayStatus reconnect(){disconnect();return connect();}
    @Override public void setEventListener(Consumer<PlatformEvent> listener){eventListener=listener==null?e->{}:listener;}
    @Override public SendReceipt sendPrivateMessage(String userId,String text){return send("send_private_msg",Map.of("user_id",userId,"message",List.of(textSegment(text))),"private",userId,text);}
    @Override public SendReceipt sendGroupMessage(String groupId,String text){return send("send_group_msg",Map.of("group_id",groupId,"message",List.of(textSegment(text))),"group",groupId,text);}
    @Override public SendReceipt replyMessage(String messageId,String text){if(messageId==null||!messageId.matches("[A-Za-z0-9_-]{1,80}"))return new SendReceipt(false,"reply",Objects.toString(messageId,""),"invalid message id");return send("send_msg",Map.of("message_type","auto","message",List.of(Map.of("type","reply","data",Map.of("id",messageId)),textSegment(text))),"reply",messageId,text);}
    private static Map<String,Object> textSegment(String text){return Map.of("type","text","data",Map.of("text",Objects.requireNonNullElse(text,"")));}
    @Override public SendReceipt sendPoke(String groupId,String userId){return send("send_poke",Map.of("group_id",groupId,"user_id",userId),"poke",groupId+":"+userId,"");}
    @Override public SendReceipt sendImage(String target,String kind,String localAssetPath){
        if(localAssetPath==null||localAssetPath.isBlank())return new SendReceipt(false,"image",target,"image asset unavailable");
        try{return sendImageBytes(target,kind,readBoundedImage(java.nio.file.Path.of(localAssetPath)));}
        catch(IOException|IllegalArgumentException e){return new SendReceipt(false,"image",target,"image asset unavailable");}
    }
    public static byte[] readBoundedImage(java.nio.file.Path path)throws IOException{
        var absolute=path.toAbsolutePath().normalize();
        for(var p=absolute;p!=null;p=p.getParent())if(java.nio.file.Files.isSymbolicLink(p))throw new IOException("symbolic link image rejected");
        if(!absolute.toRealPath().equals(absolute))throw new IOException("redirected image path rejected");
        try(var input=java.nio.file.Files.newInputStream(absolute,java.nio.file.LinkOption.NOFOLLOW_LINKS)){
            byte[] bytes=input.readNBytes(10*1024*1024+1);
            if(bytes.length==0||bytes.length>10*1024*1024)throw new IOException("invalid image size");
            return bytes;
        }
    }
    @Override public SendReceipt sendImageBytes(String target,String kind,byte[] bytes){
        if(!"private".equals(kind)&&!"group".equals(kind))return new SendReceipt(false,"image",target,"invalid target kind");
        if(bytes==null||bytes.length==0||bytes.length>10*1024*1024)return new SendReceipt(false,"image",target,"invalid image size");
        String action=kind.equals("private")?"send_private_msg":"send_group_msg",key=kind.equals("private")?"user_id":"group_id";
        return send(action,Map.of(key,target,"message",List.of(Map.of("type","image","data",Map.of("file","base64://"+Base64.getEncoder().encodeToString(bytes))))),"image-"+kind,target,"sticker");
    }
    private SendReceipt send(String action,Map<String,Object> params,String operation,String target,String content){try{JsonNode root=post(action,params);String id=root.path("data").path("message_id").asText("");return new SendReceipt(true,operation,id.isBlank()?target:id,content);}catch(RuntimeException e){return failedReceipt(operation,target,"OneBot action failed",e);}}
    private SendReceipt failedReceipt(String operation,String target,String message,RuntimeException cause){boolean unknown=!(cause instanceof OneBotApiException api)||api.statusCode()==0||api.statusCode()>=500;return new SendReceipt(false,operation,target,message,unknown?DeliveryStatus.UNKNOWN:DeliveryStatus.REJECTED);}
    /** Legacy untyped API is group-only; domain callers use the scoped overloads. */
    @Override public List<PlatformEvent> getRecentMessages(String conversationId,int limit){return getRecentMessages(new ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,ConversationType.GROUP,conversationId),limit);}
    @Override public List<PlatformEvent> getUnreadMessages(String conversationId,int limit){if(limit<=0)return List.of();synchronized(events){var identity=new ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,ConversationType.GROUP,conversationId);List<PlatformEvent> unread=getUnreadMessages(identity,cursors.get(conversationId),limit);if(!unread.isEmpty())cursors.put(conversationId,unread.getLast().message().platformMessageId());return unread;}}
    @Override public List<PlatformEvent> getRecentMessages(ConversationIdentity conversation,int limit){
        if(limit<=0)return List.of();
        List<PlatformEvent> matching; synchronized(events){matching=events.stream().filter(e->e.conversation().equals(conversation)).toList();}
        if(matching.isEmpty()&&conversation.type()==ConversationType.GROUP)return history(conversation.platformConversationId(),limit);
        return matching.stream().skip(Math.max(0,matching.size()-limit)).toList();
    }
    @Override public List<PlatformEvent> getUnreadMessages(ConversationIdentity conversation,String after,int limit){
        synchronized(events){return OneBotGateway.afterCursor(events.stream().filter(e->e.conversation().equals(conversation)).toList(),after,limit);}
    }
    @Override public List<PlatformEvent> waitForMessages(ConversationIdentity conversation,String after,Duration timeout){
        long nanos=Math.min(Duration.ofSeconds(30).toNanos(),Math.max(0,timeout.toNanos()));eventLock.lock();
        try{while(true){var current=getUnreadMessages(conversation,after,20);if(!current.isEmpty()||nanos<=0)return current;
            try{nanos=eventArrived.awaitNanos(nanos);}catch(InterruptedException e){Thread.currentThread().interrupt();return List.of();}
        }}finally{eventLock.unlock();}
    }
    @Override public List<PlatformEvent> waitForMessages(String conversationId,Duration timeout){long nanos=Math.min(Duration.ofSeconds(30).toNanos(),Math.max(0,timeout.toNanos()));eventLock.lock();try{while(true){var current=getUnreadMessages(conversationId,20);if(!current.isEmpty())return current;if(nanos<=0)return List.of();try{nanos=eventArrived.awaitNanos(nanos);}catch(InterruptedException e){Thread.currentThread().interrupt();return List.of();}}}finally{eventLock.unlock();}}
    @Override public Optional<GatewayMember> getGroupMember(String groupId,String userId){JsonNode data=post("get_group_member_info",Map.of("group_id",groupId,"user_id",userId,"no_cache",false)).path("data");if(data.isMissingNode()||data.isNull())return Optional.empty();return Optional.of(member(data));}
    @Override public List<GatewayMember> getGroupMembers(String groupId){JsonNode data=post("get_group_member_list",Map.of("group_id",groupId,"no_cache",false)).path("data");if(!data.isArray())return List.of();List<GatewayMember> list=new ArrayList<>();data.forEach(n->list.add(member(n)));return List.copyOf(list);}
    @Override public Optional<PlatformEvent> getMessage(String messageId){JsonNode data=post("get_msg",Map.of("message_id",messageId)).path("data");if(data.isMissingNode()||data.isNull())return Optional.empty();ObjectNode event=((ObjectNode)data.deepCopy());event.put("post_type","message");event.put("message_id",messageId);event.put("self_id",config.selfId());return Optional.of(normalizer.normalize(event,Set.of()));}
    private List<PlatformEvent> history(String conversationId,int limit){try{JsonNode data=post("get_group_msg_history",Map.of("group_id",conversationId,"count",Math.min(limit,100))).path("data").path("messages");if(!data.isArray())return List.of();List<PlatformEvent> result=new ArrayList<>();data.forEach(n->{try{ObjectNode e=((ObjectNode)n.deepCopy());e.put("post_type","message");e.put("message_type","group");e.put("group_id",conversationId);e.put("self_id",config.selfId());result.add(normalizer.normalize(e,Set.of()));}catch(RuntimeException ignored){}});return List.copyOf(result);}catch(RuntimeException e){return List.of();}}
    private JsonNode post(String action,Map<String,Object> params){if(!enabled.getAsBoolean())throw new OneBotApiException(0,"OneBot integration is disabled");String url=config.httpBaseUri().toString().replaceAll("/+$","")+"/"+action;String token=token(config.httpTokenEnvironmentVariable());HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url)).timeout(config.requestTimeout()).header("Content-Type","application/json");if(!token.isBlank())b.header("Authorization","Bearer "+token);try{String body=mapper.writeValueAsString(params);HttpResponse<String> response=http.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()==401||response.statusCode()==403)throw new OneBotApiException(response.statusCode(),"OneBot authentication rejected");if(response.statusCode()<200||response.statusCode()>=300)throw new OneBotApiException(response.statusCode(),"OneBot HTTP request failed");JsonNode root=mapper.readTree(response.body());if(root.path("retcode").asInt(0)!=0)throw new OneBotApiException(200,"OneBot action returned failure");return root;}catch(OneBotApiException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new OneBotApiException(0,"OneBot request interrupted");}catch(Exception e){throw new OneBotApiException(0,"OneBot response unavailable");}}
    private void accept(String json){try{PlatformEvent event=normalizer.normalize(json,Set.of());if(event.details().reply()!=null&&event.details().reply().quotedUserId().isBlank()){try{Optional<PlatformEvent> quoted=getMessage(event.details().reply().messageId());if(quoted.isPresent()&&quoted.get().conversation().equals(event.conversation())){var q=quoted.get();var reference=new online.wanan.xingchen.core.model.ReplyReference(event.details().reply().messageId(),q.actor().platformUserId(),q.actor().displayName(),q.actor().self(),q.text());event=new PlatformEvent(event.platform(),event.conversation(),event.actor(),event.message(),event.eventType(),event.actorPlatformRole(),event.text(),event.timestamp(),event.rawMetadata(),new online.wanan.xingchen.core.model.EventDetails(event.details().mentionIds(),reference,event.details().pokeTargetId()));}}catch(RuntimeException ignored){}}synchronized(events){events.addLast(event);while(events.size()>config.eventBufferSize())events.removeFirst();}lastHeartbeat=Instant.now();eventLock.lock();try{eventArrived.signalAll();}finally{eventLock.unlock();}eventListener.accept(event);}catch(RuntimeException e){detail="received malformed OneBot event";}}
    private void scheduleReconnect(){if(manuallyClosed.get()||!enabled.getAsBoolean()||scheduler.isShutdown())return;int attempt=reconnectCount.incrementAndGet();long wait=Math.min(config.maxReconnectMillis(),(long)config.reconnectBaseMillis()*(1L<<Math.min(20,attempt-1)));ScheduledFuture<?> previous=reconnectTask;if(previous!=null&&!previous.isDone())return;reconnectTask=scheduler.schedule(()->{reconnectTask=null;if(manuallyClosed.get()||!enabled.getAsBoolean())return;try{connectNow();reconnectCount.set(0);}catch(RuntimeException e){connected=false;detail="reconnect failed";scheduleReconnect();}},wait,TimeUnit.MILLISECONDS);}
    private String token(String environmentVariable){return Objects.toString(env.get(environmentVariable),"");}
    private void validateEndpoint(URI uri){validateEndpoint(config,uri);}
    private void validateEndpoint(OneBotV11Configuration forConfig,URI uri){if(uri==null||uri.getHost()==null)throw new IllegalArgumentException("OneBot endpoint must be an absolute URI");if(forConfig.allowNonLoopback())return;try{for(InetAddress address:InetAddress.getAllByName(uri.getHost()))if(!address.isLoopbackAddress())throw new IllegalArgumentException("non-loopback OneBot endpoint requires explicit opt-in");}catch(UnknownHostException e){throw new IllegalArgumentException("OneBot endpoint host could not be resolved",e);}}
    private static GatewayMember member(JsonNode data){return new GatewayMember(data.path("user_id").asText(""),data.path("nickname").asText(""),data.path("card").asText(""),data.path("role").asText("member"));}
    @Override public void close(){disconnect();scheduler.shutdownNow();}
    private final class Listener implements WebSocket.Listener {
        private final StringBuilder fragments=new StringBuilder();
        @Override public void onOpen(WebSocket webSocket){socket=webSocket;connected=true;detail="connected";lastHeartbeat=Instant.now();webSocket.request(1);}
        @Override public CompletionStage<?> onText(WebSocket webSocket,CharSequence data,boolean last){if((long)fragments.length()+data.length()>MAX_EVENT_TEXT_CHARS){fragments.setLength(0);connected=false;detail="WebSocket event exceeds size limit";webSocket.sendClose(1009,"event too large");scheduleReconnect();return CompletableFuture.completedFuture(null);}fragments.append(data);if(last){accept(fragments.toString());fragments.setLength(0);}webSocket.request(1);return CompletableFuture.completedFuture(null);}
        @Override public CompletionStage<?> onPing(WebSocket webSocket,ByteBuffer message){lastHeartbeat=Instant.now();webSocket.sendPong(message);webSocket.request(1);return CompletableFuture.completedFuture(null);}
        @Override public CompletionStage<?> onClose(WebSocket webSocket,int statusCode,String reason){connected=false;detail="WebSocket closed";scheduleReconnect();return CompletableFuture.completedFuture(null);}
        @Override public void onError(WebSocket webSocket,Throwable error){connected=false;detail="WebSocket error";scheduleReconnect();}
    }
    public static final class OneBotApiException extends RuntimeException {private final int statusCode;public OneBotApiException(int statusCode,String message){super(message);this.statusCode=statusCode;}public int statusCode(){return statusCode;}}
}
