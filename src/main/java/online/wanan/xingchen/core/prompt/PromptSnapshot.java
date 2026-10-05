package online.wanan.xingchen.core.prompt;

import java.util.UUID;

/** Immutable prompt state captured once when a new agent turn enters orchestration. */
public record PromptSnapshot(UUID profileId,String simulationVersionId,int simulationVersion,String simulationPrompt,
                             String personaVersionId,int personaVersion,String personaPrompt) {}
