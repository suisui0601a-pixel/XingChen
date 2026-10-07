package online.wanan.xingchen.core;

import online.wanan.xingchen.core.memory.*;
import online.wanan.xingchen.core.agent.ToolExecutionLedger;
import online.wanan.xingchen.core.context.SessionHandoff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SqliteStartupContractTest {
    private static final java.nio.file.Path DB=TestSqliteDatabase.create("sqlite-contract");
    @DynamicPropertySource static void isolatedDatabase(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->TestSqliteDatabase.jdbcUrl(DB));p.add("xingchen.memory.database-path",()->DB.toAbsolutePath().toString());}
    @Autowired JdbcTemplate jdbc;
    @Autowired MemoryRepository memories;
    @Autowired ToolExecutionLedger toolClaims;
    @Autowired online.wanan.xingchen.storage.SqliteSessionLifecycleManager sessions;
    @Autowired online.wanan.xingchen.core.agent.SimulationStateService simulation;
    @Autowired online.wanan.xingchen.core.agent.PendingInteractionService pending;
    @Autowired online.wanan.xingchen.storage.SqliteDshSessionMappingRepository dshMappings;
    @Autowired online.wanan.xingchen.core.agent.DshPendingInteractionStore dshPending;
    @Autowired online.wanan.xingchen.core.identity.IdentityResolver identityResolver;
    @Autowired online.wanan.xingchen.core.identity.IdentityPersistence identityPersistence;
    @Autowired online.wanan.xingchen.core.relationship.RelationshipTermRegistry relationshipTerms;
    @Autowired online.wanan.xingchen.storage.SqliteOutboundExecutionRepository outboundRepository;
    @Autowired online.wanan.xingchen.core.conversation.OutboundExecutionService outboundService;
    @Autowired online.wanan.xingchen.storage.DurableTurnExecutionStore turnJournal;
    @Autowired online.wanan.xingchen.core.conversation.ConversationResetCoordinator resetCoordinator;
    @Autowired online.wanan.xingchen.core.conversation.ShortContextResetService resetService;
    @AfterAll void cleanDatabase(){TestSqliteDatabase.clean(jdbc,DB);}

    @Test void sqlitePragmasAndFlywaySchemaAreActiveAtStartup() {
        assertThat(jdbc.queryForObject("PRAGMA journal_mode", String.class)).isEqualToIgnoringCase("wal");
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("PRAGMA busy_timeout", Integer.class)).isEqualTo(5000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='memories'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='memories_fts'", Integer.class)).isEqualTo(1);
    }
    @Test void waitStateIsDurableAndContinuationUsesGenerationCompareAndSet(){
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","wait-"+conversation,now);
        var waiting=simulation.waitForNextMessage(conversation,"origin-event-7","next-message");
        assertThat(waiting.wakeState()).isEqualTo(online.wanan.xingchen.core.agent.SimulationConversationState.WakeState.WAITING);
        assertThat(waiting.originTurnId()).isEqualTo("origin-event-7");assertThat(waiting.waitingSince()).isNotNull();
        assertThat(simulation.continueWait(conversation,waiting.generation()-1)).isEmpty();
        var recovered=simulation.get(conversation);assertThat(recovered.wakeState()).isEqualTo(online.wanan.xingchen.core.agent.SimulationConversationState.WakeState.WAITING);
        var continued=simulation.continueWait(conversation,waiting.generation()).orElseThrow();
        assertThat(continued.wakeState()).isEqualTo(online.wanan.xingchen.core.agent.SimulationConversationState.WakeState.AWAKE);
        assertThat(continued.originTurnId()).isNull();assertThat(continued.generation()).isEqualTo(waiting.generation()+1);
        assertThat(simulation.continueWait(conversation,waiting.generation())).isEmpty();
    }
    @Test void resetAdvancesGenerationRetiresWaitAndPreservesWakePolicy(){
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","reset-generation-"+conversation,now);
        simulation.configure(conversation,java.util.Map.of("mention",true,"speakers",List.of("42")),true);
        var waiting=simulation.waitForNextMessage(conversation,"old-turn","next-message");
        var reset=simulation.reset(conversation);
        assertThat(reset.generation()).isEqualTo(waiting.generation()+1);assertThat(reset.wakeState()).isEqualTo(online.wanan.xingchen.core.agent.SimulationConversationState.WakeState.IDLE);
        assertThat(reset.originTurnId()).isNull();assertThat(reset.lastReadCursor()).isNull();assertThat(reset.wakeConfig()).containsEntry("mention",true).containsEntry("speakers",List.of("42"));
        assertThat(simulation.continueWait(conversation,waiting.generation())).isEmpty();
    }
    @Test void resetWaitStale001_oldContinuationAfterResetCleanupCannotReviveWait(){
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString(),platformId="reset-wait-stale-"+conversation;
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE",platformId,now);
        var waiting=simulation.waitForNextMessage(conversation,"wait-origin-"+conversation,"next-message");long oldGeneration=waiting.generation();
        var event=new online.wanan.xingchen.core.model.PlatformEvent(online.wanan.xingchen.core.model.Platform.QQ,
                new online.wanan.xingchen.core.model.ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,online.wanan.xingchen.core.model.ConversationType.PRIVATE,platformId),
                new online.wanan.xingchen.core.model.ActorIdentity(online.wanan.xingchen.core.model.Platform.QQ,"admin-reset","Admin",false,true),
                new online.wanan.xingchen.core.model.MessageIdentity("reset-wait-event-"+conversation,null),"message","friend","/reset",Instant.now(),java.util.Map.of(),online.wanan.xingchen.core.model.EventDetails.empty());
        var ticket=resetCoordinator.interrupt(event,"admin-reset",new online.wanan.xingchen.core.conversation.ActiveTurnRegistry());
        assertThat(ticket).isNotNull();assertThat(ticket.generation()).isEqualTo(oldGeneration+1);
        assertThat(resetService.cleanupReset(conversation,ticket.generation())).isTrue();resetCoordinator.complete(ticket);
        var afterReset=simulation.get(conversation);
        assertThat(simulation.continueWait(conversation,oldGeneration)).isEmpty();
        assertThat(simulation.get(conversation)).isEqualTo(afterReset);
        assertThat(afterReset.wakeState()).isEqualTo(online.wanan.xingchen.core.agent.SimulationConversationState.WakeState.IDLE);
        assertThat(afterReset.originTurnId()).isNull();assertThat(afterReset.lastReadCursor()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE conversation_id=?",Integer.class,conversation.toString())).isZero();
    }
    @Test void resetControlInterruptAdvancesGenerationCancelsActiveTurnAndPersistsCleanupTicket(){
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE","reset-owner-"+conversation,now);
        simulation.configure(conversation,java.util.Map.of("mention",true),true);var waiting=simulation.waitForNextMessage(conversation,"prior-turn","next-message");
        var token=new online.wanan.xingchen.core.agent.CancellationToken();var registry=new online.wanan.xingchen.core.conversation.ActiveTurnRegistry();assertThat(registry.beginIfCurrent(conversation,"active-turn",waiting.generation(),token,()->simulation.get(conversation).generation())).isNotNull();
        var event=new online.wanan.xingchen.core.model.PlatformEvent(online.wanan.xingchen.core.model.Platform.QQ,new online.wanan.xingchen.core.model.ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,online.wanan.xingchen.core.model.ConversationType.PRIVATE,"reset-owner-"+conversation),new online.wanan.xingchen.core.model.ActorIdentity(online.wanan.xingchen.core.model.Platform.QQ,"admin-reset","Admin",false,true),new online.wanan.xingchen.core.model.MessageIdentity("reset-event",null),"message","friend","/reset",Instant.now(),java.util.Map.of(),online.wanan.xingchen.core.model.EventDetails.empty());long priorEpoch=resetCoordinator.epoch(event);
        var ticket=resetCoordinator.interrupt(event,"admin-reset",registry);assertThat(ticket).isNotNull();assertThat(ticket.generation()).isEqualTo(waiting.generation()+1);assertThat(token.isCancelled()).isTrue();assertThat(resetCoordinator.current(event,priorEpoch)).isFalse();assertThat(resetCoordinator.pendingResets()).contains(ticket);assertThat(resetCoordinator.resettingCount()).isPositive();
        assertThat(simulation.continueWait(conversation,waiting.generation())).isEmpty();assertThat(resetService.cleanupReset(conversation,ticket.generation())).isTrue();resetCoordinator.complete(ticket);
        assertThat(simulation.get(conversation).wakeState()).isEqualTo(online.wanan.xingchen.core.agent.SimulationConversationState.WakeState.IDLE);assertThat(resetCoordinator.resettingCount()).isZero();assertThat(jdbc.queryForObject("SELECT status FROM conversation_reset_controls WHERE conversation_id=?",String.class,conversation.toString())).isEqualTo("COMPLETE");
    }
    @Test void outboundLedgerIsStableAndUnconfirmedDeliveryCannotBeRetried(){
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","outbound-"+conversation,now);
        var successCalls=new java.util.concurrent.atomic.AtomicInteger();
        var success=outboundService.dispatch("key-success",conversation,"turn-1",4,"turn-1:0","group-1",()->{successCalls.incrementAndGet();return new online.wanan.xingchen.adapter.onebot.SendReceipt(true,"group","provider-1","ok");});
        var duplicate=outboundService.dispatch("key-success",conversation,"turn-1",4,"turn-1:0","group-1",()->{successCalls.incrementAndGet();return null;});
        assertThat(success.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.SUCCESS);
        assertThat(success.providerMessageId()).isEqualTo("provider-1");assertThat(duplicate.status()).isEqualTo(success.status());assertThat(successCalls).hasValue(1);
        var unknownCalls=new java.util.concurrent.atomic.AtomicInteger();
        var unknown=outboundService.dispatch("key-unknown",conversation,"turn-2",4,"turn-2:0","group-1",()->{unknownCalls.incrementAndGet();return new online.wanan.xingchen.adapter.onebot.SendReceipt(false,"group","group-1","",online.wanan.xingchen.adapter.onebot.DeliveryStatus.UNKNOWN);});
        var retry=outboundService.dispatch("key-unknown",conversation,"turn-2",4,"turn-2:0","group-1",()->{unknownCalls.incrementAndGet();return null;});
        assertThat(unknown.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.UNKNOWN);assertThat(retry.status()).isEqualTo(unknown.status());assertThat(unknownCalls).hasValue(1);
        var preDispatchCalls=new java.util.concurrent.atomic.AtomicInteger();
        var invalid=outboundService.dispatch("key-invalid",conversation,"turn-3",4,"turn-3:0"," ",()->{preDispatchCalls.incrementAndGet();return null;});
        assertThat(invalid.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.FAILED);assertThat(preDispatchCalls).hasValue(0);
    }
    @Test void durableTurnJournalRecordsBoundariesAndRecoveryClaimIsGenerationCas(){
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString(),key="durable-key-"+conversation,turn="durable-turn-"+conversation,event="durable-event-"+conversation;
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","durable-"+conversation,now);
        jdbc.update("INSERT INTO inbound_turn_executions(execution_key,platform,conversation_id,incoming_event_id,turn_id,generation,status,event_json,created_at,updated_at) VALUES(?,?,?,?,?,?,'CLAIMED','{}',?,?)",key,"QQ",conversation.toString(),event,turn,3,now,now);
        turnJournal.create(turn,conversation,key,event,3);
        assertThat(turnJournal.recoverable()).anySatisfy(r->{assertThat(r.turnId()).isEqualTo(turn);assertThat(r.modelTurnState()).isEqualTo("NOT_STARTED");});
        assertThat(turnJournal.claimRecovery(turn,-1)).isFalse();
        assertThat(turnJournal.claimRecovery(turn,3)).isFalse();
        turnJournal.markStale(turn);
        jdbc.update("INSERT INTO inbound_turn_executions(execution_key,platform,conversation_id,incoming_event_id,turn_id,generation,status,event_json,created_at,updated_at) VALUES(?,?,?,?,?,?,'CLAIMED','{}',?,?)",key+"-retry","QQ",conversation.toString(),event+"-retry",turn+"-retry",0,now,now);
        turnJournal.create(turn+"-retry",conversation,key+"-retry",event+"-retry",0);
        assertThat(turnJournal.claimRecovery(turn+"-retry",0)).isTrue();
        assertThat(turnJournal.claimRecovery(turn+"-retry",0)).isFalse();
        turnJournal.state(turn+"-retry","STREAMING_NO_EFFECT");
        assertThat(jdbc.queryForObject("SELECT model_turn_state FROM durable_turn_executions WHERE turn_id=?",String.class,turn+"-retry")).isEqualTo("STREAMING_NO_EFFECT");
        assertThat(jdbc.queryForObject("SELECT status FROM durable_turn_executions WHERE turn_id=?",String.class,turn+"-retry")).isEqualTo("RECOVERING");
    }

    @Test void turnExecutionObserverLeavesFragmentsInNoEffectAndPersistsToolAndOutputBoundaries(){
        var states=new ArrayList<String>();var turn=new online.wanan.xingchen.core.agent.ModelTurnExecutionState(s->states.add(s.name()));
        turn.begin();turn.modelOutputReceived();assertThat(turn.state()).isEqualTo(online.wanan.xingchen.core.agent.ModelTurnExecutionState.State.MODEL_OUTPUT_RECEIVED);
        turn.toolProposed();turn.toolExecuted();turn.outputEmitted();turn.outboundOutputUnknown();
        assertThat(states).containsSubsequence("STREAMING_NO_EFFECT","MODEL_OUTPUT_RECEIVED","TOOL_PROPOSED","TOOL_EXECUTED","OUTPUT_EMITTED","UNCERTAIN");
    }
    @Test void outboundCertaintyUsesClientDispatchBoundaryAndSurvivesRecovery() throws Exception {
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","outbound-wire-"+conversation,now);
        try(var fake=new FakeOneBotServer("");var gateway=new online.wanan.xingchen.adapter.onebot.OneBotV11Gateway(
                new online.wanan.xingchen.adapter.onebot.OneBotV11Configuration(fake.httpUri(),fake.wsUri(),"TOKEN","test-bot",false,100,120,50,200,16),
                new com.fasterxml.jackson.databind.ObjectMapper(),new online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer(new com.fasterxml.jackson.databind.ObjectMapper()),java.util.Map.of())){
            fake.sendBehavior(FakeOneBotServer.SendBehavior.SEND_SUCCESS);
            var success=outboundService.dispatch("wire-success",conversation,"turn-s",0,"turn-s:0","g1",()->fake.dispatchGroup(gateway,"g1","ok"));
            var successReplay=outboundService.dispatch("wire-success",conversation,"turn-s",0,"turn-s:0","g1",()->fake.dispatchGroup(gateway,"g1","duplicate"));
            assertThat(success.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.SUCCESS);assertThat(successReplay.status()).isEqualTo(success.status());
            fake.sendBehavior(FakeOneBotServer.SendBehavior.PRE_DISPATCH_FAILURE);
            var beforeDispatch=outboundService.dispatch("wire-pre",conversation,"turn-p",0,"turn-p:0","g1",()->fake.dispatchGroup(gateway,"g1","no"));
            assertThat(beforeDispatch.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.FAILED);
            fake.sendBehavior(FakeOneBotServer.SendBehavior.EXPLICIT_FAILURE);
            var rejected=outboundService.dispatch("wire-failed",conversation,"turn-f",0,"turn-f:0","g1",()->fake.dispatchGroup(gateway,"g1","no"));
            assertThat(rejected.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.FAILED);
            fake.delayNext("send_group_msg",400);fake.sendBehavior(FakeOneBotServer.SendBehavior.TIMEOUT_AFTER_COMMIT);
            var unknown=outboundService.dispatch("wire-unknown",conversation,"turn-u",0,"turn-u:0","g1",()->fake.dispatchGroup(gateway,"g1","uncertain"));
            var unknownReplay=outboundService.dispatch("wire-unknown",conversation,"turn-u",0,"turn-u:0","g1",()->fake.dispatchGroup(gateway,"g1","must-not-repeat"));
            assertThat(unknown.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.UNKNOWN);assertThat(unknownReplay.status()).isEqualTo(unknown.status());
            assertThat(fake.sendReceiveCount()).isEqualTo(3);assertThat(fake.appliedSendCount()).isEqualTo(2);assertThat(fake.appliedMessageIds()).hasSize(2);
            var pending=new online.wanan.xingchen.core.conversation.OutboundExecutionRecord("wire-crash",conversation,"turn-c",0,"turn-c:0",online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.PENDING,null,null,Instant.now(),Instant.now(),null);
            outboundRepository.reserve(pending);outboundRepository.startDispatch("wire-crash",Instant.now());outboundService.recoverAmbiguousDispatches();
            assertThat(outboundRepository.find("wire-crash").orElseThrow().status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.UNKNOWN);
        }
    }
    @Test void m3101_springPersistenceGraphUsesOnlySqliteImplementations(){assertThat(memories).isInstanceOf(online.wanan.xingchen.storage.MemorySqliteRepository.class);assertThat(toolClaims).isInstanceOf(online.wanan.xingchen.storage.SqliteToolExecutionLedger.class);assertThat(sessions).isInstanceOf(online.wanan.xingchen.storage.SqliteSessionLifecycleManager.class);}

    @Test void sqliteMemoryRepositoryPersistsProvenanceAndUsesFts() {
        UUID person=UUID.randomUUID(), conversation=UUID.randomUUID(), memoryId=UUID.randomUUID();
        String keyword="xctest"+UUID.randomUUID().toString().replace("-","");
        String now=Instant.now().toString();
        jdbc.update("INSERT INTO persons(id,platform,platform_user_id,created_at,updated_at) VALUES(?,?,?,?,?)",person.toString(),"QQ","contract-"+person,now,now);
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","contract-"+conversation,now);
        jdbc.update("INSERT INTO messages(id,platform,conversation_id,actor_person_id,platform_message_id,text,is_self,is_owner,created_at,raw_metadata_json) VALUES(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),"QQ",conversation.toString(),person.toString(),"message-"+memoryId,"source",0,0,now,"{}");
        var memory=new Memory(memoryId,MemoryType.SEMANTIC,person,null,null,keyword+" fact",MemoryScope.PERSON_GLOBAL,person.toString(),.9,.7,true,Instant.now(),Instant.now(),null,null,MemoryStatus.ACTIVE);
        var source=new MemorySource(memoryId,"QQ",conversation,"message-"+memoryId,person,Instant.now());
        memories.save(memory,List.of(source));
        var request=new RequestContext(person,conversation,false,null);
        assertThat(memories.searchText(keyword,request,10)).containsExactly(memory);
        assertThat(memories.sources(memoryId)).containsExactly(source);
        assertThat(memories.delete(memoryId)).isTrue();
        assertThat(memories.searchText(keyword,request,10)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories_fts WHERE memory_id=?",Integer.class,memoryId.toString())).isZero();
    }

    @Test void audit_memoryAndProvenanceWriteRollBackTogether() {
        UUID person=UUID.randomUUID(), conversation=UUID.randomUUID(), id=UUID.randomUUID();
        String now=Instant.now().toString();
        jdbc.update("INSERT INTO persons(id,platform,platform_user_id,created_at,updated_at) VALUES(?,?,?,?,?)",person.toString(),"QQ","rollback-"+person,now,now);
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","rollback-"+conversation,now);
        var memory=new Memory(id,MemoryType.PERSON,person,null,null,"rollback fact",MemoryScope.PERSON_GLOBAL,person.toString(),.9,.5,true,Instant.now(),Instant.now(),null,null,MemoryStatus.ACTIVE);
        String messageId="rollback-message-"+UUID.randomUUID();
        jdbc.update("INSERT INTO messages(id,platform,conversation_id,actor_person_id,platform_message_id,text,is_self,is_owner,created_at,raw_metadata_json) VALUES(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),"QQ",conversation.toString(),person.toString(),messageId,"source",0,0,now,"{}");
        var source=new MemorySource(id,"QQ",conversation,messageId,person,Instant.now());
        assertThatThrownBy(()->memories.save(memory,List.of(source,source))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE id=?",Integer.class,id.toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memory_sources WHERE memory_id=?",Integer.class,id.toString())).isZero();
    }

    @Test void audit_toolExecutionClaimIsAtomicAndPersistent() {
        String event="audit-event-"+UUID.randomUUID();
        assertThat(toolClaims.claim(event,"audit-call")).isTrue();
        assertThat(toolClaims.claim(event,"audit-call")).isFalse();
        assertThat(toolClaims.claim(event,"different-call")).isTrue();
    }

    @Test void phase4_rolloverGenerationSurvivesManagerRecreationAndIsIdempotent() {
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","session-"+conversation,now);
        var handoff=new SessionHandoff("summary",List.of("topic"),List.of("person"),List.of("address"),List.of("task"),List.of(),List.of("fact"),List.of("recent"));
        sessions.saveHandoff(conversation.toString(),handoff);sessions.recordContextSize(conversation.toString(),4321);
        var restarted=new online.wanan.xingchen.storage.SqliteSessionLifecycleManager(jdbc,new com.fasterxml.jackson.databind.ObjectMapper());
        assertThat(restarted.loadHandoff(conversation.toString())).isEqualTo(handoff);
        var before=restarted.loadState(conversation.toString());assertThat(before.generation()).isEqualTo(1);assertThat(before.estimatedContextTokens()).isEqualTo(4321);assertThat(before.lastRolloverAt()).isNotNull();
        restarted.saveHandoff(conversation.toString(),handoff);
        assertThat(restarted.loadState(conversation.toString()).generation()).isEqualTo(1);
    }

    @Test void reserved2_simulationWakeAndModeSurviveRepositoryReload() {
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","GROUP","simulation-"+conversation,now);
        assertThatThrownBy(()->simulation.setMode(conversation,"CLOSED_AGENT",false)).isInstanceOf(SecurityException.class);
        simulation.configure(conversation,java.util.Map.of("mention",true,"speakers",List.of("owner-1")),true);
        simulation.setMode(conversation,"CLOSED_AGENT",true);
        simulation.sleep(conversation,null,true);
        var transition=simulation.onIncoming(conversation,new online.wanan.xingchen.core.agent.SimulationStateService.IncomingSignal(true,false,false,false,"member-2","cursor-1"));
        assertThat(transition.woke()).isTrue();
        var reload=new online.wanan.xingchen.storage.SqliteSimulationStateRepository(jdbc,new com.fasterxml.jackson.databind.ObjectMapper()).load(conversation);
        assertThat(reload.mode()).isEqualTo("CLOSED_AGENT");assertThat(reload.wakeState()).isEqualTo(online.wanan.xingchen.core.agent.SimulationConversationState.WakeState.AWAKE);assertThat(reload.lastIncomingAt()).isNotNull();assertThat(reload.generation()).isGreaterThanOrEqualTo(4);
    }

    @Test void dsh_questionAndApprovalAreBoundToConversationSessionAndExpectedActor() {
        var interactions=new online.wanan.xingchen.core.agent.PendingInteractionService(jdbc,new com.fasterxml.jackson.databind.ObjectMapper(),java.time.Clock.systemUTC(),java.util.Set.of("owner"));
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE","pending-"+conversation,now);
        UUID q=UUID.randomUUID();interactions.createQuestion(q,conversation,"session-q","requester","qq-chat","owner","Confirm?",List.of("yes","no"),Instant.now().plusSeconds(600));
        assertThatThrownBy(()->interactions.answerQuestion(q,conversation,"session-q","member","yes")).isInstanceOf(SecurityException.class);
        assertThat(interactions.answerQuestion(q,conversation,"session-q","owner","yes").status()).isEqualTo(online.wanan.xingchen.core.agent.PendingInteractionService.ResolutionStatus.ACCEPTED);
        assertThat(interactions.answerQuestion(q,conversation,"session-q","owner","yes").status()).isEqualTo(online.wanan.xingchen.core.agent.PendingInteractionService.ResolutionStatus.ALREADY_RESOLVED);
        UUID approval=UUID.randomUUID();assertThatThrownBy(()->interactions.createApproval(approval,conversation,"session-q","system.write","dangerous","member","owner",Instant.now().plusSeconds(600))).isInstanceOf(SecurityException.class);
        interactions.createApproval(approval,conversation,"session-q","system.write","dangerous","owner","owner",Instant.now().plusSeconds(600));
        assertThatThrownBy(()->interactions.decideApproval(approval,conversation,"session-q","member",true)).isInstanceOf(SecurityException.class);
        assertThat(interactions.decideApproval(approval,conversation,"session-q","owner",true).status()).isEqualTo(online.wanan.xingchen.core.agent.PendingInteractionService.ResolutionStatus.ACCEPTED);
        assertThat(interactions.decideApproval(approval,conversation,"session-q","owner",true).status()).isEqualTo(online.wanan.xingchen.core.agent.PendingInteractionService.ResolutionStatus.ALREADY_RESOLVED);
    }

    @Test void pendingInteractions_expireAndCannotBeResolvedAfterExpiry() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var interactions = new online.wanan.xingchen.core.agent.PendingInteractionService(
                jdbc, new com.fasterxml.jackson.databind.ObjectMapper(), clock, java.util.Set.of("owner"));
        UUID conversation = UUID.randomUUID();
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",
                conversation.toString(), "QQ", "PRIVATE", "expired-" + conversation, clock.instant().toString());
        UUID question = UUID.randomUUID();
        UUID approval = UUID.randomUUID();
        interactions.createQuestion(question, conversation, "session-exp", "owner", "qq-chat", "owner", "Confirm?", List.of(), clock.instant().plusSeconds(1));
        interactions.createApproval(approval, conversation, "session-exp", "sensitive.action", "reason", "owner", "owner", clock.instant().plusSeconds(1));
        clock.advance(java.time.Duration.ofSeconds(1));
        assertThat(interactions.expirePending()).isEqualTo(2);
        assertThat(interactions.answerQuestion(question, conversation, "session-exp", "owner", "yes").status())
                .isEqualTo(online.wanan.xingchen.core.agent.PendingInteractionService.ResolutionStatus.EXPIRED);
        assertThat(interactions.decideApproval(approval, conversation, "session-exp", "owner", true).status())
                .isEqualTo(online.wanan.xingchen.core.agent.PendingInteractionService.ResolutionStatus.EXPIRED);
    }

    @Test void dshevt011_013_016_017_018_pendingReplayRejectsStaleGenerationKeepsOneRowAndTerminalReplayIsInert() {
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();
        String sessionId="session-e-"+UUID.randomUUID(),eventId="event-"+UUID.randomUUID();
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE","dsh-event-"+conversation,now);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var questions=mapper.createObjectNode().putArray("questions").addObject().put("id","q1").put("question","Continue?");
        var first=new online.wanan.xingchen.core.agent.DshPendingInteractionStore.Interaction(UUID.randomUUID().toString(),online.wanan.xingchen.core.agent.DshPendingInteractionStore.Kind.QUESTION,sessionId,conversation.toString(),"client-1",1,eventId,"qq:owner",Instant.now().plusSeconds(300),"PENDING",questions,null,null,null,Instant.now(),Instant.now());
        var created=dshPending.replay(first);assertThat(created.created()).isTrue();assertThat(created.terminal()).isFalse();
        var replay=new online.wanan.xingchen.core.agent.DshPendingInteractionStore.Interaction(UUID.randomUUID().toString(),first.kind(),first.dshSessionId(),first.conversationId(),"client-2",2,first.eventId(),first.requiredActorId(),first.expiresAt(),"PENDING",questions,null,null,null,Instant.now(),Instant.now());
        var refreshed=dshPending.replay(replay);assertThat(refreshed.created()).isFalse();assertThat(refreshed.interaction().id()).isEqualTo(first.id());assertThat(refreshed.interaction().clientId()).isEqualTo("client-2");assertThat(refreshed.interaction().clientGeneration()).isEqualTo(2);
        assertThat(dshPending.claimAnswer(first.id(),"qq:owner","client-1",1)).isFalse();
        assertThat(dshPending.claimAnswer(first.id(),"qq:intruder","client-2",2)).isFalse();
        assertThat(dshPending.claimAnswer(first.id(),"qq:owner","client-2",2)).isTrue();
        dshPending.finishAnswer(first.id(),"ANSWERED");
        assertThat(dshPending.replay(replay).terminal()).isTrue();
        assertThat(dshPending.reserveResult(sessionId,eventId,"answer-v1")).isTrue();
        dshPending.completeResult(sessionId,eventId,"answer-v1",online.wanan.xingchen.core.agent.DshPendingInteractionStore.ResultStatus.UNKNOWN);
        assertThat(dshPending.reserveResult(sessionId,eventId,"answer-v1")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dsh_pending_interactions WHERE dsh_session_id=? AND event_id=?",Integer.class,sessionId,eventId)).isEqualTo(1);
    }

    @Test void dshevt004_dshe2e001_questionTravelsFromFakeDshThroughOwnerOneBotReplyToDshResult() throws Exception {
        var f=interactionFixture("question");var request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();var qs=request.putArray("questions");qs.addObject().put("id","q1").put("question","Continue?").putArray("options").addObject().put("label","Yes");qs.addObject().put("id","q2").put("question","Why?").putArray("options");f.server.waterfall("evt-question",f.session,"user-questions/request",request);
        try{f.coordinator.start();awaitSent(f.gateway);assertThat(f.gateway.sent().getFirst().content()).contains("Continue?").contains("Yes");
            var first=f.event(f.owner,"1:Yes");assertThat(f.router.route(first)).isTrue();assertThat(f.server.requests("$events/result")).isEmpty();assertThat(f.store.find(f.session,"evt-question").orElseThrow().status()).isEqualTo("PENDING");
            assertThat(f.router.route(f.event(f.owner,"2:Because"))).isTrue();awaitResult(f.server);
            var args=f.server.requests("$events/result").getFirst().envelope().path("payload").path("args");var answers=args.path("outcome").path("value").path("answers");assertThat(args.path("eventId").asText()).isEqualTo("evt-question");assertThat(answers).hasSize(2);assertThat(answers.get(0).path("selected").get(0).asText()).isEqualTo("Yes");assertThat(answers.get(1).path("selected")).isEmpty();assertThat(answers.get(1).path("custom").asText()).isEqualTo("Because");assertThat(f.store.find(f.session,"evt-question").orElseThrow().status()).isEqualTo("ANSWERED");
        }finally{f.close();}
    }

    @Test void dshevt005_006_007_009_dshe2e002_003_008_ownerApprovalRejectsForeignActorAndDuplicateAnswer() throws Exception {
        var f=interactionFixture("approval");var request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","system.write").put("reason","create file");f.server.waterfall("evt-approval",f.session,"approval/request",request);
        try{f.coordinator.start();awaitSent(f.gateway);assertThat(f.gateway.sent().getFirst().content()).contains("system.write").contains("通过").contains("拒绝");
            var intruder=f.event("foreign-person","通过",true);assertThat(f.router.route(intruder)).isFalse();assertThat(f.server.requests("$events/result")).isEmpty();
            var ownerReply=f.event(f.owner,"通过");assertThat(f.router.route(ownerReply)).isTrue();awaitResult(f.server);var args=f.server.requests("$events/result").getFirst().envelope().path("payload").path("args");assertThat(args.path("outcome").path("value").asText()).isEqualTo("allowed-once");assertThat(f.store.find(f.session,"evt-approval").orElseThrow().status()).isEqualTo("APPROVED");assertThat(f.router.route(ownerReply)).isFalse();assertThat(f.server.requests("$events/result")).hasSize(1);
        }finally{f.close();}
    }

    @Test void dshe2e007_resultUnknownAfterServerCommitIsNeverAutomaticallyRetried() throws Exception {
        var dispatchCount=new java.util.concurrent.atomic.AtomicInteger();var dispatcher=recordingDispatcher(dispatchCount,null,null);var f=interactionFixture("unknown",dispatcher);var request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","system.write").put("reason","write");f.server.waterfall("evt-unknown",f.session,"approval/request",request);
        try{f.coordinator.start();awaitSent(f.gateway);f.server.timeoutAfterEventResultCommit(3000);var reply=f.event(f.owner,"通过");assertThat(f.router.route(reply)).isTrue();
            assertThat(dispatchCount).hasValue(1);assertThat(f.server.eventResultsReceived("evt-unknown")).isEqualTo(1);assertThat(f.server.eventResultsApplied("evt-unknown")).isEqualTo(1);assertThat(f.store.find(f.session,"evt-unknown").orElseThrow().status()).isEqualTo("UNKNOWN");
            assertThat(f.router.route(reply)).isFalse();assertThat(f.server.requests("$events/result")).hasSize(1);
        }finally{f.close();}
    }

    @Test void dshres001_preDispatchTimeoutSendsZeroRequestsAndRecordsDefiniteFailure() throws Exception {
        var dispatchCount=new java.util.concurrent.atomic.AtomicInteger();var reachedBarrier=new java.util.concurrent.CountDownLatch(1);var timeout=new java.util.concurrent.CountDownLatch(1);var dispatcher=recordingDispatcher(dispatchCount,reachedBarrier,timeout);
        var f=interactionFixture("pre-dispatch",dispatcher);f.server.waterfall("evt-pre-dispatch",f.session,"approval/request",new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","system.write").put("reason","write"));var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{f.coordinator.start();awaitSent(f.gateway);var reply=f.event(f.owner,"通过");var routed=executor.submit(()->f.router.route(reply));assertThat(reachedBarrier.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(dispatchCount).hasValue(0);assertThat(f.server.requests("$events/result")).isEmpty();timeout.countDown();assertThat(routed.get(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(dispatchCount).hasValue(0);assertThat(f.server.requests("$events/result")).isEmpty();assertThat(jdbc.queryForObject("SELECT status FROM dsh_event_result_ledger WHERE dsh_session_id=? AND event_id=?",String.class,f.session,"evt-pre-dispatch")).isEqualTo("FAILED");assertThat(jdbc.queryForObject("SELECT failure_reason FROM dsh_event_result_ledger WHERE dsh_session_id=? AND event_id=?",String.class,f.session,"evt-pre-dispatch")).isEqualTo("PRE_DISPATCH_TIMEOUT");assertThat(f.store.find(f.session,"evt-pre-dispatch").orElseThrow().status()).isEqualTo("INTERRUPTED");assertThat(f.router.route(reply)).isFalse();
        }finally{timeout.countDown();executor.shutdownNow();f.close();}
    }

    @Test void dshe2e009_explicitDshFailureIsRecordedFailedAndNeverRetried() throws Exception {
        var f=interactionFixture("explicit-failure");var request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","system.write").put("reason","write");f.server.waterfall("evt-explicit-failure",f.session,"approval/request",request);
        try{f.coordinator.start();awaitSent(f.gateway);f.server.rejectNextEventResult();var reply=f.event(f.owner,"拒绝");assertThat(f.router.route(reply)).isTrue();long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(f.store.find(f.session,"evt-explicit-failure").orElseThrow().status().equals("ANSWERING")&&System.nanoTime()<until)Thread.sleep(20);
            assertThat(f.store.find(f.session,"evt-explicit-failure").orElseThrow().status()).isEqualTo("INTERRUPTED");assertThat(jdbc.queryForObject("SELECT status FROM dsh_event_result_ledger WHERE dsh_session_id=? AND event_id=?",String.class,f.session,"evt-explicit-failure")).isEqualTo("FAILED");assertThat(f.server.requests("$events/result").getFirst().envelope().path("payload").path("args").path("outcome").path("value").asText()).isEqualTo("rejected");assertThat(f.router.route(reply)).isFalse();assertThat(f.server.requests("$events/result")).hasSize(1);
        }finally{f.close();}
    }

    @Test void dshe2e010_dropAfterReadingEventResultBecomesUnknownWithoutRetry() throws Exception {
        var f=interactionFixture("drop-after-read");var request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","system.write").put("reason","write");f.server.waterfall("evt-drop-after-read",f.session,"approval/request",request);
        try{f.coordinator.start();awaitSent(f.gateway);f.server.dropNextEventResultAfterRead();var reply=f.event(f.owner,"通过");assertThat(f.router.route(reply)).isTrue();
            assertThat(f.store.find(f.session,"evt-drop-after-read").orElseThrow().status()).isEqualTo("UNKNOWN");assertThat(f.server.eventResultsReceived("evt-drop-after-read")).isEqualTo(1);assertThat(f.server.eventResultsApplied("evt-drop-after-read")).isZero();assertThat(f.router.route(reply)).isFalse();assertThat(f.server.requests("$events/result")).hasSize(1);
        }finally{f.close();}
    }

    @Test void dshe2e011_dropBeforeReadingEventResultBecomesUnknownWithoutRetry() throws Exception {
        var f=interactionFixture("drop-before-read");var request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","system.write").put("reason","write");f.server.waterfall("evt-drop-before-read",f.session,"approval/request",request);
        try{f.coordinator.start();awaitSent(f.gateway);f.server.dropNextEventResultBeforeRead();var reply=f.event(f.owner,"通过");assertThat(f.router.route(reply)).isTrue();
            assertThat(f.store.find(f.session,"evt-drop-before-read").orElseThrow().status()).isEqualTo("UNKNOWN");assertThat(f.server.eventResultsReceived("evt-drop-before-read")).isZero();assertThat(f.router.route(reply)).isFalse();assertThat(f.server.requests("$events/result")).isEmpty();
        }finally{f.close();}
    }

    @Test void dshe2e012_timeoutBeforeServerReadsEventResultBecomesUnknownWithoutRetry() throws Exception {
        var f=interactionFixture("timeout-before-read");var request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","system.write").put("reason","write");f.server.waterfall("evt-timeout-before-read",f.session,"approval/request",request);
        try{f.coordinator.start();awaitSent(f.gateway);f.server.timeoutBeforeReadingNextEventResult(3000);var reply=f.event(f.owner,"通过");assertThat(f.router.route(reply)).isTrue();
            assertThat(f.store.find(f.session,"evt-timeout-before-read").orElseThrow().status()).isEqualTo("UNKNOWN");assertThat(f.server.eventResultsReceived("evt-timeout-before-read")).isZero();assertThat(f.router.route(reply)).isFalse();assertThat(f.server.requests("$events/result")).isEmpty();
        }finally{f.close();}
    }

    @Test void dshevt008_expiredApprovalCannotPostAResult() throws Exception {
        var f=interactionFixture("expired");f.server.waterfall("evt-expired",f.session,"approval/request",new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","dangerous"));
        try{f.coordinator.start();awaitSent(f.gateway);jdbc.update("UPDATE dsh_pending_interactions SET expires_at=? WHERE dsh_session_id=? AND event_id=?",Instant.now().minusSeconds(1).toString(),f.session,"evt-expired");assertThat(f.router.route(f.event(f.owner,"通过"))).isTrue();assertThat(f.store.find(f.session,"evt-expired").orElseThrow().status()).isEqualTo("EXPIRED");assertThat(f.server.requests("$events/result")).isEmpty();}finally{f.close();}
    }

    @Test void dshevt010_wrongInteractionSelectorCannotChooseAnEvent() throws Exception {
        var f=interactionFixture("wrong-selector");f.server.waterfall("evt-selector",f.session,"approval/request",new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","dangerous"));
        try{f.coordinator.start();awaitSent(f.gateway);assertThat(f.router.route(f.event(f.owner,"通过 #ffffff"))).isTrue();assertThat(f.server.requests("$events/result")).isEmpty();assertThat(f.store.find(f.session,"evt-selector").orElseThrow().status()).isEqualTo("PENDING");}finally{f.close();}
    }

    @Test void dshevt014_unmappedForeignSessionIsIgnoredWithoutQQPresentation() throws Exception {
        var f=interactionFixture("foreign-session");f.server.waterfall("evt-foreign-session","not-a-current-session","approval/request",new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("toolName","dangerous"));f.server.disconnectEventAfterFirstDelivery("event-client-2");
        try{f.coordinator.start();long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(f.server.eventConnections()<2&&System.nanoTime()<until)Thread.sleep(10);assertThat(f.server.eventConnections()).isGreaterThanOrEqualTo(2);assertThat(f.gateway.sent()).isEmpty();assertThat(f.store.find("not-a-current-session","evt-foreign-session")).isEmpty();assertThat(f.server.requests("$events/result")).isEmpty();}finally{f.close();}
    }

    @Test void dshevt015_malformedApprovalWaterfallFailsSafely() throws Exception {
        var f=interactionFixture("malformed");f.server.waterfall("evt-malformed",f.session,"approval/request",new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode().put("reason","missing tool name"));
        try{f.coordinator.start();long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(f.coordinator.isRunning()&&System.nanoTime()<until)Thread.sleep(10);assertThat(f.coordinator.isRunning()).isFalse();assertThat(f.gateway.sent()).isEmpty();assertThat(f.store.find(f.session,"evt-malformed")).isEmpty();assertThat(f.server.requests("$events/result")).isEmpty();}finally{f.close();}
    }

    @Test void dshe2e006_reconnectReplaysSameEventAndRefreshesBindingWithoutReprompting() throws Exception {
        var f=interactionFixture("replay");var request=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();request.putArray("questions").addObject().put("id","q1").put("question","Continue?");f.server.waterfall("evt-replay",f.session,"user-questions/request",request);f.server.disconnectEventAfterFirstDelivery("event-client-2");
        try{f.coordinator.start();long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);online.wanan.xingchen.core.agent.DshPendingInteractionStore.Interaction row=null;while(System.nanoTime()<until){row=f.store.find(f.session,"evt-replay").orElse(null);if(row!=null&&row.clientGeneration()>=2)break;Thread.sleep(25);}assertThat(row).isNotNull();assertThat(row.clientId()).isEqualTo("event-client-2");assertThat(row.clientGeneration()).isEqualTo(2);assertThat(f.gateway.sent()).hasSize(1);
            assertThat(f.router.route(f.event(f.owner,"done"))).isTrue();awaitResult(f.server);var args=f.server.requests("$events/result").getFirst().envelope().path("payload").path("args");assertThat(args.path("clientId").asText()).isEqualTo("event-client-2");assertThat(f.gateway.sent()).hasSize(1);
            f.server.disconnectEventStream();long terminalReplayUntil=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);while(f.server.eventConnections()<3&&System.nanoTime()<terminalReplayUntil)Thread.sleep(25);assertThat(f.server.eventConnections()).isGreaterThanOrEqualTo(3);assertThat(f.gateway.sent()).hasSize(1);assertThat(f.server.requests("$events/result")).hasSize(1);assertThat(f.store.find(f.session,"evt-replay").orElseThrow().status()).isEqualTo("ANSWERED");
        }finally{f.close();}
    }

    private InteractionFixture interactionFixture(String suffix) throws Exception {return interactionFixture(suffix,online.wanan.xingchen.adapter.dsh.DshHttpDispatcher.javaHttpClient());}
    private InteractionFixture interactionFixture(String suffix,online.wanan.xingchen.adapter.dsh.DshHttpDispatcher dispatcher) throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var normalizer=new online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer(mapper);String owner="closure-owner-"+UUID.randomUUID().toString().replace("-","");
        var localIdentities=new online.wanan.xingchen.core.identity.IdentityResolver(new online.wanan.xingchen.core.identity.IdentityRegistry("bot",List.of(owner),identityPersistence),new online.wanan.xingchen.core.relationship.AddressResolver(),relationshipTerms,"bot");
        var ownerEvent=normalizer.normalize(oneBotPrivateJson(owner,"seed"),List.of(owner));var actor=localIdentities.resolve(ownerEvent);UUID conversation=actor.conversation().id();String session="session-closure-"+suffix+"-"+UUID.randomUUID();var now=Instant.now();
        dshMappings.save(new online.wanan.xingchen.adapter.dsh.DshSessionMapping(UUID.randomUUID(),conversation,"workspace",session,"CLOSED_AGENT","qq-chat","model","policy",dshMappings.nextGeneration(conversation),online.wanan.xingchen.adapter.dsh.DshSessionMapping.Status.CURRENT,now,now));
        var server=new online.wanan.xingchen.adapter.dsh.FakeDshRc2Server("closure-token");var dsh=new online.wanan.xingchen.adapter.dsh.DshRc2Adapter(new online.wanan.xingchen.adapter.dsh.DshRc2Configuration(server.baseUri(),java.nio.file.Path.of("build").toAbsolutePath(),"closure-token","deepseek-official",java.time.Duration.ofSeconds(2)),mapper,()->true,dispatcher);
        var gateway=new online.wanan.xingchen.adapter.onebot.MockOneBotGateway(normalizer,"bot");gateway.connect();var store=new online.wanan.xingchen.core.agent.DshPendingInteractionStore(jdbc,mapper,java.time.Clock.systemUTC());var coordinator=new online.wanan.xingchen.core.agent.DshInteractionCoordinator(dsh,dshMappings,jdbc,store,gateway,mapper,java.time.Clock.systemUTC(),owner);var router=new online.wanan.xingchen.core.agent.HumanInteractionRouter(localIdentities,store,dsh,gateway,new online.wanan.xingchen.adapter.dsh.DshRc2InteractionOutcomeEncoder(mapper),new online.wanan.xingchen.core.agent.QuestionAnswerParser(),mapper,java.time.Clock.systemUTC());
        return new InteractionFixture(owner,session,server,dsh,gateway,store,coordinator,router,normalizer);
    }
    private static online.wanan.xingchen.adapter.dsh.DshHttpDispatcher recordingDispatcher(java.util.concurrent.atomic.AtomicInteger dispatchCount,java.util.concurrent.CountDownLatch reachedBarrier,java.util.concurrent.CountDownLatch timeout){return new online.wanan.xingchen.adapter.dsh.DshHttpDispatcher(){
        @Override public void beforeDispatch(String endpoint,java.net.http.HttpRequest request){if(!"$events/result".equals(endpoint)||reachedBarrier==null)return;reachedBarrier.countDown();try{if(!timeout.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new online.wanan.xingchen.adapter.dsh.DshRc2Exception("transport/pre-dispatch-timeout","DSH request timed out before HTTP dispatch");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new online.wanan.xingchen.adapter.dsh.DshRc2Exception("transport/pre-dispatch-timeout","DSH request timed out before HTTP dispatch");}throw new online.wanan.xingchen.adapter.dsh.DshRc2Exception("transport/pre-dispatch-timeout","DSH request timed out before HTTP dispatch");}
        @Override public java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<String>> dispatch(java.net.http.HttpClient client,java.net.http.HttpRequest request){if(request.uri().getPath().endsWith("/$events/result"))dispatchCount.incrementAndGet();return client.sendAsync(request,java.net.http.HttpResponse.BodyHandlers.ofString());}
    };}
    private static String oneBotPrivateJson(String user,String text){return "{\"post_type\":\"message\",\"message_type\":\"private\",\"user_id\":\""+user+"\",\"self_id\":\"bot\",\"message_id\":\"m-"+UUID.randomUUID()+"\",\"sender\":{\"user_id\":\""+user+"\",\"nickname\":\"tester\"},\"message\":[{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]}";}
    private static void awaitSent(online.wanan.xingchen.adapter.onebot.MockOneBotGateway gateway)throws InterruptedException{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(gateway.sent().isEmpty()&&System.nanoTime()<end)Thread.sleep(10);assertThat(gateway.sent()).isNotEmpty();}
    private static void awaitResult(online.wanan.xingchen.adapter.dsh.FakeDshRc2Server server)throws InterruptedException{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);while(server.requests("$events/result").isEmpty()&&System.nanoTime()<end)Thread.sleep(10);assertThat(server.requests("$events/result")).hasSize(1);}
    private record InteractionFixture(String owner,String session,online.wanan.xingchen.adapter.dsh.FakeDshRc2Server server,online.wanan.xingchen.adapter.dsh.DshRc2Adapter dsh,
            online.wanan.xingchen.adapter.onebot.MockOneBotGateway gateway,online.wanan.xingchen.core.agent.DshPendingInteractionStore store,
            online.wanan.xingchen.core.agent.DshInteractionCoordinator coordinator,online.wanan.xingchen.core.agent.HumanInteractionRouter router,
            online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer normalizer) implements AutoCloseable {
        online.wanan.xingchen.core.model.PlatformEvent event(String user,String text){return normalizer.normalize(oneBotPrivateJson(user,text),List.of(owner));}
        online.wanan.xingchen.core.model.PlatformEvent event(String user,String text,boolean group){if(!group)return event(user,text);String json="{\"post_type\":\"message\",\"message_type\":\"group\",\"group_id\":\"group-foreign\",\"user_id\":\""+user+"\",\"self_id\":\"bot\",\"message_id\":\"g-"+UUID.randomUUID()+"\",\"sender\":{\"user_id\":\""+user+"\",\"nickname\":\"member\"},\"message\":[{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]}";return normalizer.normalize(json,List.of(owner));}
        @Override public void close(){coordinator.close();dsh.close();server.close();gateway.disconnect();}
    }

    @Test void dsh_sessionMappingRetiresWhenPermissionPolicyChangesAndResetArchivesOnlySession() {
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE","dsh-map-"+conversation,now);
        var remote=new FakeDshOperations();var service=new online.wanan.xingchen.adapter.dsh.DshSessionService(dshMappings,remote,java.time.Clock.systemUTC());
        var actor=closedOwner(conversation);var access=new online.wanan.xingchen.core.agent.ClosedAgentAccessPolicy();
        var first=service.ensureClosedAgent(actor,"CLOSED_AGENT","qq-chat","model-a","policy-a",true,access);assertThat(service.ensureClosedAgent(actor,"CLOSED_AGENT","qq-chat","model-a","policy-a",true,access)).isEqualTo(first);
        var next=service.ensureClosedAgent(actor,"CLOSED_AGENT","closed-agent","model-a","policy-owner",true,access);assertThat(next.generation()).isEqualTo(first.generation()+1);assertThat(remote.stopped).containsExactly(first.sessionId());assertThat(remote.archived).containsExactly(first.sessionId());
        assertThatThrownBy(()->service.ensureClosedAgent(groupMember(conversation),"CLOSED_AGENT","closed-agent","model-a","policy-owner",true,access)).isInstanceOf(SecurityException.class);
        service.reset(conversation);assertThat(dshMappings.current(conversation)).isNull();assertThat(remote.archived).containsExactly(first.sessionId(),next.sessionId());
    }
    @Test void dshDisabledResetRetiresLocalMappingWithoutCallingUnavailableRuntime() {
        UUID conversation=UUID.randomUUID();String now=Instant.now().toString();jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE","dsh-disabled-reset-"+conversation,now);
        var remote=new FakeDshOperations();var service=new online.wanan.xingchen.adapter.dsh.DshSessionService(dshMappings,remote,java.time.Clock.systemUTC());
        service.ensureClosedAgent(closedOwner(conversation),"CLOSED_AGENT","qq-chat","model-a","policy-a",true,new online.wanan.xingchen.core.agent.ClosedAgentAccessPolicy());
        remote.enabled=false;service.reset(conversation);
        assertThat(dshMappings.current(conversation)).isNull();assertThat(remote.stopped).isEmpty();assertThat(remote.archived).isEmpty();
    }
    @Test void reset101_shortContextResetRetiresSessionButPreservesLongTermMemory() {
        UUID conversation=UUID.randomUUID(),memory=UUID.randomUUID();String now=Instant.now().toString();jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE","reset-"+conversation,now);
        var remote=new FakeDshOperations();var sessionService=new online.wanan.xingchen.adapter.dsh.DshSessionService(dshMappings,remote,java.time.Clock.systemUTC());var session=sessionService.ensureClosedAgent(closedOwner(conversation),"CLOSED_AGENT","qq-chat","model-a","policy-a",true,new online.wanan.xingchen.core.agent.ClosedAgentAccessPolicy());
        jdbc.update("INSERT INTO memories(id,type,content,scope_type,scope_id,confidence,importance,explicit,created_at,updated_at,status) VALUES(?,?,?,?,?,?,?,?,?,?,?)",memory.toString(),"PERSON","keep this long-term","OWNER_GLOBAL","owner",1.0,1.0,1,now,now,"ACTIVE");
        jdbc.update("INSERT INTO conversation_summaries(conversation_id,conversation_summary,updated_at) VALUES(?,?,?)",conversation.toString(),"transient summary",now);
        jdbc.update("INSERT INTO session_handoffs(conversation_id,generation,summary_id,handoff_json,created_at) VALUES(?,?,?,?,?)",conversation.toString(),1,"summary-"+conversation,"{}",now);
        UUID bot=UUID.randomUUID(),person=UUID.randomUUID();jdbc.update("INSERT INTO persons(id,platform,platform_user_id,is_self,is_owner,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",bot.toString(),"QQ","bot-reset-"+conversation,1,0,now,now);jdbc.update("INSERT INTO persons(id,platform,platform_user_id,is_self,is_owner,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",person.toString(),"QQ","owner-reset-"+conversation,0,1,now,now);jdbc.update("INSERT INTO relationship_terms(id,subject_person_id,target_person_id,type,value,scope_type,scope_id,explicit,confidence,source_message_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),bot.toString(),person.toString(),"ADDRESS_AS","姐姐","GLOBAL",null,1,1.0,"seed",now,now);
        jdbc.update("INSERT INTO session_state(conversation_id,session_id,status,token_estimate,updated_at,generation,current_summary_id,last_rollover_at,estimated_context_tokens) VALUES(?,?,?,?,?,1,?,?,99)",conversation.toString(),session.sessionId(),"ACTIVE",99,now,"summary-"+conversation,now);
        var window=new online.wanan.xingchen.core.conversation.ConversationWindow();window.appendAndGet(conversation.toString(),"transient recent");new online.wanan.xingchen.core.conversation.ShortContextResetService(sessionService,window,jdbc,simulation,turnJournal,dshPending).reset(conversation);
        assertThat(dshMappings.current(conversation)).isNull();assertThat(simulation.get(conversation).generation()).isEqualTo(1);assertThat(window.get(conversation.toString())).isEmpty();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversation_summaries WHERE conversation_id=?",Integer.class,conversation.toString())).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM session_handoffs WHERE conversation_id=?",Integer.class,conversation.toString())).isZero();assertThat(jdbc.queryForObject("SELECT current_summary_id FROM session_state WHERE conversation_id=?",String.class,conversation.toString())).isNull();assertThat(jdbc.queryForObject("SELECT content FROM memories WHERE id=?",String.class,memory.toString())).isEqualTo("keep this long-term");assertThat(jdbc.queryForObject("SELECT value FROM relationship_terms WHERE subject_person_id=? AND target_person_id=?",String.class,bot.toString(),person.toString())).isEqualTo("姐姐");assertThat(remote.stopped).containsExactly(session.sessionId());assertThat(remote.archived).containsExactly(session.sessionId());
    }
    private static final class FakeDshOperations implements online.wanan.xingchen.adapter.dsh.DshSessionOperations {
        int next;boolean enabled=true;final List<String> stopped=new ArrayList<>(),archived=new ArrayList<>();public boolean isEnabled(){return enabled;}public String findOrCreateWorkspace(String key){return "workspace";}public String createSession(String workspace,String mode,String preset,String model){return "session-"+(++next);}public void stop(String id){stopped.add(id);}public void archive(String id){archived.add(id);}public void prompt(String id,String text){}public void selectModel(String id,String model){}public void selectPreset(String id,String preset){}
    }

    private static online.wanan.xingchen.core.identity.ResolvedActorContext closedOwner(UUID id){
        var person=new online.wanan.xingchen.core.identity.Person(UUID.nameUUIDFromBytes("owner".getBytes()),online.wanan.xingchen.core.model.Platform.QQ,"owner",false,true);
        var conversation=new online.wanan.xingchen.core.identity.Conversation(id,new online.wanan.xingchen.core.model.ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,online.wanan.xingchen.core.model.ConversationType.PRIVATE,"dsh-map-"+id));
        var membership=new online.wanan.xingchen.core.identity.Membership(UUID.randomUUID(),person.id(),id,"owner","",online.wanan.xingchen.core.identity.IdentityRole.MEMBER,Instant.now(),Instant.now());
        return new online.wanan.xingchen.core.identity.ResolvedActorContext(person,conversation,membership,new online.wanan.xingchen.core.identity.ActorFlags(false,true,false,false),"owner",new online.wanan.xingchen.core.relationship.AddressResolution("owner","fixture",null,false,0));
    }
    private static online.wanan.xingchen.core.identity.ResolvedActorContext groupMember(UUID id){
        var actor=closedOwner(id);var group=new online.wanan.xingchen.core.identity.Conversation(id,new online.wanan.xingchen.core.model.ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,online.wanan.xingchen.core.model.ConversationType.GROUP,"g"));
        return new online.wanan.xingchen.core.identity.ResolvedActorContext(actor.person(),group,actor.membership(),new online.wanan.xingchen.core.identity.ActorFlags(false,false,false,false),"member",actor.assistantAddress());
    }

    private static final class MutableClock extends java.time.Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(java.time.Duration duration) { now = now.plus(duration); }
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
