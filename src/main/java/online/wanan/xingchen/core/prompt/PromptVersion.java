package online.wanan.xingchen.core.prompt;

import java.time.Instant;
import java.util.UUID;

public record PromptVersion(UUID id, UUID profileId, PromptLayer layer, String content, int version,
                            Instant createdAt, String createdBy, String checksum) {}
