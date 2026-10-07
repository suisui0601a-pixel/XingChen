package online.wanan.xingchen.adapter.onebot;

import online.wanan.xingchen.config.RuntimeIntegrationStatus;
import online.wanan.xingchen.console.ConfigService;
import online.wanan.xingchen.security.XingChenProperties;
import java.net.URI;

/** Applies the shared persisted Gateway settings to the live OneBot transport without JVM restart. */
public final class OneBotConfigurationApplier {
    private final OneBotV11Gateway gateway;private final ConfigService config;private final XingChenProperties properties;
    private final RuntimeIntegrationStatus switches;private final boolean allowNonLoopback;
    public OneBotConfigurationApplier(OneBotGateway gateway,ConfigService config,XingChenProperties properties,RuntimeIntegrationStatus switches,boolean allowNonLoopback){
        if(!(gateway instanceof OneBotV11Gateway concrete))throw new IllegalArgumentException("OneBot runtime does not support live configuration");
        this.gateway=concrete;this.config=config;this.properties=properties;this.switches=switches;this.allowNonLoopback=allowNonLoopback;
    }
    public synchronized void apply(){
        var next=new OneBotV11Configuration(URI.create(config.gatewayEndpoint("httpUrl",properties.onebot().httpUrl())),URI.create(config.gatewayEndpoint("wsUrl",properties.onebot().wsUrl())),"XINGCHEN_ONEBOT_HTTP_ACCESS_TOKEN","XINGCHEN_ONEBOT_WS_ACCESS_TOKEN",properties.onebot().loginUserId(),allowNonLoopback,5000,10000,1000,30000,1000,90000);
        var credentials=java.util.Map.of("XINGCHEN_ONEBOT_HTTP_ACCESS_TOKEN",config.gatewayToken(online.wanan.xingchen.console.GatewaySecretStore.Transport.HTTP),"XINGCHEN_ONEBOT_WS_ACCESS_TOKEN",config.gatewayToken(online.wanan.xingchen.console.GatewaySecretStore.Transport.WS));
        gateway.reconfigure(next,credentials,()->config.gatewayEnabled(switches.oneBotEnabled()));
    }
}
