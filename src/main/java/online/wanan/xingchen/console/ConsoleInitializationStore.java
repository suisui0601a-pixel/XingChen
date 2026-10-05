package online.wanan.xingchen.console;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConsoleInitializationStore {
    private final JdbcTemplate jdbc;
    public ConsoleInitializationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public void createFirst(String username, String hash, String now) {
        // The first statement acquires the writer lock; no read-then-write transaction upgrade race.
        int inserted = jdbc.update("INSERT INTO console_admin_credentials(username,password_hash,created_at,updated_at) "
                + "SELECT ?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM console_admin_credentials)", username, hash, now, now);
        if (inserted != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        jdbc.update("INSERT INTO console_configuration_audit(occurred_at,actor,category,config_key,action) VALUES(?,?,?,?,?)",
                now, "local-initialization", "Security", "credential", "ADMIN_INITIALIZED");
    }
}
