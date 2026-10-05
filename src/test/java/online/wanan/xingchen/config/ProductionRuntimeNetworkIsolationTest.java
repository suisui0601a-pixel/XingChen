package online.wanan.xingchen.config;

import online.wanan.xingchen.adapter.deepseek.DynamicModelProvider;
import online.wanan.xingchen.adapter.dsh.DshGateway;
import online.wanan.xingchen.adapter.dsh.DshRc2Adapter;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.adapter.onebot.OneBotV11Gateway;
import online.wanan.xingchen.core.agent.ModelProvider;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ProductionRuntimeNetworkIsolationTest {
    private static final Probe dsh=new Probe(), onebotHttp=new Probe(), onebotWs=new Probe(), model=new Probe();
    private static final java.nio.file.Path DB=online.wanan.xingchen.core.TestSqliteDatabase.create("production-runtime-graph");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->online.wanan.xingchen.core.TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());
        p.add("xingchen.dsh.base-url",()->dsh.httpUrl());p.add("xingchen.onebot.http-url",()->onebotHttp.httpUrl());p.add("xingchen.onebot.ws-url",()->onebotWs.wsUrl());p.add("xingchen.model.base-url",()->model.httpUrl());
        p.add("xingchen.integrations.dsh-enabled",()->"false");p.add("xingchen.integrations.onebot-enabled",()->"false");p.add("xingchen.integrations.model-enabled",()->"false");p.add("xingchen.social.enabled",()->"false");
    }
    @Autowired JdbcTemplate jdbc;@Autowired DshGateway dshGateway;@Autowired OneBotGateway oneBotGateway;@Autowired ModelProvider modelProvider;
    @Autowired RuntimeIntegrationStatus switches;@Autowired SocialRuntime social;
    @AfterAll void cleanup(){online.wanan.xingchen.core.TestSqliteDatabase.clean(jdbc,DB);dsh.close();onebotHttp.close();onebotWs.close();model.close();}

    @Test void productionUsesRealAdaptersButDisabledStartupDoesNotConnect(){
        assertThat(dshGateway).isInstanceOf(DshRc2Adapter.class);
        assertThat(oneBotGateway).isInstanceOf(OneBotV11Gateway.class);
        assertThat(modelProvider).isInstanceOf(DynamicModelProvider.class);
        assertThat(switches).isEqualTo(new RuntimeIntegrationStatus(false,false,false,false));
        assertThat(social.isRunning()).isFalse();
        assertThat(oneBotGateway.connect().connected()).isFalse();
        assertThatThrownBy(()->dshGateway.findOrCreateWorkspace("."))
                .isInstanceOf(online.wanan.xingchen.adapter.dsh.DshRc2Exception.class)
                .hasMessage("DSH integration is disabled");
        assertThatThrownBy(()->modelProvider.complete(null))
                .isInstanceOf(IllegalStateException.class).hasMessage("model provider is unavailable");
        assertThat(dsh.connections()).isZero();assertThat(onebotHttp.connections()).isZero();assertThat(onebotWs.connections()).isZero();assertThat(model.connections()).isZero();
    }

    private static final class Probe implements AutoCloseable {
        private final ServerSocket server;private final AtomicInteger connections=new AtomicInteger();private volatile boolean closed;
        Probe(){try{server=new ServerSocket();server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));Thread t=new Thread(this::accept,"runtime-no-network-probe");t.setDaemon(true);t.start();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
        String httpUrl(){return "http://127.0.0.1:"+server.getLocalPort();}String wsUrl(){return "ws://127.0.0.1:"+server.getLocalPort()+"/onebot";}int connections(){return connections.get();}
        private void accept(){while(!closed)try(var socket=server.accept()){connections.incrementAndGet();}catch(IOException e){if(!closed)throw new IllegalStateException(e);}}
        @Override public void close(){closed=true;try{server.close();}catch(IOException ignored){}}
    }
}
