package online.wanan.xingchen.console;

import online.wanan.xingchen.config.ConversationFakeE2eConfiguration;
import online.wanan.xingchen.core.TestSqliteDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK) @AutoConfigureMockMvc
@ActiveProfiles({"runtime-test","conversation-fake-e2e"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(online.wanan.xingchen.config.TestRuntimeConfiguration.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SocialModelConsoleContractTest {
    private static final Path ROOT=createRoot();
    private static final Path DB=ROOT.resolve("db").resolve("console.db"),SECRETS=ROOT.resolve("secrets");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());p.add("xingchen.secrets.directory",()->SECRETS.toString());p.add("xingchen.owner.platform-user-id",()->"e2e-owner");}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired FakeModelConnectionTester fakeTester;@Autowired SecretStore secretStore;@Autowired SocialSettingsService socialSettings;@Autowired ModelProviderConfigService modelConfig;
    private static Path createRoot(){try{Path path=java.nio.file.Files.createTempDirectory("xingchen-social-model-console-");java.nio.file.Files.createDirectories(path.resolve("db"));return path;}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @BeforeEach void resetConfig(){jdbc.update("DELETE FROM social_settings_override");jdbc.update("DELETE FROM social_settings_global");jdbc.update("DELETE FROM social_settings_audit");jdbc.update("DELETE FROM model_provider_config");jdbc.update("DELETE FROM model_provider_audit");jdbc.update("DELETE FROM model_provider_test_status");secretStore.clear("deepseek-api-key");fakeTester.next("SUCCESS");}
    @AfterAll void clean(){TestSqliteDatabase.clean(jdbc,DB);try{if(java.nio.file.Files.exists(ROOT))try(Stream<Path> paths=java.nio.file.Files.walk(ROOT)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{java.nio.file.Files.deleteIfExists(p);}catch(Exception ignored){}});}}catch(Exception ignored){}}

    @Test @WithMockUser(username="social-admin") void socialSettingsRequireAuthAndUseNoStore() throws Exception {mvc.perform(get("/api/social-settings/global").with(anonymous())).andExpect(status().isUnauthorized());mvc.perform(get("/api/social-settings/global")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.values.mentionWake").value(true)).andExpect(jsonPath("$.values.ordinaryMessageProbability").value(0.0));}

    @Test @WithMockUser(username="social-admin") void globalAndConversationSettingsMergeClearValidateAndAudit() throws Exception {
        String global="{\"expectedRevision\":0,\"values\":{\"ordinaryMessageProbability\":0.25,\"botNameAliases\":[\" Fish \",\"\",\"fish\"],\"configuredSpeakerIds\":[\"QQ:12345\"]}}";
        mvc.perform(patch("/api/social-settings/global").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(global)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.values.ordinaryMessageProbability").value(0.25)).andExpect(jsonPath("$.values.botNameAliases.length()").value(1)).andExpect(jsonPath("$.values.configuredSpeakerIds[0]").value("QQ:12345"));
        String id=ConversationFakeE2eConfiguration.PRIVATE_OWNER;
        mvc.perform(get("/api/social-settings/conversations/{id}",id)).andExpect(status().isOk()).andExpect(jsonPath("$.effective.mentionWake").value(true)).andExpect(jsonPath("$.overrides").isEmpty());
        mvc.perform(patch("/api/social-settings/conversations/{id}",id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0,\"values\":{\"mentionWake\":false,\"ordinaryMessageProbability\":0.8}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.overrideRevision").value(1)).andExpect(jsonPath("$.global.mentionWake").value(true)).andExpect(jsonPath("$.overrides.mentionWake").value(false)).andExpect(jsonPath("$.effective.mentionWake").value(false)).andExpect(jsonPath("$.effective.ordinaryMessageProbability").value(0.8));
        mvc.perform(patch("/api/social-settings/conversations/{id}",id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0,\"values\":{\"mentionWake\":true}}" )).andExpect(status().isConflict());
        mvc.perform(delete("/api/social-settings/conversations/{id}/override",id).param("expectedRevision","1").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.overrides").isEmpty()).andExpect(jsonPath("$.effective.mentionWake").value(true)).andExpect(jsonPath("$.overrideRevision").value(2));
        mvc.perform(patch("/api/social-settings/conversations/{id}",id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"values\":{\"mentionWake\":false}}" )).andExpect(status().isConflict());
        mvc.perform(patch("/api/social-settings/global").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"values\":{\"ordinaryMessageProbability\":1.1}}" )).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/social-settings/global").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"values\":{\"configuredSpeakerIds\":[\"Owner Alias\"]}}" )).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM social_settings_audit WHERE actor='social-admin'",Integer.class)).isGreaterThanOrEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT group_concat(setting_key||' '||COALESCE(old_value_json,'')||' '||COALESCE(new_value_json,''),' ') FROM social_settings_audit",String.class)).contains("mentionWake","ordinaryMessageProbability");
    }

    @Test @WithMockUser(username="social-admin") void socialRuntime001_capturedEffectiveSettingsAreImmutableAcrossHotApply() throws Exception {
        String id=ConversationFakeE2eConfiguration.PRIVATE_OWNER;
        mvc.perform(patch("/api/social-settings/global").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0,\"values\":{\"mentionWake\":false,\"ordinaryMessageProbability\":0.2}}" )).andExpect(status().isOk());
        var turnA=socialSettings.capture(java.util.UUID.fromString(id),java.util.Map.of());
        mvc.perform(patch("/api/social-settings/global").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"values\":{\"mentionWake\":true,\"ordinaryMessageProbability\":0.9}}" )).andExpect(status().isOk());
        var turnB=socialSettings.capture(java.util.UUID.fromString(id),java.util.Map.of());
        assertThat(turnA.mentionWake()).isFalse();assertThat(turnA.ordinaryMessageProbability()).isEqualTo(0.2);assertThat(turnA.globalRevision()).isEqualTo(1);
        assertThat(turnB.mentionWake()).isTrue();assertThat(turnB.ordinaryMessageProbability()).isEqualTo(0.9);assertThat(turnB.globalRevision()).isEqualTo(2);
    }

    @Test @WithMockUser(username="model-admin") void modelConfigSecretAndConnectionTestNeverExposeCredential() throws Exception {
        mvc.perform(get("/api/models/providers").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/models/providers")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.providers[0].providerId").value("deepseek")).andExpect(jsonPath("$.providers[0].reasoningEffortSupported").value(false)).andExpect(jsonPath("$.providers[0].configured").value(false));
        mvc.perform(patch("/api/models/providers/deepseek/config").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0,\"values\":{\"enabled\":true,\"baseUrl\":\"http://127.0.0.1:43123\",\"model\":\"fixture-model\",\"maxOutputTokens\":256}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.model").value("fixture-model"));
        mvc.perform(patch("/api/models/providers/deepseek/config").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"values\":{\"reasoningEffort\":\"HIGH\"}}" )).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/models/providers/deepseek/config").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"values\":{\"baseUrl\":\"file:///C:/secret\"}}" )).andExpect(status().isBadRequest());
        String credential="fake-provider-credential-never-return";
        mvc.perform(post("/api/models/providers/deepseek/credential").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\""+credential+"\"}" )).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(credential))));
        var oldTurn=modelConfig.runtimeSnapshot();
        mvc.perform(patch("/api/models/providers/deepseek/config").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0,\"values\":{\"model\":\"stale-model\"}}" )).andExpect(status().isConflict());
        mvc.perform(post("/api/models/providers/deepseek/credential").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"rotated-fake-e2e-credential\"}" )).andExpect(status().isOk());
        var newTurn=modelConfig.runtimeSnapshot();assertThat(oldTurn.credential()).isEqualTo(credential);assertThat(newTurn.credential()).isEqualTo("rotated-fake-e2e-credential");assertThat(oldTurn.configuration().model()).isEqualTo("fixture-model");assertThat(newTurn.configuration().model()).isEqualTo("fixture-model");
        assertThat(secretStore.read("deepseek-api-key")).contains("rotated-fake-e2e-credential");
        assertThat(jdbc.queryForList("SELECT values_json FROM model_provider_config").toString()).doesNotContain(credential);
        assertThat(jdbc.queryForList("SELECT COALESCE(old_value_json,'')||COALESCE(new_value_json,'') FROM model_provider_audit").toString()).doesNotContain(credential);
        mvc.perform(post("/api/models/providers/deepseek/test").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        fakeTester.next("AUTH_FAILED");mvc.perform(post("/api/models/providers/deepseek/test").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTH_FAILED"));
        mvc.perform(get("/api/models/providers")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(credential)))).andExpect(jsonPath("$.providers[0].lastTest.status").value("AUTH_FAILED"));
        fakeTester.next("TIMEOUT");mvc.perform(post("/api/models/providers/deepseek/test").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TIMEOUT"));
        mvc.perform(delete("/api/models/providers/deepseek/credential").with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        mvc.perform(get("/api/models/runtime")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MISSING_CREDENTIAL"));
        assertThat(secretStore.read("deepseek-api-key")).isEmpty();
        mvc.perform(patch("/api/models/providers/deepseek/config").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"values\":{\"enabled\":false}}" )).andExpect(status().isOk()).andExpect(jsonPath("$.runtimeStatus").value("DISABLED"));
    }
}
