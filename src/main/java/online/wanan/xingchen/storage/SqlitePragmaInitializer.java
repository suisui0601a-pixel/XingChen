package online.wanan.xingchen.storage;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class SqlitePragmaInitializer {
    /** Apply connection-local foreign key enforcement and WAL to Flyway and application connections alike. */
    @Bean @Primary DataSource sqliteDataSource(DataPathResolver paths, org.springframework.core.env.Environment environment) {
        String jdbcUrl = environment.getProperty("spring.datasource.url");
        String fileName = jdbcUrl.substring("jdbc:sqlite:".length());
        if (!fileName.equals(":memory:") && !fileName.startsWith("file:")) {
            Path database = paths.database();
            if (database == null) throw new IllegalStateException("Persistent SQLite database path was not resolved");
        }
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        // Acquire SQLite's single-writer slot before a transaction reads, avoiding WAL snapshot-upgrade
        // SQLITE_BUSY failures when independent conversation workers persist concurrently.
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(jdbcUrl);
        return dataSource;
    }
}
