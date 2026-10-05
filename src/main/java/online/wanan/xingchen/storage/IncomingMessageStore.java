package online.wanan.xingchen.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.identity.ResolvedActorContext;
import online.wanan.xingchen.core.model.PlatformEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@Repository
public class IncomingMessageStore {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final DurableTurnExecutionStore turns;
    public IncomingMessageStore(JdbcTemplate jdbc,ObjectMapper mapper,DurableTurnExecutionStore turns){this.jdbc=jdbc;this.mapper=mapper;this.turns=turns;}
    /** Persists identity snapshots and event atomically; duplicate delivery changes no stored state. */
    @Transactional
    public boolean persist(PlatformEvent event,ResolvedActorContext resolved){return persistOrdered(event,resolved).status()==EventPersistResult.Status.NEW_IN_ORDER;}
    @Transactional
    public EventPersistResult persistOrdered(PlatformEvent event,ResolvedActorContext resolved){
        int exists=jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE platform=? AND platform_message_id=?",Integer.class,event.platform().name(),event.message().platformMessageId());
        if(exists>0)return new EventPersistResult(EventPersistResult.Status.DUPLICATE);
        var p=resolved.person(); var c=resolved.conversation(); var m=resolved.membership();
        EventPersistResult.Status order=classify(event,c.id().toString());
        String at=event.timestamp().toString();
        jdbc.update("INSERT INTO persons(id,platform,platform_user_id,is_self,is_owner,created_at,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(platform,platform_user_id) DO UPDATE SET is_self=excluded.is_self,is_owner=excluded.is_owner,updated_at=MAX(persons.updated_at,excluded.updated_at)",p.id().toString(),p.platform().name(),p.platformUserId(),bool(p.self()),bool(p.owner()),at,at);
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?) ON CONFLICT(platform,type,platform_conversation_id) DO NOTHING",c.id().toString(),c.identity().platform().name(),c.identity().type().name(),c.identity().platformConversationId(),at);
        jdbc.update("INSERT INTO memberships(id,person_id,conversation_id,current_display_name,card,platform_role,first_seen_at,last_seen_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(person_id,conversation_id) DO UPDATE SET current_display_name=excluded.current_display_name,card=excluded.card,platform_role=excluded.platform_role,last_seen_at=MAX(memberships.last_seen_at,excluded.last_seen_at)",m.id().toString(),p.id().toString(),c.id().toString(),m.currentDisplayName(),m.card(),m.platformRole().name(),m.firstSeenAt().toString(),m.lastSeenAt().toString());
        saveAlias(p.id().toString(),m.currentDisplayName(),c.id().toString(),at); saveAlias(p.id().toString(),m.card(),c.id().toString(),at);
        String metadata; try{metadata=mapper.writeValueAsString(event.rawMetadata());}catch(JsonProcessingException e){throw new IllegalStateException("could not serialize OneBot metadata",e);}
        int inserted=jdbc.update("INSERT OR IGNORE INTO messages(id,platform,conversation_id,actor_person_id,platform_message_id,reply_to_message_id,text,is_self,is_owner,created_at,raw_metadata_json,message_type) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",event.platform()+":"+event.message().platformMessageId(),event.platform().name(),c.id().toString(),p.id().toString(),event.message().platformMessageId(),event.message().replyToMessageId(),event.text(),bool(resolved.flags().self()),bool(resolved.flags().owner()),at,metadata,event.eventType());
        if(inserted==0)return new EventPersistResult(EventPersistResult.Status.DUPLICATE);
        if(order==EventPersistResult.Status.NEW_IN_ORDER){Long sequence=sequence(event);jdbc.update("INSERT INTO event_order_state(platform,conversation_id,last_timestamp,last_message_id,last_sequence,updated_at) VALUES(?,?,?,?,?,?) ON CONFLICT(platform,conversation_id) DO UPDATE SET last_timestamp=excluded.last_timestamp,last_message_id=excluded.last_message_id,last_sequence=excluded.last_sequence,updated_at=excluded.updated_at",event.platform().name(),c.id().toString(),at,event.message().platformMessageId(),sequence,at);}
        if(order==EventPersistResult.Status.NEW_IN_ORDER){
            String key=executionKey(event,c.id().toString());
            jdbc.update("INSERT OR IGNORE INTO inbound_turn_executions(execution_key,platform,conversation_id,incoming_event_id,turn_id,generation,status,event_json,created_at,updated_at) VALUES(?,?,?,?,?,NULL,'PERSISTED',?,?,?)",
                    key,event.platform().name(),c.id().toString(),event.message().platformMessageId(),stableTurnId(key),serialize(event),at,at);
        }
        return new EventPersistResult(order);
    }
    /** Restart work is limited to inputs durably persisted before an Agent turn was claimed. */
    public List<PlatformEvent> pendingEvents(){return jdbc.query("SELECT event_json FROM inbound_turn_executions WHERE status='PERSISTED' ORDER BY created_at,execution_key",(rs,n)->deserialize(rs.getString(1)));}
    public boolean isPending(PlatformEvent event,UUID conversationId){return jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE execution_key=? AND status='PERSISTED'",Integer.class,executionKey(event,conversationId.toString()))>0;}
    /** Atomic claim binds exactly one stable logical turn to the inbound key. */
    @Transactional public boolean claim(PlatformEvent event,UUID conversationId,long generation){
        String key=executionKey(event,conversationId.toString());
        if(jdbc.update("UPDATE inbound_turn_executions SET status='CLAIMED',generation=?,updated_at=? WHERE execution_key=? AND status='PERSISTED'",generation,java.time.Instant.now().toString(),key)!=1)return false;
        turns.create(stableTurnId(key),conversationId,key,event.message().platformMessageId(),generation);
        return true;
    }
    @Transactional public void complete(PlatformEvent event,UUID conversationId){jdbc.update("UPDATE inbound_turn_executions SET status='COMPLETED',updated_at=? WHERE execution_key=? AND status='CLAIMED'",java.time.Instant.now().toString(),executionKey(event,conversationId.toString()));turns.complete(stableTurnId(executionKey(event,conversationId.toString())));}
    @Transactional public void fail(PlatformEvent event,UUID conversationId){jdbc.update("UPDATE inbound_turn_executions SET status='FAILED',updated_at=? WHERE execution_key=? AND status='CLAIMED'",java.time.Instant.now().toString(),executionKey(event,conversationId.toString()));turns.fail(stableTurnId(executionKey(event,conversationId.toString())));}
    public InboundExecution findExecution(PlatformEvent event,UUID conversationId){return jdbc.query("SELECT execution_key,turn_id,generation,status FROM inbound_turn_executions WHERE execution_key=?",(rs,n)->new InboundExecution(rs.getString(1),rs.getString(2),rs.getObject(3)==null?null:rs.getLong(3),rs.getString(4)),executionKey(event,conversationId.toString())).stream().findFirst().orElse(null);}
    public PlatformEvent eventForExecutionKey(String key){return jdbc.query("SELECT event_json FROM inbound_turn_executions WHERE execution_key=?",(rs,n)->deserialize(rs.getString(1)),key).stream().findFirst().orElse(null);}
    public static String executionKey(PlatformEvent event,String conversationId){return event.platform().name()+":"+conversationId+":"+event.message().platformMessageId();}
    public static String stableTurnId(String executionKey){return UUID.nameUUIDFromBytes(executionKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
    private String serialize(PlatformEvent event){try{return mapper.writeValueAsString(event);}catch(JsonProcessingException e){throw new IllegalStateException("could not serialize persisted inbound event",e);}}
    private PlatformEvent deserialize(String json){try{return mapper.readValue(json,PlatformEvent.class);}catch(JsonProcessingException e){throw new IllegalStateException("could not restore persisted inbound event",e);}}
    public record InboundExecution(String executionKey,String turnId,Long generation,String status){}
    private EventPersistResult.Status classify(PlatformEvent event,String conversationId){
        var previous=jdbc.query("SELECT last_timestamp,last_message_id,last_sequence FROM event_order_state WHERE platform=? AND conversation_id=?",(rs,n)->{long seq=rs.getLong(3);return new OrderPoint(java.time.Instant.parse(rs.getString(1)),rs.getString(2),rs.wasNull()?null:seq);},event.platform().name(),conversationId).stream().findFirst().orElse(null);
        if(previous==null)return event.timestamp().equals(java.time.Instant.EPOCH)?EventPersistResult.Status.UNORDERABLE_ARCHIVED:EventPersistResult.Status.NEW_IN_ORDER;
        Long nextSequence=sequence(event);
        if(nextSequence!=null&&previous.sequence()!=null){if(nextSequence<previous.sequence())return EventPersistResult.Status.LATE_ARCHIVED;if(nextSequence>previous.sequence())return EventPersistResult.Status.NEW_IN_ORDER;return EventPersistResult.Status.UNORDERABLE_ARCHIVED;}
        int byTime=event.timestamp().compareTo(previous.timestamp());
        if(byTime<0)return EventPersistResult.Status.LATE_ARCHIVED;
        if(byTime>0&& !event.timestamp().equals(java.time.Instant.EPOCH))return EventPersistResult.Status.NEW_IN_ORDER;
        BigInteger nextId=numeric(event.message().platformMessageId()),oldId=numeric(previous.messageId());
        if(nextId!=null&&oldId!=null)return nextId.compareTo(oldId)>0?EventPersistResult.Status.NEW_IN_ORDER:EventPersistResult.Status.LATE_ARCHIVED;
        // OneBot timestamps are commonly second-granularity. Equal timestamps without comparable
        // numeric IDs are treated as a tie and retain transport arrival order, not marked late.
        return EventPersistResult.Status.NEW_IN_ORDER;
    }
    private static Long sequence(PlatformEvent event){for(String key:new String[]{"message_seq","sequence","event_sequence"}){Object value=event.rawMetadata().get(key);if(value!=null)try{return Long.parseLong(value.toString());}catch(NumberFormatException ignored){return null;}}return null;}
    private static BigInteger numeric(String value){try{return new BigInteger(value);}catch(Exception ignored){return null;}}
    private record OrderPoint(java.time.Instant timestamp,String messageId,Long sequence){}
    private void saveAlias(String person,String alias,String conversation,String at){if(alias==null||alias.isBlank())return;int n=jdbc.update("UPDATE person_aliases SET last_seen_at=MAX(last_seen_at,?) WHERE person_id=? AND alias=? AND source_conversation_id=?",at,person,alias,conversation);if(n==0)jdbc.update("INSERT INTO person_aliases(id,person_id,alias,source_conversation_id,first_seen_at,last_seen_at) VALUES(?,?,?,?,?,?)",java.util.UUID.nameUUIDFromBytes((person+":"+alias+":"+conversation).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),person,alias,conversation,at,at);}
    private static int bool(boolean b){return b?1:0;}
}
