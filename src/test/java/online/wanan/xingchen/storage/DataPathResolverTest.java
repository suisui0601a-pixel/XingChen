package online.wanan.xingchen.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataPathResolverTest {
    @TempDir(factory = BuildDirectory.class) Path temporary;

    public static final class BuildDirectory implements org.junit.jupiter.api.io.TempDirFactory {
        @Override public Path createTempDirectory(org.junit.jupiter.api.extension.AnnotatedElementContext element,
                org.junit.jupiter.api.extension.ExtensionContext context) throws java.io.IOException {
            return Files.createTempDirectory(Path.of("build").toAbsolutePath(), "container-data-paths-");
        }
    }

    @Test void resolvesAndInitializesUnifiedContainerLayout() {
        Path root = temporary.resolve("volume");
        var paths = new DataPathResolver(root.toString(), "jdbc:sqlite:" + root.resolve("db/xingchen.db"),
                root.resolve("config").toString(), root.resolve("assets/stickers").toString());

        paths.initialize();

        assertThat(paths.root()).isEqualTo(root.toAbsolutePath().normalize());
        assertThat(paths.database()).isEqualTo(root.resolve("db/xingchen.db").toAbsolutePath().normalize());
        assertThat(paths.config()).isDirectory();
        assertThat(paths.prompts()).isDirectory();
        assertThat(paths.logs()).isDirectory();
        assertThat(paths.assets()).isDirectory();
        assertThat(paths.stickerRoot()).isDirectory();
        paths.closeLease();
    }

    @Test void keepsExistingDevelopmentOverridesAndInitializesTheirParents() {
        Path root = temporary.resolve("local-data");
        Path externalDatabase = temporary.resolve("custom/db.sqlite");
        Path externalSecrets = temporary.resolve("custom-secrets");
        Path externalAssets = temporary.resolve("custom-assets");
        var paths = new DataPathResolver(root.toString(), "jdbc:sqlite:" + externalDatabase,
                externalSecrets.toString(), externalAssets.toString());

        paths.initialize();

        assertThat(externalDatabase.getParent()).isDirectory();
        assertThat(paths.config()).isEqualTo(externalSecrets.toAbsolutePath().normalize());
        assertThat(paths.stickerRoot()).isEqualTo(externalAssets.toAbsolutePath().normalize());
        assertThat(paths.root().resolve("prompts")).isDirectory();
        paths.closeLease();
    }

    @Test void failsFastWhenPersistentRootIsAFile() throws Exception {
        Path file = temporary.resolve("not-a-directory");
        Files.writeString(file, "fixture");
        var paths = new DataPathResolver(file.resolve("child").toString(),
                "jdbc:sqlite:" + file.resolve("child/db/xingchen.db"),
                file.resolve("child/config").toString(), file.resolve("child/assets/stickers").toString());

        assertThatThrownBy(paths::initialize).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Persistent data directory cannot be initialized");
    }

    @Test void maintenanceLeaseRejectsSecondInstanceUntilClosed() {
        Path root = temporary.resolve("leased");
        var first = new DataPathResolver(root.toString(), "jdbc:sqlite:" + root.resolve("db/xingchen.db"),
                root.resolve("config").toString(), root.resolve("assets/stickers").toString());
        var second = new DataPathResolver(root.toString(), "jdbc:sqlite:" + root.resolve("db/xingchen.db"),
                root.resolve("config").toString(), root.resolve("assets/stickers").toString());
        try {
            first.initialize();
            assertThatThrownBy(second::initialize).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in use or in maintenance");
            first.closeLease();
            second.initialize();
        } finally {
            first.closeLease(); second.closeLease();
        }
    }
}
