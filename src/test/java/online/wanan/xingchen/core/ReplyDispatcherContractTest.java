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
