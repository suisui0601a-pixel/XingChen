package online.wanan.xingchen.core.prompt;

import java.util.*;

public interface PromptRepository {
    PromptProfile save(PromptProfile profile);
    Optional<PromptProfile> get(UUID id);
    PromptVersion saveVersion(UUID profileId, PromptLayer layer, String content, String createdBy);
    List<PromptVersion> history(UUID profileId, PromptLayer layer);
    PromptProfile rollback(UUID profileId, PromptLayer layer, int version);
}
