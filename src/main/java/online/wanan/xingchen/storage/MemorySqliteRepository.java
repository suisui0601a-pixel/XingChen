package online.wanan.xingchen.storage;

import online.wanan.xingchen.core.memory.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** SQLite/Flyway persistence adapter. Every read takes RequestContext and applies scope policy. */
@Repository
public class MemorySqliteRepository implements MemoryRepository {
    private final JdbcTemplate jdbc;
    public MemorySqliteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional
    @Override public Memory save(Memory m, Collection<MemorySource> sources) {
        if (sources == null || sources.isEmpty()) throw new IllegalArgumentException("long-term memory requires provenance");
        if (sources.stream().anyMatch(s -> !s.memoryId().equals(m.id()))) throw new IllegalArgumentException("source memory id mismatch");
        for (MemorySource source : sources) {
            var message = jdbc.query("SELECT actor_person_id,conversation_id FROM messages WHERE platform=? AND platform_message_id=?",
                    (rs,n) -> new String[]{rs.getString("actor_person_id"),rs.getString("conversation_id")}, source.platform(), source.messageId()).stream().findFirst();
            if (message.isEmpty() || !Objects.equals(message.get()[0], source.actorPersonId().toString()) || !Objects.equals(message.get()[1], str(source.conversationId())))
                throw new IllegalArgumentException("memory provenance must match a persisted source message");
        }
        jdbc.update("INSERT INTO memories(id,type,subject_person_id,conversation_id,project_key,content,scope_type,scope_id,confidence,importance,explicit,created_at,updated_at,last_accessed_at,expires_at,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                m.id().toString(),m.type().name(),str(m.subjectPersonId()),str(m.conversationId()),m.projectKey(),m.content(),m.scopeType().name(),m.scopeId(),m.confidence(),m.importance(),m.explicit()?1:0,ts(m.createdAt()),ts(m.updatedAt()),ts(m.lastAccessedAt()),ts(m.expiresAt()),m.status().name());
        for (MemorySource s : sources) jdbc.update("INSERT INTO memory_sources(memory_id,platform,conversation_id,message_id,actor_person_id,timestamp) VALUES(?,?,?,?,?,?)",m.id().toString(),s.platform(),str(s.conversationId()),s.messageId(),s.actorPersonId().toString(),ts(s.timestamp()));
        return m;
    }
    @Override public Optional<Memory> get(UUID id) { return jdbc.query("SELECT * FROM memories WHERE id=?", (rs,n)->map(rs), id.toString()).stream().findFirst(); }
    @Override public Memory update(Memory m) { int n=jdbc.update("UPDATE memories SET content=?,scope_type=?,scope_id=?,confidence=?,importance=?,updated_at=?,expires_at=?,status=? WHERE id=?",m.content(),m.scopeType().name(),m.scopeId(),m.confidence(),m.importance(),ts(m.updatedAt()),ts(m.expiresAt()),m.status().name(),m.id().toString());if(n==0)throw new NoSuchElementException("memory not found");return m; }
    @Override public boolean delete(UUID id) { return jdbc.update("UPDATE memories SET status='SOFT_DELETED', updated_at=? WHERE id=? AND status='ACTIVE'",ts(Instant.now()),id.toString())>0; }
    @Override public List<Memory> findBySubject(UUID id,RequestContext r){return query("SELECT * FROM memories WHERE subject_person_id=? ORDER BY importance DESC",r,id.toString());}
    @Override public List<Memory> findByConversation(UUID id,RequestContext r){return query("SELECT * FROM memories WHERE conversation_id=? ORDER BY importance DESC",r,id.toString());}
    @Override public List<Memory> findByType(MemoryType t,RequestContext r){return query("SELECT * FROM memories WHERE type=? ORDER BY importance DESC",r,t.name());}
    @Override public List<Memory> searchText(String text,RequestContext r,int limit){return query("SELECT m.* FROM memories_fts f JOIN memories m ON m.id=f.memory_id WHERE memories_fts MATCH ? AND m.status='ACTIVE' AND (m.expires_at IS NULL OR m.expires_at>?) ORDER BY rank LIMIT ?",r,text,Instant.now().toString(),limit);}
    @Override public List<Memory> findRelevantCandidates(RequestContext r,int limit){return query("SELECT * FROM memories ORDER BY importance DESC,updated_at DESC LIMIT ?",r,limit);}
    @Override public List<MemorySource> sources(UUID id){return jdbc.query("SELECT * FROM memory_sources WHERE memory_id=?",(rs,n)->new MemorySource(UUID.fromString(rs.getString("memory_id")),rs.getString("platform"),uuid(rs.getString("conversation_id")),rs.getString("message_id"),UUID.fromString(rs.getString("actor_person_id")),instant(rs,"timestamp")),id.toString());}
    private List<Memory> query(String sql,RequestContext r,Object... args){return jdbc.query(sql,(rs,n)->map(rs),args).stream().filter(m->MemoryVisibility.canRead(m,r)).toList();}
    private static Memory map(java.sql.ResultSet rs)throws java.sql.SQLException{return new Memory(UUID.fromString(rs.getString("id")),MemoryType.valueOf(rs.getString("type")),uuid(rs.getString("subject_person_id")),uuid(rs.getString("conversation_id")),rs.getString("project_key"),rs.getString("content"),MemoryScope.valueOf(rs.getString("scope_type")),rs.getString("scope_id"),rs.getDouble("confidence"),rs.getDouble("importance"),rs.getInt("explicit")!=0,instant(rs,"created_at"),instant(rs,"updated_at"),instant(rs,"last_accessed_at"),instant(rs,"expires_at"),MemoryStatus.valueOf(rs.getString("status")));}
    private static String str(UUID id){return id==null?null:id.toString();} private static UUID uuid(String s){return s==null?null:UUID.fromString(s);} private static String ts(Instant i){return i==null?null:i.toString();}
    private static Instant instant(java.sql.ResultSet rs,String column)throws java.sql.SQLException{String value=rs.getString(column);if(value==null)return null;try{return Instant.parse(value);}catch(java.time.format.DateTimeParseException ignored){return Timestamp.valueOf(value).toInstant();}}
}
