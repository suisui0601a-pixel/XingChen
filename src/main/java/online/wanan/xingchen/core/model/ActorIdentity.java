package online.wanan.xingchen.core.model;

import java.util.Objects;

public record ActorIdentity(Platform platform, String platformUserId, String displayName, boolean self, boolean owner) {
    public ActorIdentity { Objects.requireNonNull(platform); if (platformUserId == null || platformUserId.isBlank()) throw new IllegalArgumentException("user id is required"); }
}
