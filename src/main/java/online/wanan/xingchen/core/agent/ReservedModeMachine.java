package online.wanan.xingchen.core.agent;

import java.time.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Small V1 reserved-mode state machine; timeouts are injected through Clock and Scheduler. */
public final class ReservedModeMachine implements AutoCloseable {
    private final Clock clock;private final Scheduler scheduler;private final Duration idleAfter;private final AtomicReference<ReservedState> state=new AtomicReference<>(ReservedState.OBSERVING);private volatile Instant lastActivity;private volatile Scheduler.Cancellable idleTask;
    public ReservedModeMachine(Clock clock,Scheduler scheduler,Duration idleAfter){this.clock=Objects.requireNonNull(clock);this.scheduler=Objects.requireNonNull(scheduler);this.idleAfter=Objects.requireNonNull(idleAfter);this.lastActivity=clock.instant();}
    public synchronized ReservedState onTrigger(SocialTrigger trigger){if(state.get()==ReservedState.EXITED||trigger==SocialTrigger.IGNORE)return state.get();if(trigger==SocialTrigger.MUST_REPLY){state.set(ReservedState.ACTIVE);touch();}return state.get();}
    public synchronized ReservedState onMessage(){if(state.get()==ReservedState.EXITED)return ReservedState.EXITED;state.set(ReservedState.ACTIVE);touch();return state.get();}
    public synchronized ReservedState markIdle(){if(state.get()!=ReservedState.EXITED)state.set(ReservedState.IDLE);return state.get();}
    public synchronized ReservedState exit(){state.set(ReservedState.EXITED);if(idleTask!=null)idleTask.cancel();return state.get();}
    public ReservedState state(){return state.get();}public Instant lastActivity(){return lastActivity;}
    private void touch(){lastActivity=clock.instant();if(idleTask!=null)idleTask.cancel();idleTask=scheduler.schedule(idleAfter,()->{synchronized(this){if(state.get()!=ReservedState.EXITED&&Duration.between(lastActivity,clock.instant()).compareTo(idleAfter)>=0)state.set(ReservedState.IDLE);}});}
    @Override public void close(){exit();}
}
