package online.wanan.xingchen.core;

import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import online.wanan.xingchen.core.identity.IdentityRegistry;
import online.wanan.xingchen.core.memory.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.relationship.*;
import online.wanan.xingchen.storage.IncomingMessageStore;
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

/** Integrated final evidence over the real OneBot normalization and SocialRuntime graph with local protocol/model fakes. */
class SocialRuntimeFinalEvidenceE2ETest {
    @Test void r2final002_003_004_005_wakeSourcesResolveOneTurnAndSpeakerUsesStableId() throws Exception {
        Path db=TestSqliteDatabase.create("final-wake-sources");String url=TestSqliteDatabase.jdbcUrl(db);
        try(ConfigurableApplicationContext context=start(url,"--xingchen.prompt.simulation=SIMULATION_FINAL_WAKE","--xingchen.prompt.persona=PERSONA_FINAL_WAKE")){
            var runtime=context.getBean(SocialRuntime.class);var fake=context.getBean(FakeOneBotServer.class);var simulation=context.getBean(SimulationStateService.class);var identities=context.getBean(IdentityRegistry.class);var provider=context.getBean(MockModelProvider.class);var jdbc=context.getBean(JdbcTemplate.class);
            runtime.start();await(()->fake.wsConnections()>0,5000);

            UUID mentionConversation=sleep(context,"group-mention");provider.enqueue(reply("mention response"));
            fake.emit(group("mention-1", "group-mention", "user-mention", "Member", "", 10,
                    "{\"type\":\"at\",\"data\":{\"qq\":\"test-bot\"}},{\"type\":\"text\",\"data\":{\"text\":\"why?\"}}"));
            await(()->provider.requests().size()==1&&jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE conversation_id=? AND status='SUCCESS'",Integer.class,mentionConversation.toString())==1&&completed(jdbc,"mention-1"),8000);
            assertThat(jdbc.queryForObject("SELECT wake_reasons_json FROM durable_turn_executions WHERE incoming_event_id='mention-1'",String.class)).contains("mention").contains("question");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE incoming_event_id='mention-1'",Integer.class)).isEqualTo(1);assertThat(fake.sendReceiveCount()).isEqualTo(1);
            var mentionContext=provider.requests().getFirst().context();assertThat(mentionContext.simulationPrompt()).isEqualTo("SIMULATION_FINAL_WAKE");assertThat(mentionContext.persona()).isEqualTo("PERSONA_FINAL_WAKE");assertThat(mentionContext.actorId()).isEqualTo("qq:user-mention");assertThat(mentionContext.displayName()).isEqualTo("Member");assertThat(mentionContext.conversationId()).isEqualTo("group-mention");assertThat(mentionContext.currentMessage()).contains("why?");assertThat(mentionContext.taskState().get("simulation.wakeReasons")).contains("mention");assertThat(mentionContext.recentMessages()).anyMatch(s->s.contains("why?"));

            UUID pokeConversation=sleep(context,"group-poke");provider.enqueue(new ModelResponse(AgentDecision.noReply(),usage(),null));
            fake.emit("{\"post_type\":\"notice\",\"notice_type\":\"notify\",\"sub_type\":\"poke\",\"self_id\":\"test-bot\",\"user_id\":\"user-poke\",\"group_id\":\"group-poke\",\"target_id\":\"test-bot\",\"message_id\":\"poke-1\",\"time\":1790000000}");
            await(()->provider.requests().size()==2&&jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE incoming_event_id='poke-1'",Integer.class)==1&&completed(jdbc,"poke-1"),8000);
            assertThat(simulation.get(pokeConversation).wakeState()).isEqualTo(SimulationConversationState.WakeState.AWAKE);assertThat(jdbc.queryForObject("SELECT wake_reasons_json FROM durable_turn_executions WHERE incoming_event_id='poke-1'",String.class)).contains("poke");assertThat(fake.sendReceiveCount()).isEqualTo(1);

            UUID replyConversation=sleep(context,"group-reply");provider.enqueue(reply("reply-to-bot response"));
            fake.emit(group("reply-1","group-reply","user-reply","Member","",20,
                    "{\"type\":\"reply\",\"data\":{\"id\":\"bot-message\",\"user_id\":\"test-bot\",\"nickname\":\"Bot\"}},{\"type\":\"text\",\"data\":{\"text\":\"following up\"}}"));
            await(()->provider.requests().size()==3&&jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE conversation_id=? AND status='SUCCESS'",Integer.class,replyConversation.toString())==1&&completed(jdbc,"reply-1"),8000);
            assertThat(jdbc.queryForObject("SELECT wake_reasons_json FROM durable_turn_executions WHERE incoming_event_id='reply-1'",String.class)).contains("reply-to-bot");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE incoming_event_id='reply-1'",Integer.class)).isEqualTo(1);assertThat(fake.sendReceiveCount()).isEqualTo(2);

            UUID speakerConversation=identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"group-speaker")).id();
            simulation.configure(speakerConversation,Map.of("speakers",List.of("speaker-x")),true);simulation.sleep(speakerConversation,null,true);
            fake.emit(group("speaker-x-event","group-speaker","speaker-x","Same nickname","",30,"{\"type\":\"text\",\"data\":{\"text\":\"ordinary message\"}}"));
            await(()->completed(jdbc,"speaker-x-event"),5000);assertThat(simulation.get(speakerConversation).wakeState()).isEqualTo(SimulationConversationState.WakeState.AWAKE);
            simulation.sleep(speakerConversation,null,true);
            fake.emit(group("speaker-y-event","group-speaker","speaker-y","speaker-x","",31,"{\"type\":\"text\",\"data\":{\"text\":\"ordinary message\"}}"));
            await(()->completed(jdbc,"speaker-y-event"),5000);assertThat(simulation.get(speakerConversation).wakeState()).isEqualTo(SimulationConversationState.WakeState.SLEEPING);
            assertThat(provider.requests()).hasSize(3);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE incoming_event_id IN ('speaker-x-event','speaker-y-event') AND model_turn_state='NOT_STARTED'",Integer.class)).isEqualTo(2);
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void identFinal001_and_r2final010_personAliasesMembershipAndRelationshipSurviveRestart() throws Exception {
        Path db=TestSqliteDatabase.create("final-identity-relationship");String url=TestSqliteDatabase.jdbcUrl(db);
        UUID stablePerson;String userId="123456";
        try(ConfigurableApplicationContext first=start(url,"--xingchen.prompt.simulation=SIMULATION_IDENTITY","--xingchen.prompt.persona=PERSONA_IDENTITY")){
            var identities=first.getBean(IdentityRegistry.class);var terms=first.getBean(RelationshipTermRegistry.class);stablePerson=identities.identify(Platform.QQ,userId).id();UUID bot=identities.identify(Platform.QQ,"test-bot").id();identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"identity-a"));
            Instant at=Instant.now();terms.save(new RelationshipTerm(UUID.randomUUID(),bot,stablePerson,RelationshipType.ADDRESS_AS,"朋友",ScopeType.GLOBAL,"",true,.95,"seed-global",at,at));terms.save(new RelationshipTerm(UUID.randomUUID(),bot,stablePerson,RelationshipType.ADDRESS_AS,"姐姐",ScopeType.CONVERSATION,"identity-a",true,1,"seed-local",at,at));
            var provider=first.getBean(MockModelProvider.class);for(int i=0;i<4;i++)provider.enqueue(new ModelResponse(AgentDecision.noReply(),usage(),null));
            var runtime=first.getBean(SocialRuntime.class);runtime.start();var fake=first.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
            fake.emit(group("identity-a-1","identity-a",userId,"Alice","小A",1,mention("first")));await(()->provider.requests().size()==1&&completed(first.getBean(JdbcTemplate.class),"identity-a-1"),8000);
            fake.emit(group("identity-a-2","identity-a",userId,"Alicia","小A2",2,mention("changed card")));await(()->provider.requests().size()==2&&completed(first.getBean(JdbcTemplate.class),"identity-a-2"),8000);
            fake.emit(group("identity-b-1","identity-b",userId,"Alicia","B卡",1,mention("other group")));await(()->provider.requests().size()==3&&completed(first.getBean(JdbcTemplate.class),"identity-b-1"),8000);
            fake.emit(privateEvent("identity-private-1",1,userId,"Private display","private hello"));await(()->provider.requests().size()==4&&completed(first.getBean(JdbcTemplate.class),"identity-private-1"),8000);
            var requests=provider.requests();assertThat(requests.get(0).context().simulationPrompt()).isEqualTo("SIMULATION_IDENTITY");assertThat(requests.get(0).context().persona()).isEqualTo("PERSONA_IDENTITY");assertThat(requests.get(0).context().relationshipAddress()).isEqualTo("姐姐");assertThat(requests.get(1).context().relationshipAddress()).isEqualTo("姐姐");
            assertThat(requests.get(2).context().relationshipAddress()).isEqualTo("朋友");assertThat(requests.get(3).context().relationshipAddress()).isEqualTo("朋友");
            assertThat(requests.get(0).context().actorId()).isEqualTo("qq:"+userId);assertThat(requests.get(2).context().actorId()).isEqualTo("qq:"+userId);assertThat(requests.get(3).context().actorId()).isEqualTo("qq:"+userId);
            var jdbc=first.getBean(JdbcTemplate.class);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM persons WHERE platform='QQ' AND platform_user_id=?",Integer.class,userId)).isEqualTo(1);
            assertThat(identities.find(Platform.QQ,userId).orElseThrow().id()).isEqualTo(stablePerson);assertThat(identities.aliases(stablePerson)).extracting(online.wanan.xingchen.core.identity.PersonAlias::alias).contains("Alice","Alicia","小A","小A2","B卡","Private display");
            var memberships=identities.memberships(stablePerson);assertThat(memberships).hasSize(3);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memberships m JOIN conversations c ON c.id=m.conversation_id WHERE m.person_id=? AND c.type='GROUP'",Integer.class,stablePerson.toString())).isEqualTo(2);
            assertThat(memberships).filteredOn(m->m.conversationId().equals(identities.identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"identity-a")).id())).singleElement().satisfies(m->assertThat(m.card()).isEqualTo("小A2"));
        }
        try(ConfigurableApplicationContext second=start(url,"--xingchen.prompt.simulation=SIMULATION_IDENTITY","--xingchen.prompt.persona=PERSONA_IDENTITY")){
            var provider=second.getBean(MockModelProvider.class).enqueue(new ModelResponse(AgentDecision.noReply(),usage(),null));var runtime=second.getBean(SocialRuntime.class);runtime.start();var fake=second.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);
            fake.emit(group("identity-a-3","identity-a",userId,"Alicia","小A2",3,mention("after restart")));await(()->provider.requests().size()==1&&completed(second.getBean(JdbcTemplate.class),"identity-a-3"),8000);
            assertThat(provider.requests().getFirst().context().relationshipAddress()).isEqualTo("姐姐");assertThat(provider.requests().getFirst().context().actorId()).isEqualTo("qq:"+userId);
            assertThat(second.getBean(IdentityRegistry.class).find(Platform.QQ,userId).orElseThrow().id()).isEqualTo(stablePerson);
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void r2final006_009_021_memoryToolNoReplyPersistsProvenanceAndNextTurnRetrievesAfterRestart() throws Exception {
        Path db=TestSqliteDatabase.create("final-memory-write-retrieve");String url=TestSqliteDatabase.jdbcUrl(db);String actorId="memory-person-a",conversationId=actorId,origin="memory-write-1";
        var remember=new ModelToolCall("remember-once","memory.remember",Map.of("type","PREFERENCE","content","喜欢茉莉茶","scope","PERSON_GLOBAL","importance",.95));
        try(ConfigurableApplicationContext first=start(url,"--xingchen.prompt.simulation=SIMULATION_MEMORY","--xingchen.prompt.persona=PERSONA_MEMORY")){
            var provider=first.getBean(MockModelProvider.class).enqueue(new ModelResponse(null,null,null,List.of(remember),ModelFinishReason.TOOL_CALLS)).enqueue(new ModelResponse(AgentDecision.noReply(),usage(),null));
            first.getBean(SocialRuntime.class).start();var fake=first.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);fake.emit(privateEvent(origin,1,actorId,"Memory Person","我喜欢茉莉茶"));
            var jdbc=first.getBean(JdbcTemplate.class);await(()->provider.requests().size()==2&&jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status='COMPLETED'",Integer.class,origin)==1,8000);
            assertThat(provider.requests()).hasSize(2);assertThat(fake.sendReceiveCount()).isZero();assertThat(jdbc.queryForObject("SELECT output_type FROM durable_turn_executions WHERE incoming_event_id=?",String.class,origin)).isEqualTo("NO_REPLY");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE turn_id=(SELECT turn_id FROM durable_turn_executions WHERE incoming_event_id=?)",Integer.class,origin)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE content='喜欢茉莉茶' AND scope_type='PERSON_GLOBAL'",Integer.class)).isEqualTo(1);
            UUID person=first.getBean(IdentityRegistry.class).find(Platform.QQ,actorId).orElseThrow().id();UUID conversation=first.getBean(IdentityRegistry.class).identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.PRIVATE,actorId)).id();
            UUID memoryId=UUID.fromString(jdbc.queryForObject("SELECT id FROM memories WHERE content='喜欢茉莉茶'",String.class));var source=first.getBean(MemoryRepository.class).sources(memoryId).getFirst();
            assertThat(source.actorPersonId()).isEqualTo(person);assertThat(source.conversationId()).isEqualTo(conversation);assertThat(source.messageId()).isEqualTo(origin);assertThat(source.timestamp()).isNotNull();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims WHERE event_id=?",Integer.class,origin)).isEqualTo(1);
        }
        try(ConfigurableApplicationContext second=start(url,"--xingchen.prompt.simulation=SIMULATION_MEMORY","--xingchen.prompt.persona=PERSONA_MEMORY")){
            var provider=second.getBean(MockModelProvider.class);var runtime=second.getBean(SocialRuntime.class);runtime.start();var fake=second.getBean(FakeOneBotServer.class);await(()->fake.wsConnections()>0,5000);var jdbc=second.getBean(JdbcTemplate.class);
            provider.enqueue(new ModelResponse(AgentDecision.noReply(),usage(),null));fake.emit(privateEvent(origin,1,actorId,"Memory Person","duplicate origin"));fake.emit(privateEvent("memory-restart-drain",2,actorId,"Memory Person","drain"));
            await(()->provider.requests().size()==1&&completed(jdbc,"memory-restart-drain"),8000);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM durable_turn_executions WHERE incoming_event_id=?",Integer.class,origin)).isEqualTo(1);
            provider.enqueue(new ModelResponse(AgentDecision.noReply(),usage(),null));fake.emit(privateEvent("memory-retrieve-2",3,actorId,"Memory Person","还记得茉莉茶吗？"));
            await(()->provider.requests().size()==2&&completed(jdbc,"memory-retrieve-2"),8000);
            var modelContext=provider.requests().get(1).context();assertThat(modelContext.simulationPrompt()).isEqualTo("SIMULATION_MEMORY");assertThat(modelContext.persona()).isEqualTo("PERSONA_MEMORY");assertThat(modelContext.memories()).anySatisfy(m->assertThat(m.content()).isEqualTo("喜欢茉莉茶"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM memories WHERE content='喜欢茉莉茶'",Integer.class)).isEqualTo(1);assertThat(fake.sendReceiveCount()).isZero();
        }finally{TestSqliteDatabase.clean(db);}
    }

    private static UUID sleep(ConfigurableApplicationContext context,String group){UUID id=context.getBean(IdentityRegistry.class).identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,group)).id();context.getBean(SimulationStateService.class).sleep(id,null,true);return id;}
    private static String mention(String text){return "{\"type\":\"at\",\"data\":{\"qq\":\"test-bot\"}},{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}";}
    private static String group(String messageId,String group,String user,String nickname,String card,long sequence,String segments){return "{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"test-bot\",\"user_id\":\""+user+"\",\"group_id\":\""+group+"\",\"time\":"+Instant.now().getEpochSecond()+",\"message_id\":\""+messageId+"\",\"message_seq\":"+sequence+",\"sender\":{\"user_id\":\""+user+"\",\"nickname\":\""+nickname+"\",\"card\":\""+card+"\",\"role\":\"member\"},\"message\":["+segments+"]}";}
    private static String privateEvent(String messageId,long sequence,String user,String nickname,String text){return "{\"post_type\":\"message\",\"message_type\":\"private\",\"self_id\":\"test-bot\",\"user_id\":\""+user+"\",\"time\":"+Instant.now().getEpochSecond()+",\"message_id\":\""+messageId+"\",\"message_seq\":"+sequence+",\"sender\":{\"user_id\":\""+user+"\",\"nickname\":\""+nickname+"\"},\"message\":[{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]}";}
    private static ModelResponse reply(String text){return new ModelResponse(AgentDecision.text(text),usage(),null);}
    private static ModelUsage usage(){return new ModelUsage(5,0,5,2,0,"fixture","fixture-model",1);}
    private static boolean completed(JdbcTemplate jdbc,String eventId){return jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status='COMPLETED'",Integer.class,eventId)==1;}
    private static ConfigurableApplicationContext start(String url,String... additional){var args=new ArrayList<String>(List.of("--spring.datasource.url="+url,"--xingchen.memory.database-path="+url.substring("jdbc:sqlite:".length()),"--xingchen.onebot.login-user-id=test-bot","--xingchen.owner.platform-user-id=owner-test"));args.addAll(List.of(additional));return new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class).web(WebApplicationType.NONE).profiles("runtime-test").run(args.toArray(String[]::new));}
    private static void await(BooleanSupplier condition,long timeoutMillis)throws InterruptedException{long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(20);assertThat(condition.getAsBoolean()).isTrue();}
}
