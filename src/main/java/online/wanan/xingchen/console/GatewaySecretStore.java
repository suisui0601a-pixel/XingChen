package online.wanan.xingchen.console;

import jakarta.annotation.PostConstruct;
import online.wanan.xingchen.storage.DataPathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Independent OneBot transport credentials with an explicit, read-only legacy fallback. */
@Component
public final class GatewaySecretStore {
    public enum Transport {
        HTTP("onebot-http-access-token", "XINGCHEN_ONEBOT_HTTP_ACCESS_TOKEN"),
        WS("onebot-ws-access-token", "XINGCHEN_ONEBOT_WS_ACCESS_TOKEN");

        private final String secretName;
        private final String environmentVariable;
        Transport(String secretName, String environmentVariable) {
            this.secretName = secretName;
            this.environmentVariable = environmentVariable;
        }
    }

    private static final String LEGACY_NAME = "onebot-access-token";
    private final SecretStore secrets;
    private final Path legacyFile;

    @Autowired public GatewaySecretStore(SecretStore secrets, DataPathResolver paths) {
        this(secrets, paths.config());
    }

    GatewaySecretStore(SecretStore secrets, String root) {
        this(secrets, Path.of(root).toAbsolutePath().normalize());
    }

    private GatewaySecretStore(SecretStore secrets, Path root) {
        this.secrets = secrets;
        this.legacyFile = root.resolve(LEGACY_NAME);
    }

    /** Bootstrap without copying a shared legacy credential over either transport-specific file. */
    @PostConstruct public synchronized void bootstrap() {
        if (!secrets.initialized(LEGACY_NAME) && Files.isRegularFile(legacyFile)) {
            try { secrets.replace(LEGACY_NAME, Files.readString(legacyFile)); }
            catch (IOException exception) { throw new IllegalStateException("Existing Gateway credential could not be read safely"); }
        }
        // Keep both historical environment aliases as a single shared fallback; never fan them out.
        secrets.bootstrapEnvironment(LEGACY_NAME, "XINGCHEN_ONEBOT_ACCESS_TOKEN");
        secrets.bootstrapEnvironment(LEGACY_NAME, "ONEBOT_ACCESS_TOKEN");
        for (Transport transport : Transport.values()) {
            secrets.bootstrapEnvironment(transport.secretName, transport.environmentVariable);
        }
    }

    public Optional<String> read(Transport transport) {
        if (secrets.initialized(transport.secretName)) return secrets.read(transport.secretName);
        return secrets.read(LEGACY_NAME);
    }

    public boolean configured(Transport transport) {
        return read(transport).filter(value -> !value.isBlank()).isPresent();
    }

    public boolean transportInitialized(Transport transport) {
        return secrets.initialized(transport.secretName);
    }

    public boolean legacyConfigured() { return secrets.configured(LEGACY_NAME); }

    public void replace(Transport transport, String value) { secrets.replace(transport.secretName, value); }

    public void clear(Transport transport) { secrets.clear(transport.secretName); }
}
