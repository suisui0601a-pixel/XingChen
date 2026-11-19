package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.console.AccessControlService;
import online.wanan.xingchen.console.SocialSettingsService;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import online.wanan.xingchen.core.identity.IdentityRegistry;
import online.wanan.xingchen.core.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import static org.assertj.core.api.Assertions.assertThat;

/** Real inbound/runtime/persistence/HTTP flow, with a controlled RNG and local fakes only. */
class OrdinaryParticipationRuntimeTest {
    @Test void ordinaryParticipationHonorsProbabilityAccessSleepDedupAndModelNoReply() throws Exception {
        Path db=TestSqliteDatabase.create("ordinary-participation");String url=TestSqliteDatabase.jdbcUrl(db);
        try{
            try(var app=start(url)){
                var fake=app.getBean(FakeOneBotServer.class);var provider=app.getBean(MockModelProvider.class);
                var runtime=app.getBean(SocialRuntime.class);var settings=app.getBean(SocialSettingsService.class);
                var jdbc=app.getBean(JdbcTemplate.class);var draws=app.getBean(Draws.class);
                var access=app.getBean(AccessControlService.class);
                var rules=new ArrayList<AccessControlService.Rule>();
                for(String group:List.of("prob-zero","prob-all","prob-half","prob-sleep","prob-no-reply"))
                    rules.add(new AccessControlService.Rule("GROUP","QQ",group,"ALLOW"));
                rules.add(new AccessControlService.Rule("PRIVATE","QQ","blocked-member","DENY"));
                access.replace(rules,access.revision(),"synthetic-admin");
                assertThat(((Map<?,?>)settings.global().get("values")).get("ordinaryMessageProbability")).isEqualTo(0.0);
                runtime.start();await(()->fake.wsConnections()==1);

                probability(settings,0);
                for(int i=0;i<2;i++)emit(app,1000+i,"prob-zero","ordinary-member",false);
                assertThat(provider.requests()).isEmpty();assertThat(fake.sendReceiveCount()).isZero();

                probability(settings,1);queue(provider,"prob-all",3);
                for(int i=0;i<3;i++)emit(app,2000+i,"prob-all","ordinary-member",false);
                assertThat(provider.requests()).hasSize(3);assertThat(fake.sendReceiveCount()).isEqualTo(3);

                probability(settings,.5);draws.calls.set(0);queue(provider,"prob-half",20);
                for(int i=0;i<20;i++)emit(app,3000+i,"prob-half","ordinary-member",false);
                assertThat(draws.calls).hasValue(20);
                assertThat(provider.requests()).hasSize(13);assertThat(fake.sendReceiveCount()).isEqualTo(13);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id BETWEEN '3000' AND '3019' AND status='COMPLETED'",Integer.class)).isEqualTo(20);

                // A same-lane drain proves the duplicate and deny event were consumed first.
                probability(settings,0);
                fake.emit(message(3000,"prob-half","ordinary-member",false));
                fake.emit(message(4000,"prob-half","blocked-member",false));
                emit(app,4001,"prob-half","ordinary-member",false);
                assertThat(provider.requests()).hasSize(13);assertThat(fake.sendReceiveCount()).isEqualTo(13);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE platform_message_id='4000'",Integer.class)).isZero();

                probability(settings,1);
                UUID sleeping=app.getBean(IdentityRegistry.class).identifyConversation(new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"prob-sleep")).id();
                app.getBean(SimulationStateService.class).sleep(sleeping,null,true);
                int priorDraws=draws.calls.get();emit(app,5000,"prob-sleep","ordinary-member",false);
                assertThat(draws.calls).hasValue(priorDraws);assertThat(provider.requests()).hasSize(13);

                probability(settings,0);queue(provider,"prob-sleep",1);
                emit(app,5001,"prob-sleep","ordinary-member",true);
                assertThat(provider.requests()).hasSize(14);assertThat(fake.sendReceiveCount()).isEqualTo(14);

                probability(settings,1);
                provider.enqueueFor("prob-no-reply",new ModelResponse(AgentDecision.noReply(),null,null));
                emit(app,6000,"prob-no-reply","ordinary-member",false);
                assertThat(provider.requests()).hasSize(15);assertThat(fake.sendReceiveCount()).isEqualTo(14);
                assertThat(fake.wsConnections()).isEqualTo(1);
                System.out.println("ORDINARY_RUNTIME scenarios=31; p0=0/2; p1=3/3; p0.5=10/20; model_calls=15; outbound_calls=14; access/sleep/dedup/no_reply=PASS");
            }
            try(var restarted=start(url)){
                var settings=restarted.getBean(SocialSettingsService.class);
                assertThat(((Map<?,?>)settings.global().get("values")).get("ordinaryMessageProbability")).isEqualTo(1.0);
                assertThat(restarted.getBean(MockModelProvider.class).requests()).isEmpty();
            }
        }finally{TestSqliteDatabase.clean(db);}
    }
    private static void probability(SocialSettingsService settings,double probability){settings.updateGlobal(((Number)settings.global().get("revision")).longValue(),Map.of("ordinaryMessageProbability",probability),"synthetic-admin");}
    private static void queue(MockModelProvider provider,String group,int count){for(int i=0;i<count;i++)provider.enqueueFor(group,new ModelResponse(AgentDecision.text("synthetic reply"),null,null));}
    private static void emit(ConfigurableApplicationContext app,int id,String group,String user,boolean mention)throws Exception{
        app.getBean(FakeOneBotServer.class).emit(message(id,group,user,mention));
        await(()->app.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status='COMPLETED'",Integer.class,Integer.toString(id))==1);
    }
    private static String message(int id,String group,String user,boolean mention)throws Exception{
        List<Map<String,Object>> segments=new ArrayList<>();
        if(mention)segments.add(Map.of("type","at","data",Map.of("qq","test-bot")));
        segments.add(Map.of("type","text","data",Map.of("text","ordinary statement "+id)));
        return new ObjectMapper().writeValueAsString(Map.of("post_type","message","message_type","group","self_id","test-bot","group_id",group,"user_id",user,"sender",Map.of("user_id",user,"nickname","Synthetic Member","role","member"),"message_id",Integer.toString(id),"message_seq",id,"time",Instant.now().getEpochSecond(),"message",segments));
    }
    private static ConfigurableApplicationContext start(String url){return new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class,ControlledProbability.class)
        .web(WebApplicationType.NONE).profiles("runtime-test").run("--spring.datasource.url="+url,"--xingchen.memory.database-path="+url.substring("jdbc:sqlite:".length()),"--xingchen.onebot.login-user-id=test-bot","--xingchen.owner.platform-user-id=synthetic-owner","--xingchen.test.allow-all-access=false");}
    private static void await(BooleanSupplier condition)throws InterruptedException{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);assertThat(condition.getAsBoolean()).isTrue();}
    public static class Draws implements DoubleSupplier {
        final AtomicInteger calls=new AtomicInteger();
        @Override public double getAsDouble(){return new double[]{0,.49,.50,.99}[calls.getAndIncrement()%4];}
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class ControlledProbability {
        @Bean Draws ordinaryDraws(){return new Draws();}
        @Bean @Primary ParticipationPolicy controlledParticipationPolicy(Draws draws){return new ParticipationPolicy(draws);}
        @Bean(destroyMethod="close") @Primary OneBotGateway ordinaryWakeGateway(FakeOneBotServer fake,ObjectMapper mapper,OneBotEventNormalizer normalizer){
            return new OneBotV11Gateway(new OneBotV11Configuration(fake.httpUri(),fake.wsUri(),"UNUSED_TEST_TOKEN","test-bot",false,3000,3000,50,500,128,90_000),mapper,normalizer,Map.of());
        }
    }
}
