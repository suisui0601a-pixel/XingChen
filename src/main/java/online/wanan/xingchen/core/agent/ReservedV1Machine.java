package online.wanan.xingchen.core.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.DoubleSupplier;

/** V1 social mode with cancellable, conversation- and generation-bound timers. */
public final class ReservedV1Machine implements AutoCloseable {
    private final String conversationId;private final Clock clock;private final Scheduler scheduler;private final ReservedV1Config config;private final DoubleSupplier random;private final Runnable proactiveAction;
    private ReservedV1State state=ReservedV1State.WATCHING;private long generation;private Instant lastActivity,activeUntil;private boolean proactiveEnabled;private final List<Scheduler.Cancellable> tasks=new ArrayList<>();
    public ReservedV1Machine(String conversationId,Clock clock,Scheduler scheduler,ReservedV1Config config,DoubleSupplier random,Runnable proactiveAction){if(conversationId==null||conversationId.isBlank())throw new IllegalArgumentException("conversation id required");this.conversationId=conversationId;this.clock=Objects.requireNonNull(clock);this.scheduler=Objects.requireNonNull(scheduler);this.config=Objects.requireNonNull(config);this.random=Objects.requireNonNull(random);this.proactiveAction=Objects.requireNonNull(proactiveAction);this.lastActivity=clock.instant();}
    public synchronized Decision onEvent(SocialTrigger trigger){if(state==ReservedV1State.EXITING)return decision(false,"exiting",0);if(trigger==SocialTrigger.IGNORE)return decision(false,"ignored",0);if(trigger==SocialTrigger.MUST_REPLY){activate();return decision(true,"must-reply",1);}if(state==ReservedV1State.ACTIVE){lastActivity=clock.instant();if(draw()<config.surrenderProbability()){idle();return decision(false,"surrender",0);}if(draw()<config.skipProbability())return decision(false,"skip",0);cancelTasks();scheduleActiveCheck();scheduleActiveExpiry();if(proactiveEnabled)scheduleProactive();return decision(true,"active",burst());}if(state==ReservedV1State.WATCHING){if(draw()>=config.triggerProbability()||draw()<config.skipProbability())return decision(false,"watching",0);activate();return decision(true,"triggered",burst());}if(state==ReservedV1State.IDLE){if(draw()>=config.idleRetryProbability())return decision(false,"idle-retry-skipped",0);long expected=generation;String conversation=conversationId;schedule(config.idleRetryWait(),expected,()->{if(valid(conversation,expected,ReservedV1State.IDLE))activate();});return decision(false,"idle-retry-scheduled",0);}return decision(false,"ignored",0);}
    public synchronized void forceIdle(){if(state!=ReservedV1State.EXITING)idle();}
    public synchronized void exit(){if(state==ReservedV1State.EXITING)return;state=ReservedV1State.EXITING;generation++;cancelTasks();}
    public synchronized ReservedV1State state(){return state;}public synchronized long generation(){return generation;}public String conversationId(){return conversationId;}public synchronized Instant lastActivity(){return lastActivity;}
    public synchronized Duration nextLongGap(){return randomDuration(config.longGapMin(),config.longGapMax());}
    private void activate(){state=ReservedV1State.ACTIVE;generation++;lastActivity=clock.instant();activeUntil=lastActivity.plus(config.activeDuration());proactiveEnabled=draw()<config.proactiveProbability();cancelTasks();scheduleActiveCheck();scheduleActiveExpiry();if(proactiveEnabled)scheduleProactive();}
    private void idle(){state=ReservedV1State.IDLE;generation++;cancelTasks();}
    private void scheduleActiveCheck(){long expected=generation;String conversation=conversationId;Duration delay=randomDuration(config.activeCheckMin(),config.activeCheckMax());schedule(delay,expected,()->{if(!valid(conversation,expected,ReservedV1State.ACTIVE))return;if(Duration.between(lastActivity,clock.instant()).compareTo(config.idleWindow())>=0)idle();else scheduleActiveCheck();});}
    private void scheduleActiveExpiry(){long expected=generation;Duration remaining=Duration.between(clock.instant(),activeUntil);if(!remaining.isNegative()&&!remaining.isZero())schedule(remaining,expected,()->{if(generation==expected&&state==ReservedV1State.ACTIVE)idle();});else idle();}
    private void scheduleProactive(){long expected=generation;String conversation=conversationId;Duration delay=nextLongGap();schedule(delay,expected,()->{if(valid(conversation,expected,ReservedV1State.ACTIVE))proactiveAction.run();});}
    private boolean valid(String conversation,long expected,ReservedV1State expectedState){return conversationId.equals(conversation)&&generation==expected&&state==expectedState;}
    private int burst(){return 1+(int)Math.floor(draw()*config.burstMax());}
    private double draw(){return Math.min(Math.nextDown(1.0),Math.max(0.0,random.getAsDouble()));}
    private Duration randomDuration(Duration min,Duration max){long a=min.toMillis(),b=max.toMillis();return Duration.ofMillis(a+(long)((b-a)*draw()));}
    private void schedule(Duration delay,long expected,Runnable action){java.util.concurrent.atomic.AtomicReference<Scheduler.Cancellable> ref=new java.util.concurrent.atomic.AtomicReference<>();var task=scheduler.schedule(delay,()->{synchronized(this){tasks.remove(ref.get());if(expected==generation&&state!=ReservedV1State.EXITING)action.run();}});ref.set(task);tasks.add(task);}
    private void cancelTasks(){for(var task:tasks)task.cancel();tasks.clear();}
    private Decision decision(boolean participate,String reason,int burst){return new Decision(conversationId,generation,state,participate,reason,burst);}
    @Override public void close(){exit();}
    public record Decision(String conversationId,long generation,ReservedV1State state,boolean participate,String reason,int burstCount){}
}
