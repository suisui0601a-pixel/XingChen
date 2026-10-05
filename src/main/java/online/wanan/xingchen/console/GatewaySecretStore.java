package online.wanan.xingchen.console;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import online.wanan.xingchen.storage.DataPathResolver;
import java.util.Optional;

/** Gateway facade over the shared store; migrate the legacy filename before environment bootstrap. */
@Component
public final class GatewaySecretStore {
    private static final String NAME = "onebot-access-token";
    private final SecretStore secrets;
    private final Path legacy;
    @Autowired public GatewaySecretStore(SecretStore secrets, DataPathResolver paths) {
        this.secrets = secrets;
        this.legacy = paths.config().resolve(NAME);
    }
    GatewaySecretStore(SecretStore secrets, String root) { this.secrets=secrets;this.legacy=Path.of(root).toAbsolutePath().normalize().resolve(NAME); }
    @PostConstruct public synchronized void bootstrap() {
        if (!secrets.initialized(NAME) && Files.exists(legacy)) {
            try { secrets.replace(NAME, Files.readString(legacy)); }
            catch (IOException exception) { throw new IllegalStateException("Existing Gateway credential could not be migrated"); }
        }
        if (secrets.initialized(NAME)) {
            try { Files.deleteIfExists(legacy); }
            catch (IOException exception) { throw new IllegalStateException("Existing Gateway credential could not be retired"); }
        }
        secrets.bootstrapEnvironment(NAME, "XINGCHEN_ONEBOT_ACCESS_TOKEN");
        secrets.bootstrapEnvironment(NAME, "ONEBOT_ACCESS_TOKEN");
    }
    public Optional<String> read() { return secrets.read(NAME); }
    public boolean configured() { return secrets.configured(NAME); }
    public void replace(String value) { secrets.replace(NAME, value); }
    public void clear() { secrets.clear(NAME); }
}
