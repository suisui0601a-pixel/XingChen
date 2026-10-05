package online.wanan.xingchen.config;

import online.wanan.xingchen.adapter.snowluma.FakeGatewayManagementPort;
import online.wanan.xingchen.core.gateway.GatewayManagementPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;

/** Only activated by the isolated browser harness; never part of the default runtime. */
@Configuration(proxyBeanMethods=false)
@Profile("gateway-fake-e2e")
public class FakeGatewayE2eConfiguration {
    @Bean @Primary public FakeGatewayManagementPort fakeGatewayManagementPort(){return new FakeGatewayManagementPort();}
}
