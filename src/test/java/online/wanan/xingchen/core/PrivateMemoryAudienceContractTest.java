package online.wanan.xingchen.core;

import online.wanan.xingchen.XingChenApplication;
import online.wanan.xingchen.config.TestRuntimeConfiguration;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.console.PeopleMemoryConsoleService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** PRIVATE facts must never be supplied to a shared-audience Agent, even for the same speaker or Owner. */
class PrivateMemoryAudienceContractTest {
    @org.junit.jupiter.api.Test void groupPromptSearchUpdateAndConsolePreviewCannotReadPrivateMemoryEvenForOwner(){
        var db=TestSqliteDatabase.create("private-memory-audience");
        try(var app=new SpringApplicationBuilder(XingChenApplication.class,TestRuntimeConfiguration.class).web(WebApplicationType.NONE).profiles("runtime-test")
            .run("--spring.datasource.url="+TestSqliteDatabase.jdbcUrl(db),"--xingchen.memory.database-path="+db,"--xingchen.onebot.login-user-id=test-bot","--xingchen.owner.platform-user-id=member")){
            var fake=app.getBean(FakeOneBotServer.class);app.getBean(OneBotV11Gateway.class).reconfigure(new OneBotV11Configuration(fake.httpUri(),fake.wsUri(),"UNUSED_TEST_TOKEN","test-bot",false,3000,3000,50,500,32,90000),Map.of(),()->true);
            var model=app.getBean(MockModelProvider.class);
            model.enqueue(tool("private-write","memory.remember",Map.of("type","PREFERENCE","scope","PRIVATE","content","PRIVATE_FACT_MARKER"))).enqueue(new ModelResponse(AgentDecision.noReply(),null,null));
            var first=handle(app,event(false,"private-write-event"));String memoryId=Objects.toString(first.toolResult().data().get("memoryId"));
            model.enqueue(tool("group-search","memory.search",Map.of("query","PRIVATE_FACT_MARKER","limit",5))).enqueue(new ModelResponse(AgentDecision.noReply(),null,null));
            var group=handle(app,event(true,"group-search-event"));
            assertThat(group.actor().flags().owner()).isTrue();assertThat(group.context().memories()).isEmpty();
            assertThat(group.toolResult().data().toString()).doesNotContain("PRIVATE_FACT_MARKER");
            assertThat(model.requests().subList(2,model.requests().size()).toString()).doesNotContain("content=PRIVATE_FACT_MARKER");
            var preview=app.getBean(PeopleMemoryConsoleService.class).preview(group.actor().person().id().toString(),group.actor().conversation().id().toString(),null);
            assertThat(preview.get("items").toString()).doesNotContain("PRIVATE");
            model.enqueue(tool("group-update","memory.update",Map.of("memoryId",memoryId,"content","must not overwrite private fact"))).enqueue(new ModelResponse(AgentDecision.noReply(),null,null));
            var update=handle(app,event(true,"group-update-event"));assertThat(update.toolResult().success()).isFalse();
            model.enqueue(new ModelResponse(AgentDecision.text("private recall"),null,null));
            assertThat(handle(app,event(false,"private-recall-event")).context().memories()).extracting(online.wanan.xingchen.core.memory.Memory::content).contains("PRIVATE_FACT_MARKER");
        }finally{TestSqliteDatabase.clean(db);}
    }
    @org.junit.jupiter.api.Test void privateMemoryIsDeniedForEveryGroupAudienceButOwnPrivateRecallStillWorks(){
        var person=UUID.randomUUID();var conversation=UUID.randomUUID();var memory=fact(person,conversation,online.wanan.xingchen.core.memory.MemoryScope.PRIVATE);
        for(boolean owner:List.of(false,true))assertThat(online.wanan.xingchen.core.memory.MemoryVisibility.canRead(memory,new online.wanan.xingchen.core.memory.RequestContext(person,conversation,owner,null,ConversationType.GROUP))).isFalse();
        assertThat(online.wanan.xingchen.core.memory.MemoryVisibility.canRead(memory,new online.wanan.xingchen.core.memory.RequestContext(person,conversation,false,null,ConversationType.PRIVATE))).isTrue();
        assertThat(online.wanan.xingchen.core.memory.MemoryVisibility.canRead(memory,new online.wanan.xingchen.core.memory.RequestContext(UUID.randomUUID(),conversation,false,null,ConversationType.PRIVATE))).isFalse();
    }
    @org.junit.jupiter.api.Test void explicitOtherScopesAndAuthenticatedLegacyAdminVisibilityAreUnchanged(){
        var person=UUID.randomUUID();var conversation=UUID.randomUUID();var group=new online.wanan.xingchen.core.memory.RequestContext(person,conversation,false,null,ConversationType.GROUP);
        for(var scope:List.of(online.wanan.xingchen.core.memory.MemoryScope.CONVERSATION,online.wanan.xingchen.core.memory.MemoryScope.PERSON_GLOBAL,online.wanan.xingchen.core.memory.MemoryScope.GLOBAL))assertThat(online.wanan.xingchen.core.memory.MemoryVisibility.canRead(fact(person,conversation,scope),group)).isTrue();
        assertThat(online.wanan.xingchen.core.memory.MemoryVisibility.canRead(fact(person,conversation,online.wanan.xingchen.core.memory.MemoryScope.PRIVATE),new online.wanan.xingchen.core.memory.RequestContext(person,conversation,true,null))).isTrue();
    }
    @org.junit.jupiter.api.Test void contextBuilderBindsLegacyRequestsToTrustedAudienceAndRejectsAFalsePrivateClaim(){
        var person=UUID.randomUUID();var conversation=UUID.randomUUID();var identity=new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"synthetic-group");var memory=fact(person,conversation,online.wanan.xingchen.core.memory.MemoryScope.PRIVATE);var builder=new online.wanan.xingchen.core.context.ContextBuilder(online.wanan.xingchen.core.context.ContextBudget.defaults());
        var legacy=new online.wanan.xingchen.core.memory.RequestContext(person,conversation,true,null);
        var input=new online.wanan.xingchen.core.context.ContextBuildInput("s","p","a","d","r","synthetic-group",List.of(memory),"",List.of(),Map.of(),"m","",legacy,List.of(),identity);
        assertThat(builder.build(input).memories()).isEmpty();
        var falsePrivate=new online.wanan.xingchen.core.memory.RequestContext(person,conversation,true,null,ConversationType.PRIVATE);
        var invalid=new online.wanan.xingchen.core.context.ContextBuildInput("s","p","a","d","r","synthetic-group",List.of(memory),"",List.of(),Map.of(),"m","",falsePrivate,List.of(),identity);
        assertThatThrownBy(()->builder.build(invalid)).isInstanceOf(SecurityException.class).hasMessage("memory audience mismatch");
    }
    private static online.wanan.xingchen.core.memory.Memory fact(UUID person,UUID conversation,online.wanan.xingchen.core.memory.MemoryScope scope){var now=java.time.Instant.now();return new online.wanan.xingchen.core.memory.Memory(UUID.randomUUID(),online.wanan.xingchen.core.memory.MemoryType.PREFERENCE,person,conversation,null,"private fixture fact",scope,conversation.toString(),1,1,true,now,now,null,null,online.wanan.xingchen.core.memory.MemoryStatus.ACTIVE);}
    private static ModelResponse tool(String id,String name,Map<String,Object> args){return new ModelResponse(null,null,null,List.of(new ModelToolCall(id,name,args)),ModelFinishReason.TOOL_CALLS);}
    private static OrchestrationResult handle(ConfigurableApplicationContext app,PlatformEvent event){var o=app.getBean(ConversationOrchestrator.class);var p=o.prepare(event);var s=app.getBean(SimulationStateService.class).get(p.actor().conversation().id());assertThat(o.claimInbound(event,p.actor().conversation().id(),s.generation())).isTrue();var r=o.handle(p,true,s,Set.of());o.completeInbound(event,p.actor().conversation().id());return r;}
    private static PlatformEvent event(boolean group,String id){var m=new LinkedHashMap<String,Object>();m.put("post_type","message");m.put("message_type",group?"group":"private");m.put("self_id","test-bot");m.put("user_id","member");m.put("group_id","synthetic-group");m.put("message_id",id);m.put("time",java.time.Instant.now().getEpochSecond());m.put("message","question");m.put("sender",Map.of("user_id","member","nickname","Fixture"));return new OneBotEventNormalizer(new ObjectMapper()).normalize(new ObjectMapper().valueToTree(m),Set.of("member"));}
}
