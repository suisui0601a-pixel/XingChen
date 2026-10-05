package online.wanan.xingchen.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Startup snapshot: persisted URL (including explicit empty) wins over deployment default.
 * Saved edits remain pending until restart; Cookie and posture use this same snapshot. */
@Component
@DependsOnDatabaseInitialization
public final class EffectiveConsoleConfiguration {
    private final String publicBaseUrl;
    private final boolean cookieSecure;
    public EffectiveConsoleConfiguration(JdbcTemplate jdbc, ObjectMapper json, Environment environment) {
        String persisted = jdbc.query("SELECT value_json FROM console_configuration WHERE category='Console' AND config_key='publicBaseUrl'",
                rs -> rs.next() ? rs.getString(1) : null);
        if (persisted == null) publicBaseUrl = environment.getProperty("xingchen.console.public-base-url", "");
        else {
            try { publicBaseUrl = json.readValue(persisted, String.class); }
            catch (Exception exception) { throw new IllegalStateException("Stored Console public URL is invalid"); }
            if (publicBaseUrl == null) throw new IllegalStateException("Stored Console public URL is invalid");
        }
        cookieSecure = environment.getProperty("xingchen.console.cookie-secure", Boolean.class, false)
                || publicBaseUrl.startsWith("https://");
    }
    public String publicBaseUrl() { return publicBaseUrl; }
    public boolean cookieSecure() { return cookieSecure; }
}
