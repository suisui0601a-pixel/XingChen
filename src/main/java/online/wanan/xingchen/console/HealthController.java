package online.wanan.xingchen.console;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbc;private final String version;
    public HealthController(JdbcTemplate jdbc,@Value("${xingchen.version:0.1.0-SNAPSHOT}") String version){this.jdbc=jdbc;this.version=version;}
    @GetMapping("/health") public Map<String,String> health(){
        try{jdbc.queryForObject("SELECT 1",Integer.class);jdbc.queryForObject("SELECT COUNT(*) FROM memories",Integer.class);return Map.of("status","UP","version",version,"database","UP","memory","UP","agent","mock-ready");}
        catch(RuntimeException e){return Map.of("status","DOWN","version",version,"database","DOWN","memory","DOWN","agent","mock-ready");}
    }
}
