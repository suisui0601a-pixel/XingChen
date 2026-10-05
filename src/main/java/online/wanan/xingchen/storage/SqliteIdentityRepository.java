package online.wanan.xingchen.storage;

import online.wanan.xingchen.core.identity.*;
import online.wanan.xingchen.core.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** SQLite-backed identity snapshot repository. */
@Repository
public class SqliteIdentityRepository implements IdentityPersistence {
    private final JdbcTemplate jdbc;
    public SqliteIdentityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<Person> findPerson(Platform platform, String userId) {
        return jdbc.query("SELECT * FROM persons WHERE platform=? AND platform_user_id=?", (rs,n) -> person(rs.getString("id"),rs.getString("platform"),rs.getString("platform_user_id"),rs.getInt("is_self")!=0,rs.getInt("is_owner")!=0), platform.name(), userId).stream().findFirst();
    }
    @Override public Optional<Person> findPerson(UUID id) {
        return jdbc.query("SELECT * FROM persons WHERE id=?", (rs,n) -> person(rs.getString("id"),rs.getString("platform"),rs.getString("platform_user_id"),rs.getInt("is_self")!=0,rs.getInt("is_owner")!=0), id.toString()).stream().findFirst();
    }
    @Transactional @Override public Person save(Person p, Instant at) {
        jdbc.update("INSERT INTO persons(id,platform,platform_user_id,is_self,is_owner,created_at,updated_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(platform,platform_user_id) DO UPDATE SET is_self=excluded.is_self,is_owner=excluded.is_owner,updated_at=MAX(persons.updated_at,excluded.updated_at)", p.id().toString(),p.platform().name(),p.platformUserId(),p.self()?1:0,p.owner()?1:0,at.toString(),at.toString());
        return findPerson(p.platform(),p.platformUserId()).orElseThrow();
    }
    @Transactional @Override public Conversation save(Conversation c, Instant at) {
        var i=c.identity();
        jdbc.update("INSERT INTO conversations(id,platform,type,platform_conversation_id,created_at) VALUES(?,?,?,?,?) ON CONFLICT(platform,type,platform_conversation_id) DO NOTHING",c.id().toString(),i.platform().name(),i.type().name(),i.platformConversationId(),at.toString());
        return jdbc.query("SELECT id FROM conversations WHERE platform=? AND type=? AND platform_conversation_id=?",(rs,n)->new Conversation(UUID.fromString(rs.getString(1)),i),i.platform().name(),i.type().name(),i.platformConversationId()).stream().findFirst().orElseThrow();
    }
    @Override public Optional<Membership> findMembership(UUID person, UUID conversation) {
        return jdbc.query("SELECT * FROM memberships WHERE person_id=? AND conversation_id=?",(rs,n)->membership(rs),person.toString(),conversation.toString()).stream().findFirst();
    }
    @Transactional @Override public Membership save(Membership m) {
        jdbc.update("INSERT INTO memberships(id,person_id,conversation_id,current_display_name,card,platform_role,first_seen_at,last_seen_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(person_id,conversation_id) DO UPDATE SET current_display_name=excluded.current_display_name,card=excluded.card,platform_role=excluded.platform_role,first_seen_at=MIN(memberships.first_seen_at,excluded.first_seen_at),last_seen_at=MAX(memberships.last_seen_at,excluded.last_seen_at)",m.id().toString(),m.personId().toString(),m.conversationId().toString(),m.currentDisplayName(),m.card(),m.platformRole().name(),m.firstSeenAt().toString(),m.lastSeenAt().toString());
        return findMembership(m.personId(),m.conversationId()).orElseThrow();
    }
    @Transactional @Override public void saveAlias(PersonAlias a) {
        String conversation=a.sourceConversationId()==null?null:a.sourceConversationId().toString();
        int changed=jdbc.update("UPDATE person_aliases SET first_seen_at=MIN(first_seen_at,?),last_seen_at=MAX(last_seen_at,?) WHERE person_id=? AND alias=? AND source_conversation_id IS ?",a.firstSeenAt().toString(),a.lastSeenAt().toString(),a.personId().toString(),a.alias(),conversation);
        if(changed==0)jdbc.update("INSERT INTO person_aliases(id,person_id,alias,source_conversation_id,first_seen_at,last_seen_at) VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET last_seen_at=MAX(person_aliases.last_seen_at,excluded.last_seen_at)",a.id().toString(),a.personId().toString(),a.alias(),conversation,a.firstSeenAt().toString(),a.lastSeenAt().toString());
    }
    @Override public List<PersonAlias> aliases(UUID personId) { return jdbc.query("SELECT * FROM person_aliases WHERE person_id=? ORDER BY last_seen_at",(rs,n)->new PersonAlias(UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("person_id")),rs.getString("alias"),uuid(rs.getString("source_conversation_id")),Instant.parse(rs.getString("first_seen_at")),Instant.parse(rs.getString("last_seen_at"))),personId.toString()); }
    @Override public List<Membership> memberships(UUID personId) { return jdbc.query("SELECT * FROM memberships WHERE person_id=? ORDER BY first_seen_at",(rs,n)->membership(rs),personId.toString()); }
    private static Person person(String id,String p,String user,boolean self,boolean owner){return new Person(UUID.fromString(id),Platform.valueOf(p),user,self,owner);}
    private static Membership membership(java.sql.ResultSet rs)throws java.sql.SQLException{return new Membership(UUID.fromString(rs.getString("id")),UUID.fromString(rs.getString("person_id")),UUID.fromString(rs.getString("conversation_id")),rs.getString("current_display_name"),rs.getString("card"),IdentityRole.valueOf(rs.getString("platform_role")),Instant.parse(rs.getString("first_seen_at")),Instant.parse(rs.getString("last_seen_at")));}
    private static UUID uuid(String value){return value==null?null:UUID.fromString(value);}
}
