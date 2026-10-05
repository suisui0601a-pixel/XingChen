package online.wanan.xingchen.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@WithMockUser(username="phase4a-test")
class ConsoleReadApiContractTest {
    private static final java.nio.file.Path DB=TestSqliteDatabase.create("console-api");
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());}
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @AfterAll void cleanDatabase(){TestSqliteDatabase.clean(jdbc,DB);}

    @Test void api101_statusAndHealthReportLocalSubsystemState() throws Exception {
        mvc.perform(get("/api/status")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.database").value("UP")).andExpect(jsonPath("$.memory").value("UP")).andExpect(jsonPath("$.agent").value("mock-ready"));
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.version").exists());
    }

    @Test void api102_readCollectionsRespondWithJsonArrays() throws Exception {
        mvc.perform(get("/api/people")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/conversations")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/memories")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/prompts")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/context/diagnostics")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test void api103_personReadExposesIdentityHistoryButNotRawEventPayload() throws Exception {
        String personId="40000000-0000-0000-0000-000000000003";
        jdbc.update("INSERT OR IGNORE INTO persons(id,platform,platform_user_id,is_self,is_owner,created_at,updated_at) VALUES(?,'QQ','123',0,0,'2026-09-30T00:00:00Z','2026-09-30T00:00:00Z')",personId);
        mvc.perform(get("/api/people/"+personId)).andExpect(status().isOk()).andExpect(jsonPath("$.person.platformUserId").value("123")).andExpect(jsonPath("$.aliases").isArray()).andExpect(jsonPath("$.memberships").isArray()).andExpect(jsonPath("$.raw_metadata_json").doesNotExist()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/people/40000000-0000-0000-0000-000000000004")).andExpect(status().isNotFound());
    }

    @Test void api104_phase3ReadEndpointsAreAvailableWithoutSecrets() throws Exception {
        mvc.perform(get("/api/usage")).andExpect(status().isOk()).andExpect(jsonPath("$.calls").exists());
        mvc.perform(get("/api/usage/conversations")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/stickers")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/slang")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"));
        mvc.perform(get("/api/runtime")).andExpect(status().isOk()).andExpect(jsonPath("$.bind").value("127.0.0.1")).andExpect(jsonPath("$.liveApiEnabled").value(false)).andExpect(jsonPath("$.activeConversationCount").isNumber()).andExpect(jsonPath("$.waitingConversationCount").isNumber()).andExpect(jsonPath("$.queuedConversationCount").isNumber()).andExpect(jsonPath("$.resettingConversationCount").isNumber()).andExpect(jsonPath("$.queueDepth").isNumber()).andExpect(jsonPath("$.pendingOutboundUnknownCount").isNumber()).andExpect(jsonPath("$.interruptedTurnCount").isNumber()).andExpect(jsonPath("$.apiKey").doesNotExist()).andExpect(jsonPath("$.oneBotToken").doesNotExist()).andExpect(jsonPath("$.cookie").doesNotExist()).andExpect(jsonPath("$.prompt").doesNotExist()).andExpect(jsonPath("$.memory").doesNotExist());
    }
}
