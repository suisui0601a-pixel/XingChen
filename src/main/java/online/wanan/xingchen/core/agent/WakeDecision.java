package online.wanan.xingchen.core.agent;

import java.util.Set;

/** Immutable trigger classification; reasons are retained for prompt context and diagnostics. */
public record WakeDecision(SocialTrigger trigger, Set<String> reasons) {
    public WakeDecision { reasons=Set.copyOf(reasons==null?Set.of():reasons); }
    public boolean forced(){return trigger==SocialTrigger.MUST_REPLY;}
}
