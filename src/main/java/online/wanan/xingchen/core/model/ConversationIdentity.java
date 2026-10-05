package online.wanan.xingchen.core.model;

import java.util.Objects;

public record ConversationIdentity(Platform platform, ConversationType type, String platformConversationId) {
    public ConversationIdentity {
        Objects.requireNonNull(platform); Objects.requireNonNull(type);
        if (platformConversationId == null || platformConversationId.isBlank()) throw new IllegalArgumentException("conversation id is required");
    }
}
