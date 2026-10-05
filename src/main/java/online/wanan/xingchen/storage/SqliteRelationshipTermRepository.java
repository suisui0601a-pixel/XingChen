package online.wanan.xingchen.storage;

import online.wanan.xingchen.core.relationship.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Repository public class SqliteRelationshipTermRepository implements RelationshipTermRepository {
    private final JdbcTemplate jdbc;public SqliteRelationshipTermRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override @Transactional public RelationshipTerm save(RelationshipTerm t){jdbc.update("DELETE FROM relationship_terms WHERE subject_person_id=? AND target_person_id=? AND type=? AND scope_type=? AND scope_id IS ? AND active=1",t.subjectPersonId().toString(),t.targetPersonId().toString(),t.type().name(),t.scopeType().name(),t.scopeId());jdbc.update("INSERT INTO relationship_terms(id,subject_person_id,target_person_id,type,value,scope_type,scope_id,explicit,confidence,source_message_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",t.id().toString(),t.subjectPersonId().toString(),t.targetPersonId().toString(),t.type().name(),t.value(),t.scopeType().name(),t.scopeId(),t.explicit()?1:0,t.confidence(),t.sourceMessageId(),t.createdAt().toString(),t.updatedAt().toString());return t;}
    @Override public boolean update(RelationshipTerm t,String expected){return jdbc.update("UPDATE relationship_terms SET value=?,scope_type=?,scope_id=?,updated_at=? WHERE id=? AND updated_at=? AND active=1 AND explicit=1",t.value(),t.scopeType().name(),t.scopeId(),t.updatedAt().toString(),t.id().toString(),expected)==1;}
    @Override public boolean deactivate(UUID id,String expected){return jdbc.update("UPDATE relationship_terms SET active=0,updated_at=? WHERE id=? AND updated_at=? AND active=1 AND explicit=1",Instant.now().toString(),id.toString(),expected)==1;}
    @Override public List<RelationshipTerm> all(){return jdbc.query("SELECT * FROM relationship_terms WHERE active=1",(r,n)->new RelationshipTerm(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("subject_person_id")),UUID.fromString(r.getString("target_person_id")),RelationshipType.valueOf(r.getString("type")),r.getString("value"),ScopeType.valueOf(r.getString("scope_type")),r.getString("scope_id"),r.getInt("explicit")!=0,r.getDouble("confidence"),r.getString("source_message_id"),Instant.parse(r.getString("created_at")),Instant.parse(r.getString("updated_at"))));}
}
