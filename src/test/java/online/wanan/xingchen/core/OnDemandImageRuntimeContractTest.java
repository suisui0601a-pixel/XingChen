package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import online.wanan.xingchen.storage.ConversationImageStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnDemandImageRuntimeContractTest {
    public static final byte[] PNG=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false) static class Images {
        @Bean @Primary FixtureDownloader fixtureImageDownloader(){return new FixtureDownloader();}
    }
    static class FixtureDownloader implements QqImageInputService.Downloader {
        final AtomicInteger calls=new AtomicInteger();
        @Override public ModelImage download(String url)throws Exception{calls.incrementAndGet();if(url.contains("unavailable"))throw new java.io.IOException();return new ModelImage("image/png",PNG);}
    }
    private ConfigurableApplicationContext start(Path db){
        var app=new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class,Images.class).web(WebApplicationType.NONE).profiles("runtime-test").properties("logging.level.root=OFF","xingchen.social.enabled=false").run("--spring.datasource.url="+TestSqliteDatabase.jdbcUrl(db),"--xingchen.memory.database-path="+db,"--xingchen.owner.platform-user-id=fixture-owner","--xingchen.onebot.login-user-id=test-bot");
        app.getBean(SocialRuntime.class).start();await(()->app.getBean(FakeOneBotServer.class).wsConnections()>0);return app;
    }
    static String event(String id,String type,String target,String text,String file,String reply,int sequence)throws Exception{
        var e=new LinkedHashMap<String,Object>();e.put("post_type","message");e.put("message_type",type);e.put("self_id","test-bot");e.put("user_id",type.equals("private")?target:"fixture-member");if(type.equals("group"))e.put("group_id",target);e.put("message_id",id);e.put("message_seq",sequence);e.put("time",1790000000L+sequence);e.put("sender",Map.of("nickname","Fixture Member","role","member"));
        var segments=new ArrayList<Object>();if(reply!=null)segments.add(Map.of("type","reply","data",Map.of("id",reply)));if(type.equals("group")&&!text.isBlank())segments.add(Map.of("type","at","data",Map.of("qq","test-bot")));if(!text.isBlank())segments.add(Map.of("type","text","data",Map.of("text",text)));if(file!=null)segments.add(Map.of("type","image","data",Map.of("file",file,"url","https://gchat.qpic.cn/"+file)));e.put("message",segments);return new ObjectMapper().writeValueAsString(e);
    }
    static void emit(ConfigurableApplicationContext app,String payload,String id)throws Exception{app.getBean(FakeOneBotServer.class).emit(payload);await(()->app.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM inbound_turn_executions WHERE incoming_event_id=? AND status IN ('COMPLETED','FAILED')",Integer.class,id)>0);}
    static void await(java.util.function.BooleanSupplier ready){long end=System.nanoTime()+20_000_000_000L;while(!ready.getAsBoolean()&&System.nanoTime()<end){try{Thread.sleep(20);}catch(InterruptedException e){throw new RuntimeException(e);}}assertThat(ready.getAsBoolean()).isTrue();}

    @Test void pureImagesAreDurableAndFreeUntilAnExplicitLaterRequestThenDuplicatesStayFree()throws Exception{
        var db=TestSqliteDatabase.create("image-on-demand");
        try(var app=start(db)){
            var provider=app.getBean(MockModelProvider.class);var downloader=app.getBean(FixtureDownloader.class);var jdbc=app.getBean(JdbcTemplate.class);
            emit(app,event("image-only","private","image-person","","observed.png",null,1),"image-only");
            assertThat(provider.requests()).isEmpty();assertThat(downloader.calls).hasValue(0);
            assertThat(jdbc.queryForObject("SELECT text FROM messages WHERE platform_message_id='image-only'",String.class)).contains("已记录","尚未识别");
            assertThat(jdbc.queryForObject("SELECT raw_metadata_json FROM messages WHERE platform_message_id='image-only'",String.class)).contains("observed.png");
            provider.enqueue(new ModelResponse(AgentDecision.text("fixture picture description"),null,null));
            String request=event("image-request","private","image-person","看看刚才那张图片是什么",null,null,2);emit(app,request,"image-request");
            assertThat(provider.requests()).hasSize(1);assertThat(downloader.calls).hasValue(1);
            var inputs=provider.requests().getFirst().messages().stream().filter(m->!m.images().isEmpty()).toList();assertThat(inputs).hasSize(1);assertThat(inputs.getFirst().role()).isEqualTo(ModelRole.USER);assertThat(inputs.getFirst().images()).hasSize(1);
            assertThat(provider.requests().getFirst().messages().getFirst().content()).isEqualTo(HardSecurityPolicy.TEXT);
            app.getBean(FakeOneBotServer.class).emit(request);emit(app,event("image-fence","group","background-scope","",null,null,1),"image-fence");
            assertThat(provider.requests()).hasSize(1);assertThat(downloader.calls).hasValue(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM onebot_outbound_executions WHERE status='SUCCESS'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE raw_metadata_json LIKE '%data:image/%'",Integer.class)).isZero();
        }finally{TestSqliteDatabase.clean(db);}
    }
    @Test void groupImageIsRecordOnlyAndPrivateQuoteCannotBeReadAcrossConversation()throws Exception{
        var db=TestSqliteDatabase.create("image-scope");
        try(var app=start(db)){
            var provider=app.getBean(MockModelProvider.class);var downloader=app.getBean(FixtureDownloader.class);
            emit(app,event("private-image","private","image-private-person","","private.png",null,1),"private-image");
            emit(app,event("group-image","group","image-group","","group.png",null,1),"group-image");
            assertThat(provider.requests()).isEmpty();assertThat(downloader.calls).hasValue(0);
            provider.enqueue(new ModelResponse(AgentDecision.text("image not accessible"),null,null));
            emit(app,event("cross-quote","group","image-group","看看引用的图片是什么",null,"private-image",2),"cross-quote");
            assertThat(provider.requests()).hasSize(1);assertThat(downloader.calls).hasValue(0);
            assertThat(provider.requests().getLast().messages()).allSatisfy(m->assertThat(m.images()).isEmpty());
            assertThat(provider.requests().getLast().messages().getLast().content()).contains("没有可读取的图片记录");
            provider.enqueue(new ModelResponse(AgentDecision.text("group image description"),null,null));
            emit(app,event("group-quote","group","image-group","请识别引用图片的内容",null,"group-image",3),"group-quote");
            assertThat(downloader.calls).hasValue(1);assertThat(provider.requests().getLast().messages()).anySatisfy(m->assertThat(m.images()).hasSize(1));
        }finally{TestSqliteDatabase.clean(db);}
    }
    @Test void negativeRequestDoesNotDownloadAndDownloadFailureIsExplicit()throws Exception{
        var db=TestSqliteDatabase.create("image-failure");
        try(var app=start(db)){
            var provider=app.getBean(MockModelProvider.class);var downloader=app.getBean(FixtureDownloader.class);
            emit(app,event("no-analysis","private","failure-person","不用识别，只保存记录","unavailable.png",null,1),"no-analysis");
            assertThat(downloader.calls).hasValue(0);assertThat(provider.requests()).isEmpty();
            provider.enqueue(new ModelResponse(AgentDecision.text("cannot view image"),null,null));
            emit(app,event("failed-image","private","failure-person","看看这张图片是什么",null,null,2),"failed-image");
            assertThat(downloader.calls).hasValue(1);assertThat(provider.requests().getLast().messages()).allSatisfy(m->assertThat(m.images()).isEmpty());
            assertThat(provider.requests().getLast().messages().getLast().content()).contains("图片读取失败","不得编造");
        }finally{TestSqliteDatabase.clean(db);}
    }
    @Test void imageInputRequiresTheExistingReadCapability(){
        var records=mock(ConversationImageStore.class);var gateway=mock(OneBotGateway.class);var downloader=mock(QqImageInputService.Downloader.class);
        var actor=mock(online.wanan.xingchen.core.identity.ResolvedActorContext.class);when(actor.conversation()).thenReturn(new online.wanan.xingchen.core.identity.Conversation(UUID.randomUUID(),new online.wanan.xingchen.core.model.ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,online.wanan.xingchen.core.model.ConversationType.PRIVATE,"fixture-person")));
        var context=new online.wanan.xingchen.core.context.ContextBuilder(online.wanan.xingchen.core.context.ContextBudget.defaults()).build("p","s","a","fixture-person","",List.of(),List.of(),Map.of(),"看看这张图是什么",0,null);
        var result=new QqImageInputService(records,gateway,downloader).prepare(new AgentRequest(actor,context,Set.of(),"fixture-event"));
        assertThat(result.images()).isEmpty();assertThat(result.notice()).contains("没有读取图片的权限");verifyNoInteractions(records,gateway,downloader);
    }
    @Test void recordedImageCanBeSelectedAfterRestartWithoutPersistingItsBytes()throws Exception{
        var db=TestSqliteDatabase.create("image-restart");
        try{
            try(var app=start(db)){emit(app,event("restart-image","private","restart-person","","restart.png",null,1),"restart-image");assertThat(app.getBean(MockModelProvider.class).requests()).isEmpty();}
            try(var app=start(db)){
                var provider=app.getBean(MockModelProvider.class);provider.enqueue(new ModelResponse(AgentDecision.text("fixture recalled image"),null,null));
                emit(app,event("restart-image-request","private","restart-person","看看刚才那张图片是什么",null,null,2),"restart-image-request");
                assertThat(provider.requests()).hasSize(1);assertThat(provider.requests().getFirst().messages()).anySatisfy(m->assertThat(m.images()).hasSize(1));
                assertThat(app.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM messages WHERE raw_metadata_json LIKE '%data:image/%'",Integer.class)).isZero();
            }
        }finally{TestSqliteDatabase.clean(db);}
    }
    @Test void aRecordOnlyImageDoesNotConsumeAnExistingWait()throws Exception{
        var db=TestSqliteDatabase.create("image-wait");
        try(var app=start(db)){
            var id=app.getBean(online.wanan.xingchen.core.identity.IdentityRegistry.class).identifyConversation(new online.wanan.xingchen.core.model.ConversationIdentity(online.wanan.xingchen.core.model.Platform.QQ,online.wanan.xingchen.core.model.ConversationType.PRIVATE,"wait-image-person")).id();
            var states=app.getBean(SimulationStateService.class);states.waitForNextMessage(id,"fixture-existing-origin","awaiting human text");
            emit(app,event("wait-image-only","private","wait-image-person","","wait.png",null,1),"wait-image-only");
            assertThat(states.get(id).wakeState()).isEqualTo(SimulationConversationState.WakeState.WAITING);
            assertThat(app.getBean(MockModelProvider.class).requests()).isEmpty();assertThat(app.getBean(FixtureDownloader.class).calls).hasValue(0);
        }finally{TestSqliteDatabase.clean(db);}
    }
    @Test void explicitRecognitionIntentIsNotInferredFromEveryImage(){
        for(String text:List.of("收到一张图片","今天天气很好","不用看图","别识别这张图","只保存图片记录"))assertThat(ImageRecognitionIntent.requested(text,true)).as(text).isFalse();
        for(String text:List.of("看看这张图是什么","这是什么","帮我看看","请读出截图文字","描述一下图片内容","看看刚才那张图片"))assertThat(ImageRecognitionIntent.requested(text,true)).as(text).isTrue();
    }
}
