package online.wanan.xingchen.core.identity;

import java.time.Instant;
import java.util.UUID;

public record Membership(UUID id, UUID personId, UUID conversationId, String currentDisplayName,
                         String card, IdentityRole platformRole, Instant firstSeenAt, Instant lastSeenAt) {}
