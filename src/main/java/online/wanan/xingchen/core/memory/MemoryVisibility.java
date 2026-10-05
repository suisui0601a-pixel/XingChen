package online.wanan.xingchen.core.memory;

import java.util.Objects;

public final class MemoryVisibility {
    private MemoryVisibility() {}
    public static boolean canRead(Memory memory, RequestContext request) {
        if (memory.status() != MemoryStatus.ACTIVE) return false;
        if (memory.expiresAt() != null && memory.expiresAt().isBefore(java.time.Instant.now())) return false;
        return switch (memory.scopeType()) {
            case GLOBAL -> true;
            case OWNER_GLOBAL -> request.ownerStatus();
            // A person's memories are private to that person; owner status is not a blanket read grant.
            case PERSON_GLOBAL -> Objects.equals(memory.subjectPersonId(), request.requestingPerson());
            case CONVERSATION -> Objects.equals(memory.conversationId(), request.conversationId());
            // Project scope is an isolation boundary even for an owner request.
            case PROJECT -> request.projectKey() != null && Objects.equals(memory.projectKey(), request.projectKey());
            case PRIVATE -> request.ownerStatus() || Objects.equals(memory.subjectPersonId(), request.requestingPerson());
        };
    }
}
