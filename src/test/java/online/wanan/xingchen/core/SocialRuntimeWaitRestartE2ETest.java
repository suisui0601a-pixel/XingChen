package online.wanan.xingchen.core;

import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.adapter.onebot.OneBotV11Configuration;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import online.wanan.xingchen.core.conversation.ConversationResetCoordinator;
import online.wanan.xingchen.core.identity.IdentityRegistry;
import online.wanan.xingchen.core.memory.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.relationship.*;
import online.wanan.xingchen.storage.SqliteSimulationStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class SocialRuntimeWaitRestartE2ETest {
    @Test void promptRuntime001_databasePromptVersionsCannotOverrideBuiltInLayers() throws Exception {
        Path db=TestSqliteDatabase.create("prompt-turn-snapshot");String url=TestSqliteDatabase.jdbcUrl(db);JdbcTemplate cleanupJdbc=null;String originalPersona="",originalSimulation="";
        try(ConfigurableApplicationContext context=start(url)){
            var jdbc=context.getBean(JdbcTemplate.class);cleanupJdbc=jdbc;var console=context.getBean(online.wanan.xingchen.console.PromptConsoleService.class);var runtime=context.getBean(SocialRuntime.class);var fake=context.getBean(FakeOneBotServer.class);var model=context.getBean(MockModelProvider.class);
            var persona=(java.util.Map<?,?>)console.current("PERSONA");var simulation=(java.util.Map<?,?>)console.current("SIMULATION");originalPersona=Objects.toString(persona.get("content"));originalSimulation=Objects.toString(simulation.get("content"));var result=AgentDecision.noReply();var usage=new ModelUsage(1,0,1,1,0,"fixture","fixture-model",1);var barrier=model.enqueueBlocked(new ModelResponse(result,usage,null));runtime.start();await(()->fake.wsConnections()>0,5000);fake.emit(privateEvent("930301",40,"snapshot A"));assertThat(barrier.awaitStarted(5,TimeUnit.SECONDS)).isTrue();
            String profile="8f3e9d9f-77d9-5d99-9cb4-f1eea5678abc",personaId="legacy-persona",simulationId="legacy-simulation";
            jdbc.update("INSERT INTO prompt_profiles(id,name,simulation_prompt,persona_prompt,updated_at) VALUES(?,?,?,?,?)",profile,"Default","DB_SIMULATION_OVERRIDE","DB_PERSONA_OVERRIDE","now");
            jdbc.update("INSERT INTO prompt_versions(id,profile_id,layer,content,version,created_at,created_by,checksum) VALUES(?,?,?,?,?,?,?,?)",personaId,profile,"PERSONA","DB_PERSONA_OVERRIDE",1,"now","test","legacy");
            jdbc.update("INSERT INTO prompt_versions(id,profile_id,layer,content,version,created_at,created_by,checksum) VALUES(?,?,?,?,?,?,?,?)",simulationId,profile,"SIMULATION","DB_SIMULATION_OVERRIDE",1,"now","test","legacy");
            jdbc.update("UPDATE prompt_profiles SET active_persona_version_id=?,active_simulation_version_id=? WHERE id=?",personaId,simulationId,profile);
            barrier.release();assertThat(barrier.awaitReturned(5,TimeUnit.SECONDS)).isTrue();await(()->jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id='930301' AND status='COMPLETED'",Integer.class)==1,5000);
            var turnA=model.requests().getFirst().context();assertThat(turnA.persona()).isEqualTo(originalPersona);assertThat(turnA.simulationPrompt()).isEqualTo(originalSimulation);assertThat(turnA.taskState().get("prompt.personaVersionId")).startsWith("BUILT_IN:");assertThat(turnA.taskState().get("prompt.simulationVersionId")).startsWith("BUILT_IN:");
            model.enqueue(new ModelResponse(result,usage,null));fake.emit(privateEvent("930302",41,"snapshot B"));await(()->model.requests().size()==2,5000);var turnB=model.requests().get(1).context();assertThat(turnB.persona()).isEqualTo(originalPersona);assertThat(turnB.simulationPrompt()).isEqualTo(originalSimulation);
        }
        try(ConfigurableApplicationContext restarted=start(url)){
            var console=restarted.getBean(online.wanan.xingchen.console.PromptConsoleService.class);assertThat(console.current("PERSONA")).containsEntry("content",originalPersona).containsEntry("sha256",online.wanan.xingchen.core.prompt.PromptBaselineService.PERSONA_SHA256);assertThat(console.current("SIMULATION")).containsEntry("content",originalSimulation).containsEntry("sha256",online.wanan.xingchen.core.prompt.PromptBaselineService.SIMULATION_SHA256);
            var jdbc=restarted.getBean(JdbcTemplate.class);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE layer='PERSONA'",Integer.class)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE layer='SIMULATION'",Integer.class)).isEqualTo(1);
        }finally{TestSqliteDatabase.clean(cleanupJdbc,db);}
    }

    @Test void reset006_activePromptHistorySurvivesAndRollbackBuildsV2Context() throws Exception {
        Path db=TestSqliteDatabase.create("social-reset-prompt-context");String url=TestSqliteDatabase.jdbcUrl(db);JdbcTemplate cleanupJdbc=null;
        try(ConfigurableApplicationContext context=start(url,"--xingchen.prompt.persona-version=3","--xingchen.prompt.simulation-version=3",
                "--xingchen.prompt.persona=PERSONA_ACTIVE_V3","--xingchen.prompt.simulation=SIMULATION_ACTIVE_V3")){
            var prompts=context.getBean(online.wanan.xingchen.core.prompt.PromptSections.class);assertThat(prompts.personaVersion()).isEqualTo(1);assertThat(prompts.simulationVersion()).isEqualTo(1);
            var jdbc=context.getBean(JdbcTemplate.class);cleanupJdbc=jdbc;String profile=prompts.profileId().toString();jdbc.update("INSERT INTO prompt_profiles(id,name,simulation_prompt,persona_prompt,updated_at) VALUES(?,?,?,?,?)",profile,"Default","SIMULATION_ACTIVE_V3","PERSONA_ACTIVE_V3","now");jdbc.update("DELETE FROM prompt_versions WHERE profile_id=?",profile);jdbc.update("UPDATE prompt_profiles SET simulation_prompt='SIMULATION_ACTIVE_V3',persona_prompt='PERSONA_ACTIVE_V3',active_simulation_version_id=NULL,active_persona_version_id=NULL WHERE id=?",profile);
            seedPromptHistory(jdbc,profile,"PERSONA",List.of("PERSONA_V1","PERSONA_V2","PERSONA_ACTIVE_V3"));seedPromptHistory(jdbc,profile,"SIMULATION",List.of("SIMULATION_V1","SIMULATION_V2","SIMULATION_ACTIVE_V3"));
            jdbc.update("UPDATE prompt_profiles SET active_persona_version_id=?,active_simulation_version_id=? WHERE id=?",UUID.nameUUIDFromBytes((profile+"PERSONA2").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),UUID.nameUUIDFromBytes((profile+"SIMULATION2").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),profile);
            var promptHistoryBefore=jdbc.queryForList("SELECT id,layer,version,checksum FROM prompt_versions WHERE profile_id=? ORDER BY layer,version",profile);
            var provider=context.getBean(MockModelProvider.class);var runtime=context.getBean(SocialRuntime.class);runtime.start();var fake=context.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
            provider.enqueue(new ModelResponse(AgentDecision.noReply(),new ModelUsage(2,0,2,1,0,"fixture","fixture-model",1),null));fake.emit(privateEvent("930101",20,"before reset"));
            await(()->provider.requests().size()==1&&context.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id='930101' AND status='COMPLETED'",Integer.class)==1,8000);UUID conversation=UUID.fromString(context.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT id FROM conversations WHERE platform='QQ' AND type='PRIVATE' AND platform_conversation_id='owner-test'",String.class));
            long generation=context.getBean(SimulationStateService.class).get(conversation).generation();fake.emit(privateEvent("930102",21,"/reset"));
            await(()->context.getBean(SimulationStateService.class).get(conversation).generation()>generation
                    &&context.getBean(ConversationResetCoordinator.class).resettingCount()==0,5000);
            assertThat(jdbc.queryForList("SELECT id,layer,version,checksum FROM prompt_versions WHERE profile_id=? ORDER BY layer,version",profile)).isEqualTo(promptHistoryBefore);
            assertThat(jdbc.queryForObject("SELECT simulation_prompt FROM prompt_profiles WHERE id=?",String.class,profile)).isEqualTo("SIMULATION_ACTIVE_V3");assertThat(jdbc.queryForObject("SELECT persona_prompt FROM prompt_profiles WHERE id=?",String.class,profile)).isEqualTo("PERSONA_ACTIVE_V3");
            provider.enqueue(new ModelResponse(AgentDecision.noReply(),new ModelUsage(2,0,2,1,0,"fixture","fixture-model",1),null));fake.emit(privateEvent("930103",22,"after reset"));
            await(()->provider.requests().size()==2,8000);
            var nextContext=provider.requests().get(1).context();assertThat(nextContext.persona()).isEqualTo(context.getBean(online.wanan.xingchen.core.prompt.PromptBaselineService.class).loadPersona());
            assertThat(nextContext.simulationPrompt()).isEqualTo(context.getBean(online.wanan.xingchen.core.prompt.PromptBaselineService.class).loadSimulation());
            assertThat(context.getBean(online.wanan.xingchen.core.prompt.PromptSections.class)).isEqualTo(prompts);

            var repo=new online.wanan.xingchen.core.prompt.InMemoryPromptRepository();UUID rollbackId=UUID.randomUUID();repo.save(new online.wanan.xingchen.core.prompt.PromptProfile(rollbackId,"rollback","SIMULATION_V3","PERSONA_V3"));
            var promptService=new online.wanan.xingchen.core.prompt.PromptService(repo);for(String value:List.of("PERSONA_V1","PERSONA_V2","PERSONA_V3"))promptService.save(rollbackId,online.wanan.xingchen.core.prompt.PromptLayer.PERSONA,value,"owner");
            for(String value:List.of("SIMULATION_V1","SIMULATION_V2","SIMULATION_V3"))promptService.save(rollbackId,online.wanan.xingchen.core.prompt.PromptLayer.SIMULATION,value,"owner");
            var personaRollback=promptService.rollback(rollbackId,online.wanan.xingchen.core.prompt.PromptLayer.PERSONA,2);var simulationRollback=promptService.rollback(rollbackId,online.wanan.xingchen.core.prompt.PromptLayer.SIMULATION,2);
            assertThat(promptService.history(rollbackId,online.wanan.xingchen.core.prompt.PromptLayer.PERSONA)).extracting(online.wanan.xingchen.core.prompt.PromptVersion::version).containsExactly(1,2,3,4);
            assertThat(promptService.history(rollbackId,online.wanan.xingchen.core.prompt.PromptLayer.SIMULATION)).extracting(online.wanan.xingchen.core.prompt.PromptVersion::version).containsExactly(1,2,3,4);
            assertThat(personaRollback.personaPrompt()).isEqualTo("PERSONA_V2");assertThat(simulationRollback.simulationPrompt()).isEqualTo("SIMULATION_V2");
            var rolledBack=new online.wanan.xingchen.core.prompt.PromptEngine().assemble(new online.wanan.xingchen.core.prompt.PromptProfile(rollbackId,"rollback",simulationRollback.simulationPrompt(),personaRollback.personaPrompt()),4,4);
            var built=new online.wanan.xingchen.core.context.ContextBuilder(online.wanan.xingchen.core.context.ContextBudget.defaults()).build(new online.wanan.xingchen.core.context.ContextBuildInput(rolledBack.simulationPrompt(),rolledBack.personaPrompt(),"a","A","","c",List.of(),"",List.of(),java.util.Map.of(),"next turn","",null));
            assertThat(built.simulationPrompt()).isEqualTo("SIMULATION_V2");assertThat(built.persona()).isEqualTo("PERSONA_V2");
            // requests().size() proves model entry, not durable completion. Do not tear down
            // SQLite while the post-reset lane can still write its result/cursor/journal.
            await(()->jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id='930103' AND status='COMPLETED'",Integer.class)==1
                    &&runtime.queueDiagnostics().activeConversationCount()==0,8000);
        }finally{TestSqliteDatabase.clean(cleanupJdbc,db);}
    }

    private static void seedPromptHistory(JdbcTemplate jdbc,String profile,String layer,List<String> values){for(int i=0;i<values.size();i++){String value=values.get(i);jdbc.update("INSERT INTO prompt_versions(id,profile_id,layer,content,version,created_at,created_by,checksum) VALUES(?,?,?,?,?,?,?,?)",UUID.nameUUIDFromBytes((profile+layer+i).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),profile,layer,value,i+1,Instant.now().toString(),"test",Integer.toHexString(value.hashCode()));}}

    @Test void resetControlInterruptsActiveSocialRuntimeModelBeforeRelease() throws Exception {
        Path db=TestSqliteDatabase.create("social-reset-active-model");String url=TestSqliteDatabase.jdbcUrl(db);
        String incomingId="930001",resetId="930002";var timeline=new java.util.concurrent.CopyOnWriteArrayList<String>();
        try(ConfigurableApplicationContext context=start(url)){
            var provider=context.getBean(MockModelProvider.class);
            var staleMemory=new online.wanan.xingchen.core.memory.MemoryCandidate(null,online.wanan.xingchen.core.memory.MemoryType.SEMANTIC,
                    "stale reset memory",online.wanan.xingchen.core.memory.MemoryScope.PERSON_GLOBAL,null,null,.9,.8,true,incomingId);
            var oldResponse=new ModelResponse(AgentDecision.text("OLD RESPONSE"),null,staleMemory,
                    List.of(new ModelToolCall("old-address-call","memory.setAddress",java.util.Map.of(
                            "targetPersonId",UUID.nameUUIDFromBytes("xingchen:person:QQ:owner-test".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
                            "address","旧代称呼","scope","GLOBAL"))),ModelFinishReason.TOOL_CALLS);
            var barrier=provider.enqueueBlocked(oldResponse);var runtime=context.getBean(SocialRuntime.class);runtime.start();
            var fake=context.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
            fake.emit(privateEvent(incomingId,10,"hello"));
            assertThat(barrier.awaitStarted(5,TimeUnit.SECONDS)).as("SocialRuntime reached the model barrier").isTrue();
            timeline.add("MODEL_STARTED");var request=provider.requests().getFirst();var token=request.cancellation();
            var jdbc=context.getBean(JdbcTemplate.class);UUID conversation=UUID.fromString(jdbc.queryForObject(
                    "SELECT id FROM conversations WHERE platform='QQ' AND type='PRIVATE' AND platform_conversation_id='owner-test'",String.class));
            long before=context.getBean(SimulationStateService.class).get(conversation).generation();
            timeline.add("RESET_REQUESTED");fake.emit(privateEvent(resetId,11,"/reset"));
            await(()->context.getBean(SimulationStateService.class).get(conversation).generation()>before&&token.isCancelled()
                    &&context.getBean(ConversationResetCoordinator.class).resettingCount()==1,5000);
            timeline.add("GENERATION_INVALIDATED");timeline.add("CANCEL_SIGNALLED");timeline.add("RESET_CONTROL_COMPLETED");
            assertThat(barrier.awaitReturned(50,TimeUnit.MILLISECONDS)).as("old model remains blocked after reset control completes").isFalse();
            timeline.add("MODEL_RELEASED");barrier.release();assertThat(barrier.awaitReturned(5,TimeUnit.SECONDS)).isTrue();timeline.add("OLD_MODEL_RETURNED");
            await(()->runtime.queueDiagnostics().activeConversationCount()==0&&runtime.queueDiagnostics().queuedEventCount()==0
                    &&context.getBean(ConversationResetCoordinator.class).resettingCount()==0,5000);
            timeline.add("OLD_RESULT_DROPPED");
            assertThat(timeline).containsExactly("MODEL_STARTED","RESET_REQUESTED","GENERATION_INVALIDATED","CANCEL_SIGNALLED","RESET_CONTROL_COMPLETED","MODEL_RELEASED","OLD_MODEL_RETURNED","OLD_RESULT_DROPPED");
            assertThat(fake.sendReceiveCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims WHERE event_id=?",Integer.class,incomingId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE content=?",Integer.class,"stale reset memory")).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relationship_terms WHERE value=?",Integer.class,"旧代称呼")).isZero();
            assertThat(context.getBean(SimulationStateService.class).get(conversation).wakeState()).isEqualTo(SimulationConversationState.WakeState.IDLE);
            assertThat(jdbc.queryForObject("SELECT status FROM durable_turn_executions WHERE incoming_event_id=?",String.class,incomingId)).isIn("STALE","INTERRUPTED");
            provider.enqueue(new ModelResponse(AgentDecision.text("NEW RESPONSE"),new ModelUsage(4,0,4,2,0,"fixture","fixture-model",1),null));
            fake.emit(privateEvent("930003",12,"new generation"));
            await(()->provider.requests().size()==2&&jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE conversation_id=? AND status='SUCCESS'",Integer.class,conversation.toString())==1,8000);
            assertThat(fake.sendReceiveCount()).isEqualTo(1);
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void resetOutput001_durableTextBeforeDispatchIsSuppressedAfterReset() throws Exception {
        Path db=TestSqliteDatabase.create("social-reset-output-pre-dispatch");String url=TestSqliteDatabase.jdbcUrl(db);String eventId="930201";
        try(ConfigurableApplicationContext context=start(url)){
            var observer=context.getBean(online.wanan.xingchen.config.TestTurnBoundaryObserver.class);var gate=observer.arm(online.wanan.xingchen.config.TestTurnBoundaryObserver.Phase.OUTPUT_PERSISTED);
            var provider=context.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.text("OLD DURABLE TEXT"),new ModelUsage(4,0,4,3,0,"fixture","fixture-model",1),null));
            var runtime=context.getBean(SocialRuntime.class);runtime.start();var fake=context.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);fake.emit(privateEvent(eventId,30,"hello"));
            assertThat(gate.awaitReached(8,TimeUnit.SECONDS)).as("durable output is written before dispatch").isTrue();var jdbc=context.getBean(JdbcTemplate.class);
            var turn=jdbc.queryForObject("SELECT turn_id FROM durable_turn_executions WHERE incoming_event_id=?",String.class,eventId);
            assertThat(jdbc.queryForObject("SELECT model_turn_state FROM durable_turn_executions WHERE turn_id=?",String.class,turn)).isEqualTo("OUTPUT_EMITTED");
            assertThat(jdbc.queryForObject("SELECT output_json FROM durable_turn_executions WHERE turn_id=?",String.class,turn)).contains("OLD DURABLE TEXT");assertThat(fake.sendReceiveCount()).isZero();
            UUID conversation=UUID.fromString(jdbc.queryForObject("SELECT conversation_id FROM durable_turn_executions WHERE turn_id=?",String.class,turn));long generation=context.getBean(SimulationStateService.class).get(conversation).generation();fake.emit(privateEvent("930202",31,"/reset"));
            await(()->context.getBean(SimulationStateService.class).get(conversation).generation()>generation,5000);
            gate.release();await(()->runtime.queueDiagnostics().activeConversationCount()==0&&context.getBean(ConversationResetCoordinator.class).resettingCount()==0,5000);
            assertThat(observer.dispatchCount()).isZero();assertThat(fake.sendReceiveCount()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE turn_id=?",Integer.class,turn)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM durable_turn_executions WHERE turn_id=?",String.class,turn)).isIn("STALE","INTERRUPTED");assertThat(provider.requests()).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,eventId)).isNotEqualTo("COMPLETED");
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void resetTool001_parsedProposalCannotClaimOrExecuteAfterReset() throws Exception {
        Path db=TestSqliteDatabase.create("social-reset-tool-proposal");String url=TestSqliteDatabase.jdbcUrl(db);String eventId="930211";
        try(ConfigurableApplicationContext context=start(url)){
            var observer=context.getBean(online.wanan.xingchen.config.TestTurnBoundaryObserver.class);var gate=observer.arm(online.wanan.xingchen.config.TestTurnBoundaryObserver.Phase.TOOL_PROPOSED);
            var call=new ModelToolCall("pre-reset-address","memory.setAddress",java.util.Map.of("targetPersonId",UUID.nameUUIDFromBytes("xingchen:person:QQ:owner-test".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),"address","must-not-write","scope","GLOBAL"));
            var provider=context.getBean(MockModelProvider.class).enqueue(new ModelResponse(null,null,null,List.of(call),ModelFinishReason.TOOL_CALLS));var runtime=context.getBean(SocialRuntime.class);runtime.start();var fake=context.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);fake.emit(privateEvent(eventId,32,"hello"));
            assertThat(gate.awaitReached(8,TimeUnit.SECONDS)).as("parsed tool proposal reached pre-claim barrier").isTrue();var jdbc=context.getBean(JdbcTemplate.class);UUID conversation=UUID.fromString(jdbc.queryForObject("SELECT id FROM conversations WHERE platform='QQ' AND type='PRIVATE' AND platform_conversation_id='owner-test'",String.class));long generation=context.getBean(SimulationStateService.class).get(conversation).generation();
            fake.emit(privateEvent("930212",33,"/reset"));await(()->context.getBean(SimulationStateService.class).get(conversation).generation()>generation,5000);gate.release();await(()->runtime.queueDiagnostics().activeConversationCount()==0&&context.getBean(ConversationResetCoordinator.class).resettingCount()==0,5000);
            assertThat(observer.toolProposalCount()).isEqualTo(1);assertThat(observer.toolExecutionCount()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims WHERE event_id=?",Integer.class,eventId)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relationship_terms WHERE value=?",Integer.class,"must-not-write")).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE content=?",Integer.class,"must-not-write")).isZero();assertThat(fake.sendReceiveCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM durable_turn_executions WHERE incoming_event_id=?",String.class,eventId)).isIn("STALE","INTERRUPTED");assertThat(jdbc.queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,eventId)).isNotEqualTo("COMPLETED");assertThat(provider.requests()).hasSize(1);
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void resetTool002_executedEffectIsNotReplayedAndOldReplyIsSuppressed() throws Exception {
        Path db=TestSqliteDatabase.create("social-reset-tool-executed");String url=TestSqliteDatabase.jdbcUrl(db);String eventId="930221";
        try(ConfigurableApplicationContext context=start(url)){
            var observer=context.getBean(online.wanan.xingchen.config.TestTurnBoundaryObserver.class);var gate=observer.arm(online.wanan.xingchen.config.TestTurnBoundaryObserver.Phase.TOOL_EXECUTED);
            var call=new ModelToolCall("executed-address","memory.setAddress",java.util.Map.of("targetPersonId",UUID.nameUUIDFromBytes("xingchen:person:QQ:owner-test".getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),"address","executed-once","scope","GLOBAL"));
            var provider=context.getBean(MockModelProvider.class).enqueue(new ModelResponse(null,null,null,List.of(call),ModelFinishReason.TOOL_CALLS)).enqueue(new ModelResponse(AgentDecision.text("STALE CONTINUATION"),new ModelUsage(4,0,4,3,0,"fixture","fixture-model",1),null));
            var runtime=context.getBean(SocialRuntime.class);runtime.start();var fake=context.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);fake.emit(privateEvent(eventId,34,"hello"));assertThat(gate.awaitReached(8,TimeUnit.SECONDS)).as("tool effect completed before model continuation").isTrue();
            var jdbc=context.getBean(JdbcTemplate.class);UUID conversation=UUID.fromString(jdbc.queryForObject("SELECT id FROM conversations WHERE platform='QQ' AND type='PRIVATE' AND platform_conversation_id='owner-test'",String.class));long generation=context.getBean(SimulationStateService.class).get(conversation).generation();
            assertThat(observer.toolProposalCount()).isEqualTo(1);assertThat(observer.toolExecutionCount()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relationship_terms WHERE value=?",Integer.class,"executed-once")).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims WHERE event_id=?",Integer.class,eventId)).isEqualTo(1);assertThat(fake.sendReceiveCount()).isZero();
            fake.emit(privateEvent("930222",35,"/reset"));await(()->context.getBean(SimulationStateService.class).get(conversation).generation()>generation,5000);gate.release();await(()->runtime.queueDiagnostics().activeConversationCount()==0&&context.getBean(ConversationResetCoordinator.class).resettingCount()==0,5000);
            assertThat(provider.requests()).hasSize(1);assertThat(observer.toolExecutionCount()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relationship_terms WHERE value=?",Integer.class,"executed-once")).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims WHERE event_id=?",Integer.class,eventId)).isEqualTo(1);assertThat(fake.sendReceiveCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT model_turn_state FROM durable_turn_executions WHERE incoming_event_id=?",String.class,eventId)).isEqualTo("TOOL_EXECUTED");assertThat(jdbc.queryForObject("SELECT status FROM durable_turn_executions WHERE incoming_event_id=?",String.class,eventId)).isIn("STALE","INTERRUPTED");
            assertThat(jdbc.queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,eventId)).isNotEqualTo("COMPLETED");
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void persistedBeforeAgentCrashIsClaimedOnceAfterRestartWithStableTurnIdentity() throws Exception {
        Path db=TestSqliteDatabase.create("social-persisted-before-agent");String url=TestSqliteDatabase.jdbcUrl(db);
        String eventId="905001";UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:crash-group".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try {
            ConfigurableApplicationContext first=start(url);
            try {
                var fake=first.getBean(FakeOneBotServer.class);var gateway=first.getBean(online.wanan.xingchen.adapter.onebot.OneBotGateway.class);
                gateway.setEventListener(event->{var actor=first.getBean(online.wanan.xingchen.core.identity.IdentityResolver.class).resolve(event);first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class).persistOrdered(event,actor);});
                assertThat(gateway.connect().connected()).isTrue();await(()->fake.wsConnections()>0,5000);
                fake.emit(groupEvent("crash-group",eventId,40,"@bot recover once",true));
                var messages=first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class);
                await(()->!messages.pendingEvents().isEmpty(),5000);
                var persisted=messages.findExecution(messages.pendingEvents().getFirst(),conversationId);
                assertThat(persisted.status()).isEqualTo("PERSISTED");assertThat(persisted.turnId()).isEqualTo(online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId(persisted.executionKey()));
                assertThat(first.getBean(MockModelProvider.class).requests()).isEmpty();
            } finally {first.close();}
            try(ConfigurableApplicationContext second=start(url)) {
                var provider=second.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.text("恢复完成"),new ModelUsage(10,0,10,4,0,"fixture","fixture-model",1),null));
                second.getBean(SocialRuntime.class).start();var fake=second.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
                var messages=second.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class);
                await(()->provider.requests().size()==1&&"COMPLETED".equals(second.getBean(org.springframework.jdbc.core.JdbcTemplate.class).queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,eventId)),8000);
                var execution=second.getBean(org.springframework.jdbc.core.JdbcTemplate.class).query("SELECT execution_key,turn_id,generation,status FROM inbound_turn_executions WHERE incoming_event_id=?",(rs,n)->new online.wanan.xingchen.storage.IncomingMessageStore.InboundExecution(rs.getString(1),rs.getString(2),rs.getLong(3),rs.getString(4)),eventId).getFirst();
                assertThat(execution.status()).isEqualTo("COMPLETED");assertThat(execution.turnId()).isEqualTo(online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId(execution.executionKey()));
                assertThat(provider.requests()).hasSize(1);assertThat(fake.appliedSendCount()).isLessThanOrEqualTo(1);
            }
        } finally {TestSqliteDatabase.clean(db);}
    }

    @Test void claimedNotStartedTurnIsDurableAndRunsOnceAfterRestart() throws Exception {
        Path db=TestSqliteDatabase.create("social-claimed-not-started");String url=TestSqliteDatabase.jdbcUrl(db);String eventId="905002";
        UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:claimed-crash".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try{
            ConfigurableApplicationContext first=start(url);
            try{
                var gateway=first.getBean(online.wanan.xingchen.adapter.onebot.OneBotGateway.class);var fake=first.getBean(FakeOneBotServer.class);
                gateway.setEventListener(e->{var actor=first.getBean(online.wanan.xingchen.core.identity.IdentityResolver.class).resolve(e);first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class).persistOrdered(e,actor);});
                assertThat(gateway.connect().connected()).isTrue();await(()->fake.wsConnections()>0,5000);fake.emit(groupEvent("claimed-crash",eventId,50,"@bot recover claimed",true));
                var store=first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class);await(()->!store.pendingEvents().isEmpty(),5000);
                var event=store.pendingEvents().getFirst();var state=first.getBean(SimulationStateService.class).get(conversationId);
                assertThat(store.claim(event,conversationId,state.generation())).isTrue();
                String turn=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId(online.wanan.xingchen.storage.IncomingMessageStore.executionKey(event,conversationId.toString()));
                var journal=first.getBean(online.wanan.xingchen.storage.DurableTurnExecutionStore.class);
                assertThat(journal.recoverable()).singleElement().satisfies(r->{assertThat(r.turnId()).isEqualTo(turn);assertThat(r.modelTurnState()).isEqualTo("NOT_STARTED");});
                assertThat(first.getBean(MockModelProvider.class).requests()).isEmpty();
            }finally{first.close();}
            try(ConfigurableApplicationContext second=start(url)){
                var provider=second.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.text("claimed恢复"),new ModelUsage(10,0,10,4,0,"fixture","fixture-model",1),null));
                second.getBean(SocialRuntime.class).start();var fake=second.getBean(FakeOneBotServer.class);
                await(()->provider.requests().size()==1&&fake.appliedSendCount()==1,8000);
                await(()->"COMPLETED".equals(second.getBean(JdbcTemplate.class).queryForObject("SELECT status FROM durable_turn_executions WHERE incoming_event_id=?",String.class,eventId)),5000);
                assertThat(provider.requests()).hasSize(1);
                assertThat(second.getBean(online.wanan.xingchen.storage.DurableTurnExecutionStore.class).recoverable()).isEmpty();
                assertThat(second.getBean(JdbcTemplate.class).queryForObject("SELECT status FROM durable_turn_executions WHERE incoming_event_id=?",String.class,eventId)).isEqualTo("COMPLETED");
            }
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void streamingNoEffectTurnRestartsSameLogicalTurnWithoutReusingPartialText() throws Exception {
        Path db=TestSqliteDatabase.create("social-streaming-restart");String url=TestSqliteDatabase.jdbcUrl(db);String eventId="905006";
        UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:stream-crash".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String turn=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId("QQ:"+conversationId+":"+eventId);
        try{
            ConfigurableApplicationContext first=start(url);
            try{
                var gateway=first.getBean(online.wanan.xingchen.adapter.onebot.OneBotGateway.class);var fake=first.getBean(FakeOneBotServer.class);
                gateway.setEventListener(e->{var actor=first.getBean(online.wanan.xingchen.core.identity.IdentityResolver.class).resolve(e);first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class).persistOrdered(e,actor);});
                assertThat(gateway.connect().connected()).isTrue();await(()->fake.wsConnections()>0,5000);fake.emit(groupEvent("stream-crash",eventId,54,"@bot partial stream",true));
                var messages=first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class);await(()->!messages.pendingEvents().isEmpty(),5000);var event=messages.pendingEvents().getFirst();
                assertThat(messages.claim(event,conversationId,first.getBean(SimulationStateService.class).get(conversationId).generation())).isTrue();
                first.getBean(online.wanan.xingchen.storage.DurableTurnExecutionStore.class).state(turn,"STREAMING_NO_EFFECT");
            }finally{first.close();}
            try(ConfigurableApplicationContext second=start(url)){
                var provider=second.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.text("complete regenerated reply"),new ModelUsage(9,0,9,3,0,"fixture","fixture-model",1),null));
                second.getBean(SocialRuntime.class).start();var fake=second.getBean(FakeOneBotServer.class);await(()->provider.requests().size()==1&&fake.appliedSendCount()==1,8000);
                assertThat(provider.requests()).hasSize(1);assertThat(fake.appliedMessageIds()).hasSize(1);
                assertThat(second.getBean(JdbcTemplate.class).queryForObject("SELECT turn_id FROM durable_turn_executions WHERE incoming_event_id=?",String.class,eventId)).isEqualTo(turn);
            }
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void emittedOutputIsRecoveredThroughOutboundLedgerWithoutModelReplay() throws Exception {
        Path db=TestSqliteDatabase.create("social-emitted-output-restart");String url=TestSqliteDatabase.jdbcUrl(db);String eventId="905003";
        UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:output-crash".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String turn=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId("QQ:"+conversationId+":"+eventId);
        try{
            ConfigurableApplicationContext first=start(url);
            try{
                var gateway=first.getBean(online.wanan.xingchen.adapter.onebot.OneBotGateway.class);var fake=first.getBean(FakeOneBotServer.class);
                gateway.setEventListener(e->{var actor=first.getBean(online.wanan.xingchen.core.identity.IdentityResolver.class).resolve(e);first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class).persistOrdered(e,actor);});
                assertThat(gateway.connect().connected()).isTrue();await(()->fake.wsConnections()>0,5000);fake.emit(groupEvent("output-crash",eventId,51,"@bot output pending",true));
                var messages=first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class);await(()->!messages.pendingEvents().isEmpty(),5000);var event=messages.pendingEvents().getFirst();
                assertThat(messages.claim(event,conversationId,first.getBean(SimulationStateService.class).get(conversationId).generation())).isTrue();
                first.getBean(online.wanan.xingchen.storage.DurableTurnExecutionStore.class).output(turn,"TEXT_REPLY",List.of("durable output"));
            }finally{first.close();}
            try(ConfigurableApplicationContext second=start(url)){
                var provider=second.getBean(MockModelProvider.class);second.getBean(SocialRuntime.class).start();var fake=second.getBean(FakeOneBotServer.class);
                await(()->fake.appliedSendCount()==1,8000);
                await(()->"COMPLETED".equals(second.getBean(JdbcTemplate.class).queryForObject("SELECT status FROM durable_turn_executions WHERE turn_id=?",String.class,turn)),5000);
                assertThat(provider.requests()).isEmpty();assertThat(fake.appliedMessageIds()).hasSize(1);
            }
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void recoverableTurnFromPriorGenerationIsMarkedStaleAndNotResumed() throws Exception {
        Path db=TestSqliteDatabase.create("social-stale-turn-restart");String url=TestSqliteDatabase.jdbcUrl(db);String eventId="905004";
        UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:stale-crash".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String turn=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId("QQ:"+conversationId+":"+eventId);
        try{
            ConfigurableApplicationContext first=start(url);
            try{
                var gateway=first.getBean(online.wanan.xingchen.adapter.onebot.OneBotGateway.class);var fake=first.getBean(FakeOneBotServer.class);
                gateway.setEventListener(e->{var actor=first.getBean(online.wanan.xingchen.core.identity.IdentityResolver.class).resolve(e);first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class).persistOrdered(e,actor);});
                assertThat(gateway.connect().connected()).isTrue();await(()->fake.wsConnections()>0,5000);fake.emit(groupEvent("stale-crash",eventId,52,"@bot stale",true));
                var messages=first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class);await(()->!messages.pendingEvents().isEmpty(),5000);var event=messages.pendingEvents().getFirst();
                var simulation=first.getBean(SimulationStateService.class);var state=simulation.get(conversationId);assertThat(messages.claim(event,conversationId,state.generation())).isTrue();
                first.getBean(online.wanan.xingchen.storage.DurableTurnExecutionStore.class).state(turn,"STREAMING_NO_EFFECT");simulation.wake(conversationId,true);
            }finally{first.close();}
            try(ConfigurableApplicationContext second=start(url)){
                var provider=second.getBean(MockModelProvider.class);second.getBean(SocialRuntime.class).start();
                await(()->"STALE".equals(second.getBean(JdbcTemplate.class).queryForObject("SELECT status FROM durable_turn_executions WHERE turn_id=?",String.class,turn)),5000);
                assertThat(provider.requests()).isEmpty();
            }
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void toolEffectClaimedBeforeCrashIsFailClosedWithoutWholeTurnReplay() throws Exception {
        Path db=TestSqliteDatabase.create("social-tool-effect-crash");String url=TestSqliteDatabase.jdbcUrl(db);String eventId="905005";
        UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:tool-crash".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String turn=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId("QQ:"+conversationId+":"+eventId);
        try{
            ConfigurableApplicationContext first=start(url);
            try{
                var gateway=first.getBean(online.wanan.xingchen.adapter.onebot.OneBotGateway.class);var fake=first.getBean(FakeOneBotServer.class);
                gateway.setEventListener(e->{var actor=first.getBean(online.wanan.xingchen.core.identity.IdentityResolver.class).resolve(e);first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class).persistOrdered(e,actor);});
                assertThat(gateway.connect().connected()).isTrue();await(()->fake.wsConnections()>0,5000);fake.emit(groupEvent("tool-crash",eventId,53,"@bot tool happened",true));
                var messages=first.getBean(online.wanan.xingchen.storage.IncomingMessageStore.class);await(()->!messages.pendingEvents().isEmpty(),5000);var event=messages.pendingEvents().getFirst();
                assertThat(messages.claim(event,conversationId,first.getBean(SimulationStateService.class).get(conversationId).generation())).isTrue();
                first.getBean(online.wanan.xingchen.storage.DurableTurnExecutionStore.class).state(turn,"TOOL_EXECUTED");
                first.getBean(JdbcTemplate.class).update("INSERT INTO tool_execution_claims(event_id,call_id,claimed_at) VALUES(?,?,?)",eventId,"stable-call-1",Instant.now().toString());
            }finally{first.close();}
            try(ConfigurableApplicationContext second=start(url)){
                var provider=second.getBean(MockModelProvider.class);second.getBean(SocialRuntime.class).start();
                await(()->"INTERRUPTED".equals(second.getBean(JdbcTemplate.class).queryForObject("SELECT status FROM durable_turn_executions WHERE turn_id=?",String.class,turn)),5000);
                assertThat(provider.requests()).isEmpty();
                assertThat(second.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM tool_execution_claims WHERE event_id=?",Integer.class,eventId)).isEqualTo(1);
            }
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void waitPersistsAcrossRealContextRestartAndNextFakeOneBotEventContinuesOnce() throws Exception {
        Path db=TestSqliteDatabase.create("social-wait-restart");String url=TestSqliteDatabase.jdbcUrl(db);
        UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:restart-group".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String firstId="900001",nextId="900002",turnId=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId("QQ:"+conversationId+":900002");
        try {
        ConfigurableApplicationContext first=start(url);
        try {
            var provider=first.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.waitForNextMessage(),new ModelUsage(12,0,12,2,0,"fixture","fixture-model",1),null));
            first.getBean(SocialRuntime.class).start();var fake=first.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
            fake.emit(event(firstId,10,"please wait",true));
            var states=first.getBean(SimulationStateService.class);await(()->states.get(conversationId).wakeState()==SimulationConversationState.WakeState.WAITING,8000);
            var waiting=states.get(conversationId);
            assertThat(waiting.originTurnId()).isEqualTo(firstId);assertThat(waiting.lastReadCursor()).isEqualTo(firstId);
            assertThat(provider.requests()).hasSize(1);
            await(()->"COMPLETED".equals(first.getBean(JdbcTemplate.class).queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,firstId)),5000);
            UUID actor=first.getBean(IdentityRegistry.class).identify(Platform.QQ,"user-a").id();
            UUID bot=first.getBean(IdentityRegistry.class).identify(Platform.QQ,"test-bot").id();
            first.getBean(RelationshipTermRegistry.class).save(new RelationshipTerm(UUID.randomUUID(),bot,actor,RelationshipType.ADDRESS_AS,"姐姐",ScopeType.GLOBAL,"",true,1,"fixture",Instant.now(),Instant.now()));
            UUID memoryId=UUID.randomUUID();var memory=new Memory(memoryId,MemoryType.PERSON,actor,null,null,"User likes tea",MemoryScope.PERSON_GLOBAL,actor.toString(),.95,.9,true,Instant.now(),Instant.now(),null,null,MemoryStatus.ACTIVE);
            first.getBean(MemoryRepository.class).save(memory,List.of(new MemorySource(memoryId,"QQ",conversationId,firstId,actor,Instant.now())));
            UUID foreignProjectMemoryId=UUID.randomUUID();var foreignProjectMemory=new Memory(foreignProjectMemoryId,MemoryType.PROJECT,null,null,"unrelated-project","must not enter WAIT continuation",MemoryScope.PROJECT,"unrelated-project",.95,.9,true,Instant.now(),Instant.now(),null,null,MemoryStatus.ACTIVE);
            first.getBean(MemoryRepository.class).save(foreignProjectMemory,List.of(new MemorySource(foreignProjectMemoryId,"QQ",conversationId,firstId,actor,Instant.now())));
            JdbcTemplate jdbc=first.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM token_usage WHERE conversation_id=?",Integer.class,conversationId.toString())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM agent_traces WHERE conversation_id=?",Integer.class,conversationId.toString())).isEqualTo(1);
        } finally {first.close();}

        try(ConfigurableApplicationContext second=start(url)) {
            var provider=second.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.text("记得，你喜欢喝茶。"),new ModelUsage(14,0,14,9,0,"fixture","fixture-model",1),null));
            var states=second.getBean(SimulationStateService.class);var recovered=states.get(conversationId);
            assertThat(recovered.wakeState()).isEqualTo(SimulationConversationState.WakeState.WAITING);
            assertThat(recovered.originTurnId()).isEqualTo(firstId);
            second.getBean(SocialRuntime.class).start();var fake=second.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
            var jdbc=second.getBean(JdbcTemplate.class);
            fake.emit(event(firstId,10,"duplicate origin",true));
            await(()->provider.requests().isEmpty(),500);
            assertThat(states.get(conversationId).wakeState()).isEqualTo(SimulationConversationState.WakeState.WAITING);
            fake.emit(event("899999",9,"late mention",true));
            await(()->jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE platform_message_id='899999'",Integer.class)==1,5000);
            assertThat(states.get(conversationId).wakeState()).isEqualTo(SimulationConversationState.WakeState.WAITING);
            assertThat(provider.requests()).isEmpty();
            fake.emit(event(nextId,11,"tea?",false));
            var outbound=second.getBean(online.wanan.xingchen.storage.SqliteOutboundExecutionRepository.class);var key=conversationId+":"+turnId+":0";
            await(()->provider.requests().size()==1&&fake.appliedSendCount()==1&&outbound.find(key).map(r->r.status()==online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.SUCCESS).orElse(false),8000);
            var context=provider.receivedContexts().getFirst();
            assertThat(context.relationshipBlock()).contains("姐姐");
            assertThat(context.renderSections()).contains("User likes tea");
            assertThat(context.memories()).noneMatch(m->m.content().equals("must not enter WAIT continuation"));
            assertThat(fake.appliedMessageIds()).hasSize(1);
            assertThat(states.get(conversationId).wakeState()).isEqualTo(SimulationConversationState.WakeState.AWAKE);
            assertThat(states.get(conversationId).originTurnId()).isNull();
            assertThat(provider.requests()).hasSize(1);
            assertThat(outbound.find(key).orElseThrow().status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.SUCCESS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE incoming_event_id=?",Integer.class,nextId)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT wake_reasons_json FROM durable_turn_executions WHERE incoming_event_id=?",String.class,nextId)).contains("WAIT_CONTINUATION");await(()->"COMPLETED".equals(jdbc.queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,nextId)),5000);assertThat(jdbc.queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,nextId)).isEqualTo("COMPLETED");
        }
        try(ConfigurableApplicationContext third=start(url)) {
            var provider=third.getBean(MockModelProvider.class);third.getBean(SocialRuntime.class).start();var fake=third.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
            fake.emit(event(nextId,11,"duplicate completed reply",false));fake.emit(event("900003",12,"drain sentinel",false));
            await(()->"900003".equals(third.getBean(SimulationStateService.class).get(conversationId).lastReadCursor()),5000);
            assertThat(provider.requests()).isEmpty();assertThat(fake.sendReceiveCount()).isZero();
            assertThat(third.getBean(online.wanan.xingchen.storage.SqliteOutboundExecutionRepository.class).find(conversationId+":"+turnId+":0").orElseThrow().status())
                    .isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.SUCCESS);
        }
        } finally {TestSqliteDatabase.clean(db);}
    }

    @Test void unknownOutboundSurvivesRestartWithoutReplayingTurnOrResending() throws Exception {
        Path db=TestSqliteDatabase.create("social-unknown-restart");String url=TestSqliteDatabase.jdbcUrl(db);
        UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:unknown-group".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String eventId="910001",turnId=online.wanan.xingchen.storage.IncomingMessageStore.stableTurnId("QQ:"+conversationId+":"+eventId),key=conversationId+":"+turnId+":0";
        try {
            ConfigurableApplicationContext first=start(url);
            try {
                first.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.text("may have been sent"),new ModelUsage(10,0,10,4,0,"fixture","fixture-model",1),null));
                first.getBean(SocialRuntime.class).start();var fake=first.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
                fake.delayNext("send_group_msg",400);fake.sendBehavior(FakeOneBotServer.SendBehavior.TIMEOUT_AFTER_COMMIT);
                fake.emit(groupEvent("unknown-group",eventId,20,"please reply",true));
                var ledger=first.getBean(online.wanan.xingchen.storage.SqliteOutboundExecutionRepository.class);
                await(()->ledger.find(key).map(r->r.status()==online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.UNKNOWN).orElse(false),8000);
                assertThat(fake.appliedSendCount()).isEqualTo(1);
                assertThat(first.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE turn_id=?",Integer.class,turnId)).isEqualTo(1);
            } finally {first.close();}
            try(ConfigurableApplicationContext second=start(url)) {
                var provider=second.getBean(MockModelProvider.class);second.getBean(SocialRuntime.class).start();var fake=second.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
                fake.emit(groupEvent("unknown-group",eventId,20,"duplicate after restart",true));fake.emit(groupEvent("unknown-group","910002",21,"drain sentinel",false));
                await(()->"910002".equals(second.getBean(SimulationStateService.class).get(conversationId).lastReadCursor()),5000);
                assertThat(provider.requests()).isEmpty();assertThat(fake.sendReceiveCount()).isZero();
                var outbound=second.getBean(online.wanan.xingchen.storage.SqliteOutboundExecutionRepository.class);
                assertThat(second.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE turn_id=?",Integer.class,turnId)).isEqualTo(1);
                assertThat(outbound.find(key).orElseThrow().status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.UNKNOWN);
                assertThat(second.getBean(JdbcTemplate.class).queryForObject("SELECT status FROM durable_turn_executions WHERE turn_id=?",String.class,turnId)).isEqualTo("INTERRUPTED");
                var prevented=new java.util.concurrent.atomic.AtomicInteger();
                var existing=outbound.find(key).orElseThrow();
                var replay=second.getBean(online.wanan.xingchen.core.conversation.OutboundExecutionService.class).dispatch(key,existing.conversationId(),existing.turnId(),existing.generation(),existing.logicalMessageId(),"unknown-group",()->{prevented.incrementAndGet();return null;});
                assertThat(replay.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.UNKNOWN);assertThat(prevented).hasValue(0);
            }
        } finally {TestSqliteDatabase.clean(db);}
    }

    @Test void sleepingAndNoReplyCursorSurviveContextRestarts() throws Exception {
        Path db=TestSqliteDatabase.create("social-sleep-no-reply");String url=TestSqliteDatabase.jdbcUrl(db);
        UUID conversationId=UUID.nameUUIDFromBytes("xingchen:conversation:QQ:GROUP:sleep-group".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String sleepingId="920001",noReplyId="920002";
        try {
            ConfigurableApplicationContext first=start(url);
            try {
                JdbcTemplate jdbc=first.getBean(JdbcTemplate.class);jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversationId.toString(),"QQ","GROUP","sleep-group",Instant.now().toString());
                var states=first.getBean(SimulationStateService.class);states.sleep(conversationId,Instant.now().plusSeconds(3600),true);
                first.getBean(SocialRuntime.class).start();var fake=first.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
                fake.emit(groupEvent("sleep-group",sleepingId,30,"background chatter",false));
                await(()->jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status='COMPLETED'",Integer.class,sleepingId)==1,5000);
                assertThat(states.get(conversationId).wakeState()).isEqualTo(SimulationConversationState.WakeState.SLEEPING);
                assertThat(states.get(conversationId).lastReadCursor()).isEqualTo(sleepingId);
                assertThat(first.getBean(MockModelProvider.class).requests()).isEmpty();assertThat(jdbc.queryForObject("SELECT status FROM durable_turn_executions WHERE incoming_event_id=?",String.class,sleepingId)).isEqualTo("COMPLETED");assertThat(jdbc.queryForObject("SELECT model_turn_state FROM durable_turn_executions WHERE incoming_event_id=?",String.class,sleepingId)).isEqualTo("NOT_STARTED");assertThat(jdbc.queryForObject("SELECT output_type FROM durable_turn_executions WHERE incoming_event_id=?",String.class,sleepingId)).isNull();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE conversation_id=?",Integer.class,conversationId.toString())).isZero();
            } finally {first.close();}
            ConfigurableApplicationContext second=start(url);
            try {
                var states=second.getBean(SimulationStateService.class);assertThat(states.get(conversationId).wakeState()).isEqualTo(SimulationConversationState.WakeState.SLEEPING);
                var provider=second.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.noReply(),new ModelUsage(8,0,8,1,0,"fixture","fixture-model",1),null));
                second.getBean(SocialRuntime.class).start();var fake=second.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
                fake.emit(groupEvent("sleep-group",noReplyId,31,"@bot stay silent",true));
                await(()->provider.requests().size()==1&&second.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status='COMPLETED'",Integer.class,noReplyId)==1,8000);
                assertThat(states.get(conversationId).wakeState()).isEqualTo(SimulationConversationState.WakeState.AWAKE);
                assertThat(fake.sendReceiveCount()).isZero();
                assertThat(second.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE conversation_id=?",Integer.class,conversationId.toString())).isZero();
                var secondJdbc=second.getBean(JdbcTemplate.class);assertThat(secondJdbc.queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,noReplyId)).isEqualTo("COMPLETED");assertThat(secondJdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE incoming_event_id=?",Integer.class,noReplyId)).isEqualTo(1);assertThat(secondJdbc.queryForObject("SELECT output_type FROM durable_turn_executions WHERE incoming_event_id=?",String.class,noReplyId)).isEqualTo("NO_REPLY");
            } finally {second.close();}
            try(ConfigurableApplicationContext third=start(url)) {
                var states=third.getBean(SimulationStateService.class);var provider=third.getBean(MockModelProvider.class);third.getBean(SocialRuntime.class).start();var fake=third.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
                fake.emit(groupEvent("sleep-group",noReplyId,31,"duplicate silent event",true));fake.emit(groupEvent("sleep-group","920003",32,"drain sentinel",false));
                await(()->"920003".equals(states.get(conversationId).lastReadCursor()),5000);
                assertThat(provider.requests()).isEmpty();assertThat(states.get(conversationId).lastReadCursor()).isEqualTo("920003");assertThat(fake.sendReceiveCount()).isZero();
            }
        } finally {TestSqliteDatabase.clean(db);}
    }

    private static ConfigurableApplicationContext start(String url,String... additionalArgs){
        return new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class).web(WebApplicationType.NONE).profiles("runtime-test")
                .properties("spring.main.banner-mode=off","xingchen.integrations.dsh-enabled=false","xingchen.integrations.onebot-enabled=false","xingchen.integrations.model-enabled=false","xingchen.social.enabled=false")
                .run(join("--spring.datasource.url="+url,"--xingchen.memory.database-path="+url.substring("jdbc:sqlite:".length()),"--xingchen.onebot.login-user-id=test-bot","--xingchen.owner.platform-user-id=owner-test",additionalArgs));
    }
    private static String[] join(String a,String b,String c,String d,String[] additional){var args=new java.util.ArrayList<String>(List.of(a,b,c,d));args.addAll(List.of(additional));return args.toArray(String[]::new);}
    private static String event(String id,long sequence,String text,boolean mention){
        String prefix=mention?"{\"type\":\"at\",\"data\":{\"qq\":\"test-bot\"}},":"";
        return "{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"test-bot\",\"user_id\":\"user-a\",\"group_id\":\"restart-group\",\"time\":100000002,\"message_id\":\""+id+"\",\"message_seq\":"+sequence+",\"sender\":{\"user_id\":\"user-a\",\"nickname\":\"User A\"},\"message\":["+prefix+"{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]}";
    }
    private static String groupEvent(String group,String id,long sequence,String text,boolean mention){
        String prefix=mention?"{\"type\":\"at\",\"data\":{\"qq\":\"test-bot\"}},":"";
        return "{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"test-bot\",\"user_id\":\"user-a\",\"group_id\":\""+group+"\",\"time\":100000002,\"message_id\":\""+id+"\",\"message_seq\":"+sequence+",\"sender\":{\"user_id\":\"user-a\",\"nickname\":\"User A\"},\"message\":["+prefix+"{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]}";
    }
    private static String privateEvent(String id,long sequence,String text){return "{\"post_type\":\"message\",\"message_type\":\"private\",\"self_id\":\"test-bot\",\"user_id\":\"owner-test\",\"time\":100000002,\"message_id\":\""+id+"\",\"message_seq\":"+sequence+",\"sender\":{\"user_id\":\"owner-test\",\"nickname\":\"Owner\"},\"message\":[{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]}";}
    private static void await(BooleanSupplier condition,long timeoutMillis)throws InterruptedException{long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(20);assertThat(condition.getAsBoolean()).isTrue();}
}
