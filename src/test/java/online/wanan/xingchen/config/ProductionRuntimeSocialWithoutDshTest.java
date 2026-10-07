package online.wanan.xingchen.config;

import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.adapter.deepseek.DynamicModelProvider;
import online.wanan.xingchen.adapter.dsh.DshGateway;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.core.FakeOneBotServer;
import online.wanan.xingchen.core.TestSqliteDatabase;
import online.wanan.xingchen.core.agent.DshInteractionCoordinator;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Boots the production bean graph in isolation with DSH disabled and a loopback-only fake OneBot. */
@SpringBootTest(classes = XingChenApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "logging.level.root=ERROR",
                "xingchen.integrations.dsh-enabled=false",
                "xingchen.integrations.onebot-enabled=true",
                "xingchen.integrations.model-enabled=true",
                "xingchen.social.enabled=true",
                "xingchen.onebot.login-user-id=test-bot",
                "xingchen.owner.platform-user-id=owner-test"
        })
@Import(ProductionRuntimeSocialWithoutDshTest.FakeOneBotConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductionRuntimeSocialWithoutDshTest {
    private static final Path DB = TestSqliteDatabase.create("production-social-without-dsh");
    private static final Path DATA = createDataDirectory();
    private static final FakeOneBotServer FAKE = createFake();

    @DynamicPropertySource
    static void isolatedRuntime(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> TestSqliteDatabase.jdbcUrl(DB));
        properties.add("xingchen.memory.database-path", () -> DB.toAbsolutePath().toString());
        properties.add("xingchen.data-dir", () -> DATA.toString());
        properties.add("xingchen.secrets.directory", () -> DATA.resolve("config").toString());
        properties.add("xingchen.sticker.root", () -> DATA.resolve("assets/stickers").toString());
        properties.add("xingchen.onebot.http-url", () -> FAKE.httpUri().toString());
        properties.add("xingchen.onebot.ws-url", () -> FAKE.wsUri().toString());
    }

    @Autowired RuntimeIntegrationStatus switches;
    @Autowired SocialRuntime social;
    @Autowired OneBotGateway oneBot;
    @Autowired DynamicModelProvider modelProvider;
    @Autowired DshGateway dsh;
    @Autowired DshInteractionCoordinator dshInteractions;
    @Autowired JdbcTemplate jdbc;

    @Test void dshOffDoesNotBlockProductionSocialOneBotAndModelBeans() throws Exception {
        await(() -> FAKE.wsConnections() > 0, 5_000);
        assertThat(switches).isEqualTo(new RuntimeIntegrationStatus(false, true, true, true));
        assertThat(social.isRunning()).isTrue();
        assertThat(oneBot.status().connected()).isTrue();
        assertThat(oneBot.probeHttpConnection()).isEqualTo("CONNECTED");
        assertThat(FAKE.actionCount("get_login_info")).isEqualTo(1);
        assertThat(modelProvider).isNotNull();
        assertThat(dsh.isEnabled()).isFalse();
        assertThat(dshInteractions.isRunning()).isFalse();
    }

    @AfterAll void cleanup() {
        FAKE.close();
        TestSqliteDatabase.clean(jdbc, DB);
        deleteDataDirectory();
    }

    private static FakeOneBotServer createFake() {
        try { return new FakeOneBotServer("", ""); }
        catch (IOException failure) { throw new IllegalStateException("could not start isolated fake OneBot", failure); }
    }

    private static Path createDataDirectory() {
        try {
            Path build = Path.of("build").toAbsolutePath();
            java.nio.file.Files.createDirectories(build);
            return java.nio.file.Files.createTempDirectory(build, "xingchen-runtime-gate-");
        }
        catch (IOException failure) { throw new IllegalStateException("could not create isolated runtime data directory", failure); }
    }

    private static void deleteDataDirectory() {
        Path build = Path.of("build").toAbsolutePath().normalize();
        Path target = DATA.toAbsolutePath().normalize();
        if (!target.startsWith(build) || !target.getFileName().toString().startsWith("xingchen-runtime-gate-"))
            throw new IllegalStateException("refusing to clean an unowned isolated test directory");
        try (var paths = java.nio.file.Files.walk(target)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
        } catch (IOException failure) { throw new IllegalStateException("could not clean isolated runtime data directory", failure); }
    }

    private static void await(java.util.function.BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(20);
        assertThat(condition.getAsBoolean()).as("isolated Core established its fake OneBot WS subscription").isTrue();
    }

    @TestConfiguration
    static class FakeOneBotConfiguration {
        @Bean(destroyMethod = "") FakeOneBotServer fakeOneBotServer() { return FAKE; }
    }
}
