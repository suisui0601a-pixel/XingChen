package online.wanan.xingchen.core.identity;

import java.time.Instant;
import java.util.UUID;

public record PersonAlias(UUID id, UUID personId, String alias, UUID sourceConversationId, Instant firstSeenAt, Instant lastSeenAt) {}
