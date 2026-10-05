package online.wanan.xingchen.core.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.context.*;
import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.memory.RequestContext;
import online.wanan.xingchen.core.relationship.AddressResolution;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationResetInterruptTest {
    @Test void resetInterruptFencesBlockedModelResultBeforeToolOrOutputDecision() throws Exception {
        UUID conversationId=UUID.randomUUID();var generation=new AtomicLong(7);var registry=new ActiveTurnRegistry();var token=new CancellationToken();
        var person=new Person(UUID.randomUUID(),Platform.QQ,"owner",false,true);var conversation=new Conversation(conversationId,new ConversationIdentity(Platform.QQ,ConversationType.PRIVATE,"owner"));var now=Instant.now();var membership=new Membership(UUID.randomUUID(),person.id(),conversationId,"owner",null,IdentityRole.OWNER,now,now);var actor=new ResolvedActorContext(person,conversation,membership,new ActorFlags(false,true,false,false),"owner",new AddressResolution("姐姐","fixture",null,false,0));
        var context=new ContextBuilder(ContextBudget.defaults()).build(new ContextBuildInput("simulation","persona","qq:owner","owner","姐姐","owner",List.of(),"",List.of(),Map.of(),"old input","",new RequestContext(person.id(),conversationId,true,null)));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var toolEffects=new AtomicInteger();var security=new InMemorySecurityEventSink();
        ModelProvider blocked=new ModelProvider(){public ModelResponse complete(ModelRequest request){throw new UnsupportedOperationException();}public ModelResponse stream(ModelRequest request,java.util.function.Consumer<ModelStreamEvent> events){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("model test barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new CancellationException();}return new ModelResponse(AgentDecision.text("OLD RESPONSE"),null,null,List.of(new ModelToolCall("call-old","qq.reply",Map.of("text","OLD RESPONSE"))),ModelFinishReason.TOOL_CALLS);}};
        var executor=new AgentExecutor(blocked,(call,request,caps)->{toolEffects.incrementAndGet();return new ToolResult(true,"sent",Map.of());},new ContextBuilder(ContextBudget.defaults()),new AgentToolCatalog(),security,4,120000,200,new ApproximateTokenEstimator());
        var request=new AgentRequest(actor,context,Set.of(AgentCapability.QQ_REPLY),"event-reset-race","turn-old",null,token);var output=new AtomicReference<AgentExecutionResult>();var failure=new AtomicReference<Throwable>();
        assertThat(registry.beginIfCurrent(conversationId,"turn-old",7,token,generation::get)).isNotNull();
        Thread model=new Thread(()->{try{output.set(executor.execute(request));}catch(Throwable t){failure.set(t);}});model.start();
        try{assertThat(entered.await(5,TimeUnit.SECONDS)).as("model entered deterministic barrier").isTrue();long next=registry.invalidateAndCancel(conversationId,()->generation.incrementAndGet());assertThat(next).isEqualTo(8);assertThat(token.isCancelled()).isTrue();release.countDown();model.join(5000);assertThat(model.isAlive()).as("test infrastructure timeout, not a runtime deadlock assertion").isFalse();assertThat(failure.get()).isNull();assertThat(output.get().decision().type()).isEqualTo(DecisionType.NO_REPLY);assertThat(output.get().stopReason()).isEqualTo("cancelled");assertThat(toolEffects).hasValue(0);}
        finally{release.countDown();model.join(5000);}
    }
}
