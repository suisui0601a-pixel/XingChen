package online.wanan.xingchen.core;

import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.config.TestTurnBoundaryObserver;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.memory.MemoryRepository;
import online.wanan.xingchen.core.model.Platform;
import online.wanan.xingchen.core.model.ConversationIdentity;
import online.wanan.xingchen.core.model.ConversationType;
import online.wanan.xingchen.storage.DurableTurnExecutionStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Full local Fake OneBot → SocialRuntime → SQLite stress evidence; no external adapters are enabled. */
class SocialRuntimeStressE2ETest {
    @Test void r2stress001_sameConversationTwentyFourMixedEventsDrainExactlyOnce() throws Exception {
        Path db=TestSqliteDatabase.create("r2stress-same-conversation");String url=TestSqliteDatabase.jdbcUrl(db);
        String group="stress-same",actor="stress-user";long first=1001;
        try(ConfigurableApplicationContext context=start(url)){
            var runtime=context.getBean(SocialRuntime.class);var fake=context.getBean(FakeOneBotServer.class);var provider=context.getBean(MockModelProvider.class);var observer=context.getBean(TestTurnBoundaryObserver.class);var simulation=context.getBean(SimulationStateService.class);var identities=context.getBean(IdentityRegistry.class);var jdbc=context.getBean(JdbcTemplate.class);
            runtime.start();await(()->fake.wsConnections()>0,5000);UUID conversation=identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,group)).id();
            UUID target=identities.identify(Platform.QQ,"user-"+group).id();simulation.configure(conversation,Map.of("speakers",List.of("speaker-x")),true);simulation.sleep(conversation,null,true);generationAudit(jdbc);
            var remember=new ModelToolCall("remember-stress","memory.remember",Map.of("type","PREFERENCE","content","stress memory","scope","PERSON_GLOBAL","importance",.9));
            var address=new ModelToolCall("address-stress","memory.setAddress",Map.of("targetPersonId",target.toString(),"address","姐姐","scope","CONVERSATION","scopeId",group));
            var modelBarrier=provider.enqueueBlocked(toolResponse(remember));provider.enqueue(waitDecision()).enqueue(textDecision("WAIT continuation reply"));
            provider.enqueue(toolResponse(address)).enqueue(noReply());for(int i=0;i<24;i++)provider.enqueue(noReply());
            var toolGate=observer.arm(TestTurnBoundaryObserver.Phase.TOOL_EXECUTED);
            fake.emit(message(first,1,group,actor,"ordinary while sleeping"));await(()->completed(jdbc,String.valueOf(first)),8000);
            fake.emit(payload(first+1,group,2,"mention-question"));assertThat(modelBarrier.awaitStarted(8,TimeUnit.SECONDS)).as("slow model barrier").isTrue();
            for(long id=first+2;id<=first+8;id++)fake.emit(payload(id,group,(int)(id-first+1),eventKind(id)));
            fake.emit(payload(first+1,group,2,"mention-question"));
            await(()->runtime.queueDiagnostics().queuedEventCount()>=8,5000);assertThat(runtime.queueDiagnostics().activeConversationCount()).isEqualTo(1);
            modelBarrier.release();assertThat(toolGate.awaitReached(8,TimeUnit.SECONDS)).as("slow memory tool effect reached its post-effect barrier").isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE content='stress memory'",Integer.class)).isEqualTo(1);
            assertThat(runtime.queueDiagnostics().queuedEventCount()).isGreaterThanOrEqualTo(8);toolGate.release();
            await(()->completed(jdbc,String.valueOf(first+8))&&drained(runtime),15000);
            assertThat(jdbc.queryForObject("SELECT wake_state FROM simulation_conversation_state WHERE conversation_id=?",String.class,conversation.toString())).isEqualTo("AWAKE");
            simulation.sleep(conversation,null,true);fake.emit(payload(first+9,group,10,"speaker"));await(()->completed(jdbc,String.valueOf(first+9)),8000);
            assertThat(simulation.get(conversation).wakeState()).isEqualTo(SimulationConversationState.WakeState.AWAKE);
            assertThat(wakeReasons(jdbc,first+9)).contains("configured-speaker");
            for(long id=first+10;id<=first+23;id++)fake.emit(payload(id,group,(int)(id-first+1),eventKind(id)));
            await(()->completed(jdbc,String.valueOf(first+23))&&drained(runtime),20000);
            int callsBeforeLate=provider.requests().size();fake.emit(payload(first+24,group,0,"late-question"));
            await(()->jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE platform_message_id=?",Integer.class,String.valueOf(first+24))==1,5000);
            assertThat(provider.requests()).hasSize(callsBeforeLate);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE conversation_id=?",Integer.class,conversation.toString())).isEqualTo(24);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE conversation_id=?",Integer.class,conversation.toString())).isEqualTo(24);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE conversation_id=?",Integer.class,conversation.toString())).isEqualTo(25);
            var expectedMessageOrder=new ArrayList<String>();for(long id=first;id<=first+24;id++)expectedMessageOrder.add(String.valueOf(id));assertThat(messageIds(jdbc,conversation)).containsExactlyElementsOf(expectedMessageOrder);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE content='stress memory'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relationship_terms WHERE value='姐姐' AND scope_type='CONVERSATION' AND scope_id=?",Integer.class,group)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE conversation_id=? AND status='SUCCESS'",Integer.class,conversation.toString())).isEqualTo(1);
            assertThat(fake.appliedSendCount()).isEqualTo(1);assertThat(provider.maxConcurrentCalls()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE incoming_event_id=?",Integer.class,String.valueOf(first+1))).isEqualTo(1);
            assertThat(wakeReasons(jdbc,first+1)).contains("mention","question");assertThat(wakeReasons(jdbc,first+5)).contains("reply-to-bot");assertThat(wakeReasons(jdbc,first+6)).contains("poke");assertThat(wakeReasons(jdbc,first+7)).contains("bot-name");assertThat(wakeReasons(jdbc,first+8)).contains("question");
            assertThat(jdbc.queryForObject("SELECT is_owner FROM messages WHERE platform_message_id=?",Integer.class,String.valueOf(first+10))).as("manual wake event owner snapshot").isEqualTo(1);assertThat(wakeReasons(jdbc,first+10)).contains("owner","manual-wake");assertThat(wakeReasons(jdbc,first+11)).contains("must-reply-keyword");assertThat(wakeReasons(jdbc,first+17)).contains("manual-wake");
            var cursor=simulation.get(conversation).lastReadCursor();assertThat(cursor).isEqualTo(String.valueOf(first+23));
            assertMonotonic(jdbc,conversation);assertThat(runtime.queueDiagnostics().activeConversationCount()).isZero();assertThat(runtime.queueDiagnostics().queuedEventCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE status NOT IN ('COMPLETED','STALE','INTERRUPTED')",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE wake_reasons_json LIKE '%WAIT_CONTINUATION%'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_conversation_state WHERE wake_state='WAITING'",Integer.class)).isZero();
            assertThat(context.getBean(DurableTurnExecutionStore.class).recoverable()).isEmpty();
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void r2stress002_003_fiveConversationRuntimeParallelismWithBlockedModelAndTool() throws Exception {
        Path db=TestSqliteDatabase.create("r2stress-five-conversations");String url=TestSqliteDatabase.jdbcUrl(db);
        try(ConfigurableApplicationContext context=start(url)){
            var runtime=context.getBean(SocialRuntime.class);var fake=context.getBean(FakeOneBotServer.class);var provider=context.getBean(MockModelProvider.class);var observer=context.getBean(TestTurnBoundaryObserver.class);var identities=context.getBean(IdentityRegistry.class);var jdbc=context.getBean(JdbcTemplate.class);
            runtime.start();await(()->fake.wsConnections()>0,5000);generationAudit(jdbc);
            List<String> groups=List.of("stress-A","stress-B","stress-C","stress-D","stress-E");Map<String,UUID> conversations=new LinkedHashMap<>();Map<String,String> users=new LinkedHashMap<>();
            for(String g:groups){conversations.put(g,identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,g)).id());users.put(g,"user-"+g);}
            UUID bPerson=identities.identify(Platform.QQ,users.get("stress-B")).id();
            var slowA=provider.enqueueBlockedFor("stress-A",waitDecision());var slowTool=observer.arm(TestTurnBoundaryObserver.Phase.TOOL_EXECUTED);
            provider.enqueueFor("stress-A",textDecision("A continuation"));for(int i=0;i<4;i++)provider.enqueueFor("stress-A",noReply());
            var remember=new ModelToolCall("remember-B","memory.remember",Map.of("type","PREFERENCE","content","five-conversation memory","scope","PERSON_GLOBAL","importance",.9));
            provider.enqueueFor("stress-B",toolResponse(remember));for(int i=0;i<6;i++)provider.enqueueFor("stress-B",noReply());
            provider.enqueueFor("stress-C",waitDecision()).enqueueFor("stress-C",textDecision("C continuation"));for(int i=0;i<4;i++)provider.enqueueFor("stress-C",noReply());
            provider.enqueueFor("stress-D",textDecision("D reply"));for(int i=0;i<5;i++)provider.enqueueFor("stress-D",noReply());
            for(int i=0;i<6;i++)provider.enqueueFor("stress-E",noReply());
            long a1=2011,b1=2021,c1=2031,d1=2041,e1=2051;
            fake.emit(payload(a1,"stress-A",1,"mention"));assertThat(slowA.awaitStarted(8,TimeUnit.SECONDS)).as("conversation A slow model entered").isTrue();
            fake.emit(payload(b1,"stress-B",1,"mention"));assertThat(slowTool.awaitReached(8,TimeUnit.SECONDS)).as("conversation B slow tool entered after one committed effect").isTrue();
            List<String> timeline=new java.util.concurrent.CopyOnWriteArrayList<>(List.of("A_START","B_TOOL_BLOCKED"));
            fake.emit(payload(c1,"stress-C",1,"question"));fake.emit(payload(d1,"stress-D",1,"mention"));fake.emit(payload(e1,"stress-E",1,"question"));
            await(()->completed(jdbc,String.valueOf(c1))&&completed(jdbc,String.valueOf(d1))&&completed(jdbc,String.valueOf(e1)),10000);
            timeline.add("C_D_E_FINISH");assertThat(runtime.queueDiagnostics().activeConversationCount()).isGreaterThanOrEqualTo(2);
            for(String group:groups){long base=base(group);for(int i=2;i<=6;i++)fake.emit(payload(base+i,group,i,eventKindForGroup(group,i)));}
            fake.emit(payload(a1,"stress-A",1,"mention"));fake.emit(payload(b1,"stress-B",1,"mention"));
            await(()->runtime.queueDiagnostics().queuedEventCount()>=12,8000);
            slowTool.release();await(()->completed(jdbc,String.valueOf(b1)),10000);timeline.add("B_FINISH");
            assertThat(slowA.awaitReturned(50,TimeUnit.MILLISECONDS)).as("A model remains blocked while B finishes").isFalse();timeline.add("A_RELEASE");slowA.release();
            await(()->groups.stream().allMatch(g->allSixCompleted(jdbc,g))&&drained(runtime),25000);timeline.add("A_FINISH");
            assertThat(timeline).containsSubsequence("A_START","B_TOOL_BLOCKED","C_D_E_FINISH","B_FINISH","A_RELEASE","A_FINISH");
            assertThat(provider.maxConcurrentCalls()).isGreaterThanOrEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages",Integer.class)).isEqualTo(30);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions",Integer.class)).isEqualTo(30);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions",Integer.class)).isEqualTo(30);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE content='five-conversation memory'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='SUCCESS'",Integer.class)).isEqualTo(3);assertThat(fake.appliedSendCount()).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_conversation_state WHERE wake_state='WAITING'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE status NOT IN ('COMPLETED','STALE','INTERRUPTED')",Integer.class)).isZero();
            for(String group:groups){var expected=java.util.stream.IntStream.rangeClosed(1,6).mapToObj(i->String.valueOf(base(group)+i)).toList();assertThat(messageIds(jdbc,conversations.get(group))).containsExactlyElementsOf(expected);assertMonotonic(jdbc,conversations.get(group));}
            assertThat(wakeReasons(jdbc,String.valueOf(2032))).contains("WAIT_CONTINUATION");assertThat(wakeReasons(jdbc,String.valueOf(2012))).contains("WAIT_CONTINUATION");
            assertThat(runtime.queueDiagnostics().activeConversationCount()).isZero();assertThat(runtime.queueDiagnostics().queuedConversationCount()).isZero();assertThat(runtime.queueDiagnostics().queuedEventCount()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE conversation_id IN (SELECT id FROM conversations WHERE platform_conversation_id IN ('stress-A','stress-B','stress-C','stress-D','stress-E'))",Integer.class)).isEqualTo(30);
        }finally{TestSqliteDatabase.clean(db);}
    }

    private static String eventKind(long id){return switch((int)(id-1001)){case 1->"mention-question";case 5,13->"reply";case 6,16->"poke";case 7,14->"name";case 8,15,20,23->"question";case 9,18->"speaker";case 10,17->"manual";case 11->"keyword";case 12,21->"mention";default->"ordinary";};}
    private static String eventKindForGroup(String group,int sequence){return switch(group){case "stress-A"->switch(sequence){case 2->"ordinary";case 3->"question";case 4->"ordinary";case 5->"reply";default->"ordinary";};case "stress-B"->switch(sequence){case 2,4,6->"ordinary";case 3->"question";case 5->"poke";default->"ordinary";};case "stress-C"->switch(sequence){case 2,4,6->"ordinary";case 3->"poke";case 5->"mention";default->"ordinary";};case "stress-D"->switch(sequence){case 2,4->"ordinary";case 3->"reply";case 5->"question";default->"ordinary";};default->switch(sequence){case 2,4->"ordinary";case 3->"mention";case 5->"poke";default->"ordinary";};};}
    private static long base(String group){return switch(group){case "stress-A"->2010;case "stress-B"->2020;case "stress-C"->2030;case "stress-D"->2040;default->2050;};}
    private static List<String> messageIds(JdbcTemplate jdbc,UUID conversation){return jdbc.query("SELECT platform_message_id FROM messages WHERE conversation_id=? ORDER BY rowid",(rs,n)->rs.getString(1),conversation.toString());}
    private static String wakeReasons(JdbcTemplate jdbc,long id){return wakeReasons(jdbc,String.valueOf(id));}
    private static String wakeReasons(JdbcTemplate jdbc,String id){return jdbc.queryForObject("SELECT wake_reasons_json FROM durable_turn_executions WHERE incoming_event_id=?",String.class,id);}
    private static boolean completed(JdbcTemplate jdbc,String id){return jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status='COMPLETED'",Integer.class,id)==1;}
    private static boolean allSixCompleted(JdbcTemplate jdbc,String group){long base=base(group);for(int i=1;i<=6;i++)if(!completed(jdbc,String.valueOf(base+i)))return false;return true;}
    private static boolean drained(SocialRuntime runtime){var d=runtime.queueDiagnostics();return d.activeConversationCount()==0&&d.queuedConversationCount()==0&&d.queuedEventCount()==0;}
    private static void generationAudit(JdbcTemplate jdbc){jdbc.execute("CREATE TABLE stress_generation_audit (seq INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT NOT NULL, generation INTEGER NOT NULL, cursor TEXT, wake_state TEXT NOT NULL)");jdbc.execute("CREATE TRIGGER stress_generation_audit_update AFTER UPDATE OF generation ON simulation_conversation_state BEGIN INSERT INTO stress_generation_audit(conversation_id,generation,cursor,wake_state) VALUES(NEW.conversation_id,NEW.generation,NEW.last_read_cursor,NEW.wake_state); END");}
    private static void assertMonotonic(JdbcTemplate jdbc,UUID conversation){var rows=jdbc.query("SELECT generation,cursor FROM stress_generation_audit WHERE conversation_id=? ORDER BY seq",(rs,n)->new AuditPoint(rs.getLong(1),rs.getString(2)),conversation.toString());for(int i=1;i<rows.size();i++)assertThat(rows.get(i).generation()).as("generation monotonic for "+conversation).isGreaterThan(rows.get(i-1).generation());String previous=null;for(var row:rows)if(row.cursor()!=null){if(previous!=null)assertThat(Long.parseLong(row.cursor())).as("read cursor monotonic for "+conversation).isGreaterThanOrEqualTo(Long.parseLong(previous));previous=row.cursor();}}
    private record AuditPoint(long generation,String cursor){}
    private static String message(long id,int sequence,String group,String actor,String text){return payload(id,group,sequence,"ordinary").replace("ordinary",""+text);}
    private static String payload(long id,String group,int sequence,String kind){String actor=kind.equals("manual")?"owner-test":kind.equals("speaker")?"speaker-x":"user-"+group;long time=1_800_000_000L+sequence;return switch(kind){case "poke"->"{\"post_type\":\"notice\",\"notice_type\":\"notify\",\"sub_type\":\"poke\",\"message_type\":\"group\",\"self_id\":\"test-bot\",\"user_id\":\""+actor+"\",\"group_id\":\""+group+"\",\"target_id\":\"test-bot\",\"time\":"+time+",\"message_id\":\""+id+"\",\"message_seq\":"+sequence+"}";default->{String segments=switch(kind){case "mention-question"->"{\"type\":\"at\",\"data\":{\"qq\":\"test-bot\"}},{\"type\":\"text\",\"data\":{\"text\":\"why?\"}}";case "mention"->"{\"type\":\"at\",\"data\":{\"qq\":\"test-bot\"}},{\"type\":\"text\",\"data\":{\"text\":\"please reply\"}}";case "reply"->"{\"type\":\"reply\",\"data\":{\"id\":\"bot-1\",\"user_id\":\"test-bot\",\"nickname\":\"Bot\"}},{\"type\":\"text\",\"data\":{\"text\":\"follow up\"}}";case "name"->"{\"type\":\"text\",\"data\":{\"text\":\"大肥鱼在吗\"}}";case "question"->"{\"type\":\"text\",\"data\":{\"text\":\"question?\"}}";case "manual"->"{\"type\":\"text\",\"data\":{\"text\":\"/wake\"}}";case "keyword"->"{\"type\":\"text\",\"data\":{\"text\":\"stress-keyword please\"}}";default->"{\"type\":\"text\",\"data\":{\"text\":\"ordinary\"}}";};yield "{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"test-bot\",\"user_id\":\""+actor+"\",\"group_id\":\""+group+"\",\"time\":"+time+",\"message_id\":\""+id+"\",\"message_seq\":"+sequence+",\"sender\":{\"user_id\":\""+actor+"\",\"nickname\":\"same\",\"card\":\"\",\"role\":\"member\"},\"message\":["+segments+"]}";}};}
    private static ModelResponse toolResponse(ModelToolCall call){return new ModelResponse(null,null,null,List.of(call),ModelFinishReason.TOOL_CALLS);}
    private static ModelResponse noReply(){return new ModelResponse(AgentDecision.noReply(),usage(),null);}
    private static ModelResponse waitDecision(){return new ModelResponse(AgentDecision.waitForNextMessage(),usage(),null);}
    private static ModelResponse textDecision(String text){return new ModelResponse(AgentDecision.text(text),usage(),null);}
    private static ModelUsage usage(){return new ModelUsage(3,0,3,1,0,"fixture","stress-model",1);}
    private static ConfigurableApplicationContext start(String url){return new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class).web(WebApplicationType.NONE).profiles("runtime-test").properties("spring.main.banner-mode=off","xingchen.integrations.dsh-enabled=false","xingchen.integrations.onebot-enabled=false","xingchen.integrations.model-enabled=false","xingchen.social.enabled=false").run("--spring.datasource.url="+url,"--xingchen.memory.database-path="+url.substring("jdbc:sqlite:".length()),"--xingchen.onebot.login-user-id=test-bot","--xingchen.owner.platform-user-id=owner-test");}
    private static void await(BooleanSupplier condition,long timeoutMillis)throws InterruptedException{long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);assertThat(condition.getAsBoolean()).isTrue();}
}
