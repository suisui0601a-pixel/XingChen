package online.wanan.xingchen.core.agent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SimulationConversationState(UUID conversationId, String mode, WakeState wakeState,
        String lastReadCursor, Instant lastActionAt, Instant lastIncomingAt, Instant sleepUntil,
        Map<String,Object> wakeConfig, int consecutiveNoAction, long generation, Instant updatedAt,
        String originTurnId, Instant waitingSince, String waitReason) {
    public SimulationConversationState(UUID conversationId,String mode,WakeState wakeState,String lastReadCursor,
            Instant lastActionAt,Instant lastIncomingAt,Instant sleepUntil,Map<String,Object> wakeConfig,
            int consecutiveNoAction,long generation,Instant updatedAt) {
        this(conversationId,mode,wakeState,lastReadCursor,lastActionAt,lastIncomingAt,sleepUntil,wakeConfig,
                consecutiveNoAction,generation,updatedAt,null,null,null);
    }
    public enum WakeState { AWAKE, SLEEPING, IDLE, WAITING }
    public SimulationConversationState { wakeConfig=Map.copyOf(wakeConfig==null?Map.of():wakeConfig); }
}
