package online.wanan.xingchen.console;

import online.wanan.xingchen.storage.ContextDiagnosticsStore;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import online.wanan.xingchen.config.RuntimeIntegrationStatus;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import java.util.*;

/** Loopback-oriented, read-only Phase 2 API. It returns no credentials, tokens, cookies or raw platform payloads. */
@RestController
public class ConsoleReadController {
    private final JdbcTemplate jdbc;private final ContextDiagnosticsStore diagnostics;private final Environment environment;private final RuntimeIntegrationStatus integrations;private final SocialRuntime socialRuntime;
    public ConsoleReadController(JdbcTemplate jdbc,ContextDiagnosticsStore diagnostics,Environment environment){this(jdbc,diagnostics,environment,new RuntimeIntegrationStatus(false,false,false,false),null);}
    @Autowired public ConsoleReadController(JdbcTemplate jdbc,ContextDiagnosticsStore diagnostics,Environment environment,RuntimeIntegrationStatus integrations,SocialRuntime socialRuntime){this.jdbc=jdbc;this.diagnostics=diagnostics;this.environment=environment;this.integrations=integrations;this.socialRuntime=socialRuntime;}
    @GetMapping("/api/status") public Map<String,Object> status(){
        Map<String,Object> result=new LinkedHashMap<>();result.put("status","UP");result.put("database","UP");result.put("memory","UP");result.put("agent","mock-ready");
        result.put("people",count("persons"));result.put("conversations",count("conversations"));result.put("memories",count("memories"));return result;
    }
    @GetMapping("/api/memories") public org.springframework.http.ResponseEntity<List<Map<String,Object>>> memories(){var rows=jdbc.queryForList("SELECT id,type,subject_person_id,conversation_id,project_key,scope_type,scope_id,confidence,importance,explicit,created_at,updated_at,status FROM memories WHERE status='ACTIVE' ORDER BY importance DESC,updated_at DESC LIMIT 200");return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).header("Pragma","no-cache").body(rows);}
    @GetMapping("/api/prompts") public List<Map<String,Object>> prompts(){return jdbc.queryForList("SELECT id,name,simulation_prompt,persona_prompt,updated_at FROM prompt_profiles ORDER BY name LIMIT 200");}
    @GetMapping("/api/context/diagnostics") public List<Map<String,Object>> contextDiagnostics(){return diagnostics.list();}
    @GetMapping("/api/usage") public Map<String,Object> usage(){return jdbc.queryForMap("SELECT COUNT(*) AS calls,COALESCE(SUM(input_tokens),0) AS inputTokens,COALESCE(SUM(cache_hit_tokens),0) AS cacheHitTokens,COALESCE(SUM(cache_miss_tokens),0) AS cacheMissTokens,COALESCE(SUM(output_tokens),0) AS outputTokens,COALESCE(SUM(reasoning_tokens),0) AS reasoningTokens,'TOKENS_ONLY' AS metric,COALESCE(SUM(duration_ms),0) AS durationMillis FROM token_usage");}
    @GetMapping("/api/usage/conversations") public List<Map<String,Object>> usageByConversation(){return jdbc.queryForList("SELECT conversation_id,COUNT(*) AS calls,SUM(input_tokens) AS input_tokens,SUM(cache_hit_tokens) AS cache_hit_tokens,SUM(cache_miss_tokens) AS cache_miss_tokens,SUM(output_tokens) AS output_tokens,SUM(reasoning_tokens) AS reasoning_tokens,'TOKENS_ONLY' AS metric,SUM(duration_ms) AS duration_ms FROM token_usage GROUP BY conversation_id ORDER BY MAX(usage_time) DESC LIMIT 500");}
    @GetMapping("/api/stickers") public List<Map<String,Object>> stickers(){return jdbc.queryForList("SELECT id,path,sha256,tags_json,note,usage_count,source,created_at,last_used_at FROM stickers ORDER BY usage_count DESC,created_at DESC LIMIT 500");}
    @GetMapping("/api/slang") public org.springframework.http.ResponseEntity<List<Map<String,Object>>> slang(){var rows=jdbc.queryForList("SELECT id,term,meaning,sources_json,status,created_at,updated_at FROM slang_entries WHERE status='CONFIRMED' ORDER BY updated_at DESC LIMIT 500");return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).header("Pragma","no-cache").body(rows);}
    @GetMapping("/api/runtime") public Map<String,Object> runtime(){Map<String,Object> result=new LinkedHashMap<>();result.put("status","UP");result.put("bind",environment.getProperty("server.address","127.0.0.1"));result.put("port",environment.getProperty("server.port","3200"));result.put("runtimeEnabled",integrations.socialEnabled());result.put("dshEnabled",integrations.dshEnabled());result.put("oneBotEnabled",integrations.oneBotEnabled());result.put("modelEnabled",integrations.modelEnabled());result.put("externalAdaptersEnabled",integrations.dshEnabled()||integrations.oneBotEnabled()||integrations.modelEnabled());result.put("liveApiEnabled",Boolean.parseBoolean(environment.getProperty("xingchen.live.enabled","false")));
        result.put("activeConversationCount",safeCount("SELECT COUNT(DISTINCT conversation_id) FROM durable_turn_executions WHERE status IN ('RUNNING','RECOVERING')"));result.put("waitingConversationCount",safeCount("SELECT COUNT(*) FROM simulation_conversation_state WHERE wake_state='WAITING'"));
        var queue=socialRuntime==null?new SocialRuntime.RuntimeQueueDiagnostics(0,0,0,0):socialRuntime.queueDiagnostics();result.put("queuedConversationCount",queue.queuedConversationCount());result.put("queueDepth",queue.queuedEventCount());result.put("resettingConversationCount",queue.resettingConversationCount());
        result.put("pendingOutboundUnknownCount",safeCount("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='UNKNOWN'"));result.put("interruptedTurnCount",safeCount("SELECT COUNT(*) FROM durable_turn_executions WHERE status='INTERRUPTED'"));result.put("recoverableTurnCount",safeCount("SELECT COUNT(*) FROM durable_turn_executions WHERE status IN ('RUNNING','RECOVERABLE','RECOVERING','INTERRUPTED')"));return result;}
    private int safeCount(String sql){try{return jdbc.queryForObject(sql,Integer.class);}catch(org.springframework.dao.DataAccessException unavailable){return 0;}}
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class);}
}
