package online.wanan.xingchen.console;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class ConsoleAuthController {
    private final AuthenticationManager authenticationManager;
    private final ConsoleLoginRateLimiter rateLimiter;
    private final ConsoleSessionGenerationRegistry sessions;private final SafeOperationalLogStore logs;private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public ConsoleAuthController(AuthenticationManager authenticationManager, ConsoleLoginRateLimiter rateLimiter,ConsoleSessionGenerationRegistry sessions,SafeOperationalLogStore logs,org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.authenticationManager = authenticationManager; this.rateLimiter = rateLimiter;this.sessions=sessions;this.logs=logs;this.jdbc=jdbc;
    }

    @GetMapping("/csrf") public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

    @GetMapping("/session") public Map<String, Object> session(Authentication authentication) {
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        return Map.of("authenticated", authenticated, "username", authenticated ? authentication.getName() : "");
    }

    @PostMapping("/login") public ResponseEntity<?> login(@RequestBody LoginRequest body, HttpServletRequest request,
                                                               HttpServletResponse response) {
        String address = request.getRemoteAddr();
        if (rateLimiter.blocked(address, body.username())) {
            logs.record("WARN","AUTH","LOGIN_RATE_LIMITED");
            securityAudit("LOGIN_RATE_LIMITED");
            String traceId = java.util.UUID.randomUUID().toString();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("X-Trace-Id",traceId)
                    .body(Map.of("code", "LOGIN_RATE_LIMITED", "message", "登录尝试过多，请稍后重试", "traceId", traceId));
        }
        try {
            Authentication auth = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
            HttpSession session = request.getSession(true);
            request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            sessions.register(auth.getName(),session);
            rateLimiter.success(address, body.username());
            logs.record("INFO","AUTH","LOGIN_SUCCEEDED");
            return ResponseEntity.ok(Map.of("authenticated", true, "username", auth.getName()));
        } catch (BadCredentialsException | org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            rateLimiter.failure(address, body.username());
            logs.record("WARN","AUTH","LOGIN_FAILED");
            securityAudit("LOGIN_FAILED");
            String traceId = java.util.UUID.randomUUID().toString();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header("X-Trace-Id",traceId)
                    .body(Map.of("code", "INVALID_CREDENTIALS", "message", "用户名或密码不正确", "traceId",traceId));
        }
    }

    @PostMapping("/logout") public Map<String, Boolean> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {sessions.remove(session.getId());session.invalidate();}
        SecurityContextHolder.clearContext();
        return Map.of("loggedOut", true);
    }

    public record LoginRequest(String username, String password) {
        public LoginRequest {
            if (username == null || password == null) throw new IllegalArgumentException("username and password are required");
            username = username.trim();
            if (username.isBlank() || username.length() > 100
                    || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalArgumentException("username or password is invalid");
        }
    }
    private void securityAudit(String action){jdbc.update("INSERT INTO console_configuration_audit(occurred_at,actor,category,config_key,action) VALUES(?,?,?,?,?)",java.time.Instant.now().toString(),"anonymous","Security","login",action);}
}
