package online.wanan.xingchen.core.identity;

import online.wanan.xingchen.core.model.Platform;
import java.util.UUID;

public record Person(UUID id, Platform platform, String platformUserId, boolean self, boolean owner) {
    public Person { if (id == null || platform == null || platformUserId == null || platformUserId.isBlank()) throw new IllegalArgumentException("person identity is required"); }
    public IdentityRole role() { return self ? IdentityRole.SELF : owner ? IdentityRole.OWNER : IdentityRole.MEMBER; }
}
