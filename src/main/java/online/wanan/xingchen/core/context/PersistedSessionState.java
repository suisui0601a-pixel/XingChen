package online.wanan.xingchen.core.context;

import java.time.Instant;
import java.util.UUID;

public record PersistedSessionState(UUID conversationId, long generation, String state, UUID currentSummaryId,
                                    Instant lastRolloverAt, int estimatedContextTokens, Instant updatedAt) { }
