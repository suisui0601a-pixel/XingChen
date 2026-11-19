package online.wanan.xingchen.core;

import online.wanan.xingchen.config.RuntimeGraphConfiguration;
import online.wanan.xingchen.core.agent.SocialTrigger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProductionParticipationWiringTest {
    @Test void productionWiringDoesNotMakeEveryPositiveProbabilityCertain() {
        var policy=new RuntimeGraphConfiguration().participationPolicy();
        int hits=0;
        // Sanity check of the actual production factory, not a replacement fixture.
        // This detects a constant RNG; exact 50% frequency is deliberately not asserted.
        // For an independent uniform RNG the excluded endpoints have probability 2^-1023.
        for(int i=0;i<1024;i++)if(policy.participates(SocialTrigger.MAY_REPLY,.5))hits++;
        assertThat(hits).isBetween(1,1023);
    }
}
