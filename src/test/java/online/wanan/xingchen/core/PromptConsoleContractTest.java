package online.wanan.xingchen.core;

import online.wanan.xingchen.console.PromptConsoleService;
import online.wanan.xingchen.core.prompt.PromptBaselineService;
import online.wanan.xingchen.core.prompt.PromptSnapshotProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK) @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS) @WithMockUser(username="prompt-admin")
class PromptConsoleContractTest {
    private static final java.nio.file.Path DB=TestSqliteDatabase.create("prompt-console-readonly");
    @DynamicPropertySource static void db(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired PromptConsoleService service; @Autowired PromptBaselineService baseline; @Autowired PromptSnapshotProvider snapshots;
    @AfterAll void clean(){TestSqliteDatabase.clean(jdbc,DB);}

    @Test void baselineResourcesArePinnedAndAdminEndpointsAreNoStore() throws Exception {
        Assertions.assertEquals(PromptBaselineService.PERSONA_SHA256,baseline.baseline().personaSha256());
        Assertions.assertEquals(PromptBaselineService.SIMULATION_SHA256,baseline.baseline().simulationSha256());
        Assertions.assertFalse(baseline.loadPersona().isBlank()); Assertions.assertFalse(baseline.loadSimulation().isBlank());
        mvc.perform(get("/api/prompts/PERSONA/current").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/prompts/PERSONA/current")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.layer").value("PERSONA")).andExpect(jsonPath("$.source").value("BUILT_IN")).andExpect(jsonPath("$.mutable").value(false)).andExpect(jsonPath("$.sha256").value(PromptBaselineService.PERSONA_SHA256));
        mvc.perform(get("/api/prompts/SIMULATION/current")).andExpect(status().isOk()).andExpect(jsonPath("$.layer").value("SIMULATION")).andExpect(jsonPath("$.sha256").value(PromptBaselineService.SIMULATION_SHA256));
        mvc.perform(get("/api/prompts/baseline")).andExpect(status().isOk()).andExpect(jsonPath("$.persona.source").value("BUILT_IN")).andExpect(jsonPath("$.persona.mutable").value(false)).andExpect(jsonPath("$.simulation.mutable").value(false));
        mvc.perform(get("/api/prompts/security")).andExpect(status().isOk()).andExpect(jsonPath("$.readOnly").value(true));
    }

    @Test void databasePointersCannotOverrideBuiltInsAndIdentityContextRemainsDynamic() {
        String profile=snapshots.capture().profileId().toString();
        jdbc.update("INSERT OR IGNORE INTO prompt_profiles(id,name,simulation_prompt,persona_prompt,updated_at) VALUES(?,?,?,?,?)",profile,"Default","DB_SIMULATION_MUST_NOT_RUN","DB_PERSONA_MUST_NOT_RUN","now");
        String personaId="persona-legacy-"+java.util.UUID.randomUUID(),simulationId="simulation-legacy-"+java.util.UUID.randomUUID();
        int personaVersion=nextVersion("PERSONA"),simulationVersion=nextVersion("SIMULATION");
        jdbc.update("INSERT INTO prompt_versions(id,profile_id,layer,content,version,created_at,created_by,checksum) VALUES(?,?,?,?,?,?,?,?)",personaId,profile,"PERSONA","DB_PERSONA_MUST_NOT_RUN",personaVersion,"now","test","legacy");
        jdbc.update("INSERT INTO prompt_versions(id,profile_id,layer,content,version,created_at,created_by,checksum) VALUES(?,?,?,?,?,?,?,?)",simulationId,profile,"SIMULATION","DB_SIMULATION_MUST_NOT_RUN",simulationVersion,"now","test","legacy");
        jdbc.update("UPDATE prompt_profiles SET active_persona_version_id=?,active_simulation_version_id=? WHERE id=?",personaId,simulationId,profile);

        var captured=snapshots.capture();
        Assertions.assertEquals(baseline.loadPersona(),captured.personaPrompt()); Assertions.assertEquals(baseline.loadSimulation(),captured.simulationPrompt());
        Assertions.assertTrue(captured.personaVersionId().startsWith("BUILT_IN:")); Assertions.assertTrue(captured.simulationVersionId().startsWith("BUILT_IN:"));
        var context=new online.wanan.xingchen.core.context.ContextBuilder(new online.wanan.xingchen.core.context.ContextBudget(60000,80000,120000,20,20000,10000))
                .build(new online.wanan.xingchen.core.context.ContextBuildInput(captured.simulationPrompt(),captured.personaPrompt(),"QQ:100000007","SyntheticBot","管理员", "conversation",java.util.List.of(),"",java.util.List.of(),java.util.Map.of(),"hello","",null));
        Assertions.assertEquals("QQ:100000007",context.actorId()); Assertions.assertEquals("SyntheticBot",context.displayName()); Assertions.assertEquals("管理员",context.relationshipAddress());
        Assertions.assertFalse(context.renderSections().contains("DB_PERSONA_MUST_NOT_RUN")); Assertions.assertFalse(context.renderSections().contains("DB_SIMULATION_MUST_NOT_RUN"));
    }

    @Test void personaAndSimulationWriteAndActivationEndpointsFailClosedWithoutDatabaseWrites() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions",Integer.class);
        String body="{\"content\":\"attempted runtime write\",\"note\":\"test\",\"expectedActiveVersionId\":\"legacy\"}";
        mvc.perform(post("/api/prompts/PERSONA/versions").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Persona is built into XingChen Core and cannot be modified at runtime."));
        mvc.perform(post("/api/prompts/SIMULATION/versions").with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Simulation is built into XingChen Core and cannot be modified at runtime."));
        mvc.perform(post("/api/prompts/PERSONA/rollback").with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isConflict());
        mvc.perform(post("/api/prompts/SIMULATION/rollback").with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isConflict());
        Assertions.assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions",Integer.class));
        Assertions.assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM prompt_console_audit",Integer.class));
    }

    @Test void legacyHistoryRemainsReadOnlyAndNeverClaimsToBeRuntimeActive() throws Exception {
        String profile=snapshots.capture().profileId().toString();
        jdbc.update("INSERT OR IGNORE INTO prompt_profiles(id,name,simulation_prompt,persona_prompt,updated_at) VALUES(?,?,?,?,?)",profile,"Default","old sim","old persona","now");
        String legacyId="legacy-v1-"+java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO prompt_versions(id,profile_id,layer,content,version,created_at,created_by,checksum) VALUES(?,?,?,?,?,?,?,?)",legacyId,profile,"PERSONA","old persona",nextVersion("PERSONA"),"now","test","legacy");
        jdbc.update("UPDATE prompt_profiles SET active_persona_version_id=? WHERE id=?",legacyId,profile);
        mvc.perform(get("/api/prompts/PERSONA/history")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].active").value(false)).andExpect(jsonPath("$.items[0].legacyActive").value(true));
        Assertions.assertEquals("old persona",jdbc.queryForObject("SELECT content FROM prompt_versions WHERE id=?",String.class,legacyId));
    }

    private int nextVersion(String layer){Integer max=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM prompt_versions WHERE profile_id=? AND layer=?",Integer.class,snapshots.capture().profileId().toString(),layer);return max+1;}
}
