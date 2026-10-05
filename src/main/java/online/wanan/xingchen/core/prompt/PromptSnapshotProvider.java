package online.wanan.xingchen.core.prompt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reads both active layers in one statement so a turn cannot observe a mixed configuration. */
@Component
public final class PromptSnapshotProvider {
    private final JdbcTemplate jdbc;private final PromptSections fallback;
    public PromptSnapshotProvider(JdbcTemplate jdbc,PromptSections fallback){this.jdbc=jdbc;this.fallback=fallback;}
    public PromptSnapshot capture(){
        return jdbc.query("SELECT p.id,p.active_simulation_version_id AS sid,s.version AS sv,s.content AS sc,p.active_persona_version_id AS pid,v.version AS pv,v.content AS pc FROM prompt_profiles p LEFT JOIN prompt_versions s ON s.id=p.active_simulation_version_id LEFT JOIN prompt_versions v ON v.id=p.active_persona_version_id WHERE p.id=?",(r,n)->{
            String sid=r.getString("sid"),pid=r.getString("pid");
            return new PromptSnapshot(fallback.profileId(),sid,sid==null?fallback.simulationVersion():r.getInt("sv"),sid==null?fallback.simulationPrompt():r.getString("sc"),pid,pid==null?fallback.personaVersion():r.getInt("pv"),pid==null?fallback.personaPrompt():r.getString("pc"));
        },fallback.profileId().toString()).stream().findFirst().orElse(new PromptSnapshot(fallback.profileId(),null,fallback.simulationVersion(),fallback.simulationPrompt(),null,fallback.personaVersion(),fallback.personaPrompt()));
    }
}
