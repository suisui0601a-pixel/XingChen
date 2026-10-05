package online.wanan.xingchen.console;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles({"gateway-fake-e2e","conversation-fake-e2e"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ConversationConsoleContractTest {
    private static final Path DB=TestSqliteDatabase.create("conversation-console-contract");
    private static final String PRIVATE=ConversationFakeE2eConfiguration.PRIVATE_OWNER;
    private static final String WAITING=ConversationFakeE2eConfiguration.FIXTURES.get(4).id();
    private static final String UNKNOWN=ConversationFakeE2eConfiguration.FIXTURES.get(5).id();
    private static final String INTERRUPTED=ConversationFakeE2eConfiguration.FIXTURES.get(6).id();
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){
        p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));
        p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());
        p.add("xingchen.owner.platform-user-id",()->"e2e-owner");
    }
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper mapper;
    @AfterAll void cleanup(){TestSqliteDatabase.clean(jdbc,DB);}
    @BeforeEach void resetPrivateFixture(){jdbc.update("UPDATE simulation_conversation_state SET mode='SOCIAL',wake_state='SLEEPING',generation=7,waiting_since=NULL,origin_turn_id=NULL,wait_reason=NULL WHERE conversation_id=?",PRIVATE);}

    @Test void conv001_anonymousListAndPrivateMessagesAreRejected() throws Exception {
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations/{id}/messages",PRIVATE)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations/not-a-uuid")).andExpect(status().isUnauthorized());
    }

    @Test @WithMockUser(username="console-admin") void conv002_listHasBoundedPaginationAndServerFilters() throws Exception {
        mvc.perform(get("/api/conversations").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversations").param("state","WAITING").param("size","20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(WAITING)).andExpect(jsonPath("$.items[0].runtime_state").value("WAITING"));
        mvc.perform(get("/api/conversations").param("q","Fixture member").param("type","GROUP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(5));
        mvc.perform(get("/api/conversations").param("hasUnknown","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(UNKNOWN));
        mvc.perform(get("/api/conversations").param("interrupted","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(INTERRUPTED));
    }

    @Test @WithMockUser(username="console-admin") void conv003_004_detailAndRecentMessagesAreBoundedPrivateAndSanitized() throws Exception {
        mvc.perform(get("/api/conversations/{id}",PRIVATE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.runtime.generation").value(7)).andExpect(jsonPath("$.runtime.queueDepth").isNumber())
                .andExpect(jsonPath("$.members.items[0].role").value("OWNER"))
                .andExpect(jsonPath("$.context.currentEstimate").value(123))
                .andExpect(jsonPath("$.context.thresholds.soft").isNumber());
        MvcResult result=mvc.perform(get("/api/conversations/{id}/messages",PRIVATE).param("size","20"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Pragma","no-cache")).andExpect(jsonPath("$.items.length()").value(1)).andReturn();
        String body=result.getResponse().getContentAsString();
        assertThat(body).contains("fixture message for private-owner","image").doesNotContain("private.invalid","raw_metadata_json","https://");
        mvc.perform(get("/api/conversations/{id}/messages",PRIVATE).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/conversations/{id}/members",PRIVATE).param("size","101")).andExpect(status().isBadRequest());
    }

    @Test @WithMockUser(username="console-admin") void conv005_006_010_modeUsesGenerationCasAndClosedPolicy() throws Exception {
        Csrf csrf=csrf();
        mvc.perform(patch("/api/conversations/{id}/mode",PRIVATE).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"CLOSED_AGENT\",\"expectedGeneration\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("CLOSED_AGENT")).andExpect(jsonPath("$.generation").value(8));
        mvc.perform(patch("/api/conversations/{id}/mode",PRIVATE).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"SOCIAL\",\"expectedGeneration\":7}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        String group=ConversationFakeE2eConfiguration.FIXTURES.get(0).id();
        mvc.perform(patch("/api/conversations/{id}/mode",group).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"CLOSED_AGENT\",\"expectedGeneration\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("当前会话不允许此操作"));
        String privateMember=ConversationFakeE2eConfiguration.FIXTURES.get(3).id();
        mvc.perform(patch("/api/conversations/{id}/mode",privateMember).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"CLOSED_AGENT\",\"expectedGeneration\":6}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_console_audit WHERE operation='MODE_CHANGED' AND actor='console-admin'",Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT group_concat(old_summary||' '||new_summary||' '||result,' ') FROM conversation_console_audit",String.class)).contains("CLOSED_AGENT","CONFLICT","REJECTED").doesNotContain("fixture message");
    }

    @Test @WithMockUser(username="console-admin") void conv007_manualWakeUsesRuntimeAndRequiresOwnerPrivate() throws Exception {
        Csrf csrf=csrf();
        mvc.perform(post("/api/conversations/{id}/wake",PRIVATE).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedGeneration\":7}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.wakeState").value("AWAKE")).andExpect(jsonPath("$.generation").value(8));
        String group=ConversationFakeE2eConfiguration.FIXTURES.get(1).id();
        mvc.perform(post("/api/conversations/{id}/wake",group).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedGeneration\":4}"))
                .andExpect(status().isForbidden());
        String privateMember=ConversationFakeE2eConfiguration.FIXTURES.get(3).id();
        mvc.perform(post("/api/conversations/{id}/wake",privateMember).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedGeneration\":6}"))
                .andExpect(status().isForbidden());
    }

    @Test @WithMockUser(username="console-admin") void conv008_009_resetRetiresWaitIncrementsGenerationAndPreservesLongTermData() throws Exception {
        Csrf csrf=csrf();
        mvc.perform(post("/api/fake/conversations/waiting-group/waiting").session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WAITING"));
        long before=jdbc.queryForObject("SELECT generation FROM simulation_conversation_state WHERE conversation_id=?",Long.class,WAITING);
        mvc.perform(get("/api/conversations/{id}",WAITING)).andExpect(status().isOk()).andExpect(jsonPath("$.runtime.wake_state").value("WAITING"));
        mvc.perform(post("/api/conversations/{id}/reset",WAITING).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedGeneration\":"+(before-1)+"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
        mvc.perform(post("/api/conversations/{id}/reset",WAITING).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedGeneration\":"+before+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("RESET_COMPLETE")).andExpect(jsonPath("$.generation").value(before+1));
        mvc.perform(get("/api/conversations/{id}",WAITING)).andExpect(status().isOk()).andExpect(jsonPath("$.runtime.wake_state").value("IDLE")).andExpect(jsonPath("$.runtime.waiting_since").doesNotExist());
        mvc.perform(post("/api/fake/conversations/private-owner/waiting").session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("WAITING"));
        long privateBefore=jdbc.queryForObject("SELECT generation FROM simulation_conversation_state WHERE conversation_id=?",Long.class,PRIVATE);
        mvc.perform(post("/api/conversations/{id}/reset",PRIVATE).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"expectedGeneration\":"+privateBefore+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generation").value(privateBefore+1));
        JsonNode preserved=mapper.readTree(mvc.perform(get("/api/fake/conversations/private-owner/preservation")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(preserved.get("identity").asInt()).isPositive();assertThat(preserved.get("memberships").asInt()).isPositive();assertThat(preserved.get("relationships").asInt()).isEqualTo(1);assertThat(preserved.get("memories").asInt()).isEqualTo(1);
    }

    @Test @WithMockUser(username="console-admin") void conv011_012_unknownAndInterruptedAreVisibleWithoutResendOperation() throws Exception {
        String unknown=mvc.perform(get("/api/conversations/{id}",UNKNOWN)).andExpect(status().isOk()).andExpect(jsonPath("$.runtime.unknownOutbound[0].status").value("UNKNOWN")).andReturn().getResponse().getContentAsString();
        String interrupted=mvc.perform(get("/api/conversations/{id}",INTERRUPTED)).andExpect(status().isOk()).andExpect(jsonPath("$.runtime.interruptedTurn.status").value("INTERRUPTED")).andReturn().getResponse().getContentAsString();
        assertThat(unknown).contains("fixture-…").doesNotContain("/retry","requestBody");
        assertThat(interrupted).contains("automatic replay is blocked").doesNotContain("toolArgs","reasoning");
        assertThat(mvc.perform(get("/api/conversations/{id}/retry",UNKNOWN)).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test @WithMockUser(username="console-admin") void conv013_014_auditAndErrorsNeverContainMessageContent() throws Exception {
        Csrf csrf=csrf();String secretBody="PRIVATE-MESSAGE-DO-NOT-ECHO";
        var result=mvc.perform(patch("/api/conversations/{id}/mode",PRIVATE).session(csrf.session()).cookie(csrf.cookie()).header("X-XSRF-TOKEN",csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"BAD\",\"expectedGeneration\":9,"+"\"note\":\""+secretBody+"\"}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(secretBody);
        String audit=jdbc.queryForObject("SELECT COALESCE(group_concat(COALESCE(old_summary,'')||' '||COALESCE(new_summary,'')||' '||result,' '),'') FROM conversation_console_audit",String.class);
        assertThat(audit).doesNotContain(secretBody,"fixture message for");
    }

    private Csrf csrf() throws Exception {
        MockHttpSession session=new MockHttpSession();MvcResult result=mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode json=mapper.readTree(result.getResponse().getContentAsString());Cookie cookie=result.getResponse().getCookie("XSRF-TOKEN");
        return new Csrf(session,json.get("token").asText(),cookie);
    }
    private record Csrf(MockHttpSession session,String token,Cookie cookie){}
}
