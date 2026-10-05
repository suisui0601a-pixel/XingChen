package online.wanan.xingchen.storage;

import online.wanan.xingchen.core.context.ContextDiagnostics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

@Repository
public class ContextDiagnosticsStore {
    private final JdbcTemplate jdbc;
    public ContextDiagnosticsStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public void save(UUID conversationId,ContextDiagnostics d){jdbc.update("INSERT INTO context_diagnostics(conversation_id,total_tokens,persona_tokens,simulation_tokens,memory_tokens,recent_tokens,summary_tokens,included_memory_count,excluded_memory_count,lifecycle_state,tool_result_tokens,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(conversation_id) DO UPDATE SET total_tokens=excluded.total_tokens,persona_tokens=excluded.persona_tokens,simulation_tokens=excluded.simulation_tokens,memory_tokens=excluded.memory_tokens,recent_tokens=excluded.recent_tokens,summary_tokens=excluded.summary_tokens,included_memory_count=excluded.included_memory_count,excluded_memory_count=excluded.excluded_memory_count,lifecycle_state=excluded.lifecycle_state,tool_result_tokens=excluded.tool_result_tokens,updated_at=excluded.updated_at",conversationId.toString(),d.totalEstimatedTokens(),d.personaTokens(),d.simulationTokens(),d.memoryTokens(),d.recentTokens(),d.summaryTokens(),d.includedMemoryIds().size(),d.excludedMemoryCount(),d.lifecycleState().name(),d.toolResultTokens(),Instant.now().toString());}
    public List<Map<String,Object>> list(){return jdbc.queryForList("SELECT * FROM context_diagnostics ORDER BY updated_at DESC LIMIT 100");}
}
