package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import online.wanan.xingchen.console.ConsoleBindGuard;
import online.wanan.xingchen.console.AccessControlService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ConsoleAuthSecurityTest {
    private static final Path DB=TestSqliteDatabase.create("console-auth");
    private static final String USER="console-test-admin";
    private static final String PASSWORD="test-only-admin-password-2036";
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));
        p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());
        p.add("XINGCHEN_CONSOLE_ADMIN_USER",()->USER); p.add("XINGCHEN_CONSOLE_ADMIN_PASSWORD",()->PASSWORD);
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper mapper;
    @Autowired org.springframework.context.ApplicationContext applicationContext;
    @Autowired online.wanan.xingchen.console.UsageConsoleService usageService;
    @Autowired online.wanan.xingchen.console.AccessControlService accessService;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Autowired online.wanan.xingchen.console.SafeOperationalLogStore safeLogs;
    @Value("${server.servlet.session.timeout}") Duration sessionTimeout;
    @AfterAll void cleanup(){TestSqliteDatabase.clean(jdbc,DB);}

    @Test void auth001_protectedManagementApisRequireAuthentication() throws Exception {
        mvc.perform(get("/api/console/overview")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/gateway/status")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        String hash=jdbc.queryForObject("SELECT password_hash FROM console_admin_credentials WHERE username=?",String.class,USER);
        assertThat(hash).startsWith("$2a$").isNotEqualTo(PASSWORD);
    }

    @Test @WithMockUser(username="console-test-admin") void auth011_fakeConversationControlsAreAbsentOutsideFakeProfile() throws Exception {
        for (Class<?> type : java.util.List.of(online.wanan.xingchen.console.ConversationFakeE2eController.class,
                online.wanan.xingchen.console.FakeGatewayE2eController.class,
                online.wanan.xingchen.console.FakeModelConnectionTester.class,
                online.wanan.xingchen.adapter.snowluma.FakeGatewayManagementPort.class)) {
            assertThat(applicationContext.getBeansOfType(type)).as(type.getSimpleName()).isEmpty();
        }
        mvc.perform(get("/api/fake/conversations/private-owner/preservation")).andExpect(status().isNotFound());
        mvc.perform(get("/api/fake/conversations/internal-failure")).andExpect(status().isNotFound());
        mvc.perform(post("/api/fake/conversations/private-owner/waiting").with(realCsrf())).andExpect(status().isNotFound());
    }

    @Test void auth012_responseHeadersAndForbiddenErrorsHaveSafeJsonContracts() throws Exception {
        var result=mvc.perform(get("/api/console/overview")).andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().string("X-Frame-Options","DENY"))
            .andExpect(header().string("Referrer-Policy","no-referrer"))
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn();
        var json=mapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.fieldNames()).toIterable().containsExactlyInAnyOrder("code","message","traceId");
        mvc.perform(post("/api/security/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("REQUEST_FORBIDDEN"))
            .andExpect(jsonPath("$.traceId").isString());
    }

    @Test void auth002_003_004_bootstrapLoginRotatesSessionAndLogoutInvalidatesIt() throws Exception {
        MockHttpSession session=new MockHttpSession(); String oldId=session.getId();
        mvc.perform(post("/api/auth/login").session(session).with(realCsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+USER+"\",\"password\":\"wrong-password-000\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        MvcResult login=mvc.perform(post("/api/auth/login").session(session).with(realCsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+USER+"\",\"password\":\""+PASSWORD+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true)).andReturn();
        assertThat(session.getId()).isNotEqualTo(oldId);
        Cookie sessionCookie=login.getResponse().getCookie("JSESSIONID");
        if (sessionCookie != null) { assertThat(sessionCookie.isHttpOnly()).isTrue(); assertThat(sessionCookie.getSecure()).isFalse(); }
        mvc.perform(get("/api/console/overview").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.externalIntegrationsEnabled").value(false));
        mvc.perform(post("/api/auth/logout").session(session).with(realCsrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.loggedOut").value(true));
        mvc.perform(get("/api/console/overview").session(session)).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(username="console-test-admin") void auth005_006_mutationsRequireCsrfAndValidatedConfigIsAudited() throws Exception {
        mvc.perform(put("/api/console/config/Console/publicBaseUrl").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"https://console.example.test\"}"))
                .andExpect(status().isForbidden());
        Csrf csrf=csrf(null);
        mvc.perform(put("/api/console/config/Console/publicBaseUrl").cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"https://console.example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applyMode").value("RESTART_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM console_configuration_audit WHERE config_key='publicBaseUrl'",Integer.class)).isEqualTo(1);
        mvc.perform(put("/api/console/config/Console/publicBaseUrl").cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/console/config/Gateway/httpUrl").cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"http://127.0.0.1:3000\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.applyMode").value("HOT_APPLY"));
        mvc.perform(put("/api/console/config/Gateway/httpUrl").cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"http://example.com:3000\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/console/config/Gateway/wsUrl").cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"file:///tmp/onebot\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void auth007_nonLoopbackBindFailsClosedUnlessExplicitlyAllowed() {
        assertThatThrownBy(()->ConsoleBindGuard.validate("0.0.0.0",false)).hasMessageContaining("XINGCHEN_CONSOLE_ALLOW_REMOTE");
        assertThatThrownBy(()->ConsoleBindGuard.validate("0.0.0.0",true,false)).hasMessageContaining("authentication");
        ConsoleBindGuard.validate("0.0.0.0",true,true);
        ConsoleBindGuard.validate("127.0.0.1",false);
    }

    @Test @WithMockUser(username="console-test-admin") void auth008_secretReadReturnsOnlyConfiguredFlags() throws Exception {
        String body=mvc.perform(get("/api/console/secrets/status")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json=mapper.readTree(body);
        assertThat(json.has("deepSeekApiKeyConfigured")).isTrue();
        assertThat(json.toString()).doesNotContain("sk-").doesNotContain("apiKey").doesNotContain("token\"");
    }

    @Test @WithMockUser(username="console-test-admin") void gw001_statusIsNoStoreAndManagementDoesNotPretendLoginControlsExist() throws Exception {
        String body=mvc.perform(get("/api/gateway/status")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("accessToken").doesNotContain("Authorization");
        Csrf csrf=csrf(null);
        mvc.perform(post("/api/gateway/login").cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token()))
                .andExpect(status().isNotImplemented()).andExpect(jsonPath("$.supported").value(false));
    }

    @Test void auth009_loginEndpointReturnsRateLimitAfterFiveInvalidAttempts() throws Exception {
        String body="{\"username\":\"throttle-user\",\"password\":\"not-the-password-000\"}";
        for(int i=0;i<5;i++) mvc.perform(post("/api/auth/login").with(realCsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(realCsrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test void auth010_sessionExpiryPolicyIsThirtyMinutesAndLogoutInvalidationIsCovered() {
        assertThat(sessionTimeout).isEqualTo(Duration.ofMinutes(30));
    }

    @Test void phase4b4cUsageIsBoundedPrivateAndPricesAreRevisioned() throws Exception {
        String stamp=java.time.Instant.now().toString();jdbc.update("INSERT INTO token_usage(id,provider,conversation_id,turn_id,model,usage_time,input_tokens,cache_hit_tokens,cache_miss_tokens,output_tokens,reasoning_tokens,cost,duration_ms,reasoning_available) VALUES('usage-4c-test','fixture',NULL,'turn-4c','model-4c',?,100,20,80,40,5,0,25,0)",stamp);
        jdbc.update("INSERT INTO console_pricing(provider,model,input,cache_hit,cache_miss,output,reasoning,currency,revision,updated_at,updated_by) VALUES('fixture','model-4c',1,.2,1,2,2,'USD',1,?,'test')",stamp);
        mvc.perform(get("/api/usage/summary?window=24h")).andExpect(status().isUnauthorized());
        String summary=mvc.perform(get("/api/usage/summary?window=24h").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USER)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn().getResponse().getContentAsString();
        assertThat(summary).contains("cacheSemantics","reasoningSemantics").doesNotContain("message body","prompt body","hidden");
        assertThat(usageService.records("24h",null,null,"fixture","model-4c",null,null,0,10)).hasSize(1);
        assertThatThrownBy(()->usageService.records("custom","2020-01-01","2021-01-01",null,null,null,null,0,10)).isInstanceOf(IllegalArgumentException.class);
        String priceWrite="{\"revision\":0,\"rows\":[{\"provider\":\"fixture\",\"model\":\"model-4c\",\"input\":1.0,\"cacheHit\":0.2,\"cacheMiss\":1.0,\"output\":2.0,\"reasoning\":2.0,\"currency\":\"USD\"}]}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/pricing").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USER)).with(realCsrf()).contentType(MediaType.APPLICATION_JSON).content(priceWrite)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/pricing").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USER)).with(realCsrf()).contentType(MediaType.APPLICATION_JSON).content(priceWrite)).andExpect(status().isConflict());
        jdbc.update("DELETE FROM console_pricing_audit WHERE model='model-4c'");jdbc.update("UPDATE console_pricing_meta SET revision=0 WHERE singleton=1");
        jdbc.update("DELETE FROM console_pricing WHERE provider='fixture' AND model='model-4c'");jdbc.update("DELETE FROM token_usage WHERE id='usage-4c-test'");
    }

    @Test void phase4b4cAccessFailsClosedAndUsesStableIdentityWithDenyPriority() {
        jdbc.update("DELETE FROM console_access_rules");
        var initial=accessService.revision();assertThat(accessService.evaluate("QQ","stable-user","PRIVATE","group-x",false)).isEqualTo(new online.wanan.xingchen.console.AccessControlService.Decision(false,"DEFAULT_DENY"));
        accessService.replace(java.util.List.of(new online.wanan.xingchen.console.AccessControlService.Rule("PRIVATE","QQ","stable-user","ALLOW"),new online.wanan.xingchen.console.AccessControlService.Rule("GROUP","QQ","group-x","DENY")),initial,"test-admin");
        assertThat(accessService.evaluate("QQ","stable-user","GROUP","group-x",false)).isEqualTo(new online.wanan.xingchen.console.AccessControlService.Decision(false,"EXPLICIT_DENY"));
        assertThatThrownBy(()->accessService.replace(java.util.List.of(),initial,"test-admin")).isInstanceOf(java.util.ConcurrentModificationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM console_access_audit",Integer.class)).isGreaterThan(0);
        jdbc.update("DELETE FROM console_access_rules");
    }

    @Test void access001_privateAndGroupScopesUseStableIdsAndEmptyRulesFailClosed() {
        jdbc.update("DELETE FROM console_access_rules");
        var rules=java.util.List.of(
            new online.wanan.xingchen.console.AccessControlService.Rule("PRIVATE","QQ","access-private-allowed","ALLOW"),
            new AccessControlService.Rule("PRIVATE","QQ","access-private-denied","DENY"),
            new AccessControlService.Rule("GROUP","QQ","access-group-allowed","ALLOW"),
            new AccessControlService.Rule("GROUP","QQ","access-group-denied","DENY"));
        accessService.replace(rules,accessService.revision(),"audit-admin");
        assertThat(accessService.evaluate("QQ","access-private-allowed","PRIVATE","",false).allowed()).isTrue();
        assertThat(accessService.evaluate("QQ","access-private-denied","PRIVATE","",false).reason()).isEqualTo("EXPLICIT_DENY");
        assertThat(accessService.evaluate("QQ","unknown","GROUP","access-group-allowed",false).allowed()).isTrue();
        assertThat(accessService.evaluate("QQ","unknown","GROUP","access-group-denied",false).reason()).isEqualTo("EXPLICIT_DENY");
        assertThat(accessService.evaluate("QQ","access-private-allowed","GROUP","unknown",false).allowed()).isFalse();
        assertThat(accessService.evaluate("DISCORD","access-private-allowed","PRIVATE","",false).allowed()).isFalse();
        accessService.replace(java.util.List.of(),accessService.revision(),"audit-admin");
        assertThat(accessService.evaluate("QQ","access-private-allowed","PRIVATE","",false).allowed()).isFalse();
        assertThat(accessService.evaluate("QQ","unknown","GROUP","access-group-allowed",false).allowed()).isFalse();
    }

    @Test void phase4b4cSafeLogsAndOpsExposeOnlyBoundedApplicationDiagnostics() throws Exception {
        safeLogs.record("WARN","AUTH","LOGIN_FAILED");
        assertThat(safeLogs.list("WARN","AUTH",null,null,null,0,10)).anySatisfy(e->assertThat(e.get("message")).isEqualTo("LOGIN_FAILED"));
        mvc.perform(get("/api/operations/status")).andExpect(status().isUnauthorized());
        String body=mvc.perform(get("/api/operations/status").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USER))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("javaVersion","schemaVersion","configApplyTracking","UNKNOWN_NOT_TRACKED").doesNotContain("DEEPSEEK_API_KEY","password_hash","environmentVariables");
        String logBody=mvc.perform(get("/api/logs/operational?path=C:%5Csecret").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(USER))).andExpect(status().isOk()).andExpect(jsonPath("$.records").isArray()).andReturn().getResponse().getContentAsString();assertThat(logBody).doesNotContain("C:","secret");
    }

    @Test void phase4b4cPasswordRotationInvalidatesRegisteredSessionAndOldCredential() throws Exception {
        String user="rotation-test-admin",old="rotating-test-password-2036",next="rotated-test-password-2036";String now=java.time.Instant.now().toString();jdbc.update("INSERT OR REPLACE INTO console_admin_credentials(username,password_hash,created_at,updated_at,auth_generation) VALUES(?,?,?,?,0)",user,passwordEncoder.encode(old),now,now);
        MockHttpSession session=new MockHttpSession();Csrf csrf=csrf(session);MvcResult login=mvc.perform(post("/api/auth/login").session(session).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token()).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\""+old+"\"}")).andExpect(status().isOk()).andReturn();
        Csrf rotateCsrf=csrf(session);mvc.perform(post("/api/security/password").session(session).cookie(rotateCsrf.cookie()).header("X-XSRF-TOKEN",rotateCsrf.token()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(user)).contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\""+old+"\",\"newPassword\":\""+next+"\",\"confirmation\":\""+next+"\"}")).andExpect(status().isOk());
        assertThat(passwordEncoder.matches(next,jdbc.queryForObject("SELECT password_hash FROM console_admin_credentials WHERE username=?",String.class,user))).isTrue();
        assertThat(mvc.perform(get("/api/console/overview").session(session)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/api/auth/login").cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token()).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\""+old+"\"}")).andReturn().getResponse().getStatus()).isEqualTo(401);
        jdbc.update("DELETE FROM console_admin_credentials WHERE username=?",user);
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        MvcResult result=mvc.perform(get("/api/auth/csrf").session(session==null?new MockHttpSession():session)).andExpect(status().isOk()).andReturn();
        JsonNode json=mapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie=result.getResponse().getCookie("XSRF-TOKEN");
        if(cookie==null)for(String header:result.getResponse().getHeaders("Set-Cookie")){String pair=header.split(";",2)[0];int equals=pair.indexOf('=');if(equals>0&&pair.substring(0,equals).equals("XSRF-TOKEN")){cookie=new Cookie("XSRF-TOKEN",pair.substring(equals+1));break;}}
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(json.get("token").asText());
        return new Csrf(json.get("token").asText(),cookie);
    }
    // Exercise the actual HttpOnly Cookie repository; mock csrf() swaps in a session
    // repository and contaminates later real-cookie assertions in this shared context.
    private org.springframework.test.web.servlet.request.RequestPostProcessor realCsrf() throws Exception {
        Csrf csrf=csrf(null);
        return request->{request.setCookies(csrf.cookie());request.addHeader("X-XSRF-TOKEN",csrf.token());return request;};
    }
    private record Csrf(String token,Cookie cookie){}
}
