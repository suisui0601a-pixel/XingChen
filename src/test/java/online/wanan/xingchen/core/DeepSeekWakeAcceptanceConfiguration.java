package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.deepseek.DeepSeekConfiguration;
import online.wanan.xingchen.adapter.deepseek.DeepSeekProvider;
import online.wanan.xingchen.console.AccessControlService;
import online.wanan.xingchen.core.agent.ModelProvider;
import online.wanan.xingchen.core.agent.ParticipationPolicy;
import online.wanan.xingchen.security.XingChenProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Real DeepSeek adapter with an in-memory, non-content prompt audit; Fake OneBot remains the only gateway. */
@TestConfiguration(proxyBeanMethods=false)
public class DeepSeekWakeAcceptanceConfiguration {
    @Bean @Primary public ModelProvider realDeepSeekAcceptanceProvider(ObjectMapper mapper) {
        Map<String,String> env=System.getenv();
        DeepSeekConfiguration base=DeepSeekConfiguration.fromEnvironment(env);
        DeepSeekConfiguration bounded=new DeepSeekConfiguration(base.baseUrl(),base.model(),base.apiKeyEnvironmentVariable(),
                base.connectTimeoutMillis(),90_000,0,0,128);
        return new PromptShapeAuditProvider(new DeepSeekProvider(bounded,mapper,env));
    }

    @Bean @Primary public ParticipationPolicy seededAcceptanceParticipation() {
        AtomicInteger draw=new AtomicInteger();
        return new ParticipationPolicy(()->draw.getAndIncrement()%2==0?.25:.75);
    }

    @Bean @Primary public CountingWakeAccessControlService countingWakeAccessControlService(JdbcTemplate jdbc, XingChenProperties properties) {
        return new CountingWakeAccessControlService(jdbc, properties);
    }

    @Bean @Primary public IsolatedWakeOneBotGateway isolatedWakeOneBotGateway(
            @org.springframework.beans.factory.annotation.Qualifier("oneBotGateway") online.wanan.xingchen.adapter.onebot.OneBotGateway delegate) {
        return new IsolatedWakeOneBotGateway(delegate);
    }

    public static class CountingWakeAccessControlService extends AccessControlService {
        private final AtomicInteger evaluations=new AtomicInteger();
        CountingWakeAccessControlService(JdbcTemplate jdbc, XingChenProperties properties) { super(jdbc, properties); }
        @Override public Decision evaluate(String platform, String actorId, String scope, String conversationId, boolean owner) {
            evaluations.incrementAndGet();
            return super.evaluate(platform, actorId, scope, conversationId, owner);
        }
        int evaluations() { return evaluations.get(); }
    }
}
