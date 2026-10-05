package online.wanan.xingchen.core.relationship;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RelationshipTermRegistry {
    private final List<RelationshipTerm> terms=new CopyOnWriteArrayList<>();
    private final RelationshipTermRepository repository;
    public RelationshipTermRegistry(){this(null);}
    public RelationshipTermRegistry(RelationshipTermRepository repository){this.repository=repository;}
    public void save(RelationshipTerm term){terms.removeIf(t->t.id().equals(term.id()));if(repository!=null)repository.save(term);terms.add(term);}
    public boolean updateExplicit(RelationshipTerm term,String expectedUpdatedAt){if(!term.explicit())throw new IllegalArgumentException("only explicit terms may be edited");if(repository==null)return false;boolean updated=repository.update(term,expectedUpdatedAt);if(updated){terms.removeIf(t->t.id().equals(term.id()));terms.add(term);}return updated;}
    public boolean deactivateExplicit(UUID id,String expectedUpdatedAt){if(repository==null)return false;boolean changed=repository.deactivate(id,expectedUpdatedAt);if(changed)terms.removeIf(t->t.id().equals(id));return changed;}
    public List<RelationshipTerm> all(){if(repository==null)return List.copyOf(terms);Map<UUID,RelationshipTerm> merged=new LinkedHashMap<>();repository.all().forEach(t->merged.put(t.id(),t));terms.forEach(t->merged.put(t.id(),t));return List.copyOf(merged.values());}
}
