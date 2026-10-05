package online.wanan.xingchen.core.memory;

import java.time.Instant;
import java.util.UUID;

public record MemorySource(UUID memoryId, String platform, UUID conversationId, String messageId, UUID actorPersonId, Instant timestamp) {
    public MemorySource { if (memoryId == null || platform == null || messageId == null || actorPersonId == null || timestamp == null) throw new IllegalArgumentException("memory provenance is required"); }
}
