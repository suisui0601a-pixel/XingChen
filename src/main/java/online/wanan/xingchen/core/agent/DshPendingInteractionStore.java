package online.wanan.xingchen.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;

/** Durable correlation for RC.2 waterfall delivery and human decisions. */
public class DshPendingInteractionStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public DshPendingInteractionStore(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Inserts first delivery; a replay refreshes only the active transport binding. */
    @Transactional
    public ReplayResult replay(Interaction interaction) {
        validate(interaction, clock.instant());
        String now = clock.instant().toString();
        List<Interaction> rows = jdbc.query("SELECT id,kind,dsh_session_id,conversation_id,client_id,client_generation,event_id,required_actor_id,expires_at,status,questions_json,tool_name,call_id,reason,created_at,updated_at FROM dsh_pending_interactions WHERE dsh_session_id=? AND event_id=?",
                (rs, n) -> map(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16)),
                interaction.dshSessionId(), interaction.eventId());
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO dsh_pending_interactions(id,kind,dsh_session_id,conversation_id,client_id,client_generation,event_id,required_actor_id,expires_at,status,questions_json,tool_name,call_id,reason,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?,?,?,?,?,?)",
                    interaction.id(), interaction.kind().name(), interaction.dshSessionId(), interaction.conversationId(), interaction.clientId(), interaction.clientGeneration(), interaction.eventId(), interaction.requiredActorId(), interaction.expiresAt().toString(), json(interaction.questions()), interaction.toolName(), interaction.callId(), interaction.reason(), now, now);
            return new ReplayResult(true, false, interaction.withStatus("PENDING", now));
        }
        Interaction old = rows.getFirst();
        if (old.kind() != interaction.kind() || !old.conversationId().equals(interaction.conversationId()) || !old.requiredActorId().equals(interaction.requiredActorId()))
            throw new SecurityException("replayed DSH event conflicts with its durable binding");
        if (!old.status().equals("PENDING")) return new ReplayResult(false, true, old);
        long generation=old.clientId().equals(interaction.clientId())?old.clientGeneration():Math.max(old.clientGeneration()+1,interaction.clientGeneration());
        int changed = jdbc.update("UPDATE dsh_pending_interactions SET client_id=?,client_generation=?,updated_at=? WHERE dsh_session_id=? AND event_id=? AND status='PENDING'",
                interaction.clientId(), generation, now, interaction.dshSessionId(), interaction.eventId());
        return new ReplayResult(false, false, changed == 1 ? old.withTransport(interaction.clientId(), generation, now) : find(interaction.dshSessionId(), interaction.eventId()).orElse(old));
    }

    public Optional<Interaction> find(String sessionId, String eventId) {
        return jdbc.query("SELECT id,kind,dsh_session_id,conversation_id,client_id,client_generation,event_id,required_actor_id,expires_at,status,questions_json,tool_name,call_id,reason,created_at,updated_at FROM dsh_pending_interactions WHERE dsh_session_id=? AND event_id=?",
                (rs, n) -> map(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14), rs.getString(15), rs.getString(16)), sessionId, eventId).stream().findFirst();
    }

    public List<Interaction> pendingFor(String conversationId, String actorId) {
        return jdbc.query("SELECT id,kind,dsh_session_id,conversation_id,client_id,client_generation,event_id,required_actor_id,expires_at,status,questions_json,tool_name,call_id,reason,created_at,updated_at FROM dsh_pending_interactions WHERE conversation_id=? AND required_actor_id=? AND status='PENDING' ORDER BY created_at",
                (rs,n)->map(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getString(13),rs.getString(14),rs.getString(15),rs.getString(16)), conversationId, actorId);
    }
    public List<Interaction> pendingInConversation(String conversationId) {
        return jdbc.query("SELECT id,kind,dsh_session_id,conversation_id,client_id,client_generation,event_id,required_actor_id,expires_at,status,questions_json,tool_name,call_id,reason,created_at,updated_at FROM dsh_pending_interactions WHERE conversation_id=? AND status='PENDING' ORDER BY created_at",
                (rs,n)->map(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getLong(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getString(13),rs.getString(14),rs.getString(15),rs.getString(16)),conversationId);
    }

    public Map<String,String> questionAnswers(String id) {
        Map<String,String> result=new LinkedHashMap<>();jdbc.query("SELECT question_id,answer_text FROM dsh_pending_question_answers WHERE interaction_id=? ORDER BY question_id",rs->{result.put(rs.getString(1),rs.getString(2));},id);return Map.copyOf(result);
    }

    @Transactional
    public boolean recordQuestionAnswers(String id,String actorId,String clientId,long generation,Map<String,String> answers) {
        String now=clock.instant().toString();int valid=jdbc.update("UPDATE dsh_pending_interactions SET updated_at=? WHERE id=? AND kind='QUESTION' AND required_actor_id=? AND client_id=? AND client_generation=? AND status='PENDING' AND expires_at>?",now,id,actorId,clientId,generation,now);if(valid!=1)return false;
        for(var entry:answers.entrySet())jdbc.update("INSERT INTO dsh_pending_question_answers(interaction_id,question_id,answer_text,updated_at) VALUES(?,?,?,?) ON CONFLICT(interaction_id,question_id) DO UPDATE SET answer_text=excluded.answer_text,updated_at=excluded.updated_at",id,entry.getKey(),entry.getValue(),now);
        return true;
    }

    @Transactional public boolean expire(String id) {String now=clock.instant().toString();return jdbc.update("UPDATE dsh_pending_interactions SET status='EXPIRED',updated_at=? WHERE id=? AND status='PENDING' AND expires_at<=?",now,id,now)==1;}
    @Transactional public int expireDue() {String now=clock.instant().toString();return jdbc.update("UPDATE dsh_pending_interactions SET status='EXPIRED',updated_at=? WHERE status='PENDING' AND expires_at<=?",now,now);}
    @Transactional public int interruptConversation(String conversationId){return jdbc.update("UPDATE dsh_pending_interactions SET status='INTERRUPTED',updated_at=? WHERE conversation_id=? AND status IN ('PENDING','ANSWERING')",clock.instant().toString(),conversationId);}
    @Transactional public boolean interrupt(String id) {return jdbc.update("UPDATE dsh_pending_interactions SET status='INTERRUPTED',updated_at=? WHERE id=? AND status='PENDING'",clock.instant().toString(),id)==1;}

    @Transactional public boolean releaseAnswer(String id) {return jdbc.update("UPDATE dsh_pending_interactions SET status='PENDING',updated_at=? WHERE id=? AND status='ANSWERING'",clock.instant().toString(),id)==1;}

    /** Atomically claims a human response. Caller must validate actor, conversation, expiry and generation first. */
    @Transactional
    public boolean claimAnswer(String id, String actorId, String clientId, long generation) {
        String now = clock.instant().toString();
        return jdbc.update("UPDATE dsh_pending_interactions SET status='ANSWERING',updated_at=? WHERE id=? AND required_actor_id=? AND client_id=? AND client_generation=? AND status='PENDING' AND expires_at>?",
                now, id, actorId, clientId, generation, now) == 1;
    }

    @Transactional
    public void finishAnswer(String id, String terminalStatus) {
        if (!List.of("APPROVED", "REJECTED", "ANSWERED", "INTERRUPTED", "UNKNOWN").contains(terminalStatus)) throw new IllegalArgumentException("invalid interaction terminal status");
        int changed = jdbc.update("UPDATE dsh_pending_interactions SET status=?,updated_at=? WHERE id=? AND status='ANSWERING'", terminalStatus, clock.instant().toString(), id);
        if (changed != 1) throw new IllegalStateException("interaction answer claim was lost");
    }

    @Transactional
    public boolean reserveResult(String sessionId, String eventId, String outcomeVersion) {
        if (sessionId == null || sessionId.isBlank() || eventId == null || eventId.isBlank() || outcomeVersion == null || outcomeVersion.isBlank()) throw new IllegalArgumentException("event result key is incomplete");
        String key = "dsh-event-result:" + sessionId + ":" + eventId + ":" + outcomeVersion;
        String now = clock.instant().toString();
        return jdbc.update("INSERT INTO dsh_event_result_ledger(execution_key,dsh_session_id,event_id,logical_outcome_version,status,created_at,updated_at) VALUES(?,?,?,?,'RESERVED',?,?) ON CONFLICT(execution_key) DO NOTHING", key, sessionId, eventId, outcomeVersion, now, now) == 1;
    }

    @Transactional
    public void completeResult(String sessionId, String eventId, String outcomeVersion, ResultStatus status) {
        completeResult(sessionId,eventId,outcomeVersion,status,null);
    }

    @Transactional
    public void completeResult(String sessionId, String eventId, String outcomeVersion, ResultStatus status, String failureReason) {
        String key = "dsh-event-result:" + sessionId + ":" + eventId + ":" + outcomeVersion;
        if (jdbc.update("UPDATE dsh_event_result_ledger SET status=?,failure_reason=?,updated_at=? WHERE execution_key=? AND status='RESERVED'", status.name(), failureReason, clock.instant().toString(), key) != 1)
            throw new IllegalStateException("event result was not reserved");
    }

    private Interaction map(String id, String kind, String session, String conversation, String client, long generation, String event, String actor, String expiry, String status, String questions, String tool, String call, String reason, String created, String updated) {
        JsonNode parsed = null;
        try { if (questions != null) parsed = mapper.readTree(questions); } catch (Exception e) { throw new IllegalStateException("stored interaction questions are corrupt", e); }
        return new Interaction(id, Kind.valueOf(kind), session, conversation, client, generation, event, actor, Instant.parse(expiry), status, parsed, tool, call, reason, Instant.parse(created), Instant.parse(updated));
    }
    private String json(JsonNode value) { try { return value == null ? null : mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("interaction payload is not JSON", e); } }
    private static void validate(Interaction i, Instant now) {
        if (i == null || i.id() == null || i.id().isBlank() || i.kind() == null || blank(i.dshSessionId()) || blank(i.conversationId()) || blank(i.clientId()) || i.clientGeneration() < 1 || blank(i.eventId()) || blank(i.requiredActorId()) || i.expiresAt() == null || !i.expiresAt().isAfter(now)) throw new IllegalArgumentException("pending interaction binding is incomplete or expired");
        if (i.kind() == Kind.QUESTION && i.questions() == null) throw new IllegalArgumentException("question payload is required");
        if (i.kind() == Kind.APPROVAL && blank(i.toolName())) throw new IllegalArgumentException("approval tool name is required");
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }

    public enum Kind { QUESTION, APPROVAL }
    public enum ResultStatus { SUCCESS, FAILED, UNKNOWN }
    public record ReplayResult(boolean created, boolean terminal, Interaction interaction) { }
    public record Interaction(String id, Kind kind, String dshSessionId, String conversationId, String clientId, long clientGeneration, String eventId, String requiredActorId, Instant expiresAt, String status, JsonNode questions, String toolName, String callId, String reason, Instant createdAt, Instant updatedAt) {
        Interaction withTransport(String client, long generation, String updated) { return new Interaction(id, kind, dshSessionId, conversationId, client, generation, eventId, requiredActorId, expiresAt, status, questions, toolName, callId, reason, createdAt, Instant.parse(updated)); }
        Interaction withStatus(String value, String updated) { return new Interaction(id, kind, dshSessionId, conversationId, clientId, clientGeneration, eventId, requiredActorId, expiresAt, value, questions, toolName, callId, reason, createdAt, Instant.parse(updated)); }
    }
}
