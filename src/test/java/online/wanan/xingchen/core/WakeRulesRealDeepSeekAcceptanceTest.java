package online.wanan.xingchen.core;

import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.console.AccessControlService;
import online.wanan.xingchen.console.SocialSettingsService;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import online.wanan.xingchen.core.identity.IdentityRegistry;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.storage.IncomingMessageStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/** Full isolated inbound→access→wake→prompt→real DeepSeek→reply scheduling→Fake OneBot acceptance. */
class WakeRulesRealDeepSeekAcceptanceTest {
    private final List<String> evidence=new ArrayList<>();
    private final Map<String,Integer> categories=new LinkedHashMap<>();
    private int runtimeScenarios;

    @Test @EnabledIfEnvironmentVariable(named="XINGCHEN_RUN_REAL_WAKE_ACCEPTANCE",matches="true")
    void wakePipelineRunsAtLeastSixtyFourRuntimeScenariosAndFiftyRealProviderCalls() throws Exception {
        if(System.getenv("XINGCHEN_DEEPSEEK_API_KEY")==null||System.getenv("XINGCHEN_DEEPSEEK_API_KEY").isBlank())
            throw new IllegalStateException("DeepSeek acceptance credential unavailable");
        Path db=TestSqliteDatabase.create("wake-real-deepseek-acceptance");String url=TestSqliteDatabase.jdbcUrl(db);
        try(ConfigurableApplicationContext context=start(url)){
            var runtime=context.getBean(SocialRuntime.class);var fake=context.getBean(FakeOneBotServer.class);
            var jdbc=context.getBean(JdbcTemplate.class);var provider=(PromptShapeAuditProvider)context.getBean("realDeepSeekAcceptanceProvider");
            var settings=context.getBean(SocialSettingsService.class);var simulation=context.getBean(SimulationStateService.class);
            var identities=context.getBean(IdentityRegistry.class);var access=(DeepSeekWakeAcceptanceConfiguration.CountingWakeAccessControlService)context.getBean(AccessControlService.class);
            try{var field=AgentExecutor.class.getDeclaredField("provider");field.setAccessible(true);assertThat(field.get(context.getBean(AgentExecutor.class))).as("runtime AgentExecutor must use the audited real DeepSeek provider").isSameAs(provider);}
            catch(ReflectiveOperationException e){throw new IllegalStateException("could not verify isolated provider wiring",e);}
            int before;
            assertThat(fake.httpUri().getHost()).isEqualTo("127.0.0.1");assertThat(fake.wsUri().getHost()).isEqualTo("127.0.0.1");
            runtime.start();await(()->fake.wsConnections()>0,5000);assertThat(runtime.isRunning()).isTrue();

            String smokeMode=System.getenv("XINGCHEN_WAKE_SMOKE_ONLY");
            if("1".equals(smokeMode)||"2".equals(smokeMode)||"3".equals(smokeMode)){
                allowGroup(jdbc,"smoke-lane");if("2".equals(smokeMode))updateSettings(context.getBean(SocialSettingsService.class),Map.of("ordinaryMessageProbability",0.5));
                int smokeBefore=provider.successes();emit(fake,"49999",1,"smoke-lane","smoke-user", "2".equals(smokeMode)?"ordinary":"3".equals(smokeMode)?"name":"mention",null);
                awaitModelTurn(jdbc,provider,smokeBefore,"49999");return;
            }

            // Diagnostic gate: 10 isolated, deterministic explicit mentions. Do not start the
            // larger acceptance batch until every boundary can explain any missing provider call.
            if("1".equals(System.getenv("XINGCHEN_WAKE_DIAG_MENTION"))) {
                for(int i=0;i<10;i++)allowGroup(jdbc,"diag-mention-"+i);
                updateSettings(settings,Map.of("ordinaryMessageProbability",0.0));
                List<String> traces=new ArrayList<>();Set<String> eventIds=new HashSet<>();int anomalies=0;
                for(int i=0;i<10;i++) {
                    String id=eventId("560",i),group="diag-mention-"+i;
                    assertThat(eventIds.add(id)).as("diagnostic inbound event ids are unique").isTrue();
                    int attemptsBefore=provider.attempts(),successesBefore=provider.successes(),failuresBefore=provider.failures();
                    int accessBefore=access.evaluations(),sendsBefore=fake.appliedSendCount();
                    int shapesBefore=provider.shapes().size();
                    emit(fake,id,300+i,group,"diag-user-"+i,"mention",null);
                    await(()->Set.of("COMPLETED","FAILED").contains(status(jdbc,id)),190_000);
                    int attemptDelta=provider.attempts()-attemptsBefore;
                    Map<String,Object> agent=agentTrace(jdbc,id);
                    boolean promptShape=provider.shapes().size()>shapesBefore;
                    String classification=classify(status(jdbc,id),agent,attemptDelta,promptShape,fake.appliedSendCount()-sendsBefore);
                    if(attemptDelta==0)anomalies++;
                    traces.add("{\"case\":"+i+",\"eventId\":"+jsonString(id)+",\"accessEvaluations\":"+(access.evaluations()-accessBefore)
                            +",\"wakeReasons\":"+jsonString(safeWakeReasons(jdbc,id))+",\"journal\":"+jsonString(safeJournal(jdbc,id))+",\"agentTrace\":"+jsonString(agent.getOrDefault("status","PRESENT"))
                            +",\"modelCallCount\":"+agent.getOrDefault("calls",-1)+",\"agentDecision\":"+jsonString(agent.getOrDefault("decision","UNKNOWN"))
                            +",\"providerAttemptDelta\":"+attemptDelta+",\"providerSuccessDelta\":"+(provider.successes()-successesBefore)
                            +",\"providerFailureDelta\":"+(provider.failures()-failuresBefore)+",\"promptShapeAdded\":"+promptShape
                            +",\"outboundDelta\":"+(fake.appliedSendCount()-sendsBefore)+",\"classification\":"+jsonString(classification)+"}");
                }
                Path diagnostic=Path.of(System.getProperty("user.dir"),"wake-anomaly-trace-rerun.jsonl");
                Files.write(diagnostic,traces,java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
                assertThat(anomalies).as("all 10 explicit mention events must reach the audited real provider").isZero();
                return;
            }

            allowGroup(jdbc,"scenario-lane");allowGroup(jdbc,"sleep-lane");allowGroup(jdbc,"self-lane");allowGroup(jdbc,"system-lane");
            allowGroup(jdbc,"dedup-lane");allowGroup(jdbc,"random-zero");allowGroup(jdbc,"random-full");
            for(int i=0;i<20;i++)allowGroup(jdbc,"random-"+i);
            for(int i=0;i<5;i++)allowGroup(jdbc,"explicit-"+i);
            for(int i=0;i<6;i++)allowGroup(jdbc,"wait-"+i);
            for(int i=0;i<60;i++)allowGroup(jdbc,"calls-"+i);
            jdbc.update("INSERT INTO console_access_rules(scope,platform,stable_id,effect,updated_at,updated_by) VALUES('GROUP','QQ','denied-lane','DENY',?,'wake-acceptance')",Instant.now().toString());
            updateSettings(settings,Map.of("ordinaryMessageProbability",0.5));

            // Twenty actual runtime inbound events at p=.5. The isolated RNG is seeded to 10 hits / 10 misses.
            for(int i=0;i<20;i++){String id="500"+String.format("%02d",i),group="random-"+i;int attemptBefore=provider.attempts();String message="今天天气有些风，随机场景编号"+i+"。";emit(fake,id,i+1,group,"ordinary-user","ordinary",message);awaitRandomProcessed(jdbc,provider,attemptBefore,id,group);runtimeScenarios++;categories.merge("Random Wake",1,Integer::sum);}
            Set<String> randomConversationHashes=new HashSet<>();for(int i=0;i<20;i++)randomConversationHashes.add(sha256("random-"+i));
            int randomHits=(int)provider.shapes().stream().map(PromptShapeAuditProvider.Shape::conversationSha256).filter(randomConversationHashes::contains).distinct().count();
            assertThat(randomHits).as("ordinary p=.5 full runtime inbound hits").isEqualTo(10);

            // Runtime probability endpoints, including forced configured speaker independent of random probability.
            updateSettings(settings,Map.of("ordinaryMessageProbability",0.0));
            before=provider.successes();emit(fake,"51000",21,"random-zero","ordinary-zero","ordinary",null);awaitProcessed(jdbc,"51000");runtimeScenarios++;assertThat(provider.successes()).isEqualTo(before);categories.put("Random 0%",1);
            updateSettings(settings,Map.of("ordinaryMessageProbability",1.0));
            before=provider.successes();emit(fake,"51001",22,"random-full","ordinary-full","ordinary",null);awaitModelTurn(jdbc,provider,before,"51001");runtimeScenarios++;categories.put("Random 100%",1);
            updateSettings(settings,Map.of("ordinaryMessageProbability",0.0,"configuredSpeakerIds",List.of("QQ:configured-speaker")));
            before=provider.successes();emit(fake,"51002",23,"scenario-lane","configured-speaker","ordinary",null);awaitModelTurn(jdbc,provider,before,"51002");runtimeScenarios++;categories.put("Configured Speaker",1);

            // Explicit wake matrix: each one crosses inbound normalization, AccessControl, trigger, prompt, real provider, and outbound scheduling.
            String[][] explicit={{"mention","mention-user","mention"},{"reply","reply-user","reply"},{"poke","poke-user","poke"},{"bot-name","name-user","name"},{"question","question-user","question"}};
            for(int i=0;i<explicit.length;i++){String id="5110"+i,group="explicit-"+i;String json=payload(id,30+i,group,explicit[i][1],explicit[i][0],null);
                var conversation=identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,group)).id();
                var normalized=context.getBean(online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer.class).normalize(json,List.of("owner-test"));
                var policyDecision=context.getBean(SocialTriggerPolicy.class).evaluate(normalized,simulation.configuredSpeakers(simulation.get(conversation)),settings.capture(conversation,simulation.get(conversation).wakeConfig()));
                String expectedReason=switch(explicit[i][0]){case "mention"->"mention";case "reply"->"reply-to-bot";case "poke"->"poke";case "bot-name"->"bot-name";default->"question";};
                assertThat(policyDecision.reasons()).as("runtime policy preflight for isolated "+expectedReason).contains(expectedReason);
                before=provider.successes();fake.emit(json);awaitModelTurn(jdbc,provider,before,id);runtimeScenarios++;categories.merge(switch(explicit[i][0]){case "mention"->"Mention";case "reply"->"Reply";case "poke"->"Poke";case "bot-name"->"Bot Name";default->"Question";},1,Integer::sum);}

            // Sleep blocks ordinary traffic; explicit mention wakes the same isolated lane.
            UUID sleepConversation=identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"sleep-lane")).id();
            simulation.sleep(sleepConversation,null,true);before=provider.successes();emit(fake,"51200",40,"sleep-lane","sleep-user","ordinary",null);awaitProcessed(jdbc,"51200");runtimeScenarios++;categories.put("Sleep",2);assertThat(provider.successes()).isEqualTo(before);
            before=provider.successes();emit(fake,"51201",41,"sleep-lane","sleep-user","mention",null);awaitModelTurn(jdbc,provider,before,"51201");runtimeScenarios++;

            // WAIT's current generic-next-message behavior is checked on six isolated conversations.
            String[] waitKinds={"mention","reply","poke","owner","configured-speaker","ordinary"};boolean waitConflict=false;
            updateSettings(settings,Map.of("ordinaryMessageProbability",0.0,"configuredSpeakerIds",List.of("QQ:wait-speaker")));
            for(int i=0;i<waitKinds.length;i++){
                String group="wait-"+i,user=i==3?"owner-test":i==4?"wait-speaker":"wait-user-"+i,id="5200"+i;
                UUID conversation=identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,group)).id();
                simulation.waitForNextMessage(conversation,"wait-origin-"+i,"next-message");int attemptsBefore=provider.attempts(),shapesBefore=provider.shapes().size();
                emit(fake,id,50+i,group,user,waitKinds[i],null);awaitProviderAttempt(jdbc,provider,attemptsBefore,shapesBefore,id);runtimeScenarios++;
                String reasons=jdbc.queryForObject("SELECT wake_reasons_json FROM durable_turn_executions WHERE incoming_event_id=?",String.class,id);
                assertThat(reasons).contains("WAIT_CONTINUATION");
                if(Set.of("mention","reply","poke").contains(waitKinds[i])&&!reasons.contains(waitKinds[i].equals("reply")?"reply-to-bot":waitKinds[i]))waitConflict=true;
                categories.merge("WAIT",1,Integer::sum);
            }
            assertThat(waitConflict).as("explicit wake signals should survive WAIT continuation evaluation").isFalse();

            // Fail-closed access, owner allowance, duplicate idempotence, self/system/invalid event classes.
            before=provider.successes();int accessBefore=access.evaluations();emit(fake,"53000",60,"denied-lane","denied-user","mention",null);await(()->access.evaluations()>accessBefore,5000);runtimeScenarios++;categories.put("AccessControl",1);assertThat(provider.successes()).isEqualTo(before);assertThat(access.evaluate("QQ","denied-user","GROUP","denied-lane",false).allowed()).isFalse();
            before=provider.successes();emitPrivateOwner(fake,"53001");awaitModelTurn(jdbc,provider,before,"53001");runtimeScenarios++;categories.put("Owner",1);
            before=provider.successes();String duplicate=payload("53002",61,"dedup-lane","dedup-user","mention",null);fake.emit(duplicate);awaitModelTurn(jdbc,provider,before,"53002");int once=provider.successes();fake.emit(duplicate);Thread.sleep(300);assertThat(provider.successes()).isEqualTo(once);runtimeScenarios+=2;categories.put("Dedup",2);
            before=provider.successes();emit(fake,"53003",62,"self-lane","test-bot","mention",null);awaitProcessed(jdbc,"53003");runtimeScenarios++;assertThat(provider.successes()).isEqualTo(before);categories.put("Self/System/Invalid",3);
            emitSystem(fake,"system-lane");Thread.sleep(250);runtimeScenarios++;assertThat(provider.successes()).isEqualTo(before);
            fake.emit("{");Thread.sleep(250);runtimeScenarios++;

            // Add ordinary full runtime calls until 50 real provider responses have succeeded.
            int explicitCallIndex=0;Set<String> providerFillEventIds=new HashSet<>();
            while(provider.successes()<50){String id=eventId("540",explicitCallIndex),group="calls-"+explicitCallIndex,user="api-user-"+explicitCallIndex;assertThat(providerFillEventIds.add(id)).as("provider top-up event ids are unique").isTrue();var conversation=identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,group)).id();
                String json=payload(id,100+explicitCallIndex,group,user,"mention",null);var normalized=context.getBean(online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer.class).normalize(json,List.of("owner-test"));
                assertThat(context.getBean(SocialTriggerPolicy.class).evaluate(normalized,simulation.configuredSpeakers(simulation.get(conversation)),settings.capture(conversation,simulation.get(conversation).wakeConfig())).reasons()).contains("mention");
                assertThat(access.evaluate("QQ",user,"GROUP",group,false).allowed()).isTrue();int attemptsBefore=provider.attempts(),shapesBefore=provider.shapes().size();fake.emit(json);awaitProviderAttempt(jdbc,provider,attemptsBefore,shapesBefore,id);runtimeScenarios++;categories.merge("Mention",1,Integer::sum);explicitCallIndex++;if(explicitCallIndex>100)throw new AssertionError("real provider requests did not reach 50 within bounded isolated input");}
            // Ensure coverage count is independent of whether an earlier provider response used multiple tool rounds.
            updateSettings(settings,Map.of("ordinaryMessageProbability",0.0));
            int coverageIndex=0;
            while(runtimeScenarios<64){String group="coverage-"+coverageIndex,id=eventId("550",coverageIndex);allowGroup(jdbc,group);int attemptsBefore=provider.attempts();emit(fake,id,200+coverageIndex,group,"coverage-user","ordinary",null);awaitProcessed(jdbc,id);assertThat(provider.attempts()).isEqualTo(attemptsBefore);runtimeScenarios++;categories.merge("Random 0%",1,Integer::sum);coverageIndex++;}

            assertThat(runtimeScenarios).isGreaterThanOrEqualTo(64);assertThat(provider.attempts()).isGreaterThanOrEqualTo(50);assertThat(provider.successes()).as("real provider returned at least fifty successful responses").isGreaterThanOrEqualTo(50);
            assertThat(categories).containsEntry("Random Wake",20).containsEntry("WAIT",6).containsEntry("Sleep",2).containsEntry("AccessControl",1);
            assertThat(fake.appliedSendCount()).as("model replies traversed the intercepted OneBot outbound adapter").isGreaterThan(0);
            assertThat(provider.shapes()).hasSizeGreaterThanOrEqualTo(20);
            var firstTwenty=provider.shapes().subList(0,20);
            assertThat(firstTwenty).allSatisfy(s->{assertThat(s.personaPresent()).isTrue();assertThat(s.simulationPresent()).isTrue();assertThat(s.identityPresent()).isTrue();assertThat(s.runtimeContextPresent()).isTrue();assertThat(s.currentMessagePresent()).isTrue();});
            assertThat(firstTwenty).allSatisfy(s->{assertThat(s.personaDuplicate()).isFalse();assertThat(s.simulationDuplicate()).isFalse();});
            writeEvidence(provider,randomHits,waitConflict);
            assertThat(runtime.isRunning()).isTrue();
        }finally{TestSqliteDatabase.clean(db);}
    }

    private static ConfigurableApplicationContext start(String url){return new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class,DeepSeekWakeAcceptanceConfiguration.class)
            .web(WebApplicationType.NONE).profiles("runtime-test").properties("spring.main.banner-mode=off","logging.level.root=ERROR","logging.level.online.wanan.xingchen=ERROR",
                    "xingchen.test.allow-all-access=false","xingchen.integrations.dsh-enabled=false","xingchen.integrations.onebot-enabled=false","xingchen.integrations.model-enabled=false","xingchen.social.enabled=false")
            .run("--spring.datasource.url="+url,"--xingchen.memory.database-path="+url.substring("jdbc:sqlite:".length()),"--xingchen.onebot.login-user-id=test-bot","--xingchen.owner.platform-user-id=owner-test");}
    private void updateSettings(SocialSettingsService service,Map<String,Object> values){var state=service.global();long revision=((Number)state.get("revision")).longValue();service.updateGlobal(revision,values,"wake-acceptance");}
    private static void allowGroup(JdbcTemplate jdbc,String group){jdbc.update("INSERT OR IGNORE INTO console_access_rules(scope,platform,stable_id,effect,updated_at,updated_by) VALUES('GROUP','QQ',?,'ALLOW',?,'wake-acceptance')",group,Instant.now().toString());}
    private void emit(FakeOneBotServer fake,String id,int seq,String group,String user,String kind,String text)throws Exception{fake.emit(payload(id,seq,group,user,kind,text));}
    private static String payload(String id,int seq,String group,String user,String kind,String text){
        if(kind.equals("poke"))return "{\"post_type\":\"notice\",\"notice_type\":\"notify\",\"sub_type\":\"poke\",\"message_type\":\"group\",\"self_id\":\"test-bot\",\"user_id\":\""+user+"\",\"group_id\":\""+group+"\",\"target_id\":\"test-bot\",\"time\":100000005,\"message_id\":\""+id+"\",\"message_seq\":"+seq+"}";
        String segment=switch(kind){case "mention"->"{\"type\":\"at\",\"data\":{\"qq\":\"test-bot\"}},{\"type\":\"text\",\"data\":{\"text\":\""+escape(text==null?"请直接对我说一句简短问候，不需要查询或调用工具。":text)+"\"}}";
            case "reply"->"{\"type\":\"reply\",\"data\":{\"id\":\"bot-source\",\"user_id\":\"test-bot\",\"nickname\":\"Bot\"}},{\"type\":\"text\",\"data\":{\"text\":\""+escape(text==null?"请直接接一句简短自然的话，不需要查询或调用工具。":text)+"\"}}";
            case "name","bot-name"->"{\"type\":\"text\",\"data\":{\"text\":\"SyntheticBot，你好，请简短回复。\"}}";
            case "question"->"{\"type\":\"text\",\"data\":{\"text\":\""+escape(text==null?"今天过得怎么样？":text)+"\"}}";
            default->"{\"type\":\"text\",\"data\":{\"text\":\""+escape(text==null?"今天有点微风。":text)+"\"}}";};
        return "{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"test-bot\",\"user_id\":\""+user+"\",\"group_id\":\""+group+"\",\"time\":100000005,\"message_id\":\""+id+"\",\"message_seq\":"+seq+",\"sender\":{\"user_id\":\""+user+"\",\"nickname\":\"Tester\",\"card\":\"\",\"role\":\"member\"},\"message\":["+segment+"]}";
    }
    private static void emitPrivateOwner(FakeOneBotServer fake,String id)throws Exception{fake.emit("{\"post_type\":\"message\",\"message_type\":\"private\",\"self_id\":\"test-bot\",\"user_id\":\"owner-test\",\"time\":100000005,\"message_id\":\""+id+"\",\"sender\":{\"user_id\":\"owner-test\",\"nickname\":\"Owner\"},\"message\":[{\"type\":\"text\",\"data\":{\"text\":\"你好，请简短回复。\"}}]}");}
    private static void emitSystem(FakeOneBotServer fake,String group)throws Exception{fake.emit("{\"post_type\":\"notice\",\"notice_type\":\"meta_event\",\"sub_type\":\"heartbeat\",\"message_type\":\"group\",\"self_id\":\"test-bot\",\"operator_id\":\"system-actor\",\"group_id\":\""+group+"\",\"time\":100000006,\"message_id\":\"system-53004\"}");}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    private static String eventId(String prefix,int index){return String.format(Locale.ROOT,"%s%02d",prefix,index);}
    private static String jsonString(Object value){return "\""+Objects.toString(value,"").replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")+"\"";}
    private static void awaitModelTurn(JdbcTemplate jdbc,PromptShapeAuditProvider p,int previous,String id)throws InterruptedException{int failuresBefore=p.failures();boolean done=awaitValue(()->"COMPLETED".equals(status(jdbc,id))||p.failures()>failuresBefore,90_000);assertThat(done).as("turn="+id+" status="+status(jdbc,id)+" reasons="+safeWakeReasons(jdbc,id)+" state="+safeWakeState(jdbc,id)+" journal="+safeJournal(jdbc,id)+" attempts="+p.attempts()+" successes="+p.successes()+" failures="+p.failures()+" sanitized="+p.lastFailure()).isTrue();assertThat(p.failures()).as("sanitized real provider status: "+p.lastFailure()).isEqualTo(failuresBefore);assertThat(status(jdbc,id)).as("inbound turn completion").isEqualTo("COMPLETED");assertThat(p.successes()).as("wake should reach real provider; reasons="+safeWakeReasons(jdbc,id)+" state="+safeWakeState(jdbc,id)+" journal="+safeJournal(jdbc,id)).isGreaterThan(previous);}
    private static void awaitProcessed(JdbcTemplate jdbc,String id)throws InterruptedException{await(()->"COMPLETED".equals(status(jdbc,id)),10_000);}
    private static void awaitRandomProcessed(JdbcTemplate jdbc,PromptShapeAuditProvider p,int attemptsBefore,String id,String group)throws InterruptedException{await(()->Set.of("COMPLETED","FAILED").contains(status(jdbc,id)),190_000);if(providerReached(p,group))assertThat(p.attempts()).isGreaterThan(attemptsBefore);else assertThat(status(jdbc,id)).as("no-wake inbound completes without a real provider request").isEqualTo("COMPLETED");}
    private static void awaitProviderAttempt(JdbcTemplate jdbc,PromptShapeAuditProvider p,int attemptsBefore,int shapesBefore,String id)throws InterruptedException{await(()->Set.of("COMPLETED","FAILED").contains(status(jdbc,id)),190_000);assertThat(p.attempts()).as("real provider attempt for this sequential inbound turn; journal="+safeJournal(jdbc,id)).isGreaterThan(attemptsBefore);assertThat(p.shapes().size()).as("in-memory structural prompt audit for this sequential inbound turn").isGreaterThan(shapesBefore);}
    private static boolean providerReached(PromptShapeAuditProvider provider,String group){return provider.shapes().stream().anyMatch(s->s.conversationSha256().equals(sha256(group)));}
    private static String status(JdbcTemplate jdbc,String id){try{return jdbc.queryForObject("SELECT status FROM inbound_turn_executions WHERE incoming_event_id=?",String.class,id);}catch(Exception ignored){return "";}}
    private static String safeWakeReasons(JdbcTemplate jdbc,String id){try{return jdbc.queryForObject("SELECT wake_reasons_json FROM durable_turn_executions WHERE incoming_event_id=?",String.class,id);}catch(Exception ignored){return "NO_DURABLE_WAKE_RECORD";}}
    private static String safeWakeState(JdbcTemplate jdbc,String id){try{return jdbc.queryForObject("SELECT s.wake_state FROM simulation_conversation_state s JOIN durable_turn_executions d ON d.conversation_id=s.conversation_id WHERE d.incoming_event_id=?",String.class,id);}catch(Exception ignored){return "UNKNOWN";}}
    private static String safeJournal(JdbcTemplate jdbc,String id){try{return jdbc.queryForObject("SELECT 'inbound='||i.status||';turn='||d.status||';model='||d.model_turn_state||';output='||COALESCE(d.output_type,'NONE')||';reason='||COALESCE(d.wake_reasons_json,'NONE') FROM durable_turn_executions d JOIN inbound_turn_executions i ON i.execution_key=d.inbound_execution_key WHERE d.incoming_event_id=?",String.class,id);}catch(Exception ignored){return "NO_DURABLE_TURN";}}
    private static Map<String,Object> agentTrace(JdbcTemplate jdbc,String eventId){
        try{return jdbc.queryForMap("SELECT model_call_count AS calls,decision FROM agent_traces WHERE event_id=? ORDER BY created_at DESC LIMIT 1",eventId);}
        catch(Exception ignored){return Map.of("status","NO_TRACE");}
    }
    private static String classify(String inbound,Map<String,Object> trace,int attempts,boolean shape,int outbound){
        if(!Set.of("COMPLETED","FAILED").contains(inbound))return "INBOUND_NOT_TERMINAL";
        if(trace.containsKey("status"))return attempts==0?"NO_AGENT_TRACE_NO_PROVIDER_ATTEMPT":shape?"PROVIDER_AUDIT_WITHOUT_AGENT_TRACE":"NO_AGENT_TRACE";
        if(attempts==0)return "AGENT_TRACE_WITHOUT_PROVIDER_ATTEMPT";
        if(((Number)trace.getOrDefault("calls",0)).intValue()==0)return "AGENT_STOPPED_BEFORE_MODEL_CALL";
        if("NO_REPLY".equals(trace.get("decision")))return "PROVIDER_NO_REPLY";
        if(outbound>0)return "OUTBOUND_SENT";
        return "PROVIDER_REPLIED_NO_OUTBOUND";
    }
    private static void await(BooleanSupplier condition,long timeoutMillis)throws InterruptedException{long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(20);assertThat(condition.getAsBoolean()).as("runtime event/provider completion").isTrue();}
    private static boolean awaitValue(BooleanSupplier condition,long timeoutMillis)throws InterruptedException{long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(20);return condition.getAsBoolean();}
    private void writeEvidence(PromptShapeAuditProvider provider,int randomHits,boolean waitConflict)throws Exception{
        Path report=Path.of(System.getProperty("user.dir"),"wake-test-results.jsonl");List<String> lines=new ArrayList<>();
        for(var s:provider.shapes().subList(0,20))lines.add("{\"kind\":\"real-deepseek-prompt-shape\",\"requestNumber\":"+s.requestNumber()+",\"personaPresent\":"+s.personaPresent()+",\"personaChars\":"+s.personaChars()+",\"personaSha256\":\""+s.personaSha256()+"\",\"simulationPresent\":"+s.simulationPresent()+",\"simulationChars\":"+s.simulationChars()+",\"simulationSha256\":\""+s.simulationSha256()+"\",\"identityPresent\":"+s.identityPresent()+",\"runtimeContextPresent\":"+s.runtimeContextPresent()+",\"currentMessagePresent\":"+s.currentMessagePresent()+",\"personaDuplicate\":"+s.personaDuplicate()+",\"simulationDuplicate\":"+s.simulationDuplicate()+",\"chars\":"+s.chars()+",\"sha256\":\""+s.sha256()+"\",\"currentMessageHash\":\""+s.currentMessageSha256()+"\",\"conversationHash\":\""+s.conversationSha256()+"\"}");
        String categoryJson=categories.entrySet().stream().map(e->jsonString(e.getKey())+":"+e.getValue()).collect(java.util.stream.Collectors.joining(","));
        lines.add("{\"kind\":\"real-deepseek-wake-acceptance\",\"runtimeScenarios\":"+runtimeScenarios+",\"apiAttempts\":"+provider.attempts()+",\"apiSuccesses\":"+provider.successes()+",\"random50Hits\":"+randomHits+",\"random50Total\":20,\"waitExplicitWakeConflict\":"+waitConflict+",\"categories\":{"+categoryJson+"},\"outboundIntercepted\":true,\"productionModified\":false,\"productionOneBotUsed\":false}");
        Files.write(report,lines,java.nio.charset.StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
    }
    private static String sha256(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("test hash unavailable");}}
}
