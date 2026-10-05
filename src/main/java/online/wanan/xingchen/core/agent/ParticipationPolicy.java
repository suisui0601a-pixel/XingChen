package online.wanan.xingchen.core.agent;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/** Probability is intentionally separate from trigger classification and can be seeded in tests. */
public final class ParticipationPolicy {
    private final DoubleSupplier random;
    public ParticipationPolicy(){this(Math::random);}
    public ParticipationPolicy(DoubleSupplier random){this.random=Objects.requireNonNull(random);}
    public boolean participates(SocialTrigger trigger,double probability){if(trigger==SocialTrigger.IGNORE)return false;if(trigger==SocialTrigger.MUST_REPLY)return true;if(Double.isNaN(probability))return false;double p=Math.max(0,Math.min(1,probability));return random.getAsDouble()<p;}
}
