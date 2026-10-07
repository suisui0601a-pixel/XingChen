package online.wanan.xingchen.adapter.dsh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import online.wanan.xingchen.core.agent.CancellationToken;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Adapter pinned to the HTTP/Cookie and remote.mux contract of DSH 0.1.7-rc.2. */
public final class DshRc2Adapter implements DshSessionOperations,AutoCloseable {
    private final DshRc2Configuration configuration;private final ObjectMapper mapper;private final HttpClient http;private final DshRc2Transport transport;private final DshRc2FollowClient follow;private final BooleanSupplier enabled;private volatile DshRc2EventClient interactionStream;private volatile String boundInteractionClientId;private volatile long boundInteractionGeneration;
    public DshRc2Adapter(DshRc2Configuration configuration,ObjectMapper mapper){this(configuration,mapper,()->true);}
    public DshRc2Adapter(DshRc2Configuration configuration,ObjectMapper mapper,BooleanSupplier enabled){this(configuration,mapper,enabled,DshHttpDispatcher.javaHttpClient());}
    public DshRc2Adapter(DshRc2Configuration configuration,ObjectMapper mapper,BooleanSupplier enabled,DshHttpDispatcher dispatcher){
        this.configuration=Objects.requireNonNull(configuration);this.mapper=Objects.requireNonNull(mapper);
        this.enabled=Objects.requireNonNull(enabled);
        Objects.requireNonNull(dispatcher);
        this.http=HttpClient.newBuilder().connectTimeout(configuration.requestTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        var auth=new DshRc2AuthClient(http,configuration.baseUri(),configuration.launchToken(),configuration.requestTimeout());
        this.transport=new DshRc2Transport(configuration.baseUri(),http,mapper,auth,configuration.requestTimeout(),dispatcher);
        this.follow=new DshRc2FollowClient(http,mapper,auth,configuration.baseUri(),(int)configuration.requestTimeout().toMillis());
    }
    @Override public boolean isEnabled(){return enabled.getAsBoolean();}
    @Override public String findOrCreateWorkspace(String existingDirectory){
        requireEnabled();
        Path path=Path.of(Objects.requireNonNull(existingDirectory)).toAbsolutePath().normalize();if(!Files.isDirectory(path))throw new IllegalArgumentException("DSH workspace path must already be a directory");
        ObjectNode request=mapper.createObjectNode().put("path",path.toString());JsonNode value=transport.call("workspace/create",request,null);String id=value.path("workspace").path("id").asText();if(id.isBlank())throw malformedValue();return id;
    }
    @Override public String createSession(String workspaceId,String mode,String preset,String model){
        requireEnabled();
        if(workspaceId==null||workspaceId.isBlank()||preset==null||preset.isBlank())throw new IllegalArgumentException("DSH workspace and preset are required");
        ObjectNode request=mapper.createObjectNode().put("workspaceId",workspaceId).put("agentPreset",preset);JsonNode value=transport.call("session/create",request,null);String sessionId=value.path("sessionId").asText();if(sessionId.isBlank())throw malformedValue();
        if(model!=null&&!model.isBlank())selectModel(sessionId,model);return sessionId;
    }
    @Override public void prompt(String sessionId,String text){prompt(sessionId,text,new CancellationToken());}
    public void prompt(String sessionId,String text,CancellationToken cancellation){
        requireEnabled();
        if(sessionId==null||sessionId.isBlank()||text==null||text.isBlank())throw new IllegalArgumentException("DSH session and prompt text are required");
        ObjectNode request=mapper.createObjectNode().put("requestId",java.util.UUID.randomUUID().toString()).put("sessionId",sessionId).put("mode","queue");request.putArray("content").addObject().put("type","text").put("text",text);JsonNode value=transport.call("session/prompt",request,cancellation);if(!value.path("accepted").asBoolean(false))throw malformedValue();
    }
    @Override public void stop(String sessionId){stop(sessionId,new CancellationToken());}
    public void stop(String sessionId,CancellationToken cancellation){
        requireEnabled();
        if(sessionId==null||sessionId.isBlank())throw new IllegalArgumentException("DSH session id is required");RuntimeException failure=null;
        try{for(String itemId:follow.readQueuedItemIds(sessionId,cancellation)){ObjectNode request=mapper.createObjectNode().put("sessionId",sessionId).put("itemId",itemId);request.putObject("action").put("kind","remove");try{transport.call("session/updateQueue",request,cancellation);}catch(DshRc2Exception e){if(!"session/queue-item-not-found".equals(e.code()))throw e;}}}catch(RuntimeException e){failure=e;}
        try{ObjectNode request=mapper.createObjectNode().put("sessionId",sessionId);transport.call("session/cancel",request,cancellation);}catch(DshRc2Exception e){if(!"session/not-found".equals(e.code()))failure=failure==null?e:failure;}catch(RuntimeException e){failure=failure==null?e:failure;}
        if(failure!=null)throw failure;
    }
    @Override public void archive(String sessionId){requireEnabled();ObjectNode request=mapper.createObjectNode().put("sessionId",require(sessionId));transport.call("workspace/archiveSession",request,null);}
    @Override public void selectModel(String sessionId,String model){
        requireEnabled();
        if(model==null||model.isBlank())throw new IllegalArgumentException("DSH model id is required");ObjectNode request=mapper.createObjectNode().put("sessionId",require(sessionId)).put("provider",configuration.modelProvider()).put("model",model);transport.call("session/selectModel",request,null);
    }
    @Override public DshFollowSubscription follow(String sessionId,Consumer<DshFollowEvent> events,Consumer<Throwable> failure){requireEnabled();return follow.follow(sessionId,events,failure);}
    @Override public synchronized DshInteractionSubscription interactionEvents(Consumer<DshInteractionEvent> events,Consumer<Throwable> failure){requireEnabled();if(interactionStream!=null&&interactionStream.isOpen())throw new IllegalStateException("DSH interaction stream is already active");interactionStream=new DshRc2EventClient(http,mapper,new DshRc2AuthClient(http,configuration.baseUri(),configuration.launchToken(),configuration.requestTimeout()),configuration.baseUri(),(int)configuration.requestTimeout().toMillis(),events,failure);return interactionStream;}
    @Override public void respondToInteraction(String clientId,String eventId,JsonNode outcome){requireEnabled();DshRc2EventClient stream=interactionStream;if(stream==null||!stream.isOpen()||!Objects.equals(clientId,stream.currentClientId()))throw new DshRc2Exception("interaction/stale-client","DSH interaction belongs to a stale client generation");transport.callEventResult(clientId,eventId,outcome);}
    @Override public synchronized void bindInteractionGeneration(String clientId,long generation){DshRc2EventClient stream=interactionStream;if(stream==null||!stream.isOpen()||!Objects.equals(clientId,stream.currentClientId())||generation<1)throw new DshRc2Exception("interaction/stale-client","DSH interaction belongs to a stale client generation");boundInteractionClientId=clientId;boundInteractionGeneration=generation;}
    @Override public void respondToInteraction(String clientId,long generation,String eventId,JsonNode outcome){requireEnabled();DshRc2EventClient stream=interactionStream;if(stream==null||!stream.isOpen()||!Objects.equals(clientId,stream.currentClientId())||!Objects.equals(clientId,boundInteractionClientId)||generation!=boundInteractionGeneration)throw new DshRc2Exception("interaction/stale-client","DSH interaction belongs to a stale client generation");transport.callEventResult(clientId,eventId,outcome);}
    /** Presets are selected by session/create in RC.2; there is no selectPreset Remote method. */
    public void selectPreset(String sessionId,String preset){throw new UnsupportedOperationException("DSH 0.1.7-rc.2 selects presets only during session/create");}
    private static String require(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("DSH session id is required");return value;}
    private void requireEnabled(){if(!enabled.getAsBoolean())throw new DshRc2Exception("integration/disabled","DSH integration is disabled");}
    private static DshRc2Exception malformedValue(){return new DshRc2Exception("protocol/malformed-value","DSH returned a malformed operation value");}
    @Override public void close(){follow.close();DshRc2EventClient stream=interactionStream;if(stream!=null)stream.close();}
}
