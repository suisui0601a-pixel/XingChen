package online.wanan.xingchen.core.sticker;

import java.time.*;
import java.util.*;

public final class StickerPolicy {
    public record Decision(boolean allowed,String reason){}
    private final int maxPerTurn;private final Duration cooldown;private final Set<UUID> sentThisTurn=new HashSet<>();private Instant lastSent;
    public StickerPolicy(int maxPerTurn,Duration cooldown){if(maxPerTurn<0||cooldown.isNegative())throw new IllegalArgumentException("invalid sticker limits");this.maxPerTurn=maxPerTurn;this.cooldown=cooldown;}
    public synchronized void beginTurn(){sentThisTurn.clear();}
    public synchronized Decision evaluate(UUID stickerId,Instant now){if(sentThisTurn.size()>=maxPerTurn)return new Decision(false,"per-turn limit");if(sentThisTurn.contains(stickerId))return new Decision(false,"repetition penalty");if(lastSent!=null&&now.isBefore(lastSent.plus(cooldown)))return new Decision(false,"cooldown");return new Decision(true,"allowed");}
    public synchronized void record(UUID stickerId,Instant now){sentThisTurn.add(stickerId);lastSent=now;}
}
