package online.wanan.xingchen.core.relationship;

import java.time.Instant;
import java.util.UUID;

public record RelationshipTerm(UUID id, UUID subjectPersonId, UUID targetPersonId, RelationshipType type,
                               String value, ScopeType scopeType, String scopeId, boolean explicit,
                               double confidence, String sourceMessageId, Instant createdAt, Instant updatedAt) {
    public RelationshipTerm {
        if (id == null || subjectPersonId == null || targetPersonId == null || type == null || value == null || value.isBlank() || scopeType == null) throw new IllegalArgumentException("relationship term fields are required");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be between 0 and 1");
    }
}
