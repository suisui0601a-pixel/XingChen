package online.wanan.xingchen.console;

import online.wanan.xingchen.core.agent.HardSecurityPolicy;
import online.wanan.xingchen.core.context.TokenEstimator;
import online.wanan.xingchen.core.prompt.PromptSections;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Authenticated administration use case. Prompt bodies are never written to audit or logs. */
@Service
public class PromptConsoleService {
    public static final int MAX_CONTENT_CHARS = 30_000;
    private final JdbcTemplate jdbc;
    private final PromptSections defaults;
    private final TokenEstimator estimator;
    public PromptConsoleService(JdbcTemplate jdbc, PromptSections defaults, TokenEstimator estimator) {
        this.jdbc=jdbc; this.defaults=defaults; this.estimator=estimator;
    }

    @Transactional public void initializeDefaults() {
        String profile=defaults.profileId().toString(); String now=Instant.now().toString();
        jdbc.update("INSERT OR IGNORE INTO prompt_profiles(id,name,simulation_prompt,persona_prompt,updated_at) VALUES(?,?,?,?,?)",profile,"Default",defaults.simulationPrompt(),defaults.personaPrompt(),now);
        seed(profile,"SIMULATION",defaults.simulationPrompt(),defaults.simulationVersion(),"system-default",now);
        seed(profile,"PERSONA",defaults.personaPrompt(),defaults.personaVersion(),"system-default",now);
    }
    private void seed(String profile,String layer,String content,int requestedVersion,String actor,String now) {
        String pointer=layer.equals("SIMULATION")?"active_simulation_version_id":"active_persona_version_id";
        String active=jdbc.queryForObject("SELECT "+pointer+" FROM prompt_profiles WHERE id=?",String.class,profile);
        if(active!=null)return;
        List<Map<String,Object>> versions=jdbc.queryForList("SELECT id,version FROM prompt_versions WHERE profile_id=? AND layer=? ORDER BY version",profile,layer);
        String id; int sequence;
        if(versions.isEmpty()) { id=UUID.randomUUID().toString(); sequence=Math.max(1,requestedVersion);jdbc.update("INSERT INTO prompt_versions(id,profile_id,layer,content,version,created_at,created_by,checksum,note) VALUES(?,?,?,?,?,?,?,?, '')",id,profile,layer,content,sequence,now,actor,sha(content)); }
        else { Map<String,Object> latest=versions.get(versions.size()-1);id=latest.get("id").toString();sequence=((Number)latest.get("version")).intValue(); }
        jdbc.update("UPDATE prompt_profiles SET "+pointer+"=?,revision=revision+1,updated_at=? WHERE id=?",id,now,profile);
        String column=layer.equals("SIMULATION")?"simulation_prompt":"persona_prompt";
        if(versions.isEmpty())jdbc.update("UPDATE prompt_profiles SET "+column+"=? WHERE id=?",content,profile);
    }

    public Map<String,Object> current(String layer) {
        String l=layer(layer); Map<String,Object> row=profile(); String id=Objects.toString(row.get(pointer(l)),null);
        if(id==null)throw new IllegalStateException("active prompt is not initialized");
        Map<String,Object> result=new LinkedHashMap<>(version(id));result.put("revision",row.get("revision"));result.put("active",true);return result;
    }
    public Map<String,Object> history(String layer,int page,int size) {
        String l=layer(layer); bounds(page,size);Map<String,Object> p=profile();String active=Objects.toString(p.get(pointer(l)),"");String profile=defaults.profileId().toString();
        List<Map<String,Object>> items=jdbc.queryForList("SELECT id,version,created_at AS createdAt,created_by AS createdBy,note,checksum,content FROM prompt_versions WHERE profile_id=? AND layer=? ORDER BY version DESC LIMIT ? OFFSET ?",profile,l,size,page*size);
        items.forEach(item->{item.put("active",Objects.equals(active,item.get("id")));item.put("estimatedTokens",estimator.estimate((String)item.get("content")));item.remove("content");});
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE profile_id=? AND layer=?",Integer.class,profile,l);
        return Map.of("items",items,"page",page,"size",size,"total",total);
    }
    public Map<String,Object> version(String layer,UUID id) { Map<String,Object> v=version(id.toString());if(!layer.equalsIgnoreCase((String)v.get("layer")))throw new NoSuchElementException("prompt version not found");v.put("active",Objects.equals(profile().get(pointer(layer(layer))),id.toString()));return privateMap(v); }
    @Transactional public Map<String,Object> create(String layer,String content,String note,String expected,String actor) {
        // SqlitePragmaInitializer begins IMMEDIATE before this method reads: SQLite serializes writers.
        // Do not add a JVM lock inside the transaction. JDBC commit starts another IMMEDIATE transaction;
        // a competing writer holding SQLite while waiting for that JVM lock can deadlock the committer.
        String l=layer(layer);validate(content,note);String active=Objects.toString(profile().get(pointer(l)),"");if(!Objects.equals(active,expected))throw new ConcurrentModificationException("prompt changed");
        String id=createVersion(l,content,note,actor);activate(l,id,active,null,"CREATE",actor);return privateMap(version(id));
    }
    @Transactional public Map<String,Object> rollback(String layer,UUID target,String expected,String actor) {
        String l=layer(layer);String active=Objects.toString(profile().get(pointer(l)),"");if(!Objects.equals(active,expected))throw new ConcurrentModificationException("prompt changed");
        Map<String,Object> old=version(target.toString());if(!l.equals(old.get("layer")))throw new IllegalArgumentException("version type mismatch");
        String id=createVersion(l,(String)old.get("content"),"",actor);activate(l,id,active,target.toString(),"ROLLBACK",actor);Map<String,Object> result=privateMap(version(id));result.put("rollbackSourceVersionId",target.toString());return result;
    }
    public Map<String,Object> diff(String layer,UUID left,UUID right) {
        String l=layer(layer);Map<String,Object> a=version(left.toString()),b=version(right.toString());if(!l.equals(a.get("layer"))||!l.equals(b.get("layer")))throw new IllegalArgumentException("diff requires versions from the same prompt layer");
        return Map.of("left",privateMap(a),"right",privateMap(b));
    }
    public Map<String,Object> security() { return Map.of("readOnly",true,"source","HardSecurityPolicy.TEXT","version","built-in","summary","Trusted system policy; user-editable prompts cannot change authorization or override server-side capability checks.","content",HardSecurityPolicy.TEXT); }
    public Map<String,Object> composition() { Map<String,Object> sim=current("SIMULATION"),persona=current("PERSONA");return Map.of("precedence",List.of(Map.of("layer","HARD_SECURITY","label","Hard Security Policy","version","built-in","estimatedTokens",estimator.estimate(HardSecurityPolicy.TEXT)),Map.of("layer","SIMULATION","label","Simulation Prompt","version",sim.get("version"),"estimatedTokens",sim.get("estimatedTokens")),Map.of("layer","PERSONA","label","Persona Prompt","version",persona.get("version"),"estimatedTokens",persona.get("estimatedTokens")),Map.of("layer","RUNTIME","label","Identity / Relationship / Memory / Runtime Context"),Map.of("layer","USER","label","User Message"))); }

    private String createVersion(String layer,String content,String note,String actor) {String profile=defaults.profileId().toString();Integer seq=jdbc.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM prompt_versions WHERE profile_id=? AND layer=?",Integer.class,profile,layer);String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO prompt_versions(id,profile_id,layer,content,version,created_at,created_by,checksum,note) VALUES(?,?,?,?,?,?,?,?,?)",id,profile,layer,content,seq,Instant.now().toString(),safeActor(actor),sha(content),Objects.requireNonNullElse(note,""));return id;}
    private void activate(String layer,String next,String expected,String source,String operation,String actor) {String profile=defaults.profileId().toString(),now=Instant.now().toString(),column=pointer(layer),contentColumn=layer.equals("SIMULATION")?"simulation_prompt":"persona_prompt";String content=jdbc.queryForObject("SELECT content FROM prompt_versions WHERE id=?",String.class,next);int changed=jdbc.update("UPDATE prompt_profiles SET "+column+"=?,"+contentColumn+"=?,revision=revision+1,updated_at=? WHERE id=? AND "+column+"=?",next,content,now,profile,expected);if(changed!=1)throw new ConcurrentModificationException("prompt changed");jdbc.update("INSERT INTO prompt_console_audit(occurred_at,actor,layer,operation,from_version_id,to_version_id,source_version_id,note) VALUES(?,?,?,?,?,?,?,?)",now,safeActor(actor),layer,operation,expected,next,source,"");}
    private Map<String,Object> profile(){return jdbc.queryForMap("SELECT * FROM prompt_profiles WHERE id=?",defaults.profileId().toString());}
    private Map<String,Object> version(String id){return jdbc.query("SELECT id,profile_id AS profileId,layer,content,version,created_at AS createdAt,created_by AS createdBy,checksum,note FROM prompt_versions WHERE id=? AND profile_id=?",(r,n)->{Map<String,Object> m=new LinkedHashMap<>();for(String k:List.of("id","profileId","layer","content","version","createdAt","createdBy","checksum","note"))m.put(k,r.getObject(k));m.put("estimatedTokens",estimator.estimate((String)m.get("content")));return m;},id,defaults.profileId().toString()).stream().findFirst().orElseThrow(()->new NoSuchElementException("prompt version not found"));}
    private static Map<String,Object> privateMap(Map<String,Object> m){return new LinkedHashMap<>(m);}
    private static String pointer(String layer){return layer.equals("SIMULATION")?"active_simulation_version_id":"active_persona_version_id";}
    private static String layer(String value){if(value==null)throw new IllegalArgumentException("prompt layer required");String l=value.toUpperCase(Locale.ROOT);if(!Set.of("SIMULATION","PERSONA").contains(l))throw new IllegalArgumentException("unsupported prompt layer");return l;}
    private static void bounds(int page,int size){if(page<0||page>10000||size<1||size>50)throw new IllegalArgumentException("invalid page bounds");}
    private static void validate(String content,String note){if(content==null||content.isBlank()||content.length()>MAX_CONTENT_CHARS||content.getBytes(StandardCharsets.UTF_8).length>MAX_CONTENT_CHARS*4)throw new IllegalArgumentException("prompt must contain text within the 30000 character limit");if(note!=null&&note.length()>240)throw new IllegalArgumentException("note must be at most 240 characters");}
    private static String safeActor(String actor){if(actor==null||actor.isBlank())return "admin";return actor.length()>100?actor.substring(0,100):actor;}
    private static String sha(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record VersionInput(String content,String note,String expectedActiveVersionId){}
    public record RollbackInput(UUID targetVersionId,String expectedActiveVersionId){}
}
