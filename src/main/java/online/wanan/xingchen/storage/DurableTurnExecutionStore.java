package online.wanan.xingchen.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable journal and fail-closed recovery claims for logical Agent turns.
 * Recovery policy: NOT_STARTED/STREAMING_NO_EFFECT/MODEL_OUTPUT_RECEIVED are safe turn retries;
 * TOOL_PROPOSED retries only while the tool ledger has no claim; TOOL_EXECUTED never replays the
 * whole turn; OUTPUT_EMITTED resumes only its serialized output through the outbound ledger;
 * OUTBOUND_OUTPUT_SENT/UNCERTAIN never auto-replay. Generation comparison and a SQL CAS provide
 * stale-turn rejection and one recovery owner. Tool/outbound ledgers close effect-to-journal crash gaps.
 */
@Repository
public class DurableTurnExecutionStore {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public DurableTurnExecutionStore(JdbcTemplate jdbc,ObjectMapper mapper) { this.jdbc=jdbc;this.mapper=mapper; }

    @Transactional public void create(String turnId, UUID conversationId, String inboundKey, String eventId, long generation) {
        String now=Instant.now().toString();
        jdbc.update("INSERT OR IGNORE INTO durable_turn_executions(turn_id,conversation_id,inbound_execution_key,incoming_event_id,generation,model_turn_state,status,claimed_at,updated_at) VALUES(?,?,?,?,?,'NOT_STARTED','RUNNING',?,?)",
                turnId,conversationId.toString(),inboundKey,eventId,generation,now,now);
    }
    public void state(String turnId,String state) {
        String now=Instant.now().toString();
        int updated=jdbc.update("UPDATE durable_turn_executions SET model_turn_state=?,updated_at=? WHERE turn_id=? AND status IN ('RUNNING','RECOVERING')",state,now,turnId);
        if(updated!=1)throw new IllegalStateException("durable turn state update lost its active journal claim");
    }
    public void output(String turnId,String type,List<String> messages) {
        String json;try{json=mapper.writeValueAsString(messages);}catch(JsonProcessingException e){throw new IllegalStateException("could not serialize durable turn output",e);}
        jdbc.update("UPDATE durable_turn_executions SET model_turn_state='OUTPUT_EMITTED',output_type=?,output_json=?,updated_at=? WHERE turn_id=? AND status IN ('RUNNING','RECOVERING')",type,json,Instant.now().toString(),turnId);
    }
    public void wakeReasons(String turnId,java.util.Set<String> reasons){String json;try{json=mapper.writeValueAsString(reasons==null?List.of():new java.util.TreeSet<>(reasons));}catch(JsonProcessingException e){throw new IllegalStateException("could not serialize turn wake reasons",e);}jdbc.update("UPDATE durable_turn_executions SET wake_reasons_json=?,updated_at=? WHERE turn_id=? AND status IN ('RUNNING','RECOVERING')",json,Instant.now().toString(),turnId);}
    public void complete(String turnId) { jdbc.update("UPDATE durable_turn_executions SET status=CASE WHEN model_turn_state='UNCERTAIN' THEN 'INTERRUPTED' ELSE 'COMPLETED' END,updated_at=? WHERE turn_id=?",Instant.now().toString(),turnId); }
    public void fail(String turnId) { jdbc.update("UPDATE durable_turn_executions SET status=CASE WHEN model_turn_state IN ('TOOL_EXECUTED','OUTPUT_EMITTED','OUTBOUND_OUTPUT_SENT','UNCERTAIN') THEN 'INTERRUPTED' ELSE 'RECOVERABLE' END,updated_at=? WHERE turn_id=? AND status IN ('RUNNING','RECOVERING')",Instant.now().toString(),turnId); }
    public List<RecoveryTurn> recoverable() {
        return jdbc.query("SELECT turn_id,conversation_id,inbound_execution_key,incoming_event_id,generation,model_turn_state,status,recovery_attempts,output_type,output_json,wake_reasons_json FROM durable_turn_executions WHERE status IN ('RUNNING','RECOVERABLE') ORDER BY claimed_at",
                (rs,n)->new RecoveryTurn(rs.getString(1),UUID.fromString(rs.getString(2)),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getString(7),rs.getInt(8),rs.getString(9),readMessages(rs.getString(10)),readMessages(rs.getString(11)).stream().collect(java.util.stream.Collectors.toUnmodifiableSet())));
    }
    public boolean hasToolClaim(String eventId){return jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims WHERE event_id=?",Integer.class,eventId)>0;}
    public boolean hasOutbound(String turnId){return jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE turn_id=?",Integer.class,turnId)>0;}
    @Transactional public boolean claimRecovery(String turnId,long currentGeneration) {
        return jdbc.update("UPDATE durable_turn_executions SET status='RECOVERING',recovery_attempts=recovery_attempts+1,updated_at=? WHERE turn_id=? AND generation=? AND status IN ('RUNNING','RECOVERABLE') AND model_turn_state IN ('NOT_STARTED','STREAMING_NO_EFFECT','MODEL_OUTPUT_RECEIVED','TOOL_PROPOSED','OUTPUT_EMITTED') AND recovery_attempts<2 AND (EXISTS (SELECT 1 FROM simulation_conversation_state s WHERE s.conversation_id=durable_turn_executions.conversation_id AND s.generation=?) OR (?=0 AND NOT EXISTS (SELECT 1 FROM simulation_conversation_state s WHERE s.conversation_id=durable_turn_executions.conversation_id)))",
                Instant.now().toString(),turnId,currentGeneration,currentGeneration,currentGeneration)==1;
    }
    @Transactional public void releaseExpiredRecoveryClaims(){
        String stale=Instant.now().minusSeconds(30).toString();String now=Instant.now().toString();
        jdbc.update("UPDATE durable_turn_executions SET status=CASE WHEN recovery_attempts>=2 THEN 'INTERRUPTED' ELSE 'RECOVERABLE' END,updated_at=? WHERE status='RECOVERING' AND updated_at<?",now,stale);
        jdbc.update("UPDATE durable_turn_executions SET status='INTERRUPTED',updated_at=? WHERE status IN ('RUNNING','RECOVERABLE') AND recovery_attempts>=2",now);
    }
    public void markStale(String turnId) { jdbc.update("UPDATE durable_turn_executions SET status='STALE',updated_at=? WHERE turn_id=? AND status IN ('RUNNING','RECOVERABLE','RECOVERING')",Instant.now().toString(),turnId); }
    public int markConversationStale(UUID conversationId,long priorGeneration){return jdbc.update("UPDATE durable_turn_executions SET status='STALE',updated_at=? WHERE conversation_id=? AND generation<=? AND status IN ('RUNNING','RECOVERABLE','RECOVERING')",Instant.now().toString(),conversationId.toString(),priorGeneration);}
    public void interrupt(String turnId) { jdbc.update("UPDATE durable_turn_executions SET status='INTERRUPTED',updated_at=? WHERE turn_id=? AND status IN ('RUNNING','RECOVERABLE','RECOVERING')",Instant.now().toString(),turnId); }
    private List<String> readMessages(String json){if(json==null)return List.of();try{return mapper.readValue(json,mapper.getTypeFactory().constructCollectionType(List.class,String.class));}catch(Exception e){return List.of();}}
    public record RecoveryTurn(String turnId,UUID conversationId,String inboundExecutionKey,String eventId,long generation,String modelTurnState,String status,int recoveryAttempts,String outputType,List<String> outputMessages,java.util.Set<String> wakeReasons) {public RecoveryTurn{outputMessages=List.copyOf(outputMessages);wakeReasons=java.util.Set.copyOf(wakeReasons);}}
}
