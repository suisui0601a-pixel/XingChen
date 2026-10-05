package online.wanan.xingchen.console;

import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.core.FakeOneBotServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Boot restart over the same isolated database/store, never production integrations. */
class EffectiveConfigurationRestartTest {
    @TempDir(factory = BuildDirectory.class) Path root;
    public static final class BuildDirectory implements org.junit.jupiter.api.io.TempDirFactory {
        @Override public Path createTempDirectory(org.junit.jupiter.api.extension.AnnotatedElementContext element,
                org.junit.jupiter.api.extension.ExtensionContext context) throws java.io.IOException {
            return Files.createTempDirectory(Path.of("build").toAbsolutePath(), "xingchen-effective-config-");
        }
    }
    private ServletWebServerApplicationContext start(String url, FakeOneBotServer server) {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(XingChenApplication.class).run(
                "--server.address=127.0.0.1", "--server.port=0", "--spring.profiles.active=test",
                "--spring.datasource.url=jdbc:sqlite:" + root.resolve("test.db").toString().replace('\\','/'),
                "--xingchen.memory.database-path=" + root.resolve("test.db"),
                "--xingchen.secrets.directory=" + root.resolve("secrets"),
                "--XINGCHEN_PUBLIC_BASE_URL=" + url, "--xingchen.console.cookie-secure=false",
                "--xingchen.integrations.onebot-enabled=true", "--xingchen.integrations.model-enabled=false",
                "--xingchen.integrations.dsh-enabled=false", "--xingchen.social.enabled=false",
                "--xingchen.onebot.http-url=" + server.httpUri(), "--xingchen.onebot.ws-url=" + server.wsUri(),
                "--XINGCHEN_ONEBOT_ACCESS_TOKEN=fixture-old", "--ONEBOT_ACCESS_TOKEN=fixture-alias",
                "--XINGCHEN_DEEPSEEK_API_KEY=", "--DEEPSEEK_API_KEY=", "--DSH_LAUNCH_TOKEN=",
                "--XINGCHEN_CONSOLE_ADMIN_USER=", "--XINGCHEN_CONSOLE_ADMIN_PASSWORD=");
    }
    private MockMvc mvc(ServletWebServerApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    private void saveUrl(MockMvc mvc, String url) throws Exception {
        mvc.perform(put("/api/console/config/Console/publicBaseUrl").with(user("audit-admin")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"" + url + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applyMode").value("RESTART_REQUIRED"));
    }

    @Test void configBoot001_002_003_004_restartUsesPersistedUrlInCookieAndPostureIncludingExplicitEmpty() throws Exception {
        try (var server = new FakeOneBotServer(null)) {
            try (var context = start("http://bootstrap.example.test", server)) {
                assertThat(context.getBean(EffectiveConsoleConfiguration.class).publicBaseUrl()).isEqualTo("http://bootstrap.example.test");
                assertThat(context.getServletContext().getSessionCookieConfig().isSecure()).isFalse();
                saveUrl(mvc(context), "https://persisted.example.test");
                // RESTART_REQUIRED is truthful: no premature change to the live cookie snapshot.
                assertThat(context.getBean(EffectiveConsoleConfiguration.class).cookieSecure()).isFalse();
            }
            try (var context = start("http://bootstrap.example.test", server)) {
                assertThat(context.getBean(EffectiveConsoleConfiguration.class).publicBaseUrl()).isEqualTo("https://persisted.example.test");
                assertThat(context.getServletContext().getSessionCookieConfig().isSecure()).isTrue();
                mvc(context).perform(get("/api/security/posture").with(user("audit-admin")))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.cookieSecure").value(true))
                        .andExpect(jsonPath("$.publicBaseUrlConfigured").value(true));
                saveUrl(mvc(context), "");
            }
            try (var context = start("https://bootstrap.example.test", server)) {
                assertThat(context.getBean(EffectiveConsoleConfiguration.class).publicBaseUrl()).isEmpty();
                assertThat(context.getServletContext().getSessionCookieConfig().isSecure()).isFalse();
                mvc(context).perform(get("/api/security/posture").with(user("audit-admin")))
                        .andExpect(jsonPath("$.publicBaseUrlConfigured").value(false));
            }
        }
    }

    @Test void secretBoot001_002_003_004_andSecCred001_002_003_liveTransportAndRestartFollowConsoleState() throws Exception {
        try (var server = new FakeOneBotServer(null)) {
            try (var context = start("", server)) {
                var gateway = context.getBean(OneBotGateway.class);
                assertThat(gateway.getLoginInfo()).isPresent();
                assertThat(server.lastHttpAuthorization()).isEqualTo("Bearer fixture-old");
                var mvc = mvc(context);
                mvc.perform(post("/api/gateway/secret").with(user("audit-admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"replace\",\"token\":\"fixture-new\"}"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));
                assertThat(gateway.getLoginInfo()).isPresent();
                assertThat(server.lastHttpAuthorization()).isEqualTo("Bearer fixture-new");
                mvc.perform(post("/api/gateway/secret").with(user("audit-admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"clear\"}"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
                assertThat(gateway.getLoginInfo()).isPresent();
                assertThat(server.lastHttpAuthorization()).isNull();
                mvc.perform(post("/api/models/providers/deepseek/credential").with(user("audit-admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"fixture-provider\"}"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));
                String posture = mvc.perform(get("/api/security/posture").with(user("audit-admin")))
                        .andExpect(jsonPath("$.secretStore.oneBotTokenConfigured").value(false))
                        .andExpect(jsonPath("$.secretStore.deepSeekApiKeyConfigured").value(true))
                        .andReturn().getResponse().getContentAsString();
                assertThat(posture).doesNotContain("fixture-old", "fixture-new", "fixture-provider", "fixture-alias");
            }
            try (var context = start("", server)) {
                var gateway = context.getBean(OneBotGateway.class);
                assertThat(gateway.getLoginInfo()).isPresent();
                assertThat(server.lastHttpAuthorization()).isNull();
                assertThat(context.getBean(ConfigService.class).gatewayToken()).isEmpty();
                mvc(context).perform(post("/api/gateway/secret").with(user("audit-admin")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"replace\",\"token\":\"fixture-after-clear\"}"))
                        .andExpect(status().isOk());
                assertThat(gateway.getLoginInfo()).isPresent();
                assertThat(server.lastHttpAuthorization()).isEqualTo("Bearer fixture-after-clear");
            }
        }
    }

    @Test void secretBoot005_legacyFileAndCanonicalFileWinWithoutMetadataOrEnvironmentOverwrite() throws Exception {
        Path directory = root.resolve("secrets");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("onebot-access-token"), "fixture-legacy");
        var environment = new MockEnvironment().withProperty("XINGCHEN_ONEBOT_ACCESS_TOKEN", "fixture-env");
        var store = new FileSecretStore(directory.toString(), environment);
        var gateway = new GatewaySecretStore(store, directory.toString());
        gateway.bootstrap();
        assertThat(gateway.read()).contains("fixture-legacy");
        assertThat(Files.exists(directory.resolve("onebot-access-token"))).isFalse();
        assertThat(Files.exists(directory.resolve("onebot-access-token.secret"))).isTrue();
        gateway.clear();
        new GatewaySecretStore(new FileSecretStore(directory.toString(), environment), directory.toString()).bootstrap();
        assertThat(gateway.configured()).isFalse();
        store.replace("deepseek-api-key", "fixture-existing");
        var restarted = new FileSecretStore(directory.toString(), environment.withProperty("DEEPSEEK_API_KEY", "fixture-env-provider"));
        restarted.bootstrapEnvironment("deepseek-api-key", "DEEPSEEK_API_KEY");
        assertThat(restarted.read("deepseek-api-key")).contains("fixture-existing");
    }
}
