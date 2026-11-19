package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.onebot.*;
import online.wanan.xingchen.core.conversation.ReplyDispatcher;
import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.core.relationship.AddressResolution;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReplyDispatcherContractTest {
    @Test void rateLimitedPartsAreExplicitlyRejectedRatherThanSilentlyDropped() {
        var gateway=new MockOneBotGateway(new OneBotEventNormalizer(new ObjectMapper()),"bot");gateway.connect();
        var dispatcher=new ReplyDispatcher(gateway,5,100,1,Duration.ZERO,Clock.systemUTC(),d->{});
        var result=dispatcher.dispatch(actor(),List.of("one","two","three"));
        assertThat(result).hasSize(3);
        assertThat(result.get(0).accepted()).isTrue();
        assertThat(result.subList(1,3)).allSatisfy(r->{assertThat(r.status()).isEqualTo(DeliveryStatus.REJECTED);assertThat(r.content()).isEqualTo("rate-limited");});
        assertThat(gateway.sent()).hasSize(1);
    }

    @Test void slowTransportDoesNotHoldTheSharedRateLimitLock() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var gateway=org.mockito.Mockito.mock(OneBotGateway.class);
        org.mockito.Mockito.when(gateway.sendGroupMessage(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation->{
            if(invocation.getArgument(1).equals("slow")){entered.countDown();if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("test send not released");}
            return new SendReceipt(true,"group","synthetic","ok");
        });
        var dispatcher=new ReplyDispatcher(gateway,5,100,10,Duration.ZERO,Clock.systemUTC(),d->{});
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->dispatcher.dispatch(actor(),List.of("slow")));
            try{assertThat(entered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(pool.submit(()->dispatcher.dispatch(actor(),List.of("fast"))).get(1,java.util.concurrent.TimeUnit.SECONDS)).singleElement().satisfies(r->assertThat(r.accepted()).isTrue());
            }finally{release.countDown();}
            assertThat(first.get(2,java.util.concurrent.TimeUnit.SECONDS)).singleElement().satisfies(r->assertThat(r.accepted()).isTrue());
        }
    }
    @Test void reply301_boundsBurstAndTextAndAppliesConfiguredGap() {
        var gateway=new MockOneBotGateway(new OneBotEventNormalizer(new ObjectMapper()),"bot");gateway.connect();var gaps=new ArrayList<Duration>();
        var dispatcher=new ReplyDispatcher(gateway,2,4,10,Duration.ofMillis(25),Clock.systemUTC(),gaps::add);
        dispatcher.dispatch(actor(),List.of("abcdef","two","not-sent"));
        assertThat(gateway.sent()).hasSize(2).extracting(SendReceipt::content).containsExactly("abc…","two");assertThat(gaps).containsExactly(Duration.ofMillis(25));
    }
    @Test void reply302_rateLimitSpansTurnsAndExpiresAfterWindow() {
        var gateway=new MockOneBotGateway(new OneBotEventNormalizer(new ObjectMapper()),"bot");gateway.connect();var clock=new MutableClock();var dispatcher=new ReplyDispatcher(gateway,5,100,1,Duration.ZERO,clock,d->{});
        dispatcher.dispatch(actor(),List.of("one"));dispatcher.dispatch(actor(),List.of("two"));assertThat(gateway.sent()).hasSize(1);
        clock.advance(Duration.ofSeconds(61));dispatcher.dispatch(actor(),List.of("three"));assertThat(gateway.sent()).hasSize(2);
    }
    private static ResolvedActorContext actor(){var person=new Person(UUID.randomUUID(),Platform.QQ,"u1",false,false);var c=new Conversation(UUID.randomUUID(),new ConversationIdentity(Platform.QQ,ConversationType.GROUP,"g1"));var now=Instant.now();var member=new Membership(UUID.randomUUID(),person.id(),c.id(),"User",null,IdentityRole.MEMBER,now,now);return new ResolvedActorContext(person,c,member,new ActorFlags(false,false,false,false),"User",new AddressResolution("User","",null,false,0));}
    private static final class MutableClock extends Clock {private Instant now=Instant.parse("2026-01-01T00:00:00Z");void advance(Duration d){now=now.plus(d);}@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId z){return this;}@Override public Instant instant(){return now;}}
}
