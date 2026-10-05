package online.wanan.xingchen.core.memory;

import java.util.UUID;

public record RequestContext(UUID requestingPerson, UUID conversationId, boolean ownerStatus, String projectKey) {
    public RequestContext { if (requestingPerson == null || conversationId == null) throw new IllegalArgumentException("request identity and conversation are required"); }
}
