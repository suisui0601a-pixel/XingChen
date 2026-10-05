package online.wanan.xingchen.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves and eagerly validates the writable persistence paths owned by one application instance. */
@Component
public final class DataPathResolver {
    private final Path root;
    private final Path database;
    private final Path config;
    private final Path prompts;
    private final Path logs;
    private final Path assets;
    private final Path stickerRoot;
    private FileChannel leaseChannel;
    private FileLock lease;

    public DataPathResolver(@Value("${xingchen.data-dir:./data}") String root,
                            @Value("${spring.datasource.url}") String jdbcUrl,
                            @Value("${xingchen.secrets.directory:${XINGCHEN_SECRET_DIR:data/secrets}}") String config,
                            @Value("${xingchen.sticker.root:${XINGCHEN_STICKER_ROOT:/data/assets/stickers}}") String stickerRoot) {
        this.root = absolute(root, "data root");
        this.database = databasePath(jdbcUrl);
        this.config = absolute(config, "config directory");
        this.prompts = this.root.resolve("prompts");
        this.logs = this.root.resolve("logs");
        this.assets = this.root.resolve("assets");
        this.stickerRoot = absolute(stickerRoot, "asset directory");
    }

    private static Path absolute(String value, String description) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Persistent " + description + " must be explicitly configured");
        return Path.of(value).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() {
        Set<Path> required = new LinkedHashSet<>();
        required.add(root);
        required.add(root.resolve("db"));
        if (database != null && database.getParent() != null) required.add(database.getParent());
        required.add(config);
        required.add(prompts);
        required.add(logs);
        required.add(assets);
        required.add(stickerRoot);
        for (Path directory : required) ensureWritableDirectory(directory);
        if (database != null && Files.exists(database) && !Files.isRegularFile(database))
            throw new IllegalStateException("Persistent database path is not a regular file");
        if (database != null && database.equals(root.resolve("db/xingchen.db"))) {
            try {
                leaseChannel = FileChannel.open(database.resolveSibling(".xingchen-" + database.getFileName() + ".lock"),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                lease = leaseChannel.tryLock(0, 1, false);
                if (lease == null) throw new IOException("data maintenance lease is held");
            } catch (IOException | java.nio.channels.OverlappingFileLockException failure) {
                closeLease();
                throw new IllegalStateException("Persistent database is already in use or in maintenance", failure);
            }
        }
        boolean canonical = database != null && database.equals(root.resolve("db/xingchen.db"))
                && config.equals(root.resolve("config")) && stickerRoot.equals(root.resolve("assets/stickers"));
        try {
            Files.writeString(root.resolve("recovery-layout.json"), "{\"canonical\":" + canonical + "}\n");
        } catch (IOException failure) {
            closeLease();
            throw new IllegalStateException("Persistent recovery layout cannot be recorded", failure);
        }
    }

    @PreDestroy public void closeLease() {
        try { if (lease != null) lease.release(); } catch (IOException ignored) { }
        try { if (leaseChannel != null) leaseChannel.close(); } catch (IOException ignored) { }
        lease = null; leaseChannel = null;
    }

    private static void ensureWritableDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            if (!Files.isDirectory(directory) || !Files.isWritable(directory))
                throw new IllegalStateException("Persistent data directory is not writable: " + directory);
            Path probe = Files.createTempFile(directory, ".xingchen-write-check-", ".tmp");
            Files.delete(probe);
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException("Persistent data directory cannot be initialized: " + directory, exception);
        }
    }

    private static Path databasePath(String jdbcUrl) {
        String prefix = "jdbc:sqlite:";
        if (jdbcUrl == null || !jdbcUrl.startsWith(prefix))
            throw new IllegalStateException("SQLite datasource URL is required for persistent data initialization");
        String file = jdbcUrl.substring(prefix.length());
        if (file.equals(":memory:") || file.startsWith("file:")) return null;
        return Path.of(file).toAbsolutePath().normalize();
    }

    public Path root() { return root; }
    public Path database() { return database; }
    public Path config() { return config; }
    public Path prompts() { return prompts; }
    public Path logs() { return logs; }
    public Path assets() { return assets; }
    public Path stickerRoot() { return stickerRoot; }
}
