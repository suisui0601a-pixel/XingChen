package online.wanan.xingchen.core.sticker;

import java.time.*;
import java.util.*;

public final class StickerPolicy {
    public record Decision(boolean allowed,String reason){}
    private static final class TurnBudget { final Set<UUID> sent=new HashSet<>(),pending=new HashSet<>(); }
    public interface TurnScope extends AutoCloseable { @Override void close(); }
    public static final class Permit {
        private final TurnBudget budget;private final UUID sticker;private boolean finished;
        private Permit(TurnBudget budget,UUID sticker){this.budget=budget;this.sticker=sticker;}
    }
    public record Admission(Decision decision,Permit permit){}
    private final int maxPerTurn;private final Duration cooldown;
    private final ThreadLocal<TurnBudget> turn=new ThreadLocal<>();private Instant lastSent;private int pendingSends;
    public StickerPolicy(int maxPerTurn,Duration cooldown){if(maxPerTurn<0||cooldown.isNegative())throw new IllegalArgumentException("invalid sticker limits");this.maxPerTurn=maxPerTurn;this.cooldown=cooldown;}
    private TurnBudget budget(){var value=turn.get();if(value==null){value=new TurnBudget();turn.set(value);}return value;}
    /** Legacy direct callers may explicitly begin a turn. Production uses the closeable scope. */
    public void beginTurn(){turn.set(new TurnBudget());}
    public TurnScope openTurn(){
        TurnBudget previous=turn.get();Thread owner=Thread.currentThread();turn.set(new TurnBudget());
        return new TurnScope(){private boolean closed;public void close(){
            if(closed)return;if(Thread.currentThread()!=owner)throw new IllegalStateException("sticker turn scope closed on another thread");
            closed=true;if(previous==null)turn.remove();else turn.set(previous);
        }};
    }
    public synchronized Decision evaluate(UUID stickerId,Instant now){
        var b=budget();if(b.sent.size()+b.pending.size()>=maxPerTurn)return new Decision(false,"per-turn limit");
        if(b.sent.contains(stickerId)||b.pending.contains(stickerId))return new Decision(false,"repetition penalty");
        if((!cooldown.isZero()&&pendingSends>0)||(lastSent!=null&&now.isBefore(lastSent.plus(cooldown))))return new Decision(false,"cooldown");
        return new Decision(true,"allowed");
    }
    public synchronized void record(UUID stickerId,Instant now){budget().sent.add(stickerId);if(lastSent==null||now.isAfter(lastSent))lastSent=now;}
    /** Reserve before transport so concurrent sends cannot pass the same cooldown window. */
    public synchronized Admission reserve(UUID stickerId,Instant now){
        Decision decision=evaluate(stickerId,now);if(!decision.allowed())return new Admission(decision,null);
        TurnBudget b=budget();b.pending.add(stickerId);pendingSends++;return new Admission(decision,new Permit(b,stickerId));
    }
    /** Unknown transport outcomes consume the allowance; definite rejection releases the reservation. */
    public synchronized void finish(Permit permit,boolean consumed,Instant now){
        if(permit.finished)return;permit.finished=true;permit.budget.pending.remove(permit.sticker);pendingSends--;
        if(consumed){permit.budget.sent.add(permit.sticker);if(lastSent==null||now.isAfter(lastSent))lastSent=now;}
    }
}
