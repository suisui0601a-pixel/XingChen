package online.wanan.xingchen.adapter.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.model.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Converts OneBot v11 message/notice payloads to transport-neutral events; never infers identity from names. */
@Component
public final class OneBotEventNormalizer {
    private final ObjectMapper mapper;
    public OneBotEventNormalizer(ObjectMapper mapper) { this.mapper=mapper; }
    public PlatformEvent normalize(String json,String ownerId) { return normalize(read(json),ownerId==null?Set.of():Set.of(ownerId)); }
    public PlatformEvent normalize(String json,Collection<String> ownerIds) { return normalize(read(json),ownerIds); }
    public PlatformEvent normalize(JsonNode root,Collection<String> ownerIds) {
        String post=text(root,"post_type","message"), messageType=text(root,"message_type","");
        JsonNode sender=root.path("sender");
        String userId=first(text(sender,"user_id",""),text(root,"user_id",""),text(root,"operator_id",""));
        String selfId=text(root,"self_id","");
        boolean self=!userId.isBlank() && userId.equals(selfId);
        boolean owner=!self && ownerIds.contains(userId);
        String role=text(sender,"role","member").toLowerCase(Locale.ROOT);
        boolean group=messageType.equals("group")||(messageType.isBlank()&&root.has("group_id"));
        String conversationId=group?text(root,"group_id",""):userId;
        ConversationType type=group?ConversationType.GROUP:ConversationType.PRIVATE;
        if(conversationId.isBlank()) throw new IllegalArgumentException("OneBot event has no conversation id");
        long epoch=root.path("time").asLong(0);
        Instant timestamp=epoch>0?Instant.ofEpochSecond(epoch):Instant.EPOCH;
        String subType=text(root,"sub_type","");
        String eventType=post.equals("notice")?(subType.toLowerCase(Locale.ROOT).contains("poke")?"poke":"notice:"+subType):"message";
        String messageId=text(root,"message_id","");
        if(messageId.isBlank()) messageId=eventType+":"+text(root,"notice_type","notice")+":"+text(root,"sub_type","")+":"+userId+":"+epoch+":"+text(root,"target_id","");
        JsonNode segments=root.path("message");
        if(!segments.isArray()) segments=root.path("message_chain");
        StringBuilder body=new StringBuilder(); List<Map<String,Object>> metadataSegments=new ArrayList<>();
        List<String> mentions=new ArrayList<>(), images=new ArrayList<>(); List<Object> forwards=new ArrayList<>(); String replyTo=null;ReplyReference replyReference=null;
        if(segments.isArray()) for(JsonNode seg:segments) {
            String kind=text(seg,"type",""); JsonNode data=seg.path("data");
            Map<String,Object> item=new LinkedHashMap<>(); item.put("type",kind); item.put("data",mapper.convertValue(data,Map.class)); metadataSegments.add(item);
            switch(kind) {
                case "text" -> body.append(text(data,"text",""));
                case "reply" -> {replyTo=text(data,"id","");String quotedUser=text(data,"user_id","");replyReference=new ReplyReference(replyTo,quotedUser,text(data,"nickname",""),!quotedUser.isBlank()&&quotedUser.equals(selfId));}
                case "at" -> mentions.add(text(data,"qq",""));
                case "image" -> images.add(text(data,"file",text(data,"url","")));
                case "forward" -> forwards.add(mapper.convertValue(data,Map.class));
                case "poke" -> eventType="poke";
                default -> { }
            }
        } else body.append(text(root,"raw_message",text(root,"message","")));
        if(post.equals("notice")) body.setLength(0);
        Map<String,Object> raw=new LinkedHashMap<>(); raw.put("postType",post); raw.put("noticeType",text(root,"notice_type","")); raw.put("subType",text(root,"sub_type",""));
        if(root.has("message_seq"))raw.put("message_seq",root.path("message_seq").asText());else if(root.has("sequence"))raw.put("sequence",root.path("sequence").asText());else if(root.has("event_sequence"))raw.put("event_sequence",root.path("event_sequence").asText());
        raw.put("senderCard",text(sender,"card","")); raw.put("senderRole",role);
        raw.put("segments",metadataSegments); raw.put("mentions",mentions); raw.put("images",images); raw.put("forwards",forwards);
        if(root.has("target_id")) raw.put("pokeTargetId",text(root,"target_id",""));
        if(root.has("operator_id")) raw.put("operatorId",text(root,"operator_id",""));
        Map<String,Object> frozen=Map.copyOf(raw);
        return new PlatformEvent(Platform.QQ,new ConversationIdentity(Platform.QQ,type,conversationId),
                new ActorIdentity(Platform.QQ,userId,text(sender,"nickname",""),self,owner),
                new MessageIdentity(messageId,replyTo==null||replyTo.isBlank()?null:replyTo),eventType,role,body.toString(),timestamp,frozen,
                new EventDetails(mentions,replyReference,root.has("target_id")?text(root,"target_id",""):null));
    }
    private JsonNode read(String value) { try { return mapper.readTree(value); } catch(IOException e) { throw new IllegalArgumentException("invalid OneBot JSON fixture",e); } }
    private static String text(JsonNode n,String key,String fallback) { JsonNode v=n.path(key); return v.isMissingNode()||v.isNull()?fallback:v.asText(fallback); }
    private static String first(String... values) { for(String v:values) if(v!=null&&!v.isBlank()) return v; return ""; }
}
