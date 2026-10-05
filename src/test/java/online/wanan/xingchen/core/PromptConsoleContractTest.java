package online.wanan.xingchen.core;

import online.wanan.xingchen.console.PromptConsoleService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.ConcurrentModificationException;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK) @AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS) @WithMockUser(username="prompt-admin")
class PromptConsoleContractTest {
    private static final java.nio.file.Path DB=TestSqliteDatabase.create("prompt-console");
    @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired PromptConsoleService service;
    @AfterAll void clean(){TestSqliteDatabase.clean(jdbc,DB);}
    @Test void promptEndpointsRequireAdminAndAreNoStore() throws Exception {
        mvc.perform(get("/api/prompts/PERSONA/current").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/prompts/PERSONA/current")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.layer").value("PERSONA")).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/prompts/SIMULATION/current")).andExpect(status().isOk()).andExpect(jsonPath("$.layer").value("SIMULATION"));
    }
    @Test void savesImmutableVersionRejectsStaleWritersRollsBackAsNewVersionAndRedactsAudit() throws Exception {
        int auditBefore=jdbc.queryForObject("SELECT COUNT(*) FROM prompt_console_audit WHERE layer='PERSONA'",Integer.class);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var before=mapper.readTree(mvc.perform(get("/api/prompts/PERSONA/current")).andReturn().getResponse().getContentAsString());String oldId=before.get("id").asText();
        String body="{\"content\":\"persona unique body\",\"note\":\"tone update\",\"expectedActiveVersionId\":\""+oldId+"\"}";
        var created=mapper.readTree(mvc.perform(post("/api/prompts/PERSONA/versions").with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn().getResponse().getContentAsString());
        String id=created.get("id").asText();Assertions.assertTrue(created.get("version").asInt()>before.get("version").asInt());Assertions.assertEquals("persona unique body",created.get("content").asText());
        mvc.perform(post("/api/prompts/PERSONA/versions").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/prompts/PERSONA/history").param("page","0").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].active").value(true)).andExpect(jsonPath("$.items[0].content").doesNotExist());
        String rollback="{\"targetVersionId\":\""+oldId+"\",\"expectedActiveVersionId\":\""+id+"\"}";
        var restored=mapper.readTree(mvc.perform(post("/api/prompts/PERSONA/rollback").with(csrf()).contentType("application/json").content(rollback)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Assertions.assertTrue(restored.get("version").asInt()>created.get("version").asInt());Assertions.assertEquals(before.get("content").asText(),restored.get("content").asText());
        Assertions.assertEquals(auditBefore+2,jdbc.queryForObject("SELECT COUNT(*) FROM prompt_console_audit WHERE layer='PERSONA'",Integer.class));
        Assertions.assertEquals(oldId,jdbc.queryForObject("SELECT source_version_id FROM prompt_console_audit WHERE operation='ROLLBACK' AND layer='PERSONA'",String.class));
        Assertions.assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM prompt_console_audit WHERE note LIKE '%persona unique body%' OR actor LIKE '%persona unique body%'",Integer.class));
        mvc.perform(get("/api/prompts/security")).andExpect(status().isOk()).andExpect(jsonPath("$.readOnly").value(true));
        mvc.perform(post("/api/prompts/security").with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isMethodNotAllowed());
    }
    @Test void validatesLayerLengthAndSameLayerDiff() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var sim=mapper.readTree(mvc.perform(get("/api/prompts/SIMULATION/current")).andReturn().getResponse().getContentAsString());String id=sim.get("id").asText();
        mvc.perform(get("/api/prompts/PERSONA/history").param("size","51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/prompts/SIMULATION/diff").param("left",id).param("right",id)).andExpect(status().isOk());
        mvc.perform(get("/api/prompts/PERSONA/diff").param("left",id).param("right",id)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/prompts/PERSONA/versions").with(csrf()).contentType("application/json").content("{\"content\":\""+"x".repeat(30001)+"\",\"expectedActiveVersionId\":\""+id+"\"}")).andExpect(status().isBadRequest());
    }
    @Test void concurrentWritersFromOneSnapshotProduceOneWinnerAndOneConflict() throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for(int round=0;round<20;round++) {
                String expected=service.current("PERSONA").get("id").toString();
                int versionsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE layer='PERSONA'",Integer.class);
                int auditsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM prompt_console_audit WHERE layer='PERSONA'",Integer.class);
                var start=new java.util.concurrent.CountDownLatch(1);
                var jobs=java.util.List.of("concurrent A","concurrent B").stream().map(content->pool.submit(()->{start.await();try{return service.create("PERSONA",content,"",expected,"prompt-admin");}catch(ConcurrentModificationException conflict){return conflict;}})).toList();
                start.countDown();
                var results=java.util.List.of(jobs.get(0).get(15,java.util.concurrent.TimeUnit.SECONDS),jobs.get(1).get(15,java.util.concurrent.TimeUnit.SECONDS));
                Assertions.assertEquals(1,results.stream().filter(Map.class::isInstance).count());
                Assertions.assertEquals(1,results.stream().filter(ConcurrentModificationException.class::isInstance).count());
                var winner=(Map<?,?>)results.stream().filter(Map.class::isInstance).findFirst().orElseThrow();
                Assertions.assertEquals(winner.get("id"),service.current("PERSONA").get("id"));
                Assertions.assertEquals(versionsBefore+1,jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE layer='PERSONA'",Integer.class));
                Assertions.assertEquals(auditsBefore+1,jdbc.queryForObject("SELECT COUNT(*) FROM prompt_console_audit WHERE layer='PERSONA'",Integer.class));
            }
        } finally { pool.shutdownNow(); }
    }
}
