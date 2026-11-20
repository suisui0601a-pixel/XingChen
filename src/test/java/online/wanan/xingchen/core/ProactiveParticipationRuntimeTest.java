package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.console.*;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.*;
import online.wanan.xingchen.core.identity.IdentityRegistry;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.storage.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.assertj.core.api.Assertions.*;

/** Real WS inbound -> persisted history -> timer -> Agent -> ledger -> Fake OneBot HTTP. */
class ProactiveParticipationRuntimeTest {
    Path db;ConfigurableApplicationContext app;JdbcTemplate jdbc;MockModelProvider model;
    FakeOneBotServer fake;SocialRuntime runtime;ProactiveParticipationService timer;SocialSettingsService settings;TestClock clock;
    @BeforeEach void open()throws Exception{db=TestSqliteDatabase.create("proactive");start();}
    void start()throws Exception{
        app=new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class,Fixture.class).web(WebApplicationType.NONE).profiles("runtime-test")
                .run("--spring.datasource.url="+TestSqliteDatabase.jdbcUrl(db),"--xingchen.memory.database-path="+db,"--xingchen.onebot.login-user-id=test-bot","--xingchen.owner.platform-user-id=synthetic-owner","--xingchen.test.allow-all-access=false");
        jdbc=app.getBean(JdbcTemplate.class);model=app.getBean(MockModelProvider.class);fake=app.getBean(FakeOneBotServer.class);runtime=app.getBean(SocialRuntime.class);
        timer=app.getBean(ProactiveParticipationService.class);settings=app.getBean(SocialSettingsService.class);clock=app.getBean(TestClock.class);
        var access=app.getBean(AccessControlService.class);access.replace(List.of(new AccessControlService.Rule("GROUP","QQ","timer-group","ALLOW"),new AccessControlService.Rule("PRIVATE","QQ","timer-user","ALLOW")),access.revision(),"fixture-admin");
        runtime.start();await(()->fake.wsConnections()==1);
    }
    @AfterEach void close(){if(app!=null)app.close();TestSqliteDatabase.clean(db);}
    void configure(Map<String,Object> patch){settings.updateGlobal(((Number)settings.global().get("revision")).longValue(),patch,"fixture-admin");}
    void enable(){configure(Map.of("proactivePrivateEnabled",true,"proactiveGroupEnabled",true,"proactiveIdleMinutes",5,"proactiveIntervalMinutes",5,"proactiveCooldownMinutes",30,"proactiveProbability",1.0,"proactiveMaxPerDay",6,"proactiveQuietStartHour",0,"proactiveQuietEndHour",0));}
    void seed(String kind,String id)throws Exception{if(kind.equals("private"))model.enqueue(new ModelResponse(AgentDecision.noReply(),null,null));emit(kind,id,"ordinary context "+id);}
    void emit(String kind,String id,String text)throws Exception{
        fake.emit(new ObjectMapper().writeValueAsString(Map.of("post_type","message","message_type",kind,"self_id","test-bot","group_id","timer-group","user_id","timer-user","message_id",id,"time",clock.instant().getEpochSecond(),"sender",Map.of("user_id","timer-user","nickname","Synthetic Member","role","member"),"message",List.of(Map.of("type","text","data",Map.of("text",text))))));
        await(()->jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status='COMPLETED'",Integer.class,id)==1);
    }
    void text(String target,String text){model.enqueueFor(target,new ModelResponse(AgentDecision.text(text),null,null));}
    int attempts(){return jdbc.queryForObject("SELECT COUNT(*) FROM proactive_attempts WHERE admitted=1",Integer.class);}
    void done(){awaitUnchecked(()->timer.inFlightCount()==0);}
    UUID conversation(String type,String id){return app.getBean(IdentityRegistry.class).identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.valueOf(type),id)).id();}

    @Test void privateAndGroupIdleMessagesUseRealRuntimeAndKeepHumanHistoryUnchanged()throws Exception{
        seed("private","100");seed("group","101");clock.advance(Duration.ofMinutes(5));
        timer.tick();done();assertThat(attempts()).isZero();assertThat(model.requests()).hasSize(1);
        var identitiesBefore=jdbc.queryForList("SELECT * FROM memberships ORDER BY id");
        enable();text("timer-user","private check-in");text("timer-group","group check-in");timer.tick();done();
        assertThat(attempts()).isEqualTo(2);assertThat(fake.sendReceiveCount()).isEqualTo(2);assertThat(model.requests()).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT status FROM proactive_attempts",String.class)).containsOnly("SENT");
        assertThat(jdbc.queryForList("SELECT * FROM memberships ORDER BY id")).isEqualTo(identitiesBefore);
        for(var request:model.requests().subList(1,3)){
            assertThat(request.toolDefinitions()).isEmpty();assertThat(request.context().taskState()).containsEntry("turn.origin","IDLE_TIMER").containsEntry("turn.hasNewUserMessage","false");
            assertThat(request.context().recentMessages()).anyMatch(x->x.contains("ordinary context"));
            assertThat(request.context().currentMessage()).contains("不是用户发来的新消息");
        }
        assertThat(fake.wsConnections()).isEqualTo(1);timer.tick();done();assertThat(fake.sendReceiveCount()).isEqualTo(2);
    }
    @Test void probabilityZeroQuietHoursWaitSleepClosedAndStaleHistoryNeverCallModel()throws Exception{
        seed("group","200");enable();clock.advance(Duration.ofMinutes(5));UUID id=conversation("GROUP","timer-group");var simulation=app.getBean(SimulationStateService.class);
        configure(Map.of("proactiveProbability",0.0));timer.tick();done();assertThat(attempts()).isZero();
        configure(Map.of("proactiveProbability",1.0,"proactiveQuietStartHour",11,"proactiveQuietEndHour",13));timer.tick();done();assertThat(attempts()).isZero();
        configure(Map.of("proactiveQuietStartHour",0,"proactiveQuietEndHour",0));
        simulation.waitForNextMessage(id,"human-wait","fixture");timer.tick();done();assertThat(attempts()).isZero();assertThat(simulation.get(id).wakeState()).isEqualTo(SimulationConversationState.WakeState.WAITING);
        simulation.reset(id);simulation.markRead(id,"200");simulation.sleep(id,null,true);timer.tick();done();assertThat(attempts()).isZero();
        simulation.wake(id,true);simulation.setMode(id,"CLOSED_AGENT",true);timer.tick();done();assertThat(attempts()).isZero();
        simulation.setMode(id,"SOCIAL",true);clock.advance(Duration.ofHours(25));timer.tick();done();
        assertThat(model.requests()).isEmpty();assertThat(fake.sendReceiveCount()).isZero();
    }
    @Test void accessRevocationCannotBeBypassedByTimerAndOverrideIsIndependent()throws Exception{
        seed("group","300");enable();clock.advance(Duration.ofMinutes(5));UUID id=conversation("GROUP","timer-group");
        settings.updateConversation(id,0,Map.of("proactiveGroupEnabled",false),"fixture-admin");timer.tick();done();assertThat(attempts()).isZero();
        settings.updateConversation(id,1,Map.of("proactiveGroupEnabled",true),"fixture-admin");
        var access=app.getBean(AccessControlService.class);access.replace(List.of(),access.revision(),"fixture-admin");timer.tick();done();assertThat(attempts()).isZero();assertThat(model.requests()).isEmpty();
    }
    @Test void newInboundCancelsInFlightIdleIntentWithoutDuplicateOrSpuriousReply()throws Exception{
        seed("group","400");enable();clock.advance(Duration.ofMinutes(5));
        var barrier=model.enqueueBlockedFor("timer-group",new ModelResponse(AgentDecision.text("stale timer reply"),null,null));
        timer.tick();assertThat(barrier.awaitStarted(5,TimeUnit.SECONDS)).isTrue();
        // Queue the real message behind the blocked turn; the intake hook must cancel immediately.
        fake.emit(new ObjectMapper().writeValueAsString(Map.of("post_type","message","message_type","group","self_id","test-bot","group_id","timer-group","user_id","timer-user","message_id","401","time",clock.instant().getEpochSecond(),"message","new ordinary inbound")));
        await(()->model.requests().getFirst().cancellation().isCancelled());barrier.release();done();
        await(()->jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id='401' AND status='COMPLETED'",Integer.class)==1);
        assertThat(fake.sendReceiveCount()).isZero();assertThat(model.requests()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT status FROM proactive_attempts",String.class)).isEqualTo("CANCELLED");
    }
    @Test void revokedAccessDuringModelRequestBlocksFinalDispatch()throws Exception{
        seed("group","500");enable();clock.advance(Duration.ofMinutes(5));
        var barrier=model.enqueueBlockedFor("timer-group",new ModelResponse(AgentDecision.text("must not send"),null,null));timer.tick();assertThat(barrier.awaitStarted(5,TimeUnit.SECONDS)).isTrue();
        var access=app.getBean(AccessControlService.class);access.replace(List.of(),access.revision(),"fixture-admin");barrier.release();done();
        assertThat(fake.sendReceiveCount()).isZero();assertThat(jdbc.queryForObject("SELECT status FROM proactive_attempts",String.class)).isEqualTo("CANCELLED");
    }
    @Test void noReplyAndUnknownConsumeCooldownAndAreNotReplayedAfterRestart()throws Exception{
        seed("group","600");enable();clock.advance(Duration.ofMinutes(5));model.enqueueFor("timer-group",new ModelResponse(AgentDecision.noReply(),null,null));timer.tick();done();
        assertThat(attempts()).isEqualTo(1);assertThat(fake.sendReceiveCount()).isZero();timer.tick();done();assertThat(model.requests()).hasSize(1);
        clock.advance(Duration.ofMinutes(30));text("timer-group","single uncertain output");fake.sendBehavior(FakeOneBotServer.SendBehavior.DROP_AFTER_RECEIVE);timer.tick();done();
        assertThat(fake.sendReceiveCount()).isEqualTo(1);assertThat(jdbc.queryForList("SELECT status FROM proactive_attempts",String.class)).containsExactlyInAnyOrder("NO_REPLY","UNKNOWN");
        app.close();app=null;start();clock.advance(Duration.ofMinutes(36));timer.tick();done();
        assertThat(model.requests()).isEmpty();assertThat(fake.sendReceiveCount()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='UNKNOWN'",Integer.class)).isEqualTo(1);
    }
    @Test void admissionCrashBecomesInterruptedAndDoesNotReplayModel()throws Exception{
        seed("group","700");enable();clock.advance(Duration.ofMinutes(5));
        var store=app.getBean(ProactiveAttemptStore.class);var candidate=store.candidates(clock.instant(),"").getFirst();
        assertThat(store.reserve(candidate,settings.proactive(candidate.conversationId()),clock.instant(),true)).isNotNull();
        app.close();app=null;start();clock.advance(Duration.ofMinutes(6));timer.tick();done();
        assertThat(model.requests()).isEmpty();assertThat(jdbc.queryForObject("SELECT status FROM proactive_attempts",String.class)).isEqualTo("INTERRUPTED");
    }
    @Test void concurrentDurableAdmissionGrantsOnlyOneAttempt()throws Exception{
        seed("group","800");enable();clock.advance(Duration.ofMinutes(5));var store=app.getBean(ProactiveAttemptStore.class);var candidate=store.candidates(clock.instant(),"").getFirst();var policy=settings.proactive(candidate.conversationId());
        try(var workers=Executors.newFixedThreadPool(8)){
            var barrier=new CyclicBarrier(8);var calls=new ArrayList<Future<String>>();
            for(int i=0;i<8;i++)calls.add(workers.submit(()->{barrier.await();return store.reserve(candidate,policy,clock.instant(),true);}));
            int admitted=0;for(var call:calls)if(call.get(10,TimeUnit.SECONDS)!=null)admitted++;assertThat(admitted).isEqualTo(1);
        }assertThat(attempts()).isEqualTo(1);assertThat(model.requests()).isEmpty();
    }
    @Test void dailyBudgetPersistsAndRepeatedScansDoNotMultiplyRandomDraws()throws Exception{
        seed("group","900");enable();configure(Map.of("proactiveMaxPerDay",1));clock.advance(Duration.ofMinutes(5));
        model.enqueueFor("timer-group",new ModelResponse(AgentDecision.noReply(),null,null));timer.tick();done();
        clock.advance(Duration.ofMinutes(60));timer.tick();done();assertThat(model.requests()).hasSize(1);assertThat(attempts()).isEqualTo(1);
        app.close();app=null;start();clock.advance(Duration.ofMinutes(90));timer.tick();done();assertThat(model.requests()).isEmpty();assertThat(attempts()).isEqualTo(1);
    }
    @Test void globalBudgetAndNonMatchingRandomDrawsAreDurable()throws Exception{
        seed("group","1000");enable();clock.advance(Duration.ofMinutes(5));var store=app.getBean(ProactiveAttemptStore.class);var candidate=store.candidates(clock.instant(),"").getFirst();var p=settings.proactive(candidate.conversationId());
        String missed=store.reserve(candidate,p,clock.instant(),false);assertThat(missed).isNotNull();assertThat(store.due(candidate.conversationId(),p,clock.instant())).isFalse();assertThat(store.reserve(candidate,p,clock.instant(),true)).isNull();
        assertThat(attempts()).isZero();clock.advance(Duration.ofMinutes(5));
        // Four other authorized timer reservations consume the shared hourly model budget.
        for(int i=0;i<4;i++){
            UUID id=app.getBean(IdentityRegistry.class).identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"budget-"+i)).id();
            var c=new ProactiveAttemptStore.Candidate(id,candidate.anchor());assertThat(store.reserve(c,p,clock.instant(),true)).isNotNull();
        }
        assertThat(store.reserve(candidate,p,clock.instant(),true)).isNull();assertThat(attempts()).isEqualTo(4);assertThat(model.requests()).isEmpty();
    }
    @Test void ordinaryHumanTrafficPreventsIdleTurnAndMultiReplyHasOneTransportSend()throws Exception{
        seed("group","1100");enable();clock.advance(Duration.ofMinutes(4));timer.tick();done();assertThat(attempts()).isZero();
        emit("group","1101","new human message");clock.advance(Duration.ofMinutes(4));timer.tick();done();assertThat(attempts()).isZero();
        clock.advance(Duration.ofMinutes(1));model.enqueueFor("timer-group",new ModelResponse(AgentDecision.multi(java.util.List.of("one","two")),null,null));timer.tick();done();
        assertThat(fake.sendReceiveCount()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE failure_reason='burst-limit'",Integer.class)).isZero();
        assertThat(app.getBean(IncomingMessageStore.class).proactiveContext(conversation("GROUP","timer-group"))).anyMatch(x->x.contains("one")).noneMatch(x->x.contains("two"));
    }
    @Test void controlledHalfProbabilityMissDoesNotResampleEveryMinute()throws Exception{
        seed("group","1200");enable();configure(Map.of("proactiveProbability",.5));clock.advance(Duration.ofMinutes(5));var draws=app.getBean(Draws.class);draws.value=.75;
        timer.tick();done();timer.tick();done();assertThat(draws.calls).isEqualTo(1);assertThat(attempts()).isZero();assertThat(model.requests()).isEmpty();
        clock.advance(Duration.ofMinutes(5));draws.value=.25;model.enqueueFor("timer-group",new ModelResponse(AgentDecision.noReply(),null,null));timer.tick();done();
        assertThat(draws.calls).isEqualTo(2);assertThat(model.requests()).hasSize(1);assertThat(attempts()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT status FROM proactive_attempts",String.class)).containsExactlyInAnyOrder("SKIPPED_RANDOM","NO_REPLY");
    }
    @Test void timerActuallyFiresAndStartStopDoesNotLeakSubscriptions()throws Exception{
        seed("group","1300");enable();clock.advance(Duration.ofMinutes(5));text("timer-group","scheduled check-in");var scheduler=app.getBean(CapturedScheduler.class);
        assertThat(scheduler.pendingMinutes()).isEqualTo(1);scheduler.fireMinute();done();assertThat(fake.sendReceiveCount()).isEqualTo(1);assertThat(scheduler.pendingMinutes()).isEqualTo(1);
        runtime.stop();assertThat(scheduler.pendingMinutes()).isZero();assertThat(timer.diagnostics().get("running")).isEqualTo(false);
        runtime.start();runtime.start();assertThat(scheduler.pendingMinutes()).isEqualTo(1);
    }
    @Test void forbiddenToolProposalCannotPerformSideEffectsDuringTimerTurn()throws Exception{
        seed("group","1400");enable();clock.advance(Duration.ofMinutes(5));
        model.enqueueFor("timer-group",new ModelResponse(null,null,null,List.of(new ModelToolCall("forbidden","qq.send",Map.of("conversationId","timer-group","text","do not send"))),ModelFinishReason.TOOL_CALLS));
        timer.tick();done();assertThat(fake.sendReceiveCount()).isZero();assertThat(model.requests()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims WHERE event_id LIKE 'proactive:%'",Integer.class)).isZero();
    }
    @Test void groupTimerDoesNotBorrowLastSpeakersPrivateOrOwnerMemory()throws Exception{
        seed("group","1500");enable();UUID group=conversation("GROUP","timer-group"),other=conversation("GROUP","other-group");
        UUID person=app.getBean(IdentityRegistry.class).find(Platform.QQ,"timer-user").orElseThrow().id();
        var repository=app.getBean(online.wanan.xingchen.core.memory.MemoryRepository.class);
        for(var scope:List.of(online.wanan.xingchen.core.memory.MemoryScope.GLOBAL,online.wanan.xingchen.core.memory.MemoryScope.CONVERSATION,online.wanan.xingchen.core.memory.MemoryScope.PRIVATE,online.wanan.xingchen.core.memory.MemoryScope.PERSON_GLOBAL,online.wanan.xingchen.core.memory.MemoryScope.OWNER_GLOBAL)){
            var memory=new online.wanan.xingchen.core.memory.Memory(UUID.randomUUID(),online.wanan.xingchen.core.memory.MemoryType.SEMANTIC,person,group,null,"synthetic-"+scope,scope,null,1,1,true,clock.instant(),clock.instant(),null,null,online.wanan.xingchen.core.memory.MemoryStatus.ACTIVE);repository.save(memory,List.of(new online.wanan.xingchen.core.memory.MemorySource(memory.id(),"QQ",group,"1500",person,clock.instant())));
        }
        var otherMemory=new online.wanan.xingchen.core.memory.Memory(UUID.randomUUID(),online.wanan.xingchen.core.memory.MemoryType.SEMANTIC,person,other,null,"synthetic-other-conversation",online.wanan.xingchen.core.memory.MemoryScope.CONVERSATION,null,1,1,true,clock.instant(),clock.instant(),null,null,online.wanan.xingchen.core.memory.MemoryStatus.ACTIVE);repository.save(otherMemory,List.of(new online.wanan.xingchen.core.memory.MemorySource(otherMemory.id(),"QQ",group,"1500",person,clock.instant())));
        clock.advance(Duration.ofMinutes(5));text("timer-group","safe check-in");timer.tick();done();
        assertThat(model.requests()).hasSize(1);assertThat(model.requests().getFirst().context().memories()).extracting(online.wanan.xingchen.core.memory.Memory::content).containsExactlyInAnyOrder("synthetic-GLOBAL","synthetic-CONVERSATION");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories",Integer.class)).isEqualTo(6);
    }
    @Test void ownerMessageDoesNotGrantGroupProactiveConsent()throws Exception{
        model.enqueueFor("timer-group",new ModelResponse(AgentDecision.noReply(),null,null));
        fake.emit(new ObjectMapper().writeValueAsString(Map.of("post_type","message","message_type","group","self_id","test-bot","group_id","timer-group","user_id","synthetic-owner","message_id","1600","time",clock.instant().getEpochSecond(),"message","ordinary owner statement")));
        await(()->jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id='1600' AND status='COMPLETED'",Integer.class)==1);
        enable();clock.advance(Duration.ofMinutes(5));var access=app.getBean(AccessControlService.class);access.replace(List.of(),access.revision(),"fixture-admin");timer.tick();done();
        assertThat(attempts()).isZero();assertThat(model.requests()).hasSize(1);assertThat(fake.sendReceiveCount()).isZero();
    }
    @Test void proactiveQuotaRejectionIsRecordedAndDoesNotSilentlyDisappear()throws Exception{
        seed("group","1700");enable();configure(Map.of("maxPerMinute",1));clock.advance(Duration.ofMinutes(5));text("timer-group","first");timer.tick();done();
        // The shared outbound limiter uses its real clock; advancing the timer clock does not reset that quota.
        clock.advance(Duration.ofMinutes(30));text("timer-group","limited second");timer.tick();done();
        assertThat(model.requests()).hasSize(2);assertThat(fake.sendReceiveCount()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT status FROM proactive_attempts",String.class)).containsExactlyInAnyOrder("SENT","RATE_LIMITED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='FAILED' AND failure_reason='rate-limited'",Integer.class)).isEqualTo(1);
    }
    @Test void resetRequiresNewHumanInputAndNeverResurrectsClearedContext()throws Exception{
        seed("group","1800");enable();clock.advance(Duration.ofMinutes(5));text("timer-group","old timer output");timer.tick();done();
        UUID id=conversation("GROUP","timer-group");runtime.reset("QQ:GROUP:timer-group",id,app.getBean(SimulationStateService.class).get(id).generation());
        clock.advance(Duration.ofMinutes(30));timer.tick();done();assertThat(model.requests()).hasSize(1);
        emit("group","1801","post-reset conversation");clock.advance(Duration.ofMinutes(5));text("timer-group","new context only");timer.tick();done();
        assertThat(model.requests()).hasSize(2);assertThat(model.requests().getLast().context().recentMessages()).anyMatch(x->x.contains("post-reset conversation")).noneMatch(x->x.contains("old timer output")||x.contains("ordinary context 1800"));
    }
    @Test void priorRejectedNormalReplyPartsAreNotPresentedAsDeliveredHistory()throws Exception{
        configure(Map.of("maxReplyMessages",1));model.enqueueFor("timer-group",new ModelResponse(AgentDecision.multi(List.of("normal delivered","normal never sent")),null,null));emit("group","1900","a normal question?");
        enable();clock.advance(Duration.ofMinutes(5));model.enqueueFor("timer-group",new ModelResponse(AgentDecision.noReply(),null,null));timer.tick();done();
        assertThat(model.requests()).hasSize(2);assertThat(model.requests().getLast().context().recentMessages()).anyMatch(x->x.contains("normal delivered")).noneMatch(x->x.contains("normal never sent"));
        assertThat(fake.sendReceiveCount()).isEqualTo(1);
    }
    @Test void normalFollowupCanSeeConfirmedTimerMessageAfterRestart()throws Exception{
        seed("group","2000");enable();clock.advance(Duration.ofMinutes(5));text("timer-group","a known timer question");timer.tick();done();
        app.close();app=null;start();clock.advance(Duration.ofMinutes(6));text("timer-group","normal followup reply");emit("group","2001","what do you mean?");
        assertThat(model.requests()).hasSize(1);assertThat(model.requests().getFirst().context().taskState()).containsEntry("proactive.lastDeliveredText","a known timer question");assertThat(model.requests().getFirst().tools()).isNotEmpty();
        assertThat(fake.sendReceiveCount()).isEqualTo(1);
    }
    @Test void housekeepingNeverDeletesAmbiguousOrRecentAdmission()throws Exception{
        seed("group","2100");enable();clock.advance(Duration.ofMinutes(5));var store=app.getBean(ProactiveAttemptStore.class);var c=store.candidates(clock.instant(),"").getFirst();var p=settings.proactive(c.conversationId());
        String skipped=store.reserve(c,p,clock.instant(),false);clock.advance(Duration.ofMinutes(5));String uncertain=store.reserve(c,p,clock.instant(),true);store.finish(uncertain,"UNKNOWN",clock.instant());
        clock.advance(Duration.ofDays(9));assertThat(store.pruneKnownHistory(clock.instant())).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT status FROM proactive_attempts",String.class)).containsExactly("UNKNOWN");
    }
    @Test void staleDispatchedTimerCallbackCannotSurviveRuntimeRestart()throws Exception{
        var scheduler=app.getBean(CapturedScheduler.class);Runnable oldCallback=scheduler.tasks.stream().filter(t->t.delay.equals(Duration.ofMinutes(1))).findFirst().orElseThrow().action;
        runtime.stop();runtime.start();assertThat(scheduler.pendingMinutes()).isEqualTo(1);
        // An executor may already have taken the old task before cancel(false).
        oldCallback.run();assertThat(scheduler.pendingMinutes()).isEqualTo(1);
    }
    @Test void resetOfAnActiveNormalTurnDoesNotPermanentlyDisableFutureIdleParticipation()throws Exception{
        seed("group","2200");enable();var barrier=model.enqueueBlockedFor("timer-group",new ModelResponse(AgentDecision.text("cancelled normal answer"),null,null));
        fake.emit(new ObjectMapper().writeValueAsString(Map.of("post_type","message","message_type","group","self_id","test-bot","group_id","timer-group","user_id","timer-user","message_id","2201","time",clock.instant().getEpochSecond(),"message","an interrupted question?")));
        assertThat(barrier.awaitStarted(5,TimeUnit.SECONDS)).isTrue();UUID id=conversation("GROUP","timer-group");
        try(var worker=Executors.newSingleThreadExecutor()){
            var reset=worker.submit(()->runtime.reset("QQ:GROUP:timer-group",id,app.getBean(SimulationStateService.class).get(id).generation()));
            await(()->model.requests().getFirst().cancellation().isCancelled());barrier.release();reset.get(10,TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id='2201'",String.class)).isEqualTo("CLAIMED");
        assertThat(jdbc.queryForObject("SELECT status FROM durable_turn_executions WHERE incoming_event_id='2201'",String.class)).isEqualTo("STALE");
        emit("group","2202","fresh post-reset chat");clock.advance(Duration.ofMinutes(5));text("timer-group","fresh timer answer");timer.tick();done();
        assertThat(model.requests()).hasSize(2);assertThat(fake.sendReceiveCount()).isEqualTo(1);
    }
    @Test void morningGreetingBypassesRandomQuietAndProbabilityButIsDurableOncePerDay()throws Exception{
        seed("private","2300");seed("group","2301");enable();
        configure(Map.of("dailyGreetingsEnabled",true,"proactiveProbability",0.0,"proactiveQuietStartHour",23,"proactiveQuietEndHour",8,"nightReplyMuteEnabled",true));
        clock.now=Instant.parse("2026-11-20T23:00:00Z"); // 07:00 Shanghai next day
        text("timer-user","synthetic morning private");text("timer-group","synthetic morning group");timer.tick();done();
        assertThat(fake.sendReceiveCount()).isEqualTo(2);assertThat(app.getBean(Draws.class).calls).isZero();
        for(var request:model.requests().subList(1,3))assertThat(request.context().taskState()).containsEntry("proactive.kind","MORNING").containsEntry("turn.hasNewUserMessage","false");
        assertThat(jdbc.queryForList("SELECT intent FROM proactive_attempts",String.class)).containsOnly("MORNING");
        timer.tick();done();assertThat(fake.sendReceiveCount()).isEqualTo(2);
        app.close();app=null;start();clock.now=Instant.parse("2026-11-20T23:01:00Z");timer.tick();done();
        assertThat(model.requests()).isEmpty();assertThat(fake.sendReceiveCount()).isZero();assertThat(attempts()).isEqualTo(2);
    }
    @Test void eveningGreetingDoesNotBorrowRandomCooldownAndMissedGreetingsAreNotReplayed()throws Exception{
        seed("group","2400");enable();configure(Map.of("dailyGreetingsEnabled",true,"proactiveIntervalMinutes",120,"proactiveCooldownMinutes",120));
        clock.now=Instant.parse("2026-11-20T14:00:00Z");text("timer-group","random at 22");timer.tick();done();
        clock.advance(Duration.ofMinutes(50));text("timer-group","evening at 22:50");timer.tick();done();
        assertThat(fake.sendReceiveCount()).isEqualTo(2);assertThat(model.requests().getLast().context().taskState()).containsEntry("proactive.kind","EVENING");
        assertThat(jdbc.queryForList("SELECT intent FROM proactive_attempts ORDER BY checked_at",String.class)).containsExactly("RANDOM","EVENING");
        configure(Map.of("proactiveQuietStartHour",23,"proactiveQuietEndHour",8));clock.advance(Duration.ofMinutes(10));timer.tick();done();assertThat(model.requests()).hasSize(2);
        clock.now=Instant.parse("2026-11-20T23:10:00Z");timer.tick();done();assertThat(model.requests()).hasSize(2);
    }
    @Test void greetingStillHonorsWaitAndUnknownIsNotRetriedAfterRestart()throws Exception{
        seed("group","2500");enable();configure(Map.of("dailyGreetingsEnabled",true,"proactiveProbability",0.0));
        UUID id=conversation("GROUP","timer-group");var simulation=app.getBean(SimulationStateService.class);simulation.waitForNextMessage(id,"waiting","fixture");
        clock.now=Instant.parse("2026-11-20T23:00:00Z");timer.tick();done();assertThat(attempts()).isZero();assertThat(model.requests()).isEmpty();
        simulation.reset(id);simulation.markRead(id,"2500");text("timer-group","uncertain morning");fake.sendBehavior(FakeOneBotServer.SendBehavior.DROP_AFTER_RECEIVE);timer.tick();done();
        assertThat(fake.sendReceiveCount()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT status FROM proactive_attempts",String.class)).isEqualTo("UNKNOWN");
        app.close();app=null;start();clock.now=Instant.parse("2026-11-20T23:01:00Z");timer.tick();done();assertThat(model.requests()).isEmpty();assertThat(fake.sendReceiveCount()).isZero();
    }
    @Test void concurrentGreetingAdmissionAndSharedGreetingBudgetRemainBounded()throws Exception{
        seed("group","2600");enable();clock.now=Instant.parse("2026-11-20T23:00:00Z");configure(Map.of("dailyGreetingsEnabled",true));
        var store=app.getBean(ProactiveAttemptStore.class);var c=store.candidates(clock.instant(),"").getFirst();var p=settings.proactive(c.conversationId());var intent=settings.schedule(c.conversationId()).due(clock.instant()).getFirst();
        try(var workers=Executors.newFixedThreadPool(8)){
            var barrier=new CyclicBarrier(8);var calls=new ArrayList<Future<String>>();for(int i=0;i<8;i++)calls.add(workers.submit(()->{barrier.await();return store.reserveGreeting(c,p,clock.instant(),intent);}));
            int admitted=0;for(var call:calls)if(call.get(10,TimeUnit.SECONDS)!=null)admitted++;assertThat(admitted).isEqualTo(1);
        }
        for(int i=0;i<3;i++){UUID id=app.getBean(IdentityRegistry.class).identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"greeting-budget-"+i)).id();assertThat(store.reserveGreeting(new ProactiveAttemptStore.Candidate(id,c.anchor()),p,clock.instant(),intent)).isNotNull();}
        UUID extra=conversation("GROUP","greeting-budget-extra");assertThat(store.reserveGreeting(new ProactiveAttemptStore.Candidate(extra,c.anchor()),p,clock.instant(),intent)).isNull();assertThat(attempts()).isEqualTo(4);
    }
    void inboundAs(String kind,String user,String id,String text,String mention)throws Exception{
        var segments=new ArrayList<Map<String,Object>>();if(mention!=null)segments.add(Map.of("type","at","data",Map.of("qq",mention)));segments.add(Map.of("type","text","data",Map.of("text",text)));
        fake.emit(new ObjectMapper().writeValueAsString(Map.of("post_type","message","message_type",kind,"self_id","test-bot","group_id","timer-group","user_id",user,"message_id",id,"time",clock.instant().getEpochSecond(),"sender",Map.of("user_id",user,"nickname","Synthetic Member","role","member"),"message",segments)));
        await(()->jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status='COMPLETED'",Integer.class,id)==1);
    }
    @Test void overnightOnlyDirectMentionOrConfiguredOwnerPrivateCanReachModel()throws Exception{
        configure(Map.of("nightReplyMuteEnabled",true,"ordinaryMessageProbability",1.0,"configuredSpeakerIds",List.of("QQ:timer-user")));
        clock.now=Instant.parse("2026-11-20T16:00:00Z"); // 00:00
        inboundAs("private","timer-user","2700","ordinary private?",null);
        inboundAs("group","timer-user","2701","大肥鱼 question?",null);
        inboundAs("group","synthetic-owner","2702","owner group?",null);
        inboundAs("group","timer-user","2703","wrong mention?","other-bot");
        assertThat(model.requests()).isEmpty();assertThat(fake.sendReceiveCount()).isZero();
        assertThat(jdbc.queryForList("SELECT wake_reasons_json FROM durable_turn_executions",String.class)).hasSize(4).allMatch(s->s.contains("NIGHT_SILENCE"));
        text("synthetic-owner","owner private allowed");inboundAs("private","synthetic-owner","2704","owner ordinary",null);
        text("timer-group","direct mention allowed");inboundAs("group","timer-user","2705","direct mention","test-bot");
        assertThat(model.requests()).hasSize(2);assertThat(fake.sendReceiveCount()).isEqualTo(2);
        clock.advance(Duration.ofHours(6));text("timer-user","after 06 private");inboundAs("private","timer-user","2706","normal private",null);
        assertThat(model.requests()).hasSize(3);assertThat(fake.sendReceiveCount()).isEqualTo(3);
    }
    @Test void mutedOrdinaryInputDoesNotConsumeWaitAndAtStillResumesIt()throws Exception{
        seed("group","2800");configure(Map.of("nightReplyMuteEnabled",true));var state=app.getBean(SimulationStateService.class);UUID id=conversation("GROUP","timer-group");state.waitForNextMessage(id,"2800","next-message");
        clock.now=Instant.parse("2026-11-20T17:00:00Z");inboundAs("group","timer-user","2801","question?",null);
        assertThat(state.get(id).wakeState()).isEqualTo(SimulationConversationState.WakeState.WAITING);assertThat(model.requests()).isEmpty();
        text("timer-group","allowed wait continuation");inboundAs("group","timer-user","2802","continue","test-bot");assertThat(model.requests()).hasSize(1);assertThat(fake.sendReceiveCount()).isEqualTo(1);
    }
    @Test void midnightFenceBlocksLateOrdinaryReplyButDoesNotRetryIt()throws Exception{
        configure(Map.of("nightReplyMuteEnabled",true));clock.now=Instant.parse("2026-11-20T15:59:00Z");
        var barrier=model.enqueueBlockedFor("timer-user",new ModelResponse(AgentDecision.text("late ordinary reply"),null,null));
        fake.emit(new ObjectMapper().writeValueAsString(Map.of("post_type","message","message_type","private","self_id","test-bot","user_id","timer-user","message_id","2900","time",clock.instant().getEpochSecond(),"message","ordinary private")));
        assertThat(barrier.awaitStarted(5,TimeUnit.SECONDS)).isTrue();clock.advance(Duration.ofMinutes(1));barrier.release();
        await(()->runtime.queueDiagnostics().activeConversationCount()==0);assertThat(fake.sendReceiveCount()).isZero();assertThat(model.requests()).hasSize(1);
    }
    @Test void runtimeRestartCannotResumePersistedOrdinaryOutputDuringNightSilence()throws Exception{
        seed("private","3000");configure(Map.of("nightReplyMuteEnabled",true));runtime.stop();
        UUID id=conversation("PRIVATE","timer-user");long generation=app.getBean(SimulationStateService.class).get(id).generation();
        // Simulate the established safe-recovery point: output durable, no transport or tool claim.
        jdbc.update("UPDATE durable_turn_executions SET status='RUNNING',model_turn_state='OUTPUT_EMITTED',output_type='TEXT_REPLY',output_json='[\"synthetic pending output\"]',generation=? WHERE incoming_event_id='3000'",generation);
        jdbc.update("UPDATE inbound_turn_executions SET status='CLAIMED' WHERE incoming_event_id='3000'");
        clock.now=Instant.parse("2026-11-20T17:00:00Z");runtime.start();
        await(()->jdbc.queryForObject("SELECT status FROM durable_turn_executions WHERE incoming_event_id='3000'",String.class).equals("INTERRUPTED"));
        assertThat(model.requests()).hasSize(1);assertThat(fake.sendReceiveCount()).isZero();
        clock.advance(Duration.ofHours(6));runtime.stop();runtime.start();assertThat(fake.sendReceiveCount()).isZero();assertThat(model.requests()).hasSize(1);
    }
    static void await(BooleanSupplier condition)throws InterruptedException{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!condition.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);assertThat(condition.getAsBoolean()).isTrue();}
    static void awaitUnchecked(BooleanSupplier condition){try{await(condition);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
    static final class TestClock extends Clock {
        private volatile Instant now=Instant.parse("2026-11-20T04:00:00Z");void advance(Duration d){now=now.plus(d);}
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
    static final class Draws implements java.util.function.DoubleSupplier {double value=.25;int calls;public double getAsDouble(){calls++;return value;}}
    static final class CapturedScheduler implements Scheduler {
        final List<Task> tasks=new java.util.concurrent.CopyOnWriteArrayList<>();
        public Cancellable schedule(Duration delay,Runnable action){var task=new Task(delay,action);tasks.add(task);return task;}
        long pendingMinutes(){return tasks.stream().filter(t->!t.cancelled&&!t.ran&&t.delay.equals(Duration.ofMinutes(1))).count();}
        void fireMinute(){var task=tasks.stream().filter(t->!t.cancelled&&!t.ran&&t.delay.equals(Duration.ofMinutes(1))).findFirst().orElseThrow();task.ran=true;task.action.run();}
        static final class Task implements Cancellable {final Duration delay;final Runnable action;boolean cancelled,ran;Task(Duration d,Runnable a){delay=d;action=a;}public boolean cancel(){cancelled=true;return true;}public boolean isCancelled(){return cancelled;}}
    }
    @TestConfiguration(proxyBeanMethods=false) static class Fixture {
        @Bean @Primary TestClock proactiveTestClock(){return new TestClock();}
        @Bean Draws proactiveDraws(){return new Draws();}
        @Bean @Primary CapturedScheduler proactiveTestScheduler(){return new CapturedScheduler();}
        @Bean @Primary ProactiveParticipationService controlledProactive(ProactiveAttemptStore store,SocialSettingsService settings,AccessControlService access,SimulationStateService simulation,ConversationOrchestrator orchestrator,Clock clock,Scheduler scheduler,Draws draws){return new ProactiveParticipationService(store,settings,access,simulation,orchestrator,clock,scheduler,draws);}
        @Bean @Primary OneBotGateway proactiveGateway(FakeOneBotServer fake,ObjectMapper json,OneBotEventNormalizer normalizer){return new OneBotV11Gateway(new OneBotV11Configuration(fake.httpUri(),fake.wsUri(),"UNUSED_TEST_TOKEN","test-bot",false,3000,3000,50,500,128,90_000),json,normalizer,Map.of());}
    }
}
