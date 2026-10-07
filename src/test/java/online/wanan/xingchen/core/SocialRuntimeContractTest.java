package online.wanan.xingchen.core;

import online.wanan.xingchen.adapter.onebot.GatewayStatus;
import online.wanan.xingchen.adapter.onebot.OneBotGateway;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.conversation.ConversationOrchestrator;
import online.wanan.xingchen.core.conversation.SocialRuntime;
import online.wanan.xingchen.core.model.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SocialRuntimeContractTest {
    @Test void run301_startSubscribesConnectsAndForwardsForcedPrivateReply() throws Exception {
        var gateway=mock(OneBotGateway.class);var orchestrator=mock(ConversationOrchestrator.class);var listener=new AtomicReference<java.util.function.Consumer<PlatformEvent>>();
        when(gateway.connect()).thenReturn(new GatewayStatus(true,"bot","connected"));when(gateway.status()).thenReturn(new GatewayStatus(true,"bot","connected"));doAnswer(i->{listener.set(i.getArgument(0));return null;}).when(gateway).setEventListener(any());
        var runtime=new SocialRuntime(gateway,orchestrator,new SocialTriggerPolicy("bot",java.util.List.of()),new ParticipationPolicy(()->.99),reserved(),()->0.0);
        runtime.start();var event=event(ConversationType.PRIVATE,"u1","m1");listener.get().accept(event);
        verify(orchestrator,timeout(2000)).handle(event,true);assertThat(runtime.isRunning()).isTrue();runtime.close();verify(gateway).disconnect();
    }

    @Test void run302_probabilityGateIsSeparateAndDeniedGroupEventsAreStillPersistedSilently() throws Exception {
        var gateway=mock(OneBotGateway.class);var orchestrator=mock(ConversationOrchestrator.class);var listener=new AtomicReference<java.util.function.Consumer<PlatformEvent>>();
        when(gateway.connect()).thenReturn(new GatewayStatus(true,"bot","connected"));when(gateway.status()).thenReturn(new GatewayStatus(true,"bot","connected"));doAnswer(i->{listener.set(i.getArgument(0));return null;}).when(gateway).setEventListener(any());
        var runtime=new SocialRuntime(gateway,orchestrator,new SocialTriggerPolicy("bot",java.util.List.of()),new ParticipationPolicy(()->.99),reserved(),()->.25);
        runtime.start();var event=event(ConversationType.GROUP,"u2","m2");listener.get().accept(event);
        verify(orchestrator,timeout(2000)).handle(event,false);runtime.close();
    }

    @Test void run303_accessDenialPrecedesWakeAndRandomParticipationEvenAtOneHundredPercent() throws Exception {
        var gateway=mock(OneBotGateway.class);var orchestrator=mock(ConversationOrchestrator.class);var access=mock(online.wanan.xingchen.console.AccessControlService.class);var listener=new AtomicReference<java.util.function.Consumer<PlatformEvent>>();
        when(gateway.connect()).thenReturn(new GatewayStatus(true,"bot","connected"));when(access.evaluate(anyString(),anyString(),anyString(),anyString(),anyBoolean())).thenReturn(new online.wanan.xingchen.console.AccessControlService.Decision(false,"DEFAULT_DENY"));doAnswer(i->{listener.set(i.getArgument(0));return null;}).when(gateway).setEventListener(any());
        var runtime=new SocialRuntime(gateway,orchestrator,new SocialTriggerPolicy("bot",java.util.List.of()),new ParticipationPolicy(()->0),reserved(),()->1.0);runtime.setAccessControlService(access);
        runtime.start();var event=event(ConversationType.GROUP,"u-denied","m-denied");listener.get().accept(event);
        verify(access,timeout(2000)).evaluate("QQ","u-denied","GROUP","g1",false);verifyNoInteractions(orchestrator);runtime.close();
    }

    @Test void audit_serializesWithinConversationButRunsOtherConversationsInParallel() throws Exception {
        var gateway=mock(OneBotGateway.class);var orchestrator=mock(ConversationOrchestrator.class);var listener=new AtomicReference<java.util.function.Consumer<PlatformEvent>>();
        when(gateway.connect()).thenReturn(new GatewayStatus(true,"bot","connected"));when(gateway.status()).thenReturn(new GatewayStatus(true,"bot","connected"));doAnswer(i->{listener.set(i.getArgument(0));return null;}).when(gateway).setEventListener(any());
        var firstStarted=new CountDownLatch(1);var releaseFirst=new CountDownLatch(1);var secondStarted=new CountDownLatch(1);var otherStarted=new CountDownLatch(1);var order=new ConcurrentLinkedQueue<String>();
        doAnswer(i->{PlatformEvent e=i.getArgument(0);String id=e.message().platformMessageId();if(id.equals("m1")){firstStarted.countDown();releaseFirst.await(2,TimeUnit.SECONDS);}if(id.equals("m2")){order.add(id);secondStarted.countDown();}if(id.equals("m3"))otherStarted.countDown();return null;}).when(orchestrator).handle(any(),anyBoolean());
        var runtime=new SocialRuntime(gateway,orchestrator,new SocialTriggerPolicy("bot",java.util.List.of()),new ParticipationPolicy(()->.99),reserved(),()->0.0);runtime.start();
        listener.get().accept(event(ConversationType.GROUP,"u1","m1"));assertThat(firstStarted.await(2,TimeUnit.SECONDS)).isTrue();listener.get().accept(event(ConversationType.GROUP,"u2","m2"));var other=event(ConversationType.GROUP,"u3","m3");listener.get().accept(new PlatformEvent(other.platform(),new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"g2"),other.actor(),other.message(),other.eventType(),other.actorPlatformRole(),other.text(),other.timestamp(),other.rawMetadata()));
        assertThat(otherStarted.await(2,TimeUnit.SECONDS)).isTrue();assertThat(secondStarted.await(100,TimeUnit.MILLISECONDS)).isFalse();releaseFirst.countDown();assertThat(secondStarted.await(2,TimeUnit.SECONDS)).isTrue();assertThat(new java.util.ArrayList<>(order)).containsExactly("m2");runtime.close();
    }

    private static ReservedModeMachine reserved(){return new ReservedModeMachine(Clock.systemUTC(),(delay,task)->new Scheduler.Cancellable(){public boolean cancel(){return true;}public boolean isCancelled(){return false;}},Duration.ofSeconds(10));}
    private static PlatformEvent event(ConversationType type,String user,String message){return new PlatformEvent(Platform.QQ,new ConversationIdentity(Platform.QQ,type,type==ConversationType.PRIVATE?user:"g1"),new ActorIdentity(Platform.QQ,user,user,false,false),new MessageIdentity(message,null),"message","member","hello",Instant.now(),java.util.Map.of());}
}
