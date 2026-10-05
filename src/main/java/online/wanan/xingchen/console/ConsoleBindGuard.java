package online.wanan.xingchen.console;

import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Read APIs include private identity/memory data, so non-loopback binding requires an explicit opt-in. */
@Component
@org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
public final class ConsoleBindGuard implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    private final Environment environment;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public ConsoleBindGuard(Environment environment, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.environment = environment;
        this.jdbc = jdbc;
    }

    @Override public void customize(ConfigurableWebServerFactory factory) {
        String address = environment.getProperty("server.address", "127.0.0.1");
        boolean allowRemote = environment.getProperty("xingchen.console.allow-remote", Boolean.class, false);
        boolean authConfigured = !environment.getProperty("XINGCHEN_CONSOLE_ADMIN_USER", "").isBlank()
                && environment.getProperty("XINGCHEN_CONSOLE_ADMIN_PASSWORD", "").length() >= 14;
        if (environment.getProperty("xingchen.console.use-persisted-admin", Boolean.class, false))
            authConfigured = jdbc.queryForObject("SELECT COUNT(*) FROM console_admin_credentials WHERE password_hash <> ''", Integer.class) > 0;
        if (environment.getProperty("xingchen.console.initialize-enabled", Boolean.class, false))
            validate(address, false, false); // Setup mode cannot opt into remote binding, even after initialization.
        validate(address, allowRemote, authConfigured);
    }

    public static void validate(String address, boolean allowNonLoopback) {
        validate(address, allowNonLoopback, false);
    }

    public static void validate(String address, boolean allowNonLoopback, boolean authenticationConfigured) {
        if (address == null || address.isBlank()) throw new IllegalStateException("console bind address must be explicit");
        try {
            for (InetAddress resolved : InetAddress.getAllByName(address))
                if (!resolved.isLoopbackAddress() && !(allowNonLoopback && authenticationConfigured))
                    throw new IllegalStateException("non-loopback Console bind requires XINGCHEN_CONSOLE_ALLOW_REMOTE=true and authentication");
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Console bind address could not be resolved", e);
        }
    }
}
