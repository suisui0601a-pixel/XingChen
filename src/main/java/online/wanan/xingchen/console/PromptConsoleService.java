package online.wanan.xingchen.console;

import online.wanan.xingchen.core.agent.HardSecurityPolicy;
import online.wanan.xingchen.core.context.TokenEstimator;
import online.wanan.xingchen.core.prompt.PromptBaseline;
import online.wanan.xingchen.core.prompt.PromptBaselineService;
import online.wanan.xingchen.core.prompt.PromptSections;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Authenticated read-only prompt diagnostics; runtime writes are deliberately fail-closed. */
@Service
public class PromptConsoleService {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final PromptSections defaults;
    private final PromptBaselineService baselineService;
    private final TokenEstimator estimator;

    public PromptConsoleService(org.springframework.jdbc.core.JdbcTemplate jdbc, PromptSections defaults,
                                PromptBaselineService baselineService, TokenEstimator estimator) {
        this.jdbc=jdbc; this.defaults=defaults; this.baselineService=baselineService; this.estimator=estimator;
    }

    public Map<String,Object> current(String layer) {
        String selected=layer(layer); PromptBaseline baseline=baselineService.baseline();
        String content=selected.equals("PERSONA")?baseline.persona():baseline.simulation();
        String hash=selected.equals("PERSONA")?baseline.personaSha256():baseline.simulationSha256();
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("id","BUILT_IN:"+hash); result.put("profileId",defaults.profileId().toString()); result.put("layer",selected);
        result.put("content",content); result.put("version","BUILT_IN"); result.put("createdAt",null); result.put("createdBy","XingChen Core");
        result.put("checksum",hash); result.put("sha256",hash); result.put("chars",content.length());
        result.put("note","Immutable classpath baseline"); result.put("estimatedTokens",estimator.estimate(content));
        result.put("revision",0); result.put("active",true); result.put("source","BUILT_IN"); result.put("mutable",false);
        return result;
    }

    public Map<String,Object> history(String layer,int page,int size) {
        String selected=layer(layer); bounds(page,size); String profile=defaults.profileId().toString();
        String activeColumn=selected.equals("PERSONA")?"active_persona_version_id":"active_simulation_version_id";
        String legacyActive=jdbc.query("SELECT "+activeColumn+" FROM prompt_profiles WHERE id=?",r->r.next()?r.getString(1):null,profile);
        List<Map<String,Object>> items=jdbc.queryForList("SELECT id,version,created_at AS createdAt,created_by AS createdBy,note,checksum,content FROM prompt_versions WHERE profile_id=? AND layer=? ORDER BY version DESC LIMIT ? OFFSET ?",profile,selected,size,page*size);
        items.forEach(item->{item.put("active",false);item.put("legacyActive",Objects.equals(legacyActive,item.get("id")));item.put("source","LEGACY_DATABASE_HISTORY");item.put("estimatedTokens",estimator.estimate((String)item.remove("content")));});
        int total=jdbc.queryForObject("SELECT COUNT(*) FROM prompt_versions WHERE profile_id=? AND layer=?",Integer.class,profile,selected);
        return Map.of("items",items,"page",page,"size",size,"total",total,"mutable",false,"source","LEGACY_DATABASE_HISTORY");
    }

    public Map<String,Object> version(String layer,UUID id) {
        String selected=layer(layer); Map<String,Object> result=legacyVersion(id.toString());
        if(!selected.equals(result.get("layer"))) throw new NoSuchElementException("prompt version not found");
        result.put("active",false); result.put("source","LEGACY_DATABASE_HISTORY");
        String col=selected.equals("PERSONA")?"active_persona_version_id":"active_simulation_version_id";
        String legacy=jdbc.query("SELECT "+col+" FROM prompt_profiles WHERE id=?",r->r.next()?r.getString(1):null,defaults.profileId().toString());
        result.put("legacyActive",Objects.equals(legacy,id.toString())); return result;
    }

    public Map<String,Object> diff(String layer,UUID left,UUID right) {
        String selected=layer(layer); Map<String,Object> a=version(selected,left),b=version(selected,right);
        return Map.of("left",a,"right",b,"source","LEGACY_DATABASE_HISTORY");
    }

    public Map<String,Object> baseline() {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("persona",baselineLayer("PERSONA")); result.put("simulation",baselineLayer("SIMULATION"));
        return result;
    }

    public Map<String,Object> security() {
        return Map.of("readOnly",true,"source","HardSecurityPolicy.TEXT","version","built-in","summary","Trusted system policy; user-editable prompts cannot change authorization or override server-side capability checks.","content",HardSecurityPolicy.TEXT);
    }

    public Map<String,Object> composition() {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("precedence",List.of(
                Map.of("layer","HARD_SECURITY","label","Hard Security Policy","version","built-in","estimatedTokens",estimator.estimate(HardSecurityPolicy.TEXT)),
                Map.of("layer","IDENTITY","label","Identity"),
                compositionLayer("PERSONA"),compositionLayer("SIMULATION"),
                Map.of("layer","RELATIONSHIP","label","Relationship"),Map.of("layer","MEMORY","label","Memory"),
                Map.of("layer","RUNTIME","label","Runtime Context"),Map.of("layer","USER","label","User Message")));
        result.putAll(baseline()); return result;
    }

    public Map<String,Object> create(String layer,String content,String note,String expected,String actor) {
        throw immutable(layer);
    }

    public Map<String,Object> rollback(String layer,UUID target,String expected,String actor) {
        throw immutable(layer);
    }

    private Map<String,Object> baselineLayer(String layer) {
        Map<String,Object> active=current(layer); Map<String,Object> result=new LinkedHashMap<>();
        for(String key:List.of("source","mutable","sha256","chars","estimatedTokens","content","version")) result.put(key,active.get(key));
        return result;
    }

    private Map<String,Object> compositionLayer(String layer) {
        Map<String,Object> active=current(layer);
        return Map.of("layer",layer,"label",layer.equals("PERSONA")?"Built-in Persona":"Built-in Simulation","source","BUILT_IN","mutable",false,
                "version","BUILT_IN","sha256",active.get("sha256"),"chars",active.get("chars"),"estimatedTokens",active.get("estimatedTokens"));
    }

    private Map<String,Object> legacyVersion(String id) {
        return jdbc.query("SELECT id,profile_id AS profileId,layer,content,version,created_at AS createdAt,created_by AS createdBy,checksum,note FROM prompt_versions WHERE id=? AND profile_id=?",
                (r,n)->{Map<String,Object> m=new LinkedHashMap<>();for(String k:List.of("id","profileId","layer","content","version","createdAt","createdBy","checksum","note"))m.put(k,r.getObject(k));m.put("estimatedTokens",estimator.estimate((String)m.get("content")));return m;},id,defaults.profileId().toString()).stream().findFirst().orElseThrow(()->new NoSuchElementException("prompt version not found"));
    }

    private static ResponseStatusException immutable(String layer) {
        String selected=layer(layer); String name=selected.equals("PERSONA")?"Persona":"Simulation";
        String message=name+" is built into XingChen Core and cannot be modified at runtime.";
        return new ResponseStatusException(HttpStatus.CONFLICT,message);
    }

    private static String layer(String value) { if(value==null)throw new IllegalArgumentException("prompt layer required");String l=value.toUpperCase(Locale.ROOT);if(!Set.of("SIMULATION","PERSONA").contains(l))throw new IllegalArgumentException("unsupported prompt layer");return l; }
    private static void bounds(int page,int size){if(page<0||page>10000||size<1||size>50)throw new IllegalArgumentException("invalid page bounds");}
    public record VersionInput(String content,String note,String expectedActiveVersionId){}
    public record RollbackInput(UUID targetVersionId,String expectedActiveVersionId){}
}
