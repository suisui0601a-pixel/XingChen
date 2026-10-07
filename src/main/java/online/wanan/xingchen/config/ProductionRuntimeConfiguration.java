package online.wanan.xingchen.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.deepseek.*;
import online.wanan.xingchen.adapter.dsh.*;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.adapter.snowluma.SnowLumaGatewayAdapter;
import online.wanan.xingchen.core.gateway.GatewayManagementPort;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.security.XingChenProperties;
import online.wanan.xingchen.console.GatewaySecretStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Clock;
import java.util.Map;

/** Production adapters with explicit opt-in startup. Bean construction is local-only. */
@Configuration
@Profile("!runtime-test")
@Import(RuntimeGraphConfiguration.class)
public class ProductionRuntimeConfiguration {
    @Bean public Clock runtimeClock() { return Clock.systemUTC(); }
    @Bean(destroyMethod="close") public Scheduler runtimeScheduler() { return new ExecutorScheduler(); }
    @Bean public RuntimeIntegrationStatus runtimeIntegrationStatus(
            @Value("${xingchen.integrations.dsh-enabled:false}") boolean dsh,
            @Value("${xingchen.integrations.onebot-enabled:false}") boolean onebot,
            @Value("${xingchen.integrations.model-enabled:false}") boolean model,
            @Value("${xingchen.social.enabled:false}") boolean social,online.wanan.xingchen.console.ConfigService config) {
        return new RuntimeIntegrationStatus(dsh,config.gatewayEnabled(onebot),model,social);
    }
    @Bean public ModelProvider modelProvider(online.wanan.xingchen.console.ModelProviderConfigService config,ObjectMapper mapper) { return new DynamicModelProvider(config,mapper); }
    @Bean public OneBotV11Configuration oneBotConfiguration(XingChenProperties p,online.wanan.xingchen.console.ConfigService config,
            @Value("${xingchen.onebot.allow-non-loopback:false}") boolean allowNonLoopback) {
        return new OneBotV11Configuration(java.net.URI.create(config.gatewayEndpoint("httpUrl",p.onebot().httpUrl())),java.net.URI.create(config.gatewayEndpoint("wsUrl",p.onebot().wsUrl())),
                "XINGCHEN_ONEBOT_HTTP_ACCESS_TOKEN","XINGCHEN_ONEBOT_WS_ACCESS_TOKEN",p.onebot().loginUserId(),allowNonLoopback,5000,10000,1000,30000,1000,90000);
    }
    @Bean(destroyMethod="close") public OneBotGateway oneBotGateway(OneBotV11Configuration config,ObjectMapper mapper,OneBotEventNormalizer normalizer,RuntimeIntegrationStatus switches,online.wanan.xingchen.console.ConfigService storedConfig) {
        var credentials=Map.of("XINGCHEN_ONEBOT_HTTP_ACCESS_TOKEN",storedConfig.gatewayToken(GatewaySecretStore.Transport.HTTP),"XINGCHEN_ONEBOT_WS_ACCESS_TOKEN",storedConfig.gatewayToken(GatewaySecretStore.Transport.WS));
        return new OneBotV11Gateway(config,mapper,normalizer,credentials,()->storedConfig.gatewayEnabled(switches.oneBotEnabled()));
    }
    @Bean @Profile("!gateway-fake-e2e") public OneBotConfigurationApplier oneBotConfigurationApplier(OneBotGateway oneBot,online.wanan.xingchen.console.ConfigService config,XingChenProperties properties,RuntimeIntegrationStatus switches,
            @Value("${xingchen.onebot.allow-non-loopback:false}") boolean allowNonLoopback){return new OneBotConfigurationApplier(oneBot,config,properties,switches,allowNonLoopback);}
    @Bean @Profile("!gateway-fake-e2e") @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(GatewayManagementPort.class)
    public GatewayManagementPort gatewayManagementPort(OneBotGateway oneBot,RuntimeIntegrationStatus switches,online.wanan.xingchen.console.ConfigService config) { return new SnowLumaGatewayAdapter(oneBot,switches,()->config.gatewayEnabled(switches.oneBotEnabled())); }
    @Bean public DshRc2Configuration dshConfiguration(XingChenProperties p,
            @Value("${DSH_WORKSPACE_PATH:${user.dir}}") String workspace,online.wanan.xingchen.console.ConfigService storedConfig,
            @Value("${DSH_MODEL_PROVIDER:deepseek-official}") String provider) {
        return new DshRc2Configuration(java.net.URI.create(p.dsh().baseUrl()),Path.of(workspace).toAbsolutePath(),storedConfig.dshLaunchToken(),provider,Duration.ofSeconds(10));
    }
    @Bean(destroyMethod="close") public DshGateway dshGateway(DshRc2Configuration config,ObjectMapper mapper,RuntimeIntegrationStatus switches) { return new DshRc2Adapter(config,mapper,switches::dshEnabled); }
    @Bean public ApplicationRunner integrationStartup(RuntimeIntegrationStatus switches,OneBotGateway oneBot,
            online.wanan.xingchen.core.conversation.SocialRuntime social,DshInteractionCoordinator interactions) {
        return args -> { if(switches.allEnabled()){social.start();interactions.start();} };
    }
}
