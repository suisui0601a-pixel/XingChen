package online.wanan.xingchen.packaging;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerPackagingContractTest {
    @Test void sqliteNativeLoadsFromImmutableImageInsteadOfNoexecTmpfs() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(dockerfile).contains("java container/ExtractSqliteNative.java /app/build/libs/xingchen-core-*.jar /tmp/sqlite-native",
                "COPY --from=build /tmp/sqlite-native/ /app/native/", "-Dorg.sqlite.lib.path=/app/native");
        assertThat(Files.readString(Path.of("compose.yml"))).contains("/tmp:rw,noexec,nosuid,nodev");
    }
    @Test void allPersistentAncestorsAreOwnedBeforeFreshVolumePopulation() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String runtimeStage = dockerfile.substring(dockerfile.indexOf(" AS runtime"));
        assertThat(runtimeStage).contains("install -d -o 10001 -g 10001 /data /data/db");
        String install = runtimeStage.lines().filter(line -> line.contains("install -d -o 10001 -g 10001")).findFirst().orElseThrow();
        var directories = new java.util.HashSet<>(List.of(install.trim().split("\\s+")));
        assertThat(directories).contains("/data/assets", "/data/assets/stickers");
        for (String directory : directories.stream().filter(value -> value.startsWith("/data/")).toList()) {
            String parent = directory.substring(0, directory.lastIndexOf('/'));
            while (parent.startsWith("/data")) {
                assertThat(directories).as("owned persistent ancestor of %s", directory).contains(parent);
                parent = parent.substring(0, parent.lastIndexOf('/'));
            }
        }
    }

    @Test void copiedNpmLibraryHasItsCliSymlinkRecreatedAndVerifiedInBuildStage() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String buildStage = dockerfile.substring(dockerfile.indexOf(" AS build"), dockerfile.indexOf(" AS runtime"));
        assertThat(buildStage).contains("COPY --from=frontend-tools /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm",
                "ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm", "$(npm --version)", "11.17.0");
        assertThat(buildStage).doesNotContain("COPY --from=frontend-tools /usr/local/bin/npm /usr/local/bin/npm");
    }

    @SuppressWarnings("unchecked")
    @Test void composeIsLoopbackOnlyHardenedAndUsesOnePersistentDataRoot() throws Exception {
        Map<String, Object> compose;
        try (InputStream input = Files.newInputStream(Path.of("compose.yml"))) {
            compose = new Yaml().load(input);
        }
        Map<String, Object> services = (Map<String, Object>) compose.get("services");
        Map<String, Object> core = (Map<String, Object>) services.get("xingchen-core");
        assertThat((List<String>) core.get("ports")).containsExactly("127.0.0.1:3200:3200");
        assertThat(core.get("read_only")).isEqualTo(true);
        assertThat(core.get("cap_drop")).isEqualTo(List.of("ALL"));
        assertThat(core.get("security_opt")).isEqualTo(List.of("no-new-privileges:true"));
        assertThat((List<String>) core.get("tmpfs")).anySatisfy(value -> assertThat(value).startsWith("/tmp:"));
        assertThat(core.get("privileged")).isNotEqualTo(true);
        assertThat((List<String>) core.get("volumes")).containsExactly("xingchen-data:/data");
        Map<String, String> environment = (Map<String, String>) core.get("environment");
        assertThat(environment.get("SPRING_PROFILES_ACTIVE")).isEqualTo("container");
        assertThat(environment.get("XINGCHEN_CONSOLE_ALLOW_REMOTE")).contains("false");
        assertThat(environment.get("XINGCHEN_INTEGRATIONS_ONEBOT_ENABLED")).isEqualTo("false");
        Map<String, Object> networks = (Map<String, Object>) compose.get("networks");
        assertThat(((Map<String, Object>) networks.get("xingchen-core")).get("driver")).isEqualTo("bridge");
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(dockerfile).contains("AS build", "AS runtime", "USER 10001:10001", "HEALTHCHECK", "ENTRYPOINT [\"java\"");
        String runtimeStage = dockerfile.substring(dockerfile.indexOf(" AS runtime"));
        assertThat(runtimeStage).doesNotContain("node:", "npm", "gradle", "/var/run/docker.sock");
    }
}
