package online.wanan.xingchen.core.prompt;

import java.util.Objects;

/** Runtime view of the two independently versioned prompt layers. */
public final class PromptEngine {
    public PromptSections assemble(PromptProfile profile,int simulationVersion,int personaVersion){
        Objects.requireNonNull(profile);
        return new PromptSections(profile.id(),simulationVersion,personaVersion,profile.simulationPrompt(),profile.personaPrompt());
    }
}
