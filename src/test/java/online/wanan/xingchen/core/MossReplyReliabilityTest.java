package online.wanan.xingchen.core;

import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.ConversationOrchestrator;
import online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic model + loopback HTTP transport; never uses a live account/provider. */
class MossReplyReliabilityTest {
    @Test void rateRejectionsAreDurableAndReplayDoesNotConsumeMoreQuotaOrSendAgain() {
        var db=TestSqliteDatabase.create("moss-durable-rate");
        try(var app=start(TestSqliteDatabase.jdbcUrl(db))){
            var actor=app.getBean(online.wanan.xingchen.core.identity.IdentityResolver.class).resolve(event("group","rate-event"));
            var gateway=app.getBean(online.wanan.xingchen.adapter.onebot.OneBotGateway.class);
            var outbound=app.getBean(online.wanan.xingchen.core.conversation.OutboundExecutionService.class);
            var dispatcher=new online.wanan.xingchen.core.conversation.ReplyDispatcher(gateway,5,100,1,java.time.Duration.ZERO,java.time.Clock.systemUTC(),d->{},outbound);
            for(int i=0;i<2;i++){
                var result=dispatcher.dispatch(actor,List.of("one","two","three"),"same-turn",0);
                assertThat(result).hasSize(3);
                assertThat(result.get(0).accepted()).isTrue();
                assertThat(result.subList(1,3)).allSatisfy(r->assertThat(r.content()).isEqualTo("rate-limited"));
            }
            assertThat(app.getBean(FakeOneBotServer.class).sendReceiveCount()).isEqualTo(1);
            var jdbc=app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='FAILED' AND failure_reason='rate-limited'",Integer.class)).isEqualTo(2);
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void toolsPreserveWaitNoReplyAndAllowAFinalDecisionAfterAReadFailure() {
        var db=TestSqliteDatabase.create("moss-tool-decisions");
        try(var app=start(TestSqliteDatabase.jdbcUrl(db))){
            var provider=app.getBean(MockModelProvider.class);var orchestrator=app.getBean(ConversationOrchestrator.class);
            for(var decision:List.of(AgentDecision.noReply(),AgentDecision.waitForNextMessage())){
                provider.enqueue(tool("read-"+decision.type(),"qq.readRecent",Map.of("conversation","group-final","limit",5)));
                provider.enqueue(new ModelResponse(decision,null,null));
                assertThat(handle(app,event("group","decision-"+decision.type())).decision().type()).isEqualTo(decision.type());
            }
            assertThat(app.getBean(FakeOneBotServer.class).sendReceiveCount()).isZero();
            provider.enqueue(tool("denied-scope","qq.readRecent",Map.of("conversation","other-group","limit",5)));
            provider.enqueue(new ModelResponse(AgentDecision.text("read failed safely"),null,null));
            var result=handle(app,event("group","failed-read"));
            assertThat(result.toolResult().success()).isFalse();
            assertThat(app.getBean(FakeOneBotServer.class).sendReceiveCount()).isEqualTo(1);
        }finally{TestSqliteDatabase.clean(db);}
    }
    @Test void unknownToolSendStopsContinuationAndIsNotReplayedAfterRestart() {
        var db=TestSqliteDatabase.create("moss-unknown-tool");String url=TestSqliteDatabase.jdbcUrl(db);
        var incoming=event("group","unknown-tool");
        try{
            try(var app=start(url)){
                var provider=app.getBean(MockModelProvider.class);var fake=app.getBean(FakeOneBotServer.class);
                fake.sendBehavior(FakeOneBotServer.SendBehavior.DROP_AFTER_RECEIVE);
                provider.enqueue(tool("uncertain-send","qq.send",Map.of("target","group-final","kind","group","text","send once")));
                provider.enqueue(new ModelResponse(AgentDecision.text("must not continue"),null,null));
                var result=handle(app,incoming);
                assertThat(result.decision().type()).isEqualTo(DecisionType.NO_REPLY);
                assertThat(provider.requests()).hasSize(1);assertThat(fake.sendReceiveCount()).isEqualTo(1);
                assertThat(app.getBean(org.springframework.jdbc.core.JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='UNKNOWN'",Integer.class)).isEqualTo(1);
            }
            try(var app=start(url)){
                assertThat(handle(app,incoming).duplicate()).isTrue();
                assertThat(app.getBean(MockModelProvider.class).requests()).isEmpty();
                assertThat(app.getBean(FakeOneBotServer.class).sendReceiveCount()).isZero();
            }
        }finally{TestSqliteDatabase.clean(db);}
    }
    @Test void readToolsDoNotSuppressFinalTextInPrivateOrGroupAndDuplicateInboundDoesNotReplay() {
        var db=TestSqliteDatabase.create("moss-read-final");
        try(var app=start(TestSqliteDatabase.jdbcUrl(db))){
            var provider=app.getBean(MockModelProvider.class);
            var orchestrator=app.getBean(ConversationOrchestrator.class);
            var fake=app.getBean(FakeOneBotServer.class);
            for(String type:List.of("private","group")){
                String conversation=type.equals("private")?"member":"group-final";
                provider.enqueue(tool("read-"+type,"qq.readRecent",Map.of("conversation",conversation,"limit",5)));
                provider.enqueue(tool("read-again-"+type,"qq.readRecent",Map.of("conversation",conversation,"limit",5)));
                provider.enqueue(new ModelResponse(AgentDecision.text("final "+type),null,null));
                var event=event(type,"read-final-"+type);
                var result=handle(app,event);
                assertThat(result.toolResult().success()).isTrue();
                assertThat(result.decision().messages()).containsExactly("final "+type);
                assertThat(handle(app,event).duplicate()).isTrue();
            }
            assertThat(provider.requests()).hasSize(6);
            assertThat(fake.appliedSendCount()).isEqualTo(2);
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void outboundToolAndFinalTextHaveSeparateLedgerEntriesWithoutRepeatingTheTool() {
        var db=TestSqliteDatabase.create("moss-tool-final");
        try(var app=start(TestSqliteDatabase.jdbcUrl(db))){
            var provider=app.getBean(MockModelProvider.class);
            var orchestrator=app.getBean(ConversationOrchestrator.class);
            var fake=app.getBean(FakeOneBotServer.class);
            provider.enqueue(tool("send-once","qq.send",Map.of("target","group-final","kind","group","text","tool text")));
            provider.enqueue(new ModelResponse(AgentDecision.text("final text"),null,null));
            var incoming=event("group","tool-final");
            handle(app,incoming);
            assertThat(handle(app,incoming).duplicate()).isTrue();
            assertThat(fake.appliedSendCount()).isEqualTo(2);
            assertThat(provider.requests()).hasSize(2);
            var jdbc=app.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tool_execution_claims",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='SUCCESS'",Integer.class)).isEqualTo(2);
        }finally{TestSqliteDatabase.clean(db);}
    }

    @Test void localStickerRejectionHasADurableSafeReasonAndIsNotReplayed() {
        var db=TestSqliteDatabase.create("moss-sticker-rejection");
        try(var app=start(TestSqliteDatabase.jdbcUrl(db))){
            var actor=app.getBean(online.wanan.xingchen.core.identity.IdentityResolver.class).resolve(event("group","sticker-rejection"));
            var outbound=app.getBean(online.wanan.xingchen.core.conversation.OutboundExecutionService.class);
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            for(int i=0;i<2;i++){
                var record=outbound.dispatch("sticker-rejection",actor.conversation().id(),"turn",0,"logical","group-final",()->{
                    calls.incrementAndGet();return new online.wanan.xingchen.adapter.onebot.SendReceipt(false,"sticker","group-final","per-turn limit");
                });
                assertThat(record.status()).isEqualTo(online.wanan.xingchen.core.conversation.OutboundExecutionRecord.Status.FAILED);
                assertThat(record.failureReason()).isEqualTo("sticker-per-turn-limit");
            }
            assertThat(calls).hasValue(1);
            var unknown=outbound.dispatch("untrusted-error",actor.conversation().id(),"turn",0,"other","group-final",()->
                new online.wanan.xingchen.adapter.onebot.SendReceipt(false,"sticker","group-final","synthetic sensitive transport body"));
            assertThat(unknown.failureReason()).isEqualTo("onebot-rejected");
            assertThat(app.getBean(FakeOneBotServer.class).sendReceiveCount()).isZero();
        }finally{TestSqliteDatabase.clean(db);}
    }

    private static online.wanan.xingchen.core.conversation.OrchestrationResult handle(ConfigurableApplicationContext app,online.wanan.xingchen.core.model.PlatformEvent event){
        var orchestrator=app.getBean(ConversationOrchestrator.class);var prepared=orchestrator.prepare(event);
        if(!prepared.mayStartTurn())return orchestrator.handle(prepared,false,null,Set.of());
        var conversation=prepared.actor().conversation().id();var state=app.getBean(SimulationStateService.class).get(conversation);
        assertThat(orchestrator.claimInbound(event,conversation,state.generation())).isTrue();
        try{var result=orchestrator.handle(prepared,true,state,Set.of());orchestrator.completeInbound(event,conversation);return result;}
        catch(RuntimeException|Error failure){orchestrator.failInbound(event,conversation);throw failure;}
    }
    private static ModelResponse tool(String id,String name,Map<String,Object> args){return new ModelResponse(null,null,null,List.of(new ModelToolCall(id,name,args)),ModelFinishReason.TOOL_CALLS);}
    private static online.wanan.xingchen.core.model.PlatformEvent event(String type,String id){
        var body=new LinkedHashMap<String,Object>();
        body.put("post_type","message");body.put("message_type",type);body.put("self_id","test-bot");body.put("user_id","member");
        body.put("group_id","group-final");body.put("message_id",id);body.put("message","hello");
        body.put("time",java.time.Instant.now().getEpochSecond());
        body.put("sender",Map.of("user_id","member","nickname","Synthetic Member"));
        return new OneBotEventNormalizer(new ObjectMapper()).normalize(new ObjectMapper().valueToTree(body),Set.of());
    }
    private static ConfigurableApplicationContext start(String url){
        var app=new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class)
            .web(WebApplicationType.NONE).profiles("runtime-test").run("--spring.datasource.url="+url,"--xingchen.memory.database-path="+url.substring("jdbc:sqlite:".length()),"--xingchen.onebot.login-user-id=test-bot");
        // The shared fixture uses 100ms to inject transport timeouts. Ordinary success
        // cases use a separate bounded budget; UNKNOWN is injected by dropping a response.
        var fake=app.getBean(FakeOneBotServer.class);
        app.getBean(online.wanan.xingchen.adapter.onebot.OneBotV11Gateway.class).reconfigure(
            new online.wanan.xingchen.adapter.onebot.OneBotV11Configuration(fake.httpUri(),fake.wsUri(),"UNUSED_TEST_TOKEN","test-bot",false,3000,3000,50,500,32,90_000),Map.of(),()->true);
        return app;
    }
}
