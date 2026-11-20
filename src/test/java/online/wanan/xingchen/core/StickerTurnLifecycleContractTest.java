package online.wanan.xingchen.core;

import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.context.*;
import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.relationship.AddressResolution;
import online.wanan.xingchen.core.sticker.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StickerTurnLifecycleContractTest {
    @TempDir Path root;
    private record Fixture(StickerService stickers,SocialToolExecutor tools,OneBotGateway gateway,UUID sticker,AtomicInteger sends){}
    private Fixture fixture(Clock clock,Duration cooldown)throws Exception{
        var value=new AtomicReference<StickerMetadata>();var repo=mock(StickerRepository.class);
        when(repo.findByHash(anyString())).thenAnswer(a->Optional.ofNullable(value.get()).filter(v->v.sha256().equals(a.getArgument(0))));
        when(repo.save(any())).thenAnswer(a->{value.set(a.getArgument(0));return value.get();});
        when(repo.get(any())).thenAnswer(a->Optional.ofNullable(value.get()).filter(v->v.id().equals(a.getArgument(0))));
        when(repo.recordUse(any(),any())).thenAnswer(a->Optional.ofNullable(value.get()));
        var gateway=mock(OneBotGateway.class);var sends=new AtomicInteger();
        when(gateway.sendImageBytes(anyString(),anyString(),any())).thenAnswer(a->{sends.incrementAndGet();return new SendReceipt(true,"image",a.getArgument(0),"accepted");});
        var service=new StickerService(root,repo,new StickerPolicy(1,cooldown),gateway,clock);
        Files.createDirectories(root.resolve("inbox"));var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),"png",bytes);Files.write(root.resolve("inbox/fixture.png"),bytes.toByteArray());
        var sticker=service.collect("fixture.png",List.of("synthetic"),"test");
        var tools=new SocialToolExecutor(gateway,new AgentToolCatalog(),null,null,null,null,null,"bot",service,null,clock);
        return new Fixture(service,tools,gateway,sticker.id(),sends);
    }
    private AgentRequest request(String event){
        var person=new Person(UUID.randomUUID(),Platform.QQ,"synthetic-user",false,false);
        var conversation=new Conversation(UUID.randomUUID(),new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"synthetic-group"));var now=Instant.now();
        var member=new Membership(UUID.randomUUID(),person.id(),conversation.id(),"Fixture",null,IdentityRole.MEMBER,now,now);
        var actor=new ResolvedActorContext(person,conversation,member,new ActorFlags(false,false,false,false),"Fixture",new AddressResolution("Fixture","default",null,true,1));
        var context=new ContextBuilder(ContextBudget.defaults()).build("fixture persona","fixture simulation","actor",conversation.id().toString(),"",List.of(),List.of(),Map.of(),"send fixture",0,null);
        return new AgentRequest(actor,context,Set.of(AgentCapability.STICKER_SEND),event,event);
    }
    private AgentExecutor executor(Fixture fixture,int calls){
        ModelProvider provider=req->{
            if(req.messages().stream().anyMatch(m->m.role()==ModelRole.TOOL))return new ModelResponse(AgentDecision.text("done"),null,null);
            var toolCalls=new ArrayList<ModelToolCall>();
            for(int i=0;i<calls;i++)toolCalls.add(new ModelToolCall("fixture-call-"+i,"sticker.send",Map.of("stickerId",fixture.sticker().toString(),"target","synthetic-group","kind","group")));
            return new ModelResponse(AgentDecision.tool(toolCalls.getFirst().toToolCall()),null,null,toolCalls,ModelFinishReason.TOOL_CALLS);
        };
        return AgentExecutor.defaults(provider,fixture.tools(),new ContextBuilder(ContextBudget.defaults()),new InMemorySecurityEventSink());
    }
    @Test void toolReceiptSeparatesConversationTargetFromProviderMessageId()throws Exception{
        var f=fixture(Clock.systemUTC(),Duration.ZERO);
        when(f.gateway().sendImageBytes(anyString(),anyString(),any())).thenReturn(new SendReceipt(true,"image","fixture-message-reference","accepted"));
        var result=executor(f,1).execute(request("receipt-target"));
        assertThat(result.toolResults()).singleElement().satisfies(r->{
            assertThat(r.data()).containsEntry("target","synthetic-group").containsEntry("kind","group").containsEntry("conversationType","GROUP").containsEntry("messageId","fixture-message-reference");
        });
        verify(f.gateway(),times(1)).sendImageBytes(anyString(),anyString(),any());
    }
    @Test void successiveAgentTurnsEachGetTheirOwnStickerAllowance()throws Exception{
        var f=fixture(Clock.systemUTC(),Duration.ZERO);var executor=executor(f,1);
        var first=executor.execute(request("event-one"));var second=executor.execute(request("event-two"));
        assertThat(first.toolResults()).singleElement().satisfies(r->assertThat(r.success()).isTrue());
        assertThat(second.toolResults()).singleElement().satisfies(r->assertThat(r.success()).isTrue());
        assertThat(f.sends()).hasValue(2);
    }
    @Test void aSecondStickerInTheSameTurnIsStillRejected()throws Exception{
        var f=fixture(Clock.systemUTC(),Duration.ZERO);var result=executor(f,2).execute(request("same-turn"));
        assertThat(result.toolResults()).extracting(ModelToolResult::success).containsExactly(true,false);assertThat(result.toolResults().get(1).data()).containsEntry("reason","sticker-per-turn-limit");assertThat(f.sends()).hasValue(1);
    }
    @Test void cooldownSurvivesNewTurnsAndExpiresNormally()throws Exception{
        var instant=new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return instant.get();}};
        var f=fixture(clock,Duration.ofSeconds(30));var executor=executor(f,1);
        assertThat(executor.execute(request("cooldown-first")).toolResults().getFirst().success()).isTrue();
        assertThat(executor.execute(request("cooldown-second")).toolResults().getFirst().success()).isFalse();
        instant.updateAndGet(t->t.plusSeconds(31));
        assertThat(executor.execute(request("cooldown-third")).toolResults().getFirst().success()).isTrue();assertThat(f.sends()).hasValue(2);
    }
    @Test void unknownDeliveryStopsContinuationAndKeepsCooldown()throws Exception{
        var f=fixture(Clock.systemUTC(),Duration.ofSeconds(30));
        when(f.gateway().sendImageBytes(anyString(),anyString(),any())).thenReturn(new SendReceipt(false,"image","synthetic-group","unconfirmed",DeliveryStatus.UNKNOWN));
        var executor=executor(f,2);var result=executor.execute(request("unknown-turn"));
        assertThat(result.stopReason()).isEqualTo("outbound-unknown");assertThat(result.toolSteps()).isEqualTo(1);
        assertThat(executor.execute(request("after-unknown")).toolResults()).allSatisfy(r->assertThat(r.success()).isFalse());
        verify(f.gateway(),times(1)).sendImageBytes(anyString(),anyString(),any());
    }
    @Test void exceptionalAgentExitReleasesThreadLocalTurnBudget()throws Exception{
        var f=fixture(Clock.systemUTC(),Duration.ZERO);
        ModelProvider provider=req->{
            if(req.messages().stream().anyMatch(m->m.role()==ModelRole.TOOL))throw new IllegalStateException("synthetic provider failure");
            return new ModelResponse(AgentDecision.tool(new ToolCall("sticker.send",Map.of("stickerId",f.sticker().toString(),"target","synthetic-group","kind","group"))),null,null);
        };
        var executor=AgentExecutor.defaults(provider,f.tools(),new ContextBuilder(ContextBudget.defaults()),new InMemorySecurityEventSink());
        assertThatThrownBy(()->executor.execute(request("exception-turn"))).isInstanceOf(IllegalStateException.class);
        assertThat(f.stickers().send(f.sticker(),"synthetic-group","group").accepted()).isTrue();assertThat(f.sends()).hasValue(2);
    }
    @Test void concurrentTurnScopeCannotResetAnotherTurnsBudget()throws Exception{
        var policy=new StickerPolicy(1,Duration.ZERO);var sent=new java.util.concurrent.CountDownLatch(1);var otherFinished=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->{try(var scope=policy.openTurn()){
                var id=UUID.randomUUID();var permit=policy.reserve(id,Instant.now());assertThat(permit.decision().allowed()).isTrue();policy.finish(permit.permit(),true,Instant.now());sent.countDown();
                assertThat(otherFinished.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(policy.evaluate(UUID.randomUUID(),Instant.now()).reason()).isEqualTo("per-turn limit");
            }return true;});
            var second=pool.submit(()->{assertThat(sent.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();try(var scope=policy.openTurn()){
                var permit=policy.reserve(UUID.randomUUID(),Instant.now());assertThat(permit.decision().allowed()).isTrue();policy.finish(permit.permit(),true,Instant.now());
            }finally{otherFinished.countDown();}return true;});
            assertThat(first.get(8,java.util.concurrent.TimeUnit.SECONDS)).isTrue();assertThat(second.get(8,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test void pendingTransportReservationPreventsConcurrentCooldownBypass()throws Exception{
        var policy=new StickerPolicy(1,Duration.ofSeconds(30));var now=Instant.now();
        try(var scope=policy.openTurn()){
            var reserved=policy.reserve(UUID.randomUUID(),now);assertThat(reserved.decision().allowed()).isTrue();
            try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()){
                assertThat(pool.submit(()->{try(var other=policy.openTurn()){return policy.reserve(UUID.randomUUID(),now).decision().reason();}}).get(5,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("cooldown");
            }
            policy.finish(reserved.permit(),false,now);
            var next=policy.reserve(UUID.randomUUID(),now);assertThat(next.decision().allowed()).isTrue();policy.finish(next.permit(),false,now);
        }
    }
}
