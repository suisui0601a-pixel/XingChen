package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.adapter.snowluma.SnowLumaGatewayAdapter;
import online.wanan.xingchen.config.RuntimeIntegrationStatus;
import online.wanan.xingchen.core.gateway.GatewayState;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SnowLumaGatewayAdapterContractTest {
    @Test void gatewayReportsOnlyOneBotTransportAndDocumentedLoginIdentity() throws Exception {
        try(var fake=new FakeOneBotServer("secret");var onebot=new OneBotV11Gateway(new OneBotV11Configuration(fake.httpUri(),fake.wsUri(),"TOKEN","configured-bot",false,1000,1500,50,200,20),new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of("TOKEN","secret"))) {
            var adapter=new SnowLumaGatewayAdapter(onebot,new RuntimeIntegrationStatus(false,true,false,false));
            assertThat(adapter.getStatus().state()).isEqualTo(GatewayState.DISCONNECTED);
            assertThat(adapter.capabilities().qrLogin()).isFalse();assertThat(adapter.capabilities().logout()).isFalse();assertThat(adapter.capabilities().reconnect()).isFalse();
            assertThat(onebot.connect().connected()).isTrue();assertThat(adapter.getStatus().state()).isEqualTo(GatewayState.CONNECTED);
            assertThat(adapter.getAccountInfo()).get().extracting("userId").isEqualTo("123456789");
            assertThat(fake.actionCount("get_login_info")).isEqualTo(1);
        }
    }
    @Test void disabledGatewayDoesNotClaimConnectedOrAccount(){
        var onebot=new OneBotV11Gateway(new OneBotV11Configuration(java.net.URI.create("http://127.0.0.1:1"),java.net.URI.create("ws://127.0.0.1:1"),"TOKEN","",false,100,100,50,100,1),new ObjectMapper(),new OneBotEventNormalizer(new ObjectMapper()),Map.of(),()->false);
        try(onebot){var adapter=new SnowLumaGatewayAdapter(onebot,new RuntimeIntegrationStatus(false,false,false,false));assertThat(adapter.getStatus().state()).isEqualTo(GatewayState.DISABLED);assertThat(adapter.getAccountInfo()).isEmpty();}
    }
}
