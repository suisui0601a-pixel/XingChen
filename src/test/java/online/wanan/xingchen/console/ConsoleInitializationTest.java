package online.wanan.xingchen.console;

import jakarta.servlet.http.Cookie;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConsoleInitializationTest {
    private static final Path DB = TestSqliteDatabase.create("console-initialization");
    private static final String PASSWORD = "initialization-fixture-password-2037";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> TestSqliteDatabase.jdbcUrl(DB));
        p.add("xingchen.memory.database-path", () -> DB.toAbsolutePath().toString());
        p.add("xingchen.console.initialize-enabled", () -> "true");
        p.add("XINGCHEN_CONSOLE_ADMIN_USER", () -> "");
        p.add("XINGCHEN_CONSOLE_ADMIN_PASSWORD", () -> "");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ConsoleInitializationStore store;
    @BeforeEach void empty() { jdbc.update("DELETE FROM console_admin_credentials"); }
    @AfterAll void clean() { TestSqliteDatabase.clean(jdbc, DB); }
    private MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr("127.0.0.1"); request.setLocalAddr("127.0.0.1");
            request.setServerName("127.0.0.1"); request.setServerPort(13200); return request;
        });
    }
    private String payload(String user) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + PASSWORD + "\",\"confirmation\":\"" + PASSWORD + "\"}";
    }
    private MockHttpServletRequestBuilder initialize(String user) {
        return local(post("/api/auth/initialize")).with(csrf()).header("Origin", "http://127.0.0.1:13200")
                .contentType(MediaType.APPLICATION_JSON).content(payload(user));
    }
    @Test void setup001_emptyLocalStatusIsNoStoreButManagementStillUnauthorized() throws Exception {
        mvc.perform(local(get("/api/auth/initialize"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.initializationRequired").value(true)).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(local(get("/api/console/overview"))).andExpect(status().isUnauthorized());
    }
    @Test void setup002_creationHashesCredentialClosesBothEndpointsAndGrantsNoSession() throws Exception {
        var created = mvc.perform(initialize("fixture-first-admin")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.initialized").value(true)).andReturn();
        assertThat(created.getResponse().getContentAsString()).doesNotContain(PASSWORD, "fixture-first-admin");
        if (created.getRequest().getSession(false) != null)
            assertThat(created.getRequest().getSession(false).getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        var hash = jdbc.queryForObject("SELECT password_hash FROM console_admin_credentials", String.class);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        mvc.perform(local(get("/api/auth/initialize"))).andExpect(status().isNotFound());
        mvc.perform(initialize("fixture-other-admin")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM console_admin_credentials", Integer.class)).isEqualTo(1);
    }
    @Test void setup003_realCsrfCookieRequiredAndSuccessfulInitializationDoesNotBypassLogin() throws Exception {
        mvc.perform(local(post("/api/auth/initialize")).header("Origin", "http://127.0.0.1:13200")
                .contentType(MediaType.APPLICATION_JSON).content(payload("fixture-real-csrf"))).andExpect(status().isForbidden());
        var tokenResult = mvc.perform(local(get("/api/auth/csrf"))).andExpect(status().isOk()).andReturn();
        var token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(tokenResult.getResponse().getContentAsString()).get("token").asText();
        Cookie cookie = tokenResult.getResponse().getCookie("XSRF-TOKEN");
        mvc.perform(local(post("/api/auth/initialize")).header("Origin", "http://127.0.0.1:13200")
                .cookie(cookie).header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON)
                .content(payload("fixture-real-csrf"))).andExpect(status().isCreated())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
        mvc.perform(local(get("/api/auth/session"))).andExpect(status().isUnauthorized());
    }
    @Test void setup004_externalSourcesAndForgedForwardingNeverEnableSetup() throws Exception {
        mvc.perform(initialize("fixture-remote").with(request -> { request.setRemoteAddr("198.51.100.8"); return request; })
                .header("X-Forwarded-For", "127.0.0.1")).andExpect(status().isNotFound());
        mvc.perform(initialize("fixture-proxy").with(request -> { request.setLocalAddr("172.19.0.5"); return request; })
                .header("Forwarded", "for=127.0.0.1;host=127.0.0.1;proto=http")).andExpect(status().isNotFound());
        mvc.perform(initialize("fixture-host").with(request -> { request.setServerName("attacker.example"); return request; })
                .header("X-Forwarded-Host", "127.0.0.1")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM console_admin_credentials", Integer.class)).isZero();
    }
    @Test void setup005_crossOriginMissingOriginAndCrossSiteFetchAreRejected() throws Exception {
        mvc.perform(initialize("fixture-origin").with(request -> {
            request.removeHeader("Origin"); request.addHeader("Origin", "http://attacker.example"); return request;
        })).andExpect(status().isNotFound());
        mvc.perform(local(post("/api/auth/initialize")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(payload("fixture-origin"))).andExpect(status().isNotFound());
        mvc.perform(initialize("fixture-origin").header("Sec-Fetch-Site", "cross-site")).andExpect(status().isNotFound());
    }
    @Test void setup006_invalidPasswordsAndConfirmationCannotCreateAnAccount() throws Exception {
        for (String password : java.util.List.of("short", "a".repeat(30), "界".repeat(25))) {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    java.util.Map.of("username", "fixture-invalid", "password", password, "confirmation", password));
            mvc.perform(initialize("fixture-invalid").content(json)).andExpect(status().isBadRequest());
        }
        mvc.perform(initialize("fixture-invalid").content(payload("fixture-invalid").replace("\"confirmation\":\"" + PASSWORD, "\"confirmation\":\"different-")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM console_admin_credentials", Integer.class)).isZero();
        assertThat(new ConsoleInitializationController.Credentials("fixture", PASSWORD, PASSWORD).toString()).isEqualTo("Credentials[REDACTED]");
    }
    @Test void setup007_atomicConcurrentClaimsCreateExactlyOneAdministrator() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(index -> pool.submit(() -> {
                barrier.await();
                try { store.createFirst("concurrent-" + index, encoder.encode(PASSWORD), java.time.Instant.now().toString()); return true; }
                catch (org.springframework.web.server.ResponseStatusException closed) { return false; }
            })).toList();
            int successes = 0;
            for (var job : jobs) if (job.get(30, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM console_admin_credentials", Integer.class)).isEqualTo(1);
    }
}
