package online.wanan.xingchen.core.prompt;

import java.util.UUID;

public record PromptSections(UUID profileId,int simulationVersion,int personaVersion,
                             String simulationPrompt,String personaPrompt) {}
