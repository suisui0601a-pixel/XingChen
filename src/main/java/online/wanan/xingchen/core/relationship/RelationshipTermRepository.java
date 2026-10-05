package online.wanan.xingchen.core.relationship;

import java.util.*;

public interface RelationshipTermRepository {
    RelationshipTerm save(RelationshipTerm term);
    List<RelationshipTerm> all();
    default boolean update(RelationshipTerm term,String expectedUpdatedAt){return false;}
    default boolean deactivate(UUID id,String expectedUpdatedAt){return false;}
}
