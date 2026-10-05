package online.wanan.xingchen.core.relationship;

import java.util.*;

public final class AddressResolver {
    public AddressResolution resolveAddress(UUID speaker, UUID target, String conversationId,
                                            String displayName, Collection<RelationshipTerm> terms) {
        Comparator<RelationshipTerm> priority = Comparator
                .comparingInt((RelationshipTerm t) -> priority(t, conversationId))
                .thenComparing(RelationshipTerm::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        Optional<RelationshipTerm> chosen = terms.stream()
                .filter(t -> t.subjectPersonId().equals(speaker) && t.targetPersonId().equals(target) && t.type() == RelationshipType.ADDRESS_AS)
                .filter(t -> visible(t, conversationId))
                .min(priority);
        return chosen.map(t -> new AddressResolution(t.value(), "relationship-term", t.scopeType(), t.explicit(), t.confidence()))
                .orElseGet(() -> new AddressResolution(displayName == null || displayName.isBlank() ? "" : displayName, "display-name", null, false, 0));
    }
    private static boolean visible(RelationshipTerm t, String conversationId) {
        return t.scopeType() == ScopeType.GLOBAL || t.scopeType()==ScopeType.PRIVATE&&Objects.equals(t.scopeId(),conversationId) || t.scopeType()==ScopeType.CONVERSATION&&Objects.equals(t.scopeId(),conversationId);
    }
    public static int priority(RelationshipTerm t, String conversationId) {
        boolean local = (t.scopeType() == ScopeType.CONVERSATION||t.scopeType()==ScopeType.PRIVATE) && Objects.equals(t.scopeId(), conversationId);
        if (local && t.explicit()) return 0;
        if (t.scopeType() == ScopeType.GLOBAL && t.explicit()) return 1;
        if (local) return 2;
        return 3;
    }
}
