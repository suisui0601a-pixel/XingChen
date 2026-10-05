package online.wanan.xingchen.core;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@WithMockUser(username="phase4b-admin")
class PeopleMemoryConsoleContractTest {
    private static final java.nio.file.Path DB=TestSqliteDatabase.create("people-memory-console");
    private static final String PERSON="40000000-0000-0000-0000-000000000001", OTHER="40000000-0000-0000-0000-000000000002", OWNER="40000000-0000-0000-0000-000000000003", CONV="50000000-0000-0000-0000-000000000001", OTHER_CONV="50000000-0000-0000-0000-000000000002";
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
    @BeforeAll void seed(){String at="2026-10-01T00:00:00Z";for(String[] p:new String[][]{{PERSON,"10001","0"},{OTHER,"10002","0"},{OWNER,"10003","1"}})jdbc.update("INSERT OR IGNORE INTO persons(id,platform,platform_user_id,is_self,is_owner,created_at,updated_at) VALUES(?,'QQ',?,0,?,?,?)",p[0],p[1],Integer.parseInt(p[2]),at,at);jdbc.update("INSERT OR IGNORE INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,'QQ','GROUP','group-test',?)",CONV,at);jdbc.update("INSERT OR IGNORE INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,'QQ','GROUP','other-group-test',?)",OTHER_CONV,at);jdbc.update("INSERT OR IGNORE INTO memberships(id,person_id,conversation_id,current_display_name,card,platform_role,first_seen_at,last_seen_at) VALUES('60000000-0000-0000-0000-000000000001',?,?, 'Visible name','Group card','MEMBER',?,?)",PERSON,CONV,at,at);jdbc.update("INSERT OR IGNORE INTO person_aliases(id,person_id,alias,source_conversation_id,first_seen_at,last_seen_at) VALUES('60000000-0000-0000-0000-000000000002',?,'Old alias',?,?,?)",PERSON,CONV,at,at);}
    @AfterAll void cleanup(){TestSqliteDatabase.clean(jdbc,DB);}

    @Test void peopleAndMemoryAdministrationRequireAuthenticatedSession() throws Exception {
        mvc.perform(get("/api/people").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/memory").with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/memory").with(anonymous()).with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
    }

    @Test void peopleArePagedSearchableAndIdentityIsReadOnly() throws Exception {
        mvc.perform(get("/api/people").param("q","10001").param("page","0").param("size","20")).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.items[0].platformUserId").value("10001"));
        mvc.perform(get("/api/people").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/people").param("q","Old alias")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/people/"+PERSON)).andExpect(status().isOk()).andExpect(jsonPath("$.aliases[0].alias").value("Old alias")).andExpect(jsonPath("$.memberships[0].platformConversationId").value("group-test"));
    }
    @Test void manualMemoryUsesFtsNoStoreAuditRedactionAndSoftForget() throws Exception {
        String create="{\"content\":\"private fern fact\",\"type\":\"SEMANTIC\",\"scope\":\"PERSON_GLOBAL\",\"personId\":\""+PERSON+"\",\"conversationId\":\"\",\"projectKey\":\"\",\"confirmScopeExpansion\":false,\"operation\":\"CREATE\"}";
        String response=mvc.perform(post("/api/memory").with(csrf()).contentType("application/json").content(create)).andExpect(status().isOk()).andExpect(jsonPath("$.provenance").value("ADMIN_MANUAL")).andReturn().getResponse().getContentAsString();
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);String id=json.get("id").asText(),updated=json.get("updatedAt").asText();
        mvc.perform(get("/api/memory").param("q","fern")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].content").value("private fern fact"));
        mvc.perform(get("/api/memory/"+id)).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.memory.content").value("private fern fact"));
        String forget="{\"expectedUpdatedAt\":\""+updated+"\"}";
        mvc.perform(post("/api/memory/"+id+"/forget").with(csrf()).contentType("application/json").content(forget)).andExpect(status().isOk());
        mvc.perform(get("/api/memory").param("q","fern")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        org.junit.jupiter.api.Assertions.assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM memory_console_audit WHERE memory_id=? AND (old_scope LIKE '%fern%' OR new_scope LIKE '%fern%')",Integer.class,id));
    }
    @Test void memoryEditPersistsTypeAndRequiresConfirmedVisibilityExpansion() throws Exception {
        String id=createMemory("editable scope note","PERSON_GLOBAL",PERSON,null,null);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var detail=mapper.readTree(mvc.perform(get("/api/memory/"+id)).andReturn().getResponse().getContentAsString()).get("memory");String version=detail.get("updatedAt").asText();
        String denied="{\"content\":\"edited global scope note\",\"type\":\"PREFERENCE\",\"scope\":\"GLOBAL\",\"personId\":\"\",\"conversationId\":\"\",\"projectKey\":\"\",\"expectedUpdatedAt\":\""+version+"\",\"confirmScopeExpansion\":false,\"operation\":\"EDIT\"}";
        mvc.perform(put("/api/memory/"+id).with(csrf()).contentType("application/json").content(denied)).andExpect(status().isBadRequest());
        String approved=denied.replace("\"confirmScopeExpansion\":false","\"confirmScopeExpansion\":true");
        String saved=mvc.perform(put("/api/memory/"+id).with(csrf()).contentType("application/json").content(approved)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();String nextVersion=mapper.readTree(saved).get("updatedAt").asText();
        mvc.perform(put("/api/memory/"+id).with(csrf()).contentType("application/json").content(approved)).andExpect(status().isConflict());
        mvc.perform(get("/api/memory").param("q","global scope").param("type","PREFERENCE").param("scope","GLOBAL")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
        org.junit.jupiter.api.Assertions.assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM memory_console_audit WHERE memory_id=? AND (old_scope LIKE '%edited global scope note%' OR new_scope LIKE '%edited global scope note%')",Integer.class,id));
        org.junit.jupiter.api.Assertions.assertFalse(nextVersion.isBlank());
    }
    @Test void relationshipMutationIsExplicitScopedAndOptimisticallyConcurrent() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        String global=mvc.perform(post("/api/relationships").with(csrf()).contentType("application/json").content(relationship(PERSON,OTHER,"姐姐","GLOBAL","",null,"CREATE"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String globalId=mapper.readTree(global).get("id").asText();
        String local=mvc.perform(post("/api/relationships").with(csrf()).contentType("application/json").content(relationship(PERSON,OTHER,"称呼","CONVERSATION",CONV,null,"CREATE"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=mapper.readTree(local).get("id").asText(),createdAt=mapper.readTree(local).get("updatedAt").asText();
        mvc.perform(put("/api/relationships/"+id).with(csrf()).contentType("application/json").content(relationship(PERSON,OTHER,"重复全局","GLOBAL","",createdAt,"EDIT"))).andExpect(status().isConflict());
        String update=relationship(PERSON,OTHER,"学姐","PRIVATE",CONV,createdAt,"EDIT");
        mvc.perform(put("/api/relationships/"+id).with(csrf()).contentType("application/json").content(update)).andExpect(status().isOk());
        mvc.perform(put("/api/relationships/"+id).with(csrf()).contentType("application/json").content(update)).andExpect(status().isConflict());
        mvc.perform(get("/api/relationships/preview").param("subjectPersonId",PERSON).param("targetPersonId",OTHER).param("conversationId",CONV)).andExpect(status().isOk()).andExpect(jsonPath("$.address").value("学姐")).andExpect(jsonPath("$.reason").value("CONVERSATION_EXPLICIT")).andExpect(jsonPath("$.candidates[0].term").value("学姐"));
        var updated=mvc.perform(get("/api/relationships").param("personId",PERSON)).andExpect(status().isOk()).andReturn();
        var rows=mapper.readTree(updated.getResponse().getContentAsString()).get("items");String currentAt=null;for(var row:rows)if(row.get("id").asText().equals(id)){currentAt=row.get("updatedAt").asText();org.junit.jupiter.api.Assertions.assertEquals("PRIVATE",row.get("scope").asText());}
        mvc.perform(post("/api/relationships/"+id+"/forget").with(csrf()).contentType("application/json").content("{\"expectedUpdatedAt\":\""+currentAt+"\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/relationships/preview").param("subjectPersonId",PERSON).param("targetPersonId",OTHER).param("conversationId",CONV)).andExpect(status().isOk()).andExpect(jsonPath("$.address").value("姐姐")).andExpect(jsonPath("$.reason").value("GLOBAL_EXPLICIT"));
        org.junit.jupiter.api.Assertions.assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM relationship_terms WHERE id IN (?,?)",Integer.class,globalId,id));
    }
    private static String relationship(String subject,String target,String term,String scope,String scopeId,String expected,String operation){return "{\"subjectPersonId\":\""+subject+"\",\"targetPersonId\":\""+target+"\",\"term\":\""+term+"\",\"scope\":\""+scope+"\",\"scopeId\":\""+scopeId+"\",\"explicit\":true,\"expectedUpdatedAt\":"+(expected==null?"null":"\""+expected+"\"")+",\"operation\":\""+operation+"\"}";}
    @Test void retrievalPreviewUsesExistingPersonConversationAndProjectPolicyWithoutModel() throws Exception {
        String personMemory=createMemory("persona boundary","PERSON_GLOBAL",PERSON,null,null);
        preview(PERSON,CONV,null).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(personMemory)).andExpect(jsonPath("$.modelInvoked").value(false));
        preview(OTHER,CONV,null).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(personMemory))));
        String privateMemory=createMemory("private boundary","PRIVATE",PERSON,null,null);
        preview(PERSON,CONV,null).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(privateMemory)));
        preview(OTHER,CONV,null).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(privateMemory))));
        String conversationMemory=createMemory("conversation boundary","CONVERSATION",PERSON,CONV,null);
        preview(PERSON,CONV,null).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(conversationMemory)));
        String projectMemory=createMemory("project boundary","PROJECT",PERSON,null,"private-test-project");
        preview(PERSON,CONV,"private-test-project").andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(projectMemory)));
        preview(PERSON,CONV,"another-project").andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(projectMemory))));
        mvc.perform(get("/api/memory").param("person",PERSON).param("project","private-test-project").param("type","SEMANTIC").param("scope","PROJECT").param("status","ACTIVE").param("from","2020-01-01T00:00:00Z").param("to","2099-01-01T00:00:00Z")).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(projectMemory)));
        mvc.perform(get("/api/memory").param("person",PERSON).param("conversation",CONV).param("type","SEMANTIC").param("scope","CONVERSATION").param("status","ACTIVE")).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(conversationMemory)));
        preview(PERSON,OTHER_CONV,null).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(conversationMemory))));
        String ownerMemory=createMemory("owner-only boundary","OWNER_GLOBAL",OWNER,null,null);
        preview(OWNER,CONV,null).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(ownerMemory)));
        preview(PERSON,CONV,null).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(ownerMemory))));
    }
    private String createMemory(String body,String scope,String person,String conversation,String project)throws Exception {
        String json="{\"content\":\""+body+"\",\"type\":\"SEMANTIC\",\"scope\":\""+scope+"\",\"personId\":\""+(person==null?"":person)+"\",\"conversationId\":\""+(conversation==null?"":conversation)+"\",\"projectKey\":\""+(project==null?"":project)+"\",\"confirmScopeExpansion\":false,\"operation\":\"CREATE\"}";
        String response=mvc.perform(post("/api/memory").with(csrf()).contentType("application/json").content(json)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();
    }
    private org.springframework.test.web.servlet.ResultActions preview(String person,String conversation,String project)throws Exception {var request=get("/api/memory/retrieval-preview").param("personId",person).param("conversationId",conversation);if(project!=null)request.param("projectKey",project);return mvc.perform(request);}
    @Test void memoryMidnightRangesAreDeterministicAndHalfOpen() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String time : new String[]{"2040-12-31T23:59:00Z", "2041-01-01T00:00:00Z", "2041-01-01T00:01:00Z"}) {
            var base = java.time.Instant.parse(time);
            String id = createMemory("midnight deterministic boundary", "PERSON_GLOBAL", PERSON, null, null);
            // Fixed fixture timestamp, not the system clock or a wait for real midnight.
            var record = base.plusSeconds(120);
            jdbc.update("UPDATE memories SET created_at=? WHERE id=?", record.toString(), id);
            String response = mvc.perform(get("/api/memory").param("from", base.minusSeconds(3 * 86400).toString()).param("to", base.plusSeconds(3600).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertTrue(java.util.stream.StreamSupport.stream(mapper.readTree(response).get("items").spliterator(), false).anyMatch(row -> row.get("id").asText().equals(id)));
            mvc.perform(get("/api/memory").param("from", record.toString()).param("to", record.plusSeconds(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(id)));
            mvc.perform(get("/api/memory").param("from", record.minusSeconds(1).toString()).param("to", record.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(id))));
        }
    }
    @Test void memoryTimeRangeUsesUtcHalfOpenBoundsAndRejectsInvalidInput() throws Exception {
        String created=createMemory("time boundary","PERSON_GLOBAL",PERSON,null,null);
        var detail=new com.fasterxml.jackson.databind.ObjectMapper().readTree(mvc.perform(get("/api/memory/"+created)).andReturn().getResponse().getContentAsString()).get("memory");
        java.time.Instant instant=java.time.Instant.parse(detail.get("createdAt").asText());
        mvc.perform(get("/api/memory").param("person",PERSON).param("type","SEMANTIC").param("scope","PERSON_GLOBAL").param("status","ACTIVE").param("from",instant.minusSeconds(1).toString()).param("to",instant.plusSeconds(1).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.hasItem(created)));
        mvc.perform(get("/api/memory").param("from",instant.toString()).param("to",instant.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/memory").param("from",instant.plusSeconds(1).toString()).param("to",instant.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/memory").param("from","not-a-timestamp")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/memory").param("to",detail.get("createdAt").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.items[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(created))));
    }
}
