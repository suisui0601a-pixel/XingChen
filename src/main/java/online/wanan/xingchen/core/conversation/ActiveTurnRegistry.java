package online.wanan.xingchen.core.conversation;

import online.wanan.xingchen.core.agent.CancellationToken;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Minimal cancellation registry: conversation identity to active turn metadata and token only. */
public final class ActiveTurnRegistry {
    private final Map<UUID,ActiveTurn> active=new ConcurrentHashMap<>();
    public synchronized ActiveTurn beginIfCurrent(UUID conversation,String turnId,long generation,CancellationToken token,LongSupplier currentGeneration){if(currentGeneration.getAsLong()!=generation)return null;token.generationFence(()->currentGeneration.getAsLong()==generation);var value=new ActiveTurn(turnId,generation,token);active.put(conversation,value);return value;}
    public synchronized void finish(UUID conversation,ActiveTurn turn){active.remove(conversation,turn);}
    public synchronized long invalidateAndCancel(UUID conversation,LongSupplier invalidator){var turn=active.get(conversation);return turn==null?invalidator.getAsLong():turn.cancellation().invalidateGenerationAndCancel(invalidator);}
    public record ActiveTurn(String turnId,long generation,CancellationToken cancellation){}
}
