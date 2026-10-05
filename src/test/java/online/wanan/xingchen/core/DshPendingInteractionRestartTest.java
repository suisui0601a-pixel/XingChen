package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.core.agent.DshPendingInteractionStore;
import online.wanan.xingchen.core.agent.DshInteractionCoordinator;
import online.wanan.xingchen.adapter.dsh.*;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.relationship.AddressResolver;
import online.wanan.xingchen.core.relationship.RelationshipTermRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DshPendingInteractionRestartTest {
    @Test void pendingQuestionApprovalAndOriginalExpirySurviveARealSpringContextRestart() {
        final Path temp;try{temp=Files.createTempDirectory(Path.of("build").toAbsolutePath(),"dsh-restart-");}catch(java.io.IOException e){throw new IllegalStateException(e);}
        String db=temp.resolve("interaction-restart.sqlite").toAbsolutePath().toString().replace('\\','/');
        String url="jdbc:sqlite:"+db;
        try {
        UUID conversation=UUID.randomUUID();String session="restart-session";Instant now=Instant.now(),persistedExpiry=now.plusSeconds(2);
        ConfigurableApplicationContext first=context(url);
        try {
            JdbcTemplate jdbc=first.getBean(JdbcTemplate.class);jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE","owner-restart-"+conversation,now.toString());
            DshPendingInteractionStore store=first.getBean(DshPendingInteractionStore.class);ObjectMapper mapper=first.getBean(ObjectMapper.class);
            var payload=mapper.createObjectNode();payload.putArray("questions").addObject().put("id","q").put("question","Continue?");
            var question=interaction(UUID.randomUUID().toString(),DshPendingInteractionStore.Kind.QUESTION,session,conversation,"client-A",1,"event-question","qq:owner",now.plusSeconds(600),payload,null,null);
            var approval=interaction(UUID.randomUUID().toString(),DshPendingInteractionStore.Kind.APPROVAL,session,conversation,"client-A",1,"event-approval","qq:owner",now.plusSeconds(600),null,"system.write","reason");
            store.replay(question);store.replay(approval);jdbc.update("UPDATE dsh_pending_interactions SET expires_at=? WHERE event_id='event-question'",persistedExpiry.toString());
        } finally { first.close(); }

        try(ConfigurableApplicationContext second=context(url)) {
            DshPendingInteractionStore restored=second.getBean(DshPendingInteractionStore.class);
            var q=restored.find(session,"event-question").orElseThrow();var a=restored.find(session,"event-approval").orElseThrow();
            assertThat(q.kind()).isEqualTo(DshPendingInteractionStore.Kind.QUESTION);assertThat(q.requiredActorId()).isEqualTo("qq:owner");assertThat(q.conversationId()).isEqualTo(conversation.toString());assertThat(q.clientId()).isEqualTo("client-A");assertThat(q.clientGeneration()).isEqualTo(1);assertThat(q.expiresAt()).isEqualTo(persistedExpiry);
            assertThat(a.kind()).isEqualTo(DshPendingInteractionStore.Kind.APPROVAL);assertThat(a.requiredActorId()).isEqualTo("qq:owner");assertThat(a.status()).isEqualTo("PENDING");
            // Persisted expiry is not extended on restart. Move the deadline behind the store clock boundary,
            // then exercise the due-expiry transition without relying on wall-clock scheduling.
            second.getBean(JdbcTemplate.class).update("UPDATE dsh_pending_interactions SET expires_at=? WHERE event_id='event-question'",Instant.now().minusMillis(1).toString());
            assertThat(restored.expireDue()).isEqualTo(1);assertThat(restored.find(session,"event-question").orElseThrow().status()).isEqualTo("EXPIRED");assertThat(restored.find(session,"event-approval").orElseThrow().status()).isEqualTo("PENDING");
        }
        } finally {online.wanan.xingchen.core.TestSqliteDatabase.clean(Path.of(db));}
    }

    @Test void dshe2e004_005_questionAndApprovalReplayAfterRealSpringRestartRebindAndContinue() throws Exception {
        Path temp=Files.createTempDirectory(Path.of("build").toAbsolutePath(),"dsh-restart-e2e-");String db=temp.resolve("runtime.sqlite").toString().replace('\\','/');String url="jdbc:sqlite:"+db;
        FakeDshRc2Server server=null;
        try {
        String owner="restart-owner-"+UUID.randomUUID().toString().replace("-","");UUID conversation=UUID.randomUUID();String session="restart-e2e-session-"+UUID.randomUUID();String eventId="restart-e2e-event";
        String approvalEvent="restart-e2e-approval";var mapper=new ObjectMapper();server=new FakeDshRc2Server("restart-token");var request=mapper.createObjectNode();request.putArray("questions").addObject().put("id","question-1").put("question","Continue?").putArray("options").addObject().put("label","Yes");server.waterfall(eventId,session,"user-questions/request",request);server.waterfall(approvalEvent,session,"approval/request",mapper.createObjectNode().put("toolName","system.write").put("reason","write file"));server.eventClientId("client-A");
        DshRc2Adapter adapterA=null;DshInteractionCoordinator coordinatorA=null;ConfigurableApplicationContext first=context(url);
        try {
            JdbcTemplate jdbc=first.getBean(JdbcTemplate.class);jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?)",conversation.toString(),"QQ","PRIVATE",owner,Instant.now().toString());
            var mappings=first.getBean(online.wanan.xingchen.storage.SqliteDshSessionMappingRepository.class);Instant now=Instant.now();mappings.save(new DshSessionMapping(UUID.randomUUID(),conversation,"workspace",session,"CLOSED_AGENT","qq-chat","model","policy",1,DshSessionMapping.Status.CURRENT,now,now));
            var normalizer=new OneBotEventNormalizer(mapper);var gateway=new MockOneBotGateway(normalizer,"bot");gateway.connect();adapterA=adapter(server,mapper);var store=first.getBean(DshPendingInteractionStore.class);coordinatorA=new DshInteractionCoordinator(adapterA,mappings,jdbc,store,gateway,mapper,java.time.Clock.systemUTC(),owner);coordinatorA.start();awaitInteraction(store,session,eventId,1);awaitGateway(gateway,2);
            assertThat(gateway.sent()).hasSize(2);assertThat(store.find(session,eventId).orElseThrow().status()).isEqualTo("PENDING");assertThat(store.find(session,approvalEvent).orElseThrow().requiredActorId()).isEqualTo("qq:"+owner);gateway.disconnect();
        } finally {if(coordinatorA!=null)coordinatorA.close();if(adapterA!=null)adapterA.close();first.close();}

        server.eventClientId("client-B");ConfigurableApplicationContext second=context(url);DshRc2Adapter adapterB=null;DshInteractionCoordinator coordinatorB=null;MockOneBotGateway gatewayB=null;
        try {
            JdbcTemplate jdbc=second.getBean(JdbcTemplate.class);var mappings=second.getBean(online.wanan.xingchen.storage.SqliteDshSessionMappingRepository.class);var store=second.getBean(DshPendingInteractionStore.class);
            var before=store.find(session,eventId).orElseThrow();assertThat(before.clientId()).isEqualTo("client-A");assertThat(before.clientGeneration()).isEqualTo(1);
            var normalizer=new OneBotEventNormalizer(mapper);gatewayB=new MockOneBotGateway(normalizer,"bot");gatewayB.connect();adapterB=adapter(server,mapper);
            var identities=new IdentityResolver(new IdentityRegistry("bot",List.of(owner),second.getBean(IdentityPersistence.class)),new AddressResolver(),second.getBean(RelationshipTermRegistry.class),"bot");
            coordinatorB=new DshInteractionCoordinator(adapterB,mappings,jdbc,store,gatewayB,mapper,java.time.Clock.systemUTC(),owner);coordinatorB.start();var rebound=awaitInteraction(store,session,eventId,2);var approval=awaitInteraction(store,session,approvalEvent,2);
            assertThat(rebound.clientId()).isEqualTo("client-B");assertThat(rebound.clientGeneration()).isEqualTo(2);assertThat(approval.requiredActorId()).isEqualTo("qq:"+owner);assertThat(approval.clientGeneration()).isEqualTo(2);assertThat(gatewayB.sent()).isEmpty();
            var router=new online.wanan.xingchen.core.agent.HumanInteractionRouter(identities,store,adapterB,gatewayB,new DshRc2InteractionOutcomeEncoder(mapper),new online.wanan.xingchen.core.agent.QuestionAnswerParser(),mapper,java.time.Clock.systemUTC());
            String questionCode=rebound.id().replace("-","").substring(0,6),approvalCode=approval.id().replace("-","").substring(0,6);
            assertThat(router.route(normalizer.normalize(privateMessage(owner,"#"+questionCode+" Yes"),List.of(owner)))).isTrue();awaitResult(server);var args=server.requests("$events/result").getFirst().envelope().path("payload").path("args");assertThat(args.path("clientId").asText()).isEqualTo("client-B");assertThat(store.find(session,eventId).orElseThrow().status()).isEqualTo("ANSWERED");
            String intruder="restart-member-"+UUID.randomUUID();assertThat(router.route(normalizer.normalize(groupMessage(intruder,"通过"),List.of(owner)))).isFalse();assertThat(server.requests("$events/result")).hasSize(1);
            assertThat(router.route(normalizer.normalize(privateMessage(owner,"通过 #"+approvalCode),List.of(owner)))).isTrue();long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);while(server.requests("$events/result").size()<2&&System.nanoTime()<end)Thread.sleep(20);assertThat(server.requests("$events/result")).hasSize(2);assertThat(server.requests("$events/result").get(1).envelope().path("payload").path("args").path("outcome").path("value").asText()).isEqualTo("allowed-once");assertThat(store.find(session,approvalEvent).orElseThrow().status()).isEqualTo("APPROVED");
        } finally {if(coordinatorB!=null)coordinatorB.close();if(adapterB!=null)adapterB.close();if(gatewayB!=null)gatewayB.disconnect();second.close();}
        } finally {if(server!=null)server.close();online.wanan.xingchen.core.TestSqliteDatabase.clean(Path.of(db));}
    }

    private ConfigurableApplicationContext context(String url) {
        return new SpringApplicationBuilder(XingChenApplication.class).web(WebApplicationType.NONE).properties(
                "spring.main.banner-mode=off","xingchen.integrations.dsh-enabled=false","xingchen.integrations.onebot-enabled=false","xingchen.integrations.model-enabled=false","xingchen.social.enabled=false")
                .run("--spring.datasource.url="+url,"--xingchen.memory.database-path="+url.substring("jdbc:sqlite:".length()));
    }
    private static DshRc2Adapter adapter(FakeDshRc2Server server,ObjectMapper mapper){return new DshRc2Adapter(new DshRc2Configuration(server.baseUri(),Path.of("build").toAbsolutePath(),"restart-token","deepseek-official",java.time.Duration.ofSeconds(2)),mapper);}
    private static DshPendingInteractionStore.Interaction awaitInteraction(DshPendingInteractionStore store,String session,String event,long generation)throws InterruptedException{long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(6);DshPendingInteractionStore.Interaction item=null;while(System.nanoTime()<until){item=store.find(session,event).orElse(null);if(item!=null&&item.clientGeneration()>=generation)return item;Thread.sleep(20);}return item;}
    private static void awaitResult(FakeDshRc2Server server)throws InterruptedException{long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);while(server.requests("$events/result").isEmpty()&&System.nanoTime()<until)Thread.sleep(20);assertThat(server.requests("$events/result")).hasSize(1);}
    private static void awaitGateway(MockOneBotGateway gateway,int count)throws InterruptedException{long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);while(gateway.sent().size()<count&&System.nanoTime()<until)Thread.sleep(20);assertThat(gateway.sent()).hasSize(count);}
    private static String privateMessage(String user,String text){return "{\"post_type\":\"message\",\"message_type\":\"private\",\"user_id\":\""+user+"\",\"self_id\":\"bot\",\"message_id\":\"restart-"+UUID.randomUUID()+"\",\"sender\":{\"user_id\":\""+user+"\",\"nickname\":\"owner\"},\"message\":[{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]}";}
    private static String groupMessage(String user,String text){return "{\"post_type\":\"message\",\"message_type\":\"group\",\"group_id\":\"foreign-group\",\"user_id\":\""+user+"\",\"self_id\":\"bot\",\"message_id\":\"group-"+UUID.randomUUID()+"\",\"sender\":{\"user_id\":\""+user+"\",\"nickname\":\"member\"},\"message\":[{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]}";}
    private static DshPendingInteractionStore.Interaction interaction(String id,DshPendingInteractionStore.Kind kind,String session,UUID conversation,String client,long generation,String event,String actor,Instant expires,JsonNode questions,String tool,String reason){Instant now=Instant.now();return new DshPendingInteractionStore.Interaction(id,kind,session,conversation.toString(),client,generation,event,actor,expires,"PENDING",questions,tool,null,reason,now,now);}
}
