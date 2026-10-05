import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Build-time extraction from the exact JDBC dependency already packaged in bootJar. */
public final class ExtractSqliteNative {
    public static void main(String[] args) throws Exception {
        String architecture = switch (System.getProperty("os.arch")) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64" -> "aarch64";
            default -> throw new IllegalStateException("Unsupported Linux image architecture");
        };
        String resource = "org/sqlite/native/Linux/" + architecture + "/libsqlitejdbc.so";
        try (ZipFile app = new ZipFile(args[0])) {
            var dependencies = app.stream().filter(entry -> entry.getName().startsWith("BOOT-INF/lib/sqlite-jdbc-")
                    && entry.getName().endsWith(".jar")).toList();
            if (dependencies.size() != 1) throw new IllegalStateException("Expected one packaged SQLite JDBC dependency");
            try (ZipInputStream jdbc = new ZipInputStream(app.getInputStream(dependencies.getFirst()))) {
                for (var entry = jdbc.getNextEntry(); entry != null; entry = jdbc.getNextEntry()) {
                    if (entry.getName().equals(resource)) {
                        Path target = Path.of(args[1]);
                        Files.createDirectories(target);
                        Files.copy(jdbc, target.resolve("libsqlitejdbc.so"));
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("Packaged SQLite native library was not found");
    }
}
