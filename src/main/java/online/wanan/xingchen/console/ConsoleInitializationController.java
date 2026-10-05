package online.wanan.xingchen.console;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** anwaning: first ownership is established locally, never through a trusted-proxy header. */
@RestController
@RequestMapping("/api/auth/initialize")
@ConditionalOnProperty(name = "xingchen.console.initialize-enabled", havingValue = "true")
public class ConsoleInitializationController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final ConsoleInitializationStore store;

    public ConsoleInitializationController(JdbcTemplate jdbc, PasswordEncoder encoder, ConsoleInitializationStore store) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.store = store;
    }

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> status(HttpServletRequest request) {
        requireLocal(request, false);
        requireEmpty();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("initializationRequired", true));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<Map<String, Boolean>> initialize(@RequestBody Credentials credentials,
                                                          HttpServletRequest request) {
        requireLocal(request, true);
        requireEmpty();
        if (credentials == null || credentials.username() == null || credentials.password() == null
                || !credentials.password().equals(credentials.confirmation()))
            throw new IllegalArgumentException("Invalid initialization input");
        String username = credentials.username().trim();
        String password = credentials.password();
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (username.isBlank() || username.length() > 100 || username.chars().anyMatch(Character::isISOControl)
                || password.length() < 14 || bytes > 72 || password.chars().distinct().count() < 4)
            throw new IllegalArgumentException("Invalid initialization input");
        String hash = encoder.encode(password);
        String now = Instant.now().toString();
        // One SQLite statement serializes competing first-admin claims; a prior read alone cannot do this.
        store.createFirst(username, hash, now);
        // Initialization grants no session. The user must authenticate through the ordinary login flow.
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(Map.of("initialized", true));
    }

    private void requireEmpty() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM console_admin_credentials", Integer.class) != 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    static void requireLocal(HttpServletRequest request, boolean mutation) {
        Set<String> addresses = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");
        if (!addresses.contains(request.getRemoteAddr()) || !addresses.contains(request.getLocalAddr())
                || !Set.of("127.0.0.1", "localhost", "[::1]", "::1").contains(request.getServerName()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !Set.of("same-origin", "none").contains(fetchSite))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String origin = request.getHeader("Origin");
        if (origin == null && !mutation) return;
        try {
            URI uri = URI.create(origin);
            int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            if (!request.getScheme().equals(uri.getScheme()) || !request.getServerName().equals(uri.getHost())
                    || port != request.getServerPort() || uri.getUserInfo() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null)
                throw new IllegalArgumentException();
        } catch (Exception invalidOrigin) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    public record Credentials(String username, String password, String confirmation) {
        @Override public String toString() { return "Credentials[REDACTED]"; }
    }
}
