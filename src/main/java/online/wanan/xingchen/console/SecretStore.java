package online.wanan.xingchen.console;

import java.util.Optional;

/** Restricted filesystem credential storage, intentionally separate from ordinary configuration rows. */
public interface SecretStore {
    Optional<String> read(String name);
    boolean configured(String name);
    void replace(String name,String value);
    void clear(String name);
    void bootstrapEnvironment(String name,String environmentVariable);
    /** Explicit absence is initialized too; it must never fall back to deployment defaults. */
    default boolean initialized(String name) { return configured(name); }
}
