package online.wanan.xingchen.core.prompt;

import org.springframework.stereotype.Component;

/** Captures the immutable built-in layers together so every turn sees one baseline snapshot. */
@Component
public final class PromptSnapshotProvider {
    private final PromptSections profile;private final PromptBaselineService baseline;
    public PromptSnapshotProvider(PromptSections profile,PromptBaselineService baseline){this.profile=profile;this.baseline=baseline;}
    public PromptSnapshot capture(){
        PromptBaseline value=baseline.baseline();
        return new PromptSnapshot(profile.profileId(),"BUILT_IN:"+value.simulationSha256(),1,value.simulation(),
                "BUILT_IN:"+value.personaSha256(),1,value.persona());
    }
}
