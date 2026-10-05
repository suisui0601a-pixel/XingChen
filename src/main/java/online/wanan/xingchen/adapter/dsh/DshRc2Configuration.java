package online.wanan.xingchen.adapter.dsh;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Non-secret configuration for the exact DSH 0.1.7-rc.2 adapter. */
public record DshRc2Configuration(URI baseUri, Path workspaceDirectory, String launchToken,
                                  String modelProvider, Duration requestTimeout) {
    public DshRc2Configuration {
        if (baseUri == null || baseUri.getHost() == null || !("http".equals(baseUri.getScheme()) || "https".equals(baseUri.getScheme()))) throw new IllegalArgumentException("DSH base URL must be an absolute HTTP(S) URL");
        if (workspaceDirectory == null || !workspaceDirectory.isAbsolute()) throw new IllegalArgumentException("DSH workspace must be an absolute existing directory");
        launchToken = launchToken == null ? "" : launchToken;
        modelProvider = modelProvider == null || modelProvider.isBlank() ? "deepseek-official" : modelProvider;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
        if (requestTimeout.isNegative() || requestTimeout.isZero()) throw new IllegalArgumentException("DSH request timeout must be positive");
    }
    public static DshRc2Configuration fromEnvironment(Map<String,String> env) {
        return new DshRc2Configuration(URI.create(env.getOrDefault("DSH_BASE_URL", "http://127.0.0.1:3190")),
                Path.of(env.getOrDefault("DSH_WORKSPACE_PATH", System.getProperty("user.dir"))).toAbsolutePath(),
                env.get("DSH_LAUNCH_TOKEN"), env.getOrDefault("DSH_MODEL_PROVIDER", "deepseek-official"), Duration.ofMillis(10_000));
    }
}
