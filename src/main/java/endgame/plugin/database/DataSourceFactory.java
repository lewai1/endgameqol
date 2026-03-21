package endgame.plugin.database;

import com.hypixel.hytale.logger.HytaleLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;

/**
 * Creates a HikariCP connection pool with auto-detected driver,
 * input validation, and connection health check.
 * Supports SQLite, MySQL, MariaDB, and PostgreSQL.
 */
public class DataSourceFactory {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Database");

    /**
     * Create and validate a HikariCP DataSource.
     *
     * @param config the database configuration
     * @return configured and validated DataSource
     * @throws DatabaseSyncException if validation or connection fails
     */
    public static HikariDataSource create(DatabaseConfig config) {
        validateConfig(config);

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.getJdbcUrl());
        hikari.setDriverClassName(detectDriver(config.getJdbcUrl()));
        hikari.setMaximumPoolSize(config.getMaxPoolSize());
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(10_000);
        hikari.setValidationTimeout(5_000);
        hikari.setInitializationFailTimeout(10_000);
        hikari.setIdleTimeout(300_000);
        hikari.setMaxLifetime(600_000);
        hikari.setPoolName("EndgameQoL-DB");

        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            hikari.setUsername(config.getUsername());
        }
        if (config.getPassword() != null && !config.getPassword().isBlank()) {
            hikari.setPassword(config.getPassword());
        }

        LOGGER.atInfo().log("[Database] Creating connection pool: %s (pool size: %d)",
                config.getJdbcUrl(), config.getMaxPoolSize());

        HikariDataSource ds = new HikariDataSource(hikari);

        try (Connection conn = ds.getConnection()) {
            if (!conn.isValid(2)) {
                ds.close();
                throw new DatabaseSyncException("Connection validation failed (isValid returned false)");
            }
            LOGGER.atInfo().log("[Database] Connection validated — %s",
                    conn.getMetaData().getDatabaseProductName());
        } catch (DatabaseSyncException e) {
            throw e;
        } catch (Exception e) {
            ds.close();
            throw new DatabaseSyncException("Failed to validate database connection", e);
        }

        return ds;
    }

    /**
     * Auto-detect the JDBC driver class from the URL prefix.
     */
    static String detectDriver(String jdbcUrl) {
        String lower = jdbcUrl.toLowerCase();
        if (lower.startsWith("jdbc:sqlite:")) return "org.sqlite.JDBC";
        if (lower.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
        if (lower.startsWith("jdbc:mariadb:")) return "org.mariadb.jdbc.Driver";
        if (lower.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
        throw new IllegalArgumentException(
                "Unsupported JDBC URL prefix. Supported: jdbc:sqlite, jdbc:mysql, jdbc:mariadb, jdbc:postgresql. Got: " + jdbcUrl);
    }

    /**
     * Validate configuration before creating the pool.
     */
    static void validateConfig(DatabaseConfig config) {
        String url = config.getJdbcUrl();
        if (url == null || !url.toLowerCase().startsWith("jdbc:")) {
            throw new IllegalArgumentException("JdbcUrl must start with 'jdbc:' — got: " + url);
        }

        if (url.toLowerCase().startsWith("jdbc:postgresql:") && url.contains("@")) {
            throw new IllegalArgumentException(
                    "PostgreSQL JDBC URL must not contain credentials (user:pass@host). Use Username/Password config fields instead.");
        }

        boolean isEmbedded = url.toLowerCase().startsWith("jdbc:sqlite:");
        if (!isEmbedded) {
            boolean hasUsername = config.getUsername() != null && !config.getUsername().isBlank();
            boolean hasEmbeddedCreds = url.contains("user=");
            if (!hasUsername && !hasEmbeddedCreds) {
                throw new IllegalArgumentException(
                        "Remote databases require credentials. Set Username in config or include user= in JDBC URL.");
            }
        }

        if (config.getMaxPoolSize() < 1) {
            throw new IllegalArgumentException("MaxPoolSize must be >= 1, got: " + config.getMaxPoolSize());
        }
    }
}
