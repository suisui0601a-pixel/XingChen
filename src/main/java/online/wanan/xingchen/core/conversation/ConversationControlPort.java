package online.wanan.xingchen.core.conversation;

import java.util.UUID;

/** Serialized, generation-fenced operator actions over the same runtime lane as inbound events. */
public interface ConversationControlPort {
    int queuedFor(String laneKey);
    boolean laneActive(String laneKey);
    SimulationSnapshot changeMode(String laneKey, UUID conversationId, String mode, long expectedGeneration, boolean ownerAllowed);
    SimulationSnapshot wake(String laneKey, UUID conversationId, long expectedGeneration, boolean ownerAllowed);
    SimulationSnapshot reset(String laneKey, UUID conversationId, long expectedGeneration);

    record SimulationSnapshot(String mode, String wakeState, long generation, String updatedAt) {}
}
