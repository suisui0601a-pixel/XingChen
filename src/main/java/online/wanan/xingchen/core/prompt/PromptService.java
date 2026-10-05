package online.wanan.xingchen.core.prompt;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Use-case boundary for independent prompt layer edits and version inspection. */
public final class PromptService {
    private final PromptRepository repository;
    public PromptService(PromptRepository repository) { this.repository = Objects.requireNonNull(repository); }
    public PromptVersion save(UUID profileId, PromptLayer layer, String content, String actor) {
        PromptProfile current = repository.get(profileId).orElseThrow();
        PromptProfile changed = layer == PromptLayer.SIMULATION
                ? new PromptProfile(current.id(), current.name(), content, current.personaPrompt())
                : new PromptProfile(current.id(), current.name(), current.simulationPrompt(), content);
        repository.save(changed);
        return repository.saveVersion(profileId, layer, content, actor);
    }
    public List<PromptVersion> history(UUID profileId, PromptLayer layer) { return repository.history(profileId, layer); }
    public String diff(String before, String after) { return InMemoryPromptRepository.diff(before, after); }
    public PromptProfile rollback(UUID profileId, PromptLayer layer, int version) { return repository.rollback(profileId, layer, version); }
}
