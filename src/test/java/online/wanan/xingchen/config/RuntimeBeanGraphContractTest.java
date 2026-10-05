package online.wanan.xingchen.config;

import online.wanan.xingchen.adapter.deepseek.DeepSeekProvider;
import online.wanan.xingchen.adapter.dsh.*;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.FakeOneBotServer;
import online.wanan.xingchen.core.context.SessionLifecycleManager;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import online.wanan.xingchen.core.identity.IdentityPersistence;
import online.wanan.xingchen.core.identity.IdentityRegistry;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.memory.MemoryRepository;
import online.wanan.xingchen.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("runtime-test")
@Import(TestRuntimeConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class RuntimeBeanGraphContractTest {
    private static final java.nio.file.Path DB=online.wanan.xingchen.core.TestSqliteDatabase.create("runtime-bean-graph");
    @DynamicPropertySource static void isolatedDb(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->online.wanan.xingchen.core.TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());}
    @Autowired JdbcTemplate jdbc;
    @AfterAll void cleanDatabase(){online.wanan.xingchen.core.TestSqliteDatabase.clean(jdbc,DB);}
    @Autowired MemoryRepository memories;@Autowired SqliteToolExecutionLedger idempotency;@Autowired SessionLifecycleManager sessions;
    @Autowired SqliteSimulationStateRepository simulation;@Autowired DshGateway dsh;@Autowired OneBotGateway oneBot;
    @Autowired ModelProvider provider;@Autowired SocialRuntime social;@Autowired RuntimeIntegrationStatus switches;
    @Autowired FakeDshRc2Server fakeDsh;@Autowired FakeOneBotServer fakeOneBot;@Autowired IdentityPersistence identities;

    @Test void graphUsesDurableAdaptersAndCreatesWithoutExternalConnections(){
        assertThat(memories).isInstanceOf(MemorySqliteRepository.class);
        assertThat(idempotency).isInstanceOf(SqliteToolExecutionLedger.class);
        assertThat(sessions).isInstanceOf(SqliteSessionLifecycleManager.class);
        assertThat(simulation).isInstanceOf(SqliteSimulationStateRepository.class);
        assertThat(identities).isInstanceOf(SqliteIdentityRepository.class);
        assertThat(dsh).isInstanceOf(DshRc2Adapter.class);
        assertThat(oneBot).isInstanceOf(OneBotV11Gateway.class);
        assertThat(provider).isInstanceOf(online.wanan.xingchen.core.agent.MockModelProvider.class);
        assertThat(social.isRunning()).isFalse();
        assertThat(switches).isEqualTo(new RuntimeIntegrationStatus(false,false,false,false));
        assertThat(fakeDsh.requests()).isEmpty();
        assertThat(fakeOneBot.wsConnections()).isZero();
    }

    @Test void identitySnapshotsSurviveRegistryRecreation(){
        IdentityRegistry first=new IdentityRegistry("bot",java.util.Set.of("owner"),identities);
        var membership=first.observe(Platform.QQ,"member",new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"identity-fixture"),"昵称","群名片",online.wanan.xingchen.core.identity.IdentityRole.MEMBER,java.time.Instant.parse("2026-01-01T00:00:00Z"));
        IdentityRegistry afterRestart=new IdentityRegistry("bot",java.util.Set.of("owner"),identities);
        var person=afterRestart.find(Platform.QQ,"member").orElseThrow();
        assertThat(afterRestart.findById(person.id())).isPresent();
        assertThat(afterRestart.aliases(person.id())).extracting(online.wanan.xingchen.core.identity.PersonAlias::alias).contains("昵称","群名片");
        assertThat(afterRestart.memberships(person.id())).extracting(online.wanan.xingchen.core.identity.Membership::id).contains(membership.id());
    }
}
