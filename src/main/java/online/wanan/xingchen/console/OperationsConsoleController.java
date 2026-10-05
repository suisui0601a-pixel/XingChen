package online.wanan.xingchen.console;

import online.wanan.xingchen.config.RuntimeIntegrationStatus;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.core.agent.ModelProvider;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Manifest;

@RestController @RequestMapping("/api/operations")
public class OperationsConsoleController {
    private final JdbcTemplate jdbc; private final Environment env;
    private final RuntimeIntegrationStatus integrations; private final SocialRuntime runtime; private final OneBotGateway gateway; private final ModelProvider model;
    public OperationsConsoleController(JdbcTemplate jdbc, Environment env, RuntimeIntegrationStatus integrations, SocialRuntime runtime,OneBotGateway gateway,ModelProvider model) {
        this.jdbc=jdbc; this.env=env; this.integrations=integrations; this.runtime=runtime;this.gateway=gateway;this.model=model;
    }
    @GetMapping("/status") public ResponseEntity<Map<String,Object>> status() {
        var memory=ManagementFactory.getMemoryMXBean(); var jvm=ManagementFactory.getRuntimeMXBean();
        String db="UP"; Long bytes=null;
        try { jdbc.queryForObject("SELECT 1",Integer.class); String raw=env.getProperty("XINGCHEN_DB_PATH",env.getProperty("spring.datasource.url",""));
            if(raw.startsWith("jdbc:sqlite:")){Path p=Path.of(raw.substring("jdbc:sqlite:".length()));if(Files.isRegularFile(p))bytes=Files.size(p);}
        } catch(Exception ex){db="DOWN";}
        String flyway="unknown"; try { flyway=jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1",String.class); } catch(Exception ignored){}
        var queue=runtime.queueDiagnostics(); Map<String,Object> result=new LinkedHashMap<>();
        result.put("version",Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("development"));
        result.put("gitCommit",manifestCommit()); result.put("uptimeMillis",jvm.getUptime()); result.put("javaVersion",System.getProperty("java.version"));
        result.put("database",db);result.put("schemaVersion",flyway);result.put("databaseBytes",bytes);
        result.put("activeConversations",count("SELECT COUNT(DISTINCT conversation_id) FROM durable_turn_executions WHERE status IN ('RUNNING','RECOVERING')"));
        result.put("waitingConversations",count("SELECT COUNT(*) FROM simulation_conversation_state WHERE wake_state='WAITING'"));
        result.put("outboundUnknown",count("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='UNKNOWN'"));
        result.put("interruptedTurns",count("SELECT COUNT(*) FROM durable_turn_executions WHERE status='INTERRUPTED'"));
        result.put("queueDepth",queue.queuedEventCount());result.put("oneBotEnabled",integrations.oneBotEnabled());result.put("dshEnabled",integrations.dshEnabled());
        result.put("modelEnabled",integrations.modelEnabled());result.put("externalIntegrationsEnabled",integrations.oneBotEnabled()||integrations.dshEnabled()||integrations.modelEnabled());
        result.put("oneBotConnected",safeGatewayConnected());result.put("modelStatus",!integrations.modelEnabled()?"DISABLED":model.available()?"AVAILABLE":"UNAVAILABLE");
        result.put("heapUsed",memory.getHeapMemoryUsage().getUsed());result.put("heapMax",memory.getHeapMemoryUsage().getMax());result.put("threadCount",ManagementFactory.getThreadMXBean().getThreadCount());
        result.put("configApplyTracking","NOT_TRACKED");result.put("pendingReconnect","UNKNOWN_NOT_TRACKED");result.put("pendingRestart","UNKNOWN_NOT_TRACKED");
        result.put("assetRootConfigured",!env.getProperty("xingchen.sticker.root","").isBlank());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
    private int count(String sql){try{return jdbc.queryForObject(sql,Integer.class);}catch(Exception ex){return 0;}}
    private boolean safeGatewayConnected(){try{return gateway.status().connected();}catch(Exception ex){return false;}}
    private String manifestCommit(){try(var stream=getClass().getResourceAsStream("/META-INF/MANIFEST.MF")){if(stream==null)return "unavailable";return Objects.toString(new Manifest(stream).getMainAttributes().getValue("Implementation-Commit"),"unavailable");}catch(Exception ex){return "unavailable";}}
}
