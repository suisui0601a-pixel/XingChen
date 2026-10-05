package online.wanan.xingchen.core.agent;

import online.wanan.xingchen.storage.SqliteSimulationStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** Persisted reserved2/simulation transitions. Transport-specific message fetching remains behind tools/adapters. */
@Service
public final class SimulationStateService {
    private final SqliteSimulationStateRepository repository; private final Clock clock;
    @Autowired public SimulationStateService(SqliteSimulationStateRepository repository,Clock clock){this.repository=repository;this.clock=clock;}
    public SimulationConversationState get(UUID conversation){var s=repository.load(conversation);return s==null?initial(conversation):s;}
    public SimulationConversationState configure(UUID conversation,Map<String,Object> wakeConfig,boolean owner){requireOwner(owner);var s=get(conversation);return save(s,s.mode(),s.wakeState(),s.lastReadCursor(),s.lastActionAt(),s.lastIncomingAt(),s.sleepUntil(),validated(wakeConfig),s.consecutiveNoAction());}
    public SimulationConversationState setMode(UUID conversation,String mode,boolean owner){String normalized=Objects.requireNonNull(mode).toUpperCase(Locale.ROOT);if(!Set.of("SOCIAL","CLOSED_AGENT").contains(normalized))throw new IllegalArgumentException("unsupported simulation mode");if(normalized.equals("CLOSED_AGENT"))requireOwner(owner);var s=get(conversation);return save(s,normalized,s.wakeState(),s.lastReadCursor(),s.lastActionAt(),s.lastIncomingAt(),s.sleepUntil(),s.wakeConfig(),s.consecutiveNoAction());}
    public SimulationConversationState changeMode(UUID conversation,String mode,long expectedGeneration,boolean owner){String normalized=Objects.requireNonNull(mode).toUpperCase(Locale.ROOT);if(!Set.of("SOCIAL","CLOSED_AGENT").contains(normalized))throw new IllegalArgumentException("unsupported simulation mode");if(normalized.equals("CLOSED_AGENT"))requireOwner(owner);if(!repository.changeMode(conversation,expectedGeneration,normalized,clock.instant()))throw new java.util.ConcurrentModificationException("conversation generation changed");return get(conversation);}
    public SimulationConversationState sleep(UUID conversation,Instant until,boolean owner){requireOwner(owner);if(until!=null&&!until.isAfter(clock.instant()))throw new IllegalArgumentException("sleep deadline must be in the future");var s=get(conversation);return save(s,s.mode(),SimulationConversationState.WakeState.SLEEPING,s.lastReadCursor(),s.lastActionAt(),s.lastIncomingAt(),until,s.wakeConfig(),s.consecutiveNoAction());}
    public SimulationConversationState wake(UUID conversation,boolean owner){requireOwner(owner);var s=get(conversation);return save(s,s.mode(),SimulationConversationState.WakeState.AWAKE,s.lastReadCursor(),s.lastActionAt(),s.lastIncomingAt(),null,s.wakeConfig(),0);}
    public SimulationConversationState wake(UUID conversation,long expectedGeneration,boolean owner){requireOwner(owner);if(get(conversation).wakeState()==SimulationConversationState.WakeState.WAITING)throw new IllegalStateException("WAITING conversations must be reset to retire the pending turn safely");if(!repository.wake(conversation,expectedGeneration,clock.instant()))throw new java.util.ConcurrentModificationException("conversation generation changed");return get(conversation);}
    /** Event-triggered wake is distinct from the owner-only administrative wake command. */
    public SimulationConversationState wakeFromTrigger(UUID conversation){var s=get(conversation);if(s.wakeState()==SimulationConversationState.WakeState.AWAKE)return s;return save(s,s.mode(),SimulationConversationState.WakeState.AWAKE,s.lastReadCursor(),s.lastActionAt(),s.lastIncomingAt(),null,s.wakeConfig(),0);}
    public SimulationConversationState waitForNextMessage(UUID conversation,String originTurnId,String reason){
        if(originTurnId==null||originTurnId.isBlank())throw new IllegalArgumentException("origin turn id is required");
        var old=get(conversation);Instant now=clock.instant();
        var waiting=new SimulationConversationState(old.conversationId(),old.mode(),SimulationConversationState.WakeState.WAITING,
                old.lastReadCursor(),old.lastActionAt(),old.lastIncomingAt(),null,old.wakeConfig(),old.consecutiveNoAction(),
                old.generation()+1,now,originTurnId,now,reason==null?"next-message":reason);
        repository.save(waiting);return waiting;
    }
    public Optional<SimulationConversationState> continueWait(UUID conversation,long expectedGeneration){
        if(!repository.claimWait(conversation,expectedGeneration,"WAIT_CONTINUATION",clock.instant()))return Optional.empty();
        return Optional.of(get(conversation));
    }
    public Set<String> configuredSpeakers(SimulationConversationState state){return speakers(state.wakeConfig());}
    public SimulationConversationState markRead(UUID conversation,String cursor){if(cursor==null||cursor.isBlank())throw new IllegalArgumentException("read cursor is required");var s=get(conversation);if(cursor.equals(s.lastReadCursor()))return s;if(s.lastReadCursor()!=null){try{if(new java.math.BigInteger(cursor).compareTo(new java.math.BigInteger(s.lastReadCursor()))<=0)throw new IllegalArgumentException("read cursor cannot move backwards");}catch(NumberFormatException e){throw new IllegalArgumentException("cannot prove cursor ordering for non-numeric platform IDs");}}return save(s,s.mode(),s.wakeState(),cursor,s.lastActionAt(),s.lastIncomingAt(),s.sleepUntil(),s.wakeConfig(),s.consecutiveNoAction());}
    public SimulationConversationState recordAction(UUID conversation){var s=get(conversation);return save(s,s.mode(),SimulationConversationState.WakeState.AWAKE,s.lastReadCursor(),clock.instant(),s.lastIncomingAt(),null,s.wakeConfig(),0);}
    public SimulationConversationState recordNoAction(UUID conversation){var s=get(conversation);return save(s,s.mode(),s.wakeState(),s.lastReadCursor(),s.lastActionAt(),s.lastIncomingAt(),s.sleepUntil(),s.wakeConfig(),Math.addExact(s.consecutiveNoAction(),1));}
    /** Fences all callbacks from the prior generation and retires WAIT without discarding policy. */
    public SimulationConversationState reset(UUID conversation){var old=get(conversation);var reset=new SimulationConversationState(old.conversationId(),old.mode(),SimulationConversationState.WakeState.IDLE,null,old.lastActionAt(),old.lastIncomingAt(),null,old.wakeConfig(),0,Math.addExact(old.generation(),1),clock.instant(),null,null,null);repository.save(reset);return reset;}
    /** Phase-one reset interrupt: atomically advance only generation; serialized cleanup follows in the lane. */
    public long invalidateForReset(UUID conversation){var snapshot=get(conversation);return repository.invalidateGeneration(snapshot,clock.instant());}
    public long invalidateForReset(UUID conversation,long expectedGeneration){long next=repository.invalidateGeneration(conversation,expectedGeneration,clock.instant());if(next<0)throw new java.util.ConcurrentModificationException("conversation generation changed");return next;}
    /** Phase-two cleanup preserves the generation written by the control interrupt. */
    public boolean cleanupReset(UUID conversation,long expectedGeneration){return repository.cleanupReset(conversation,expectedGeneration,clock.instant());}
    public SimulationConversationState markIdle(UUID conversation){var s=get(conversation);return save(s,s.mode(),SimulationConversationState.WakeState.IDLE,s.lastReadCursor(),s.lastActionAt(),s.lastIncomingAt(),s.sleepUntil(),s.wakeConfig(),s.consecutiveNoAction());}
    public IncomingTransition onIncoming(UUID conversation,IncomingSignal signal){return onIncoming(conversation,signal,null);}
    public IncomingTransition onIncoming(UUID conversation,IncomingSignal signal,SocialSettingsSnapshot settings){var s=get(conversation);Instant now=clock.instant();boolean expired=s.wakeState()==SimulationConversationState.WakeState.SLEEPING&&s.sleepUntil()!=null&&!s.sleepUntil().isAfter(now);boolean triggered=matches(s.wakeConfig(),signal);if(settings!=null)triggered|=(settings.mentionWake()&&signal.mentioned())||(settings.replyToBotWake()&&signal.replyToBot())||(settings.pokeWake()&&signal.poke())||(settings.botNameWake()&&signal.nameMentioned())||(settings.questionWake()&&signal.question())||(settings.configuredSpeakerWake()&&settings.configuredSpeakerIds().stream().anyMatch(x->x.equalsIgnoreCase("QQ:"+signal.speakerId())));var next=expired||triggered?SimulationConversationState.WakeState.AWAKE:s.wakeState();Instant sleepUntil=next==SimulationConversationState.WakeState.AWAKE?null:s.sleepUntil();var saved=save(s,s.mode(),next,s.lastReadCursor(),s.lastActionAt(),now,sleepUntil,s.wakeConfig(),s.consecutiveNoAction());return new IncomingTransition(saved,triggered||expired,triggered?"configured-trigger":expired?"sleep-expired":"no-wake-trigger");}
    private boolean matches(Map<String,Object> c,IncomingSignal e){return flag(c,"mention")&&e.mentioned()||flag(c,"name")&&e.nameMentioned()||flag(c,"question")&&e.question()||flag(c,"poke")&&e.poke()||speakers(c).contains(e.speakerId());}
    private static boolean flag(Map<String,Object> c,String key){return Boolean.TRUE.equals(c.get(key));}
    private static Set<String> speakers(Map<String,Object> c){Object v=c.get("speakers");if(!(v instanceof Collection<?> values))return Set.of();Set<String> result=new HashSet<>();for(Object value:values)if(value instanceof String s&&!s.isBlank())result.add(s);return result;}
    private Map<String,Object> validated(Map<String,Object> value){if(value==null)return Map.of();Set<String> allowed=Set.of("mention","name","question","poke","speakers");if(!allowed.containsAll(value.keySet()))throw new IllegalArgumentException("unknown wake configuration key");for(String key:List.of("mention","name","question","poke"))if(value.containsKey(key)&&!(value.get(key) instanceof Boolean))throw new IllegalArgumentException("wake flags must be boolean");if(value.containsKey("speakers")&&(!(value.get("speakers") instanceof Collection<?> c)||c.stream().anyMatch(x->!(x instanceof String))))throw new IllegalArgumentException("speakers must be a list of ids");return Map.copyOf(value);}
    private SimulationConversationState save(SimulationConversationState old,String mode,SimulationConversationState.WakeState wake,String cursor,Instant action,Instant incoming,Instant sleepUntil,Map<String,Object> config,int noAction){boolean waiting=wake==SimulationConversationState.WakeState.WAITING;var s=new SimulationConversationState(old.conversationId(),mode,wake,cursor,action,incoming,sleepUntil,config,noAction,old.generation()+1,clock.instant(),waiting?old.originTurnId():null,waiting?old.waitingSince():null,waiting?old.waitReason():null);repository.save(s);return s;}
    private SimulationConversationState initial(UUID id){return new SimulationConversationState(id,"SOCIAL",SimulationConversationState.WakeState.AWAKE,null,null,null,null,Map.of(),0,0,clock.instant());}
    private static void requireOwner(boolean owner){if(!owner)throw new SecurityException("owner authorization required");}
    public record IncomingSignal(boolean mentioned,boolean nameMentioned,boolean question,boolean poke,boolean replyToBot,String speakerId,String cursor){public IncomingSignal(boolean mentioned,boolean nameMentioned,boolean question,boolean poke,String speakerId,String cursor){this(mentioned,nameMentioned,question,poke,false,speakerId,cursor);}}
    public record IncomingTransition(SimulationConversationState state,boolean woke,String reason){}
}
