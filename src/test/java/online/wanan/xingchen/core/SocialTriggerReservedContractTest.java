package online.wanan.xingchen.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.wanan.xingchen.adapter.onebot.OneBotEventNormalizer;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.model.PlatformEvent;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class SocialTriggerReservedContractTest {
    private final OneBotEventNormalizer normalizer=new OneBotEventNormalizer(new ObjectMapper());
    private PlatformEvent message(String type,String user,String text,String segments,String subtype){String json="{\"post_type\":\""+(subtype==null?"message":"notice")+"\",\"notice_type\":\"notify\",\"sub_type\":\""+Objects.toString(subtype,"")+"\",\"message_type\":\""+type+"\",\"self_id\":\"bot\",\"user_id\":\""+user+"\",\"group_id\":\"g1\",\"time\":1790000000,\"message_id\":\"m1\",\"sender\":{\"user_id\":\""+user+"\",\"nickname\":\"same\"},\"message\":"+segments+"}";return normalizer.normalize(json,Set.of("owner"));}
    private PlatformEvent text(String type,String user,String text){return message(type,user,text,"[{\"type\":\"text\",\"data\":{\"text\":\""+text+"\"}}]",null);}
    @Test void trig101_privateAndOwnerAreMustReply(){var policy=new SocialTriggerPolicy("bot",Set.of());assertThat(policy.evaluate(text("private","u1","hello"))).isEqualTo(SocialTrigger.MUST_REPLY);assertThat(policy.evaluate(text("group","owner","hello"))).isEqualTo(SocialTrigger.MUST_REPLY);}
    @Test void trig102_keywordCanForceMustReply(){assertThat(new SocialTriggerPolicy("bot",Set.of("help me")).evaluate(text("group","u","please HELP ME"))).isEqualTo(SocialTrigger.MUST_REPLY);}
    @Test void trig103_mentionBotIsMustReply(){var e=message("group","u","", "[{\"type\":\"at\",\"data\":{\"qq\":\"bot\"}}]",null);assertThat(new SocialTriggerPolicy("bot",Set.of()).evaluate(e)).isEqualTo(SocialTrigger.MUST_REPLY);}
    @Test void trig104_mentionMemberIsNotDirectedAtBot(){var e=message("group","u","", "[{\"type\":\"at\",\"data\":{\"qq\":\"other\"}},{\"type\":\"text\",\"data\":{\"text\":\"hello\"}}]",null);assertThat(new SocialTriggerPolicy("bot",Set.of()).evaluate(e)).isEqualTo(SocialTrigger.MAY_REPLY);}
    @Test void trig105_replyToBotIsMustReply(){var e=message("group","u","", "[{\"type\":\"reply\",\"data\":{\"id\":\"m0\",\"user_id\":\"bot\"}},{\"type\":\"text\",\"data\":{\"text\":\"follow up\"}}]",null);assertThat(new SocialTriggerPolicy("bot",Set.of()).evaluate(e)).isEqualTo(SocialTrigger.MUST_REPLY);}
    @Test void trig106_replyToAnotherMemberIsMayReply(){var e=message("group","u","", "[{\"type\":\"reply\",\"data\":{\"id\":\"m0\",\"user_id\":\"other\"}},{\"type\":\"text\",\"data\":{\"text\":\"follow up\"}}]",null);assertThat(new SocialTriggerPolicy("bot",Set.of()).evaluate(e)).isEqualTo(SocialTrigger.MAY_REPLY);}
    @Test void trig107_pokeBotIsMustReplyButPokeMemberIsIgnored(){var p=normalizer.normalize("{\"post_type\":\"notice\",\"notice_type\":\"notify\",\"sub_type\":\"poke\",\"user_id\":\"u\",\"group_id\":\"g1\",\"target_id\":\"other\",\"time\":1790000000}",Set.of());var n=normalizer.normalize("{\"post_type\":\"notice\",\"notice_type\":\"notify\",\"sub_type\":\"poke\",\"user_id\":\"u\",\"group_id\":\"g1\",\"target_id\":\"bot\",\"time\":1790000000}",Set.of());var policy=new SocialTriggerPolicy("bot",Set.of());assertThat(policy.evaluate(n)).isEqualTo(SocialTrigger.MUST_REPLY);assertThat(policy.evaluate(p)).isEqualTo(SocialTrigger.IGNORE);}
    @Test void trig108_groupConversationCanBeMayReply(){assertThat(new SocialTriggerPolicy("bot",Set.of()).evaluate(text("group","u","hello everyone"))).isEqualTo(SocialTrigger.MAY_REPLY);}
    @Test void trig110_questionBotNameAndManualWakeRetainWakeReasons(){
        var policy=new SocialTriggerPolicy("bot",Set.of(),Set.of("大肥鱼"));
        var question=policy.evaluate(text("group","u","大肥鱼，你好吗？"),Set.of());
        assertThat(question.trigger()).isEqualTo(SocialTrigger.MUST_REPLY);
        assertThat(question.reasons()).contains("bot-name","question");
        var manual=policy.evaluate(text("group","owner","/wake"),Set.of());
        assertThat(manual.reasons()).contains("owner","manual-wake");
    }
    @Test void trig111_configuredSpeakerIsAContextualWakeIndependentOfRandomChance(){
        var decision=new SocialTriggerPolicy("bot",Set.of()).evaluate(text("group","speaker","hello"),Set.of("speaker"));
        assertThat(decision.trigger()).isEqualTo(SocialTrigger.MUST_REPLY);
        assertThat(decision.reasons()).containsExactly("configured-speaker");
        assertThat(new ParticipationPolicy(()->0.99).participates(decision.trigger(),0)).isTrue();
    }
    @Test void trig112_emptyWhitespaceAndInvisibleOnlyMessagesNeverWakeEvenForPrivateOrOwner(){
        var policy=new SocialTriggerPolicy("bot",Set.of());
        assertThat(policy.evaluate(text("group","u1"," "))).isEqualTo(SocialTrigger.IGNORE);
        assertThat(policy.evaluate(text("private","u2","   "))).isEqualTo(SocialTrigger.IGNORE);
        var invisible=message("group","owner","","[{\"type\":\"text\",\"data\":{\"text\":\"\\u200b\\u2060\"}}]",null);
        assertThat(policy.evaluate(invisible)).isEqualTo(SocialTrigger.IGNORE);
        var mention=message("group","u3","","[{\"type\":\"at\",\"data\":{\"qq\":\"bot\"}}]",null);
        assertThat(policy.evaluate(mention)).isEqualTo(SocialTrigger.MUST_REPLY);
    }
    @Test void trig113_englishBotNameMustNotMatchInsideAnotherAsciiWord(){
        var policy=new SocialTriggerPolicy("bot",Set.of(),Set.of("bot"));
        assertThat(policy.evaluate(text("group","u1","robotics is neat"),Set.of()).reasons()).doesNotContain("bot-name");
        assertThat(policy.evaluate(text("group","u2","Hey, BOT!"),Set.of()).reasons()).contains("bot-name");
    }
    @Test void trig114_twentyRealRandomDrawsExerciseTheLiveOrdinaryProbabilityGate(){
        var participation=new ParticipationPolicy();int hits=0;
        for(int i=0;i<20;i++)if(participation.participates(SocialTrigger.MAY_REPLY,0.5))hits++;
        System.out.println("WAKE_RNG_SAMPLE_HITS="+hits+"/20");
        assertThat(hits).isBetween(1,19);
    }
    @Test void trig109_selfAndLifecycleNoticeAreIgnored(){var self=normalizer.normalize("{\"post_type\":\"message\",\"message_type\":\"private\",\"self_id\":\"bot\",\"user_id\":\"bot\",\"message_id\":\"m\",\"sender\":{\"user_id\":\"bot\"},\"message\":[]}",Set.of());var notice=message("group","u","", "[]","approve");var p=new SocialTriggerPolicy("bot",Set.of());assertThat(p.evaluate(self)).isEqualTo(SocialTrigger.IGNORE);assertThat(p.evaluate(notice)).isEqualTo(SocialTrigger.IGNORE);}
    @Test void part101_mustReplyIgnoresParticipationProbability(){var policy=new ParticipationPolicy(()->.99);assertThat(policy.participates(SocialTrigger.MUST_REPLY,0)).isTrue();}
    @Test void part102_probabilityClampsAtBoundsAndIgnoreAlwaysSkips(){var p=new ParticipationPolicy(()->.5);assertThat(p.participates(SocialTrigger.MAY_REPLY,-1)).isFalse();assertThat(p.participates(SocialTrigger.MAY_REPLY,2)).isTrue();assertThat(p.participates(SocialTrigger.IGNORE,1)).isFalse();}
    @Test void part103_nanProbabilityFailsClosed(){assertThat(new ParticipationPolicy(()->0).participates(SocialTrigger.MAY_REPLY,Double.NaN)).isFalse();}
    @Test void part104_randomParticipationUsesInjectedBoundaryAndZeroOneMeansNeverAlways(){assertThat(new ParticipationPolicy(()->0.999999).participates(SocialTrigger.MAY_REPLY,0)).isFalse();assertThat(new ParticipationPolicy(()->0.999999).participates(SocialTrigger.MAY_REPLY,1)).isTrue();assertThat(new ParticipationPolicy(()->0.49).participates(SocialTrigger.MAY_REPLY,0.5)).isTrue();assertThat(new ParticipationPolicy(()->0.5).participates(SocialTrigger.MAY_REPLY,0.5)).isFalse();}
    @Test void part105_explicitMentionAndReplyIgnoreZeroRandomChance(){var p=new ParticipationPolicy(()->0.999999);var mention=message("group","u","","[{\"type\":\"at\",\"data\":{\"qq\":\"bot\"}}]",null);var reply=message("group","u","","[{\"type\":\"reply\",\"data\":{\"id\":\"m0\",\"user_id\":\"bot\"}}]",null);var trigger=new SocialTriggerPolicy("bot",Set.of());assertThat(p.participates(trigger.evaluate(mention),0)).isTrue();assertThat(p.participates(trigger.evaluate(reply),0)).isTrue();}
    @Test void part106_selfMessagesNeverParticipateEvenAtOneHundredPercent(){var self=normalizer.normalize("{\"post_type\":\"message\",\"message_type\":\"group\",\"self_id\":\"bot\",\"user_id\":\"bot\",\"group_id\":\"g1\",\"message_id\":\"self\",\"sender\":{\"user_id\":\"bot\"},\"message\":\"hello\"}",Set.of());var trigger=new SocialTriggerPolicy("bot",Set.of()).evaluate(self);assertThat(new ParticipationPolicy(()->0).participates(trigger,1)).isFalse();}
    @Test void res101_startsObservingAndMayRemainQuiet(){var clock=new ManualClock();var scheduler=new ManualScheduler(clock);var mode=new ReservedModeMachine(clock,scheduler,Duration.ofSeconds(5));assertThat(mode.state()).isEqualTo(ReservedState.OBSERVING);assertThat(mode.onTrigger(SocialTrigger.MAY_REPLY)).isEqualTo(ReservedState.OBSERVING);}
    @Test void res102_mustTriggerActivatesAndSchedulerTransitionsToIdle(){var clock=new ManualClock();var scheduler=new ManualScheduler(clock);var mode=new ReservedModeMachine(clock,scheduler,Duration.ofSeconds(5));mode.onTrigger(SocialTrigger.MUST_REPLY);assertThat(mode.state()).isEqualTo(ReservedState.ACTIVE);scheduler.advance(Duration.ofSeconds(5));assertThat(mode.state()).isEqualTo(ReservedState.IDLE);}
    @Test void res103_activityRefreshesIdleTimer(){var clock=new ManualClock();var scheduler=new ManualScheduler(clock);var mode=new ReservedModeMachine(clock,scheduler,Duration.ofSeconds(5));mode.onMessage();scheduler.advance(Duration.ofSeconds(4));mode.onMessage();scheduler.advance(Duration.ofSeconds(4));assertThat(mode.state()).isEqualTo(ReservedState.ACTIVE);scheduler.advance(Duration.ofSeconds(1));assertThat(mode.state()).isEqualTo(ReservedState.IDLE);}
    @Test void res104_exitIsTerminalAndCancelsTimer(){var clock=new ManualClock();var scheduler=new ManualScheduler(clock);var mode=new ReservedModeMachine(clock,scheduler,Duration.ofSeconds(5));mode.onMessage();mode.exit();scheduler.advance(Duration.ofSeconds(10));assertThat(mode.state()).isEqualTo(ReservedState.EXITED);assertThat(mode.onMessage()).isEqualTo(ReservedState.EXITED);}
    @Test void res105_ignoreTriggerDoesNotActivate(){var clock=new ManualClock();var scheduler=new ManualScheduler(clock);var mode=new ReservedModeMachine(clock,scheduler,Duration.ofSeconds(5));assertThat(mode.onTrigger(SocialTrigger.IGNORE)).isEqualTo(ReservedState.OBSERVING);}
    @Test void resv101_v1UsesBoundedTimersRetryAndRejectsStaleTasks(){var clock=new ManualClock();var scheduler=new ManualScheduler(clock);var cfg=new ReservedV1Config(1,Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofSeconds(2),1,Duration.ofSeconds(3),Duration.ofSeconds(10),0,0,0,3,Duration.ofSeconds(5),Duration.ofSeconds(8));var mode=new ReservedV1Machine("conversation-1",clock,scheduler,cfg,()->0,()->{});var active=mode.onEvent(SocialTrigger.MAY_REPLY);assertThat(active.state()).isEqualTo(ReservedV1State.ACTIVE);assertThat(active.participate()).isTrue();assertThat(active.burstCount()).isBetween(1,3);scheduler.advance(Duration.ofSeconds(3));assertThat(mode.state()).isEqualTo(ReservedV1State.IDLE);mode.onEvent(SocialTrigger.MAY_REPLY);long retryGeneration=mode.generation();mode.exit();scheduler.advance(Duration.ofSeconds(5));assertThat(mode.state()).isEqualTo(ReservedV1State.EXITING);assertThat(mode.generation()).isGreaterThan(retryGeneration);}

    private static final class ManualClock extends Clock {private final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));void advance(Duration d){now.updateAndGet(i->i.plus(d));}@Override public ZoneId getZone(){return ZoneOffset.UTC;}@Override public Clock withZone(ZoneId zone){return this;}@Override public Instant instant(){return now.get();}}
    private static final class ManualScheduler implements Scheduler {private record Task(Instant at,Runnable runnable,AtomicReference<Boolean> cancelled){}private final ManualClock clock;private final List<Task> tasks=new ArrayList<>();ManualScheduler(ManualClock clock){this.clock=clock;}@Override public Cancellable schedule(Duration delay,Runnable task){AtomicReference<Boolean> c=new AtomicReference<>(false);tasks.add(new Task(clock.instant().plus(delay),task,c));return new Cancellable(){public boolean cancel(){return c.getAndSet(true)==false;}public boolean isCancelled(){return c.get();}};}void advance(Duration duration){clock.advance(duration);for(Task t:List.copyOf(tasks))if(!t.cancelled().get()&&!t.at().isAfter(clock.instant())){t.cancelled().set(true);t.runnable().run();}}}
}
