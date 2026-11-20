package online.wanan.xingchen.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.agent.ProactivePolicy;
import online.wanan.xingchen.core.agent.SocialSchedule;
import online.wanan.xingchen.core.model.PlatformEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;

/** Durable at-most-once admission. A claimed timer turn is never automatically replayed. */
@Repository
public class ProactiveAttemptStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public ProactiveAttemptStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public record Candidate(UUID conversationId,PlatformEvent anchor) {
        public String lane(){var c=anchor.conversation();return c.platform()+":"+c.type()+":"+c.platformConversationId();}
    }
    public List<Candidate> candidates(Instant now,String after){
        return jdbc.query("""
            SELECT i.conversation_id,i.event_json FROM event_order_state e
            JOIN inbound_turn_executions i ON i.conversation_id=e.conversation_id AND i.incoming_event_id=e.last_message_id
            JOIN conversations c ON c.id=i.conversation_id
            WHERE c.platform='QQ' AND c.type IN ('PRIVATE','GROUP') AND i.status='COMPLETED' AND c.id>?
              AND julianday(e.last_timestamp)>=julianday(?) AND julianday(e.last_timestamp)<=julianday(?)
              AND NOT EXISTS(SELECT 1 FROM inbound_turn_executions pending WHERE pending.conversation_id=c.id AND
                (pending.status='PERSISTED' OR (pending.status='CLAIMED' AND NOT EXISTS(
                  SELECT 1 FROM durable_turn_executions retired WHERE retired.inbound_execution_key=pending.execution_key
                    AND retired.status IN ('COMPLETED','INTERRUPTED','STALE','FAILED')))))
              AND NOT EXISTS(SELECT 1 FROM durable_turn_executions running WHERE running.conversation_id=c.id AND running.status IN ('RUNNING','RECOVERABLE','RECOVERING'))
              AND NOT EXISTS(SELECT 1 FROM conversation_reset_controls resetting WHERE resetting.conversation_id=c.id AND resetting.status='RESETTING')
            ORDER BY c.id
            LIMIT 100
            """,(rs,n)->{try{return new Candidate(UUID.fromString(rs.getString(1)),json.readValue(rs.getString(2),PlatformEvent.class));}
                catch(Exception invalid){throw new IllegalStateException("invalid persisted proactive anchor");}},after,now.minus(Duration.ofHours(24)).toString(),now.toString());
    }
    public boolean current(Candidate candidate){return jdbc.queryForObject("SELECT COUNT(*) FROM event_order_state e WHERE conversation_id=? AND last_message_id=? AND NOT EXISTS(SELECT 1 FROM conversation_reset_controls r WHERE r.conversation_id=e.conversation_id AND r.status='RESETTING')",Integer.class,candidate.conversationId().toString(),candidate.anchor().message().platformMessageId())==1;}
    public boolean due(UUID id,ProactivePolicy policy,Instant now){return eligible(id,policy,now)>0;}
    private int eligible(UUID id,ProactivePolicy p,Instant now){return jdbc.queryForObject("SELECT "+eligibility(),Integer.class,arguments(id,p,now));}
    private static String eligibility(){return """
        NOT EXISTS(SELECT 1 FROM proactive_attempts WHERE intent='RANDOM' AND conversation_id=? AND julianday(checked_at)>julianday(?))
        AND NOT EXISTS(SELECT 1 FROM proactive_attempts WHERE intent='RANDOM' AND conversation_id=? AND admitted=1 AND julianday(checked_at)>julianday(?))
        AND (SELECT COUNT(*) FROM proactive_attempts WHERE intent='RANDOM' AND conversation_id=? AND admitted=1 AND local_day=?)<?
        AND (SELECT COUNT(*) FROM proactive_attempts WHERE intent='RANDOM' AND admitted=1 AND julianday(checked_at)>julianday(?))<4
        """;}
    private static Object[] arguments(UUID id,ProactivePolicy p,Instant now){return new Object[]{id.toString(),now.minus(Duration.ofMinutes(p.intervalMinutes())).toString(),id.toString(),now.minus(Duration.ofMinutes(p.cooldownMinutes())).toString(),id.toString(),now.atZone(ZoneId.of(p.zoneId())).toLocalDate().toString(),p.maxPerDay(),now.minus(Duration.ofHours(1)).toString()};}
    /** One atomic INSERT/SELECT rechecks every budget, including the shared four-model-turns/hour cap. */
    public String reserve(Candidate c,ProactivePolicy p,Instant now,boolean admitted){
        String id=UUID.randomUUID().toString();String turnId=IncomingMessageStore.stableTurnId(c.anchor().platform()+":"+c.conversationId()+":proactive:"+id);
        var args=new ArrayList<Object>(List.of(id,c.conversationId().toString(),c.anchor().message().platformMessageId(),turnId,now.toString(),now.atZone(ZoneId.of(p.zoneId())).toLocalDate().toString(),admitted?1:0,admitted?"CLAIMED":"SKIPPED_RANDOM",now.toString()));
        args.addAll(Arrays.asList(arguments(c.conversationId(),p,now)));
        int inserted=jdbc.update("INSERT INTO proactive_attempts(id,conversation_id,source_message_id,turn_id,checked_at,local_day,admitted,status,updated_at) SELECT ?,?,?,?,?,?,?,?,? WHERE "+eligibility(),args.toArray());
        return inserted==1?id:null;
    }
    public boolean greetingPending(Candidate c,SocialSchedule.Intent intent){return jdbc.queryForObject("SELECT COUNT(*) FROM proactive_attempts WHERE conversation_id=? AND schedule_key=?",Integer.class,c.conversationId().toString(),intent.key())==0;}
    /** Greetings have their own four/hour budget so a 22:00 random turn cannot consume the 22:50 greeting. */
    public String reserveGreeting(Candidate c,ProactivePolicy p,Instant now,SocialSchedule.Intent intent){
        if(!intent.greeting()||!intent.current(now))return null;
        String id=UUID.randomUUID().toString(),turnId=IncomingMessageStore.stableTurnId(c.anchor().platform()+":"+c.conversationId()+":proactive:"+id);
        int inserted=jdbc.update("INSERT OR IGNORE INTO proactive_attempts(id,conversation_id,source_message_id,turn_id,checked_at,local_day,admitted,status,updated_at,intent,schedule_key) SELECT ?,?,?,?,?,?,1,'CLAIMED',?,?,? WHERE (SELECT COUNT(*) FROM proactive_attempts WHERE intent!='RANDOM' AND admitted=1 AND julianday(checked_at)>julianday(?))<4",id,c.conversationId().toString(),c.anchor().message().platformMessageId(),turnId,now.toString(),now.atZone(ZoneId.of(p.zoneId())).toLocalDate().toString(),now.toString(),intent.kind(),intent.key(),now.minus(Duration.ofHours(1)).toString());
        return inserted==1?id:null;
    }
    public void finish(String id,String status,Instant now){jdbc.update("UPDATE proactive_attempts SET status=?,updated_at=? WHERE id=? AND status='CLAIMED'",status,now.toString(),id);}
    public void output(String id,List<String> messages){try{if(jdbc.update("UPDATE proactive_attempts SET output_json=? WHERE id=? AND status='CLAIMED'",json.writeValueAsString(messages),id)!=1)throw new IllegalStateException("proactive output lost its admission claim");}catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalStateException("proactive output could not be encoded");}}
    /** Keep enough history for the maximum seven-day cooldown; never discard ambiguous outcomes. */
    public int pruneKnownHistory(Instant now){return jdbc.update("DELETE FROM proactive_attempts WHERE id IN (SELECT id FROM proactive_attempts WHERE julianday(checked_at)<julianday(?) AND status IN ('SKIPPED_RANDOM','SENT','NO_REPLY','CANCELLED','FAILED','RATE_LIMITED') ORDER BY checked_at LIMIT 500)",now.minus(Duration.ofDays(8)).toString());}
    public void interruptClaimed(Instant now){jdbc.update("""
        UPDATE proactive_attempts SET status=CASE
          WHEN EXISTS(SELECT 1 FROM onebot_outbound_executions o WHERE o.turn_id=proactive_attempts.turn_id AND o.status IN ('PENDING','UNKNOWN')) THEN 'UNKNOWN'
          WHEN EXISTS(SELECT 1 FROM onebot_outbound_executions o WHERE o.turn_id=proactive_attempts.turn_id AND o.status='SUCCESS') THEN 'SENT'
          ELSE 'INTERRUPTED' END,updated_at=? WHERE status='CLAIMED'
        """,now.toString());}
    public String deliveryStatus(String turnId){
        var rows=jdbc.queryForList("SELECT status,failure_reason FROM onebot_outbound_executions WHERE turn_id=?",turnId);
        if(rows.stream().anyMatch(r->Set.of("UNKNOWN","PENDING").contains(r.get("status"))))return "UNKNOWN";
        if(rows.stream().anyMatch(r->"SUCCESS".equals(r.get("status"))))return "SENT";
        if(rows.stream().anyMatch(r->Objects.toString(r.get("failure_reason"),"").contains("limit")))return "RATE_LIMITED";
        return rows.isEmpty()?"NO_REPLY":"FAILED";
    }
    public Map<String,Object> diagnostics(){return Map.of("outcomes",jdbc.queryForList("SELECT status,COUNT(*) AS count FROM proactive_attempts GROUP BY status"),"recent",jdbc.queryForList("SELECT conversation_id AS conversationId,checked_at AS checkedAt,status,intent FROM proactive_attempts ORDER BY checked_at DESC LIMIT 20"));}
}
