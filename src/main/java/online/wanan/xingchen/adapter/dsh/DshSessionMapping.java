package online.wanan.xingchen.adapter.dsh;

import java.time.Instant;
import java.util.UUID;

public record DshSessionMapping(UUID id,UUID conversationId,String workspaceId,String sessionId,String mode,
        String preset,String model,String policyHash,long generation,Status status,Instant createdAt,Instant updatedAt) {
    public enum Status { CURRENT, RETIRED }
}
