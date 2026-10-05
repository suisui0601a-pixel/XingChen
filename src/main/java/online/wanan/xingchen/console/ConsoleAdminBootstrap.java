package online.wanan.xingchen.console;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.time.Instant;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConsoleAdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public ConsoleAdminBootstrap(JdbcTemplate jdbc, PasswordEncoder encoder,
            @Value("${XINGCHEN_CONSOLE_ADMIN_USER:}") String username,
            @Value("${XINGCHEN_CONSOLE_ADMIN_PASSWORD:}") String password) {
        this.jdbc = jdbc; this.encoder = encoder; this.username = username; this.password = password;
    }

    @Override @Transactional public void run(ApplicationArguments args) {
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM console_admin_credentials", Integer.class);
        if (count == 0) {
            if (username.isBlank() && password.isBlank()) return;
            if (username.isBlank() || username.length() > 100 || password.length() < 14
                    || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException("Console admin bootstrap requires a valid username and a password of 14–72 UTF-8 bytes");
            String now = Instant.now().toString();
            jdbc.update("INSERT INTO console_admin_credentials(username,password_hash,created_at,updated_at) VALUES(?,?,?,?)",
                    username.trim(), encoder.encode(password), now, now);
        }
    }
}
