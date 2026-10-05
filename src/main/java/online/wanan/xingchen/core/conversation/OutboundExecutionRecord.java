package online.wanan.xingchen.core.conversation;

import java.time.Instant;
import java.util.UUID;

public record OutboundExecutionRecord(String executionKey, UUID conversationId, String turnId, long generation,
        String logicalMessageId, Status status, String providerMessageId, String failureReason,
        Instant createdAt, Instant updatedAt, Instant dispatchStartedAt) {
    public enum Status { PENDING, SUCCESS, FAILED, UNKNOWN }
}
