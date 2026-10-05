package online.wanan.xingchen.storage;

import online.wanan.xingchen.core.agent.ToolExecutionLedger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SqliteToolExecutionLedger implements ToolExecutionLedger {
    private final JdbcTemplate jdbc;
    public SqliteToolExecutionLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public boolean requiresStableEventId() { return true; }

    @Override public boolean claim(String eventId, String callId) {
        if (eventId == null || eventId.isBlank() || callId == null || callId.isBlank()) return false;
        return jdbc.update("INSERT OR IGNORE INTO tool_execution_claims(event_id,call_id,claimed_at) VALUES(?,?,?)",
                eventId, callId, java.time.Instant.now().toString()) == 1;
    }
}
