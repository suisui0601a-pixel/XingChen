package online.wanan.xingchen.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.agent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository public class SqliteAgentTraceStore implements AgentTraceSink {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;public SqliteAgentTraceStore(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    @Override public void record(AgentTrace t){var d=t.diagnostics();jdbc.update("INSERT INTO agent_traces(id,conversation_id,event_id,actor_id,memory_ids_json,context_tokens,memory_tokens,recent_tokens,summary_tokens,tool_result_tokens,provider,model,model_call_count,tool_calls_json,decision,outgoing_actions_json,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),str(t.conversationId()),t.eventId(),t.actorId(),json(t.memoryIds()),d==null?0:d.totalEstimatedTokens(),d==null?0:d.memoryTokens(),d==null?0:d.recentTokens(),d==null?0:d.summaryTokens(),d==null?0:d.toolResultTokens(),t.provider(),t.model(),t.modelCallCount(),json(t.toolNames()),t.decision()==null?"UNKNOWN":t.decision().name(),json(Map.of("count",t.outgoingActionCount())),t.createdAt().toString());}
    private String json(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException("trace serialization failed",e);}}private static String str(UUID id){return id==null?null:id.toString();}
}
