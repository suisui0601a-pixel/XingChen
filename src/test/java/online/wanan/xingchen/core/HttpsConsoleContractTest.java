package online.wanan.xingchen.core;

import online.wanan.xingchen.console.EffectiveConsoleConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"XINGCHEN_PUBLIC_BASE_URL=https://console.example.invalid"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HttpsConsoleContractTest {
    private static final Path DB = TestSqliteDatabase.create("https-console");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", () -> TestSqliteDatabase.jdbcUrl(DB));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EffectiveConsoleConfiguration configuration;
    @AfterAll void cleanup() { TestSqliteDatabase.clean(jdbc, DB); }

    @Test void secureCsrfCookieIsExplicitAndSpoofedHeadersCannotChangePublicUrl() throws Exception {
        var response = mvc.perform(get("/api/auth/csrf")
                .header("Forwarded", "proto=http;host=attacker.invalid")
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Host", "attacker.invalid"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Set-Cookie")).contains("Secure", "HttpOnly");
        // Servlet 6 stores SameSite as a Cookie attribute; MockMvc's header renderer omits it.
        assertThat(response.getCookie("XSRF-TOKEN").getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(configuration.cookieSecure()).isTrue();
        assertThat(configuration.publicBaseUrl()).isEqualTo("https://console.example.invalid");
    }
}
