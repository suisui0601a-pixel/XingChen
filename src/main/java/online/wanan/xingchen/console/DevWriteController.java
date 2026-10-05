package online.wanan.xingchen.console;

import online.wanan.xingchen.core.memory.*;
import online.wanan.xingchen.core.relationship.*;
import online.wanan.xingchen.core.identity.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Write APIs exist only under local-dev and refuse startup unless HTTP bind is loopback. */
@RestController
@Profile("local-dev")
@RequestMapping("/api/dev")
public class DevWriteController {
    private final JdbcTemplate jdbc;private final MemoryRepository memories;private final Environment environment;
    public DevWriteController(JdbcTemplate jdbc,MemoryRepository memories,Environment environment){this.jdbc=jdbc;this.memories=memories;this.environment=environment;String bind=environment.getProperty("server.address","127.0.0.1");try{if(!InetAddress.getByName(bind).isLoopbackAddress())throw new IllegalStateException("local-dev writes require a loopback server bind");}catch(java.net.UnknownHostException e){throw new IllegalStateException("local-dev server bind is invalid",e);}}
    public record MemoryEdit(String type,String subjectPersonId,String conversationId,String content,String scope,String sourceMessageId,String actorPersonId,String platform,double confidence,double importance,boolean explicit){}
    @PostMapping("/memories") @Transactional public Map<String,Object> memory(@RequestBody MemoryEdit e){MemoryScope scope=parse(MemoryScope.class,e.scope());if(!Set.of(MemoryScope.CONVERSATION,MemoryScope.PERSON_GLOBAL).contains(scope))throw bad("local-dev endpoint only accepts person or conversation memory");if(e.content()==null||e.content().isBlank()||e.content().length()>5000||e.sourceMessageId()==null||e.sourceMessageId().isBlank()||e.actorPersonId()==null||e.platform()==null)throw bad("memory content and provenance are required");UUID subject=uuid(e.subjectPersonId()),conversation=uuid(e.conversationId()),actor=uuid(e.actorPersonId());if(scope==MemoryScope.CONVERSATION&&conversation==null||scope==MemoryScope.PERSON_GLOBAL&&subject==null)throw bad("scope identity required");Instant now=Instant.now();String scopeId=scope==MemoryScope.CONVERSATION?conversation.toString():subject.toString();Memory m=new Memory(UUID.randomUUID(),parse(MemoryType.class,e.type()),subject,scope==MemoryScope.CONVERSATION?conversation:null,null,e.content(),scope,scopeId,clamp(e.confidence()),clamp(e.importance()),e.explicit(),now,now,null,null,MemoryStatus.ACTIVE);MemorySource source=new MemorySource(m.id(),e.platform(),conversation,e.sourceMessageId(),actor,now);memories.save(m,List.of(source));return Map.of("id",m.id().toString(),"scope",scope.name(),"subjectPersonId",Objects.toString(subject,""));}
    public record MemoryContentEdit(String content){}
    @PutMapping("/memories/{id}") public Map<String,Object> memoryUpdate(@PathVariable String id,@RequestBody MemoryContentEdit edit){UUID memoryId=uuid(id);if(edit.content()==null||edit.content().isBlank()||edit.content().length()>5000)throw bad("invalid memory content");Memory m=memories.get(memoryId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"memory not found"));memories.update(new Memory(m.id(),m.type(),m.subjectPersonId(),m.conversationId(),m.projectKey(),edit.content(),m.scopeType(),m.scopeId(),m.confidence(),m.importance(),m.explicit(),m.createdAt(),Instant.now(),m.lastAccessedAt(),m.expiresAt(),m.status()));return Map.of("id",id,"updated",true);}
    public record RelationshipEdit(String subjectPersonId,String targetPersonId,String value,String scope,String scopeId,String sourceMessageId,double confidence){}
    @PostMapping("/relationships") @Transactional public Map<String,Object> relationship(@RequestBody RelationshipEdit e){ScopeType scope=parse(ScopeType.class,e.scope());if(!Set.of(ScopeType.PRIVATE,ScopeType.CONVERSATION).contains(scope)||e.value()==null||e.value().isBlank()||e.value().length()>40||e.sourceMessageId()==null||e.sourceMessageId().isBlank())throw bad("relationship must be explicit and scoped");UUID subject=uuid(e.subjectPersonId()),target=uuid(e.targetPersonId());if(subject==null||target==null)throw bad("stable subject and target IDs are required");int persons=jdbc.queryForObject("SELECT COUNT(*) FROM persons WHERE id IN (?,?)",Integer.class,subject.toString(),target.toString());if(persons<2)throw bad("both identities must already exist");String scopeId=Objects.requireNonNullElse(e.scopeId(),"");Instant now=Instant.now();RelationshipTerm t=new RelationshipTerm(UUID.randomUUID(),subject,target,RelationshipType.ADDRESS_AS,e.value(),scope,scopeId,true,clamp(e.confidence()),e.sourceMessageId(),now,now);jdbc.update("INSERT INTO relationship_terms(id,subject_person_id,target_person_id,type,value,scope_type,scope_id,explicit,confidence,source_message_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",t.id().toString(),subject.toString(),target.toString(),t.type().name(),t.value(),t.scopeType().name(),t.scopeId(),1,t.confidence(),t.sourceMessageId(),now.toString(),now.toString());return Map.of("id",t.id().toString(),"targetPersonId",target.toString(),"scope",scope.name());}
    private static RuntimeException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}private static UUID uuid(String s){if(s==null||s.isBlank())return null;try{return UUID.fromString(s);}catch(IllegalArgumentException e){throw bad("invalid UUID");}}private static double clamp(double n){if(Double.isNaN(n))throw bad("invalid confidence");return Math.max(0,Math.min(1,n));}private static <E extends Enum<E>> E parse(Class<E> c,String s){try{return Enum.valueOf(c,s.toUpperCase(Locale.ROOT));}catch(Exception e){throw bad("invalid "+c.getSimpleName());}}
}
