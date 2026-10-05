package online.wanan.xingchen.core.memory;

import java.time.Instant;
import java.util.UUID;

public record Memory(UUID id, MemoryType type, UUID subjectPersonId, UUID conversationId, String projectKey,
                     String content, MemoryScope scopeType, String scopeId, double confidence, double importance,
                     boolean explicit, Instant createdAt, Instant updatedAt, Instant lastAccessedAt,
                     Instant expiresAt, MemoryStatus status) {
    public Memory {
        if (id == null || type == null || content == null || content.isBlank() || scopeType == null || status == null) throw new IllegalArgumentException("memory fields are required");
        if (confidence < 0 || confidence > 1 || importance < 0 || importance > 1) throw new IllegalArgumentException("confidence and importance must be between 0 and 1");
        if (scopeType == MemoryScope.PERSON_GLOBAL && subjectPersonId == null) throw new IllegalArgumentException("PERSON_GLOBAL requires a subject person");
        if (scopeType == MemoryScope.CONVERSATION && conversationId == null) throw new IllegalArgumentException("CONVERSATION requires a conversation");
        if (scopeType == MemoryScope.PROJECT && (projectKey == null || projectKey.isBlank())) throw new IllegalArgumentException("PROJECT requires a project key");
    }
}
