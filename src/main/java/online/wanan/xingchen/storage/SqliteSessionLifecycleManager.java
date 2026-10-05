package online.wanan.xingchen.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.context.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.Clock;
import java.util.UUID;

/** Durable rollover generations and handoffs. Identical consecutive handoffs are idempotent. */
@Repository
public class SqliteSessionLifecycleManager implements SessionLifecycleManager {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ContextBuilder contexts;
    private final Clock clock;

    public SqliteSessionLifecycleManager(JdbcTemplate jdbc,ObjectMapper mapper) {
        this(jdbc,mapper,new ContextBuilder(ContextBudget.defaults()),Clock.systemUTC());
    }
    public SqliteSessionLifecycleManager(JdbcTemplate jdbc, ObjectMapper mapper, ContextBuilder contexts) {
        this(jdbc,mapper,contexts,Clock.systemUTC());
    }
    @Autowired
    public SqliteSessionLifecycleManager(JdbcTemplate jdbc,ObjectMapper mapper,ContextBuilder contexts,Clock clock) {
        this.jdbc=jdbc; this.mapper=mapper; this.contexts=contexts;this.clock=clock;
    }

    @Override public SessionLifecycleStatus evaluate(int estimatedTokens) { return contexts.lifecycle(estimatedTokens); }

    @Override public SessionHandoff loadHandoff(String conversationId) {
        return jdbc.query("SELECT h.handoff_json FROM session_state s JOIN session_handoffs h ON h.summary_id=s.current_summary_id WHERE s.conversation_id=?",
                (rs,n)->read(rs.getString(1)), conversationId).stream().findFirst().orElse(null);
    }

    @Transactional
    @Override public void saveHandoff(String conversationId, SessionHandoff handoff) {
        UUID id=UUID.fromString(conversationId);
        String json=write(handoff); Instant now=clock.instant();
        var existing=jdbc.query("SELECT generation,current_summary_id FROM session_state WHERE conversation_id=?",
                (rs,n)->new Object[]{rs.getLong(1),rs.getString(2)}, conversationId).stream().findFirst();
        long generation=existing.map(row->(Long)row[0]).orElse(0L);
        String currentSummary=existing.map(row->(String)row[1]).orElse(null);
        if (currentSummary!=null) {
            String prior=jdbc.query("SELECT handoff_json FROM session_handoffs WHERE summary_id=?",(rs,n)->rs.getString(1),currentSummary).stream().findFirst().orElse(null);
            if (json.equals(prior)) return;
        }
        generation++;
        String summaryId=UUID.nameUUIDFromBytes((conversationId+":"+generation+":"+json).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        jdbc.update("INSERT INTO session_handoffs(conversation_id,generation,summary_id,handoff_json,created_at) VALUES(?,?,?,?,?)",conversationId,generation,summaryId,json,now.toString());
        jdbc.update("INSERT INTO session_state(conversation_id,session_id,status,token_estimate,updated_at,generation,current_summary_id,last_rollover_at,estimated_context_tokens) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(conversation_id) DO UPDATE SET session_id=excluded.session_id,status=excluded.status,updated_at=excluded.updated_at,generation=excluded.generation,current_summary_id=excluded.current_summary_id,last_rollover_at=excluded.last_rollover_at",
                id.toString(),conversationId+"-"+generation,"ROLLED_OVER",0,now.toString(),generation,summaryId,now.toString(),0);
    }

    @Transactional
    @Override public void recordContextSize(String conversationId, int estimatedTokens) {
        if (estimatedTokens < 0) throw new IllegalArgumentException("context token estimate cannot be negative");
        UUID.fromString(conversationId);
        Instant now=clock.instant();
        jdbc.update("INSERT INTO session_state(conversation_id,session_id,status,token_estimate,updated_at,generation,current_summary_id,last_rollover_at,estimated_context_tokens) VALUES(?,?,?,?,?,0,NULL,NULL,?) ON CONFLICT(conversation_id) DO UPDATE SET token_estimate=excluded.token_estimate,estimated_context_tokens=excluded.estimated_context_tokens,updated_at=excluded.updated_at",
                conversationId,conversationId+"-0","ACTIVE",estimatedTokens,now.toString(),estimatedTokens);
    }

    public PersistedSessionState loadState(String conversationId) {
        return jdbc.query("SELECT generation,status,current_summary_id,last_rollover_at,estimated_context_tokens,updated_at FROM session_state WHERE conversation_id=?",
                (rs,n)->new PersistedSessionState(UUID.fromString(conversationId),rs.getLong(1),rs.getString(2),uuid(rs.getString(3)),instant(rs.getString(4)),rs.getInt(5),instant(rs.getString(6))),conversationId).stream().findFirst().orElse(null);
    }

    private String write(SessionHandoff value) { try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("session handoff could not be encoded",e);} }
    private SessionHandoff read(String json) { try{return mapper.readValue(json,SessionHandoff.class);}catch(Exception e){throw new IllegalStateException("persisted session handoff is invalid",e);} }
    private static UUID uuid(String value){return value==null?null:UUID.fromString(value);}
    private static Instant instant(String value){return value==null?null:Instant.parse(value);}
}
