package online.wanan.xingchen.core;

import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.context.*;
import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.memory.RequestContext;
import online.wanan.xingchen.core.relationship.AddressResolution;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class SocialAgentContractTest {
    private final AgentToolCatalog catalog=new AgentToolCatalog();
    private final ContextPackage context=new ContextBuilder(ContextBudget.defaults()).build(new ContextBuildInput("sim","persona","qq:u","User","姐姐","g",List.of(),"",List.of(),Map.of(),"hello","",new RequestContext(UUID.randomUUID(),UUID.randomUUID(),false,null)));
    private final ResolvedActorContext actor(){UUID id=UUID.randomUUID();var p=new Person(id,Platform.QQ,"u",false,false);var c=new Conversation(UUID.randomUUID(),new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"g"));return new ResolvedActorContext(p,c,new Membership(UUID.randomUUID(),id,c.id(),"User",null,IdentityRole.MEMBER,Instant.now(),Instant.now()),new ActorFlags(false,false,false,false),"User",new AddressResolution("姐姐","term",null,true,1));}
    private AgentRequest req(Set<AgentCapability> caps){return new AgentRequest(actor(),context,caps);}
    @Test void agent101_textReplyDecisionIsPreservedAndContextRecorded(){var provider=new MockModelProvider().enqueue(new ModelResponse(AgentDecision.text("你好"),new ModelUsage(10,2,8,3,1),null));var result=new SocialAgent(provider,catalog).decide(req(CapabilityPolicy.capabilities(AgentRole.SOCIAL,false)));assertThat(result.type()).isEqualTo(DecisionType.TEXT_REPLY);assertThat(result.messages()).containsExactly("你好");assertThat(provider.receivedContexts()).containsExactly(context);}
    @Test void agent102_multiReplyDecisionKeepsOrderedMessages(){var provider=new MockModelProvider().enqueue(new ModelResponse(AgentDecision.multi(List.of("第一句","第二句")),null,null));assertThat(new SocialAgent(provider,catalog).decide(req(Set.of())).messages()).containsExactly("第一句","第二句");}
    @Test void agent103_noReplyIsExplicit(){var provider=new MockModelProvider().enqueue(new ModelResponse(AgentDecision.noReply(),null,null));assertThat(new SocialAgent(provider,catalog).decide(req(Set.of())).type()).isEqualTo(DecisionType.NO_REPLY);}
    @Test void agent104_toolCallOutsideAllowedCapabilitiesFailsClosed(){var provider=new MockModelProvider().enqueue(new ModelResponse(AgentDecision.tool(new ToolCall("shell.exec",Map.of("cmd","whoami"))),null,null));assertThat(new SocialAgent(provider,catalog).decide(req(EnumSet.allOf(AgentCapability.class))).type()).isEqualTo(DecisionType.NO_REPLY);}
    @Test void tool101_catalogContainsSchemaBackedSafeQQMemoryStickerAndSlangTools(){assertThat(AgentToolCatalog.all()).extracting(AgentTool::name).contains("qq.readRecent","qq.readUnread","qq.markRead","qq.send","qq.sendMultiple","qq.reply","qq.wait","qq.setWakeConfig","qq.getMember","qq.getMembers","qq.poke","memory.search","memory.remember","memory.update","memory.setAddress","memory.getPerson","sticker.list","sticker.search","sticker.send","sticker.collect","sticker.note","slang.search","slang.submit");assertThat(AgentToolCatalog.all()).allSatisfy(t->assertThat(t.inputSchema()).containsKey("properties"));assertThat(AgentToolCatalog.all()).extracting(AgentTool::name).noneMatch(n->n.contains("shell")||n.contains("ssh")||n.contains("docker")||n.contains("filesystem")||n.contains("process"));}
    @Test void tool102_mockExecutorUsesOnlyMockGatewayAndHonorsCapability(){var n=new OneBotEventNormalizer(new com.fasterxml.jackson.databind.ObjectMapper());var gateway=new MockOneBotGateway(n,"bot");gateway.connect();var exec=new MockToolExecutor(gateway,catalog);var denied=exec.execute(new ToolCall("qq.send",Map.of("target","g","text","hi","kind","group")),Set.of());assertThat(denied.success()).isFalse();var result=exec.execute(new ToolCall("qq.send",Map.of("target","g","text","hi","kind","group")),Set.of(AgentCapability.QQ_SEND));assertThat(result.success()).isTrue();assertThat(gateway.sent()).singleElement().satisfies(s->assertThat(s.operation()).isEqualTo("group"));}
    @Test void agent105_mockProviderStreamingReportsFinalUsage(){var provider=new MockModelProvider().enqueue(new ModelResponse(AgentDecision.text("streamed"),new ModelUsage(3,1,2,2,0),null));List<ModelStreamEvent> events=new ArrayList<>();provider.stream(new ModelRequest(context,List.of()),events::add);assertThat(events).extracting(ModelStreamEvent::text).containsExactly("streamed","");assertThat(events.getLast().complete()).isTrue();}
}
