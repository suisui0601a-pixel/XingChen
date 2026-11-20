package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.console.AccessControlService;
import online.wanan.xingchen.console.SocialSettingsService;
import online.wanan.xingchen.core.agent.*;
import online.wanan.xingchen.core.model.*;
import online.wanan.xingchen.storage.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

/** A bounded timer producer for the existing conversation lanes; never a second OneBot consumer. */
@Service
public final class ProactiveParticipationService implements AutoCloseable {
    private static final System.Logger LOG=System.getLogger(ProactiveParticipationService.class.getName());
    private final ProactiveAttemptStore store;
    private final SocialSettingsService settings;
    private final AccessControlService access;
    private final SimulationStateService simulation;
    private final ConversationOrchestrator orchestrator;
    private final Clock clock;
    private final Scheduler scheduler;
    private final DoubleSupplier random;
    private final Map<String,CancellationToken> inFlight=new ConcurrentHashMap<>();
    private RuntimePort runtime;
    private Scheduler.Cancellable timer;
    private volatile boolean running;
    private long timerEpoch;
    private String cursor="";
    public interface RuntimePort {
        boolean running();
        boolean idle(String lane);
        boolean enqueue(String lane,Runnable task);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public ProactiveParticipationService(ProactiveAttemptStore store,SocialSettingsService settings,AccessControlService access,
            SimulationStateService simulation,ConversationOrchestrator orchestrator,Clock clock,Scheduler scheduler){
        this(store,settings,access,simulation,orchestrator,clock,scheduler,Math::random);
    }
    public ProactiveParticipationService(ProactiveAttemptStore store,SocialSettingsService settings,AccessControlService access,
            SimulationStateService simulation,ConversationOrchestrator orchestrator,Clock clock,Scheduler scheduler,DoubleSupplier random){
        this.store=store;this.settings=settings;this.access=access;this.simulation=simulation;this.orchestrator=orchestrator;
        this.clock=clock;this.scheduler=scheduler;this.random=random;
    }
    public synchronized void start(RuntimePort port){if(running)return;runtime=port;store.interruptClaimed(clock.instant());running=true;schedule(++timerEpoch);}
    private void schedule(long epoch){timer=scheduler.schedule(Duration.ofMinutes(1),()->{
        synchronized(this){
            // cancel(false) cannot retire an already-dispatched callback. Its original
            // epoch must still match both before scanning and before renewing the lease.
            if(!running||epoch!=timerEpoch)return;
            try{tick();}catch(RuntimeException failure){LOG.log(System.Logger.Level.WARNING,"proactive scan failed; no automatic turn replay ({0})",failure.getClass().getSimpleName());}
            finally{if(running&&epoch==timerEpoch)schedule(epoch);}
        }
    });}
    /** Public for deterministic isolated checks; serialized scans and four bounded in-flight jobs. */
    public synchronized void tick(){
        if(!running||!runtime.running())return;
        Instant now=clock.instant();store.pruneKnownHistory(now);var candidates=store.candidates(now,cursor);
        if(candidates.isEmpty()&&!cursor.isEmpty())candidates=store.candidates(now,"");
        cursor=candidates.isEmpty()?"":candidates.getLast().conversationId().toString();
        for(var c:candidates){
            if(inFlight.size()>=4)break;
            if(!runtime.idle(c.lane())||inFlight.containsKey(c.lane()))continue;
            var p=settings.proactive(c.conversationId());var state=simulation.get(c.conversationId());var calendar=settings.schedule(c.conversationId());
            var intent=calendar.due(now).stream().filter(i->store.greetingPending(c,i)).findFirst().orElse(SocialSchedule.Intent.random());
            if(!eligible(c,p,calendar,intent,state,now))continue;
            String id;
            if(intent.greeting())id=store.reserveGreeting(c,p,now,intent);
            else{
                if(!store.due(c.conversationId(),p,now))continue;
                double draw=random.getAsDouble();if(!Double.isFinite(draw)||draw<0||draw>=1)continue;
                boolean admitted=draw<p.probability();id=store.reserve(c,p,now,admitted);if(!admitted)continue;
            }
            if(id==null)continue;
            final String attemptId=id;var token=new CancellationToken();inFlight.put(c.lane(),token);
            if(!runtime.enqueue(c.lane(),()->run(c,p,calendar,intent,state.generation(),attemptId,token))){inFlight.remove(c.lane(),token);store.finish(id,"CANCELLED",clock.instant());}
        }
    }
    private boolean eligible(ProactiveAttemptStore.Candidate c,ProactivePolicy p,SocialSchedule calendar,SocialSchedule.Intent intent,SimulationConversationState state,Instant now){
        var event=c.anchor();var identity=event.conversation();
        if(event.platform()!=Platform.QQ||event.actor().self()||!event.eventType().equals("message")||!p.scopeEnabled(identity.type())||calendar.muted(now)||!intent.current(now)
                ||(intent.greeting()?(!calendar.greetingsEnabled()||!calendar.due(now).contains(intent)):(!p.enabled(identity.type())||p.quiet(now)))
                ||state.lastReadCursor()==null||!state.mode().equals("SOCIAL")||state.wakeState()==SimulationConversationState.WakeState.WAITING||state.wakeState()==SimulationConversationState.WakeState.SLEEPING)return false;
        if(!SocialTriggerPolicy.hasVisibleText(event.text())&&event.details().mentionIds().isEmpty()&&event.details().reply()==null)return false;
        if(identity.type()==ConversationType.PRIVATE&&!identity.platformConversationId().equals(event.actor().platformUserId()))return false;
        // Owner recovery privileges must not turn an unapproved group into a timer target.
        String actor=identity.type()==ConversationType.GROUP?"":event.actor().platformUserId();
        if(!access.evaluate("QQ",actor,identity.type().name(),identity.platformConversationId(),false).allowed())return false;
        Instant latest=event.timestamp();if(state.lastIncomingAt()!=null&&state.lastIncomingAt().isAfter(latest))latest=state.lastIncomingAt();
        if(state.lastActionAt()!=null&&state.lastActionAt().isAfter(latest))latest=state.lastActionAt();
        return (intent.greeting()||!latest.isAfter(now.minus(Duration.ofMinutes(p.idleMinutes()))))&&!event.timestamp().isBefore(now.minus(Duration.ofHours(24)))&&store.current(c);
    }
    private void run(ProactiveAttemptStore.Candidate c,ProactivePolicy policy,SocialSchedule calendar,SocialSchedule.Intent intent,long generation,String id,CancellationToken token){
        String turnId=ConversationOrchestrator.proactiveTurnId(c.anchor(),c.conversationId(),id);
        try{
            token.generationFence(()->current(c,policy,calendar,intent,generation));
            token.runWithCurrentGeneration(()->{});
            var state=simulation.get(c.conversationId());
            orchestrator.handleProactive(c.anchor(),c.conversationId(),id,state,token,settings.capture(c.conversationId(),state.wakeConfig()),decision->store.output(id,decision.messages()),intent,clock.instant().atZone(calendar.zone()));
            token.runWithCurrentGeneration(()->{});
            store.finish(id,store.deliveryStatus(turnId),clock.instant());
        }catch(java.util.concurrent.CancellationException cancelled){String delivery=store.deliveryStatus(turnId);store.finish(id,delivery.equals("NO_REPLY")?"CANCELLED":delivery,clock.instant());}
        catch(RuntimeException failure){String delivery=store.deliveryStatus(turnId);store.finish(id,delivery.equals("NO_REPLY")?"FAILED":delivery,clock.instant());LOG.log(System.Logger.Level.WARNING,"proactive turn stopped; no retry ({0})",failure.getClass().getSimpleName());}
        finally{inFlight.remove(c.lane(),token);}
    }
    private boolean current(ProactiveAttemptStore.Candidate c,ProactivePolicy policy,SocialSchedule calendar,SocialSchedule.Intent intent,long generation){
        return running&&runtime.running()&&simulation.get(c.conversationId()).generation()==generation
                &&policy.equals(settings.proactive(c.conversationId()))&&calendar.equals(settings.schedule(c.conversationId()))&&eligible(c,policy,calendar,intent,simulation.get(c.conversationId()),clock.instant());
    }
    /** Real input wins over idle intent, including an input queued behind an in-flight model request. */
    public void incoming(PlatformEvent event){if(!event.actor().self()&&event.eventType().equals("message")){var c=event.conversation();var token=inFlight.get(c.platform()+":"+c.type()+":"+c.platformConversationId());if(token!=null)token.cancel();}}
    public void interruptConversation(String lane){var token=inFlight.get(lane);if(token!=null)token.cancel();}
    public int inFlightCount(){return inFlight.size();}
    public Map<String,Object> diagnostics(){return Map.of("running",running&&runtime!=null&&runtime.running(),"inFlight",inFlight.size(),"scanIntervalSeconds",60,"globalHourlyTurnLimit",4,"globalHourlyGreetingLimit",4,"history",store.diagnostics());}
    @Override public synchronized void close(){running=false;timerEpoch++;if(timer!=null){timer.cancel();timer=null;}inFlight.values().forEach(CancellationToken::cancel);inFlight.clear();}
}
