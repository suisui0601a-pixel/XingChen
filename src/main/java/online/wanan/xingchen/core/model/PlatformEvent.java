package online.wanan.xingchen.core.model;

import java.time.Instant;
import java.util.Map;

public record PlatformEvent(Platform platform, ConversationIdentity conversation, ActorIdentity actor,
                            MessageIdentity message, String eventType, String actorPlatformRole,
                            String text, Instant timestamp, Map<String, Object> rawMetadata,EventDetails details) {
    public PlatformEvent(Platform platform, ConversationIdentity conversation, ActorIdentity actor,
                         MessageIdentity message, String text, Instant timestamp, Map<String,Object> rawMetadata) {
        this(platform,conversation,actor,message,"message","member",text,timestamp,rawMetadata,EventDetails.empty());
    }
    public PlatformEvent(Platform platform,ConversationIdentity conversation,ActorIdentity actor,MessageIdentity message,String eventType,String actorPlatformRole,String text,Instant timestamp,Map<String,Object> rawMetadata){this(platform,conversation,actor,message,eventType,actorPlatformRole,text,timestamp,rawMetadata,EventDetails.empty());}
    public PlatformEvent {
        if (platform == null || conversation == null || actor == null || message == null || timestamp == null) throw new IllegalArgumentException("event identity and timestamp are required");
        rawMetadata = rawMetadata == null ? Map.of() : Map.copyOf(rawMetadata);
        eventType = eventType == null ? "message" : eventType;
        actorPlatformRole = actorPlatformRole == null ? "member" : actorPlatformRole;
        details=details==null?EventDetails.empty():details;
        text = text == null ? "" : text;
    }
}
