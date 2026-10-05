package online.wanan.xingchen.console;

import online.wanan.xingchen.config.RuntimeIntegrationStatus;
import online.wanan.xingchen.core.gateway.GatewayManagementPort;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class ConsoleOverviewController {
    private final JdbcTemplate jdbc; private final RuntimeIntegrationStatus runtime; private final Environment env; private final GatewayManagementPort gateway;private final ModelProviderConfigService models;
    public ConsoleOverviewController(JdbcTemplate jdbc, RuntimeIntegrationStatus runtime, Environment env,GatewayManagementPort gateway,ModelProviderConfigService models) { this.jdbc=jdbc;this.runtime=runtime;this.env=env;this.gateway=gateway;this.models=models; }
    @GetMapping("/api/console/overview") public Map<String,Object> overview() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("status","UP"); result.put("version",env.getProperty("xingchen.version","0.1.0-SNAPSHOT"));
        result.put("database","UP"); result.put("runtimeEnabled",runtime.socialEnabled());
        result.put("gateway",gateway.getStatus().state().name().toLowerCase(java.util.Locale.ROOT).replace('_','-'));
        Map<String,Object> model=models.get();boolean modelReady=Boolean.TRUE.equals(model.get("enabled"))&&Boolean.TRUE.equals(model.get("configured"));result.put("model",modelReady?"configured":Boolean.TRUE.equals(model.get("enabled"))?"not-configured":"disabled");
        result.put("activeConversations",safeCount("SELECT COUNT(DISTINCT conversation_id) FROM durable_turn_executions WHERE status IN ('RUNNING','RECOVERING')"));
        result.put("waitingConversations",safeCount("SELECT COUNT(*) FROM simulation_conversation_state WHERE wake_state='WAITING'"));
        result.put("outboundUnknown",safeCount("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='UNKNOWN'"));
        result.put("interruptedTurns",safeCount("SELECT COUNT(*) FROM durable_turn_executions WHERE status='INTERRUPTED'"));
        result.put("externalIntegrationsEnabled",runtime.dshEnabled()||runtime.oneBotEnabled()||modelReady);
        return result;
    }
    private int safeCount(String sql){try{return jdbc.queryForObject(sql,Integer.class);}catch(RuntimeException ignored){return 0;}}
}
