package online.wanan.xingchen.core.prompt;

import java.util.UUID;

public record PromptProfile(UUID id, String name, String simulationPrompt, String personaPrompt) {}
