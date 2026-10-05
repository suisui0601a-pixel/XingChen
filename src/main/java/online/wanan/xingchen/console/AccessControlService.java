package online.wanan.xingchen.console;

import online.wanan.xingchen.security.XingChenProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** The runtime and access preview share this fail-closed evaluator. */
@Service
public class AccessControlService {
    private final JdbcTemplate jdbc; private final XingChenProperties properties;
    public AccessControlService(JdbcTemplate jdbc, XingChenProperties properties){this.jdbc=jdbc;this.properties=properties;}
    public record Decision(boolean allowed,String reason){}
    public Decision evaluate(String platform,String actorId,String scope,String conversationId,boolean owner){
        if(!properties.owner().platformUserId().isBlank() && actorId.equals(properties.owner().platformUserId()) && platform.equalsIgnoreCase(properties.owner().platform())) return new Decision(true,"OWNER");
        try {
            var denied=jdbc.queryForObject("SELECT COUNT(*) FROM console_access_rules WHERE effect='DENY' AND platform=? AND ((scope='PRIVATE' AND stable_id=?) OR (scope='GROUP' AND stable_id=?))",Integer.class,platform,actorId,conversationId)>0;
            if(denied)return new Decision(false,"EXPLICIT_DENY");
            if("PRIVATE".equals(scope)) { var allowed=jdbc.queryForObject("SELECT COUNT(*) FROM console_access_rules WHERE scope='PRIVATE' AND platform=? AND stable_id=? AND effect='ALLOW'",Integer.class,platform,actorId)>0;if(allowed)return new Decision(true,"EXPLICIT_ALLOW"); }
            if("GROUP".equals(scope)) { var allowed=jdbc.queryForObject("SELECT COUNT(*) FROM console_access_rules WHERE scope='GROUP' AND platform=? AND stable_id=? AND effect='ALLOW'",Integer.class,platform,conversationId)>0;if(allowed)return new Decision(true,"GROUP_ALLOW"); }
            return new Decision(false,"DEFAULT_DENY");
        } catch(RuntimeException invalid){ return new Decision(false,"POLICY_UNAVAILABLE"); }
    }
    public Map<String,Object> policy(){var rules=jdbc.queryForList("SELECT scope,platform,stable_id AS stableId,effect,updated_at AS updatedAt,updated_by AS updatedBy FROM console_access_rules ORDER BY scope,stable_id");return Map.of("owner",Map.of("platform",properties.owner().platform(),"stableId",properties.owner().platformUserId(),"source","environment/recovery configuration"),"defaultBehavior","DENY","priority","Configured owner recovery identity > explicit deny > explicit allow > default deny","revision",revision(),"rules",rules,"failClosed",true);}
    public long revision(){return jdbc.queryForObject("SELECT revision FROM console_access_meta WHERE singleton=1",Long.class);}
    @Transactional public Map<String,Object> replace(List<Rule> rules,long expected,String actor){if(rules==null||rules.size()>1000)throw new IllegalArgumentException("Too many access rules");for(var r:rules)validate(r);if(jdbc.update("UPDATE console_access_meta SET revision=revision+1 WHERE singleton=1 AND revision=?",expected)!=1)throw new ConcurrentModificationException();var old=jdbc.queryForList("SELECT scope,platform,stable_id,effect FROM console_access_rules");String now=Instant.now().toString();jdbc.update("DELETE FROM console_access_rules");for(var r:rules)jdbc.update("INSERT INTO console_access_rules(scope,platform,stable_id,effect,updated_at,updated_by) VALUES(?,?,?,?,?,?)",r.scope(),r.platform().toUpperCase(Locale.ROOT),r.stableId(),r.effect().toUpperCase(Locale.ROOT),now,actor);long next=revision();for(var r:rules){String normalizedPlatform=r.platform().toUpperCase(Locale.ROOT);String prior=old.stream().filter(x->Objects.equals(x.get("scope"),r.scope())&&Objects.equals(x.get("platform"),normalizedPlatform)&&Objects.equals(x.get("stable_id"),r.stableId())).map(x->Objects.toString(x.get("effect"),null)).findFirst().orElse(null);jdbc.update("INSERT INTO console_access_audit(occurred_at,actor,scope,platform,stable_id,old_effect,new_effect,revision) VALUES(?,?,?,?,?,?,?,?)",now,actor,r.scope(),normalizedPlatform,r.stableId(),prior,r.effect().toUpperCase(Locale.ROOT),next);}for(var prior:old){boolean retained=rules.stream().anyMatch(r->Objects.equals(prior.get("scope"),r.scope())&&Objects.equals(prior.get("platform"),r.platform())&&Objects.equals(prior.get("stable_id"),r.stableId()));if(!retained)jdbc.update("INSERT INTO console_access_audit(occurred_at,actor,scope,platform,stable_id,old_effect,new_effect,revision) VALUES(?,?,?,?,?,?,?,?)",now,actor,prior.get("scope"),prior.get("platform"),prior.get("stable_id"),prior.get("effect"),"REMOVED",next);}jdbc.update("INSERT INTO console_configuration_audit(occurred_at,actor,category,config_key,action) VALUES(?,?,?,?,?)",now,actor,"Access","rules","REPLACE");return policy();}
    public record Rule(String scope,String platform,String stableId,String effect){}
    private void validate(Rule r){if(r==null||!Set.of("PRIVATE","GROUP").contains(r.scope())||!Set.of("QQ","DISCORD","TELEGRAM","OTHER").contains(r.platform().toUpperCase(Locale.ROOT))||r.stableId()==null||!r.stableId().matches("[A-Za-z0-9_-]{1,128}")||!Set.of("ALLOW","DENY").contains(r.effect().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("Invalid stable access rule");if(r.scope().equals("PRIVATE")&&r.effect().equalsIgnoreCase("DENY")&&r.stableId().equals(properties.owner().platformUserId())&&r.platform().equalsIgnoreCase(properties.owner().platform()))throw new IllegalArgumentException("Configured owner cannot be denied; use recovery configuration to change owner");}
}
