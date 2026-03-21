package endgame.plugin.database;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * Configuration for optional database persistence (SQLite, MySQL, MariaDB, PostgreSQL).
 * When Enabled=false (default), everything stays JSON-only with zero overhead.
 */
public class DatabaseConfig {

    @Nonnull
    public static final BuilderCodec<DatabaseConfig> CODEC = BuilderCodec
            .builder(DatabaseConfig.class, DatabaseConfig::new)
            .append(new KeyedCodec<Boolean>("Enabled", Codec.BOOLEAN),
                    (c, v) -> c.enabled = v != null ? v : false, c -> c.enabled).add()
            .append(new KeyedCodec<String>("JdbcUrl", Codec.STRING),
                    (c, v) -> { if (v != null) c.jdbcUrl = v; }, c -> c.jdbcUrl).add()
            .append(new KeyedCodec<String>("Username", Codec.STRING),
                    (c, v) -> { if (v != null) c.username = v; }, c -> c.username).add()
            .append(new KeyedCodec<String>("Password", Codec.STRING),
                    (c, v) -> { if (v != null) c.password = v; }, c -> c.password).add()
            .append(new KeyedCodec<Integer>("MaxPoolSize", Codec.INTEGER),
                    (c, v) -> c.maxPoolSize = v != null ? v : 10, c -> c.maxPoolSize).add()
            .build();

    private boolean enabled = false;
    private String jdbcUrl = "jdbc:sqlite:./data/endgame_sync.db";
    private String username = "";
    private String password = "";
    private int maxPoolSize = 10;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String url) { this.jdbcUrl = url; }

    public String getUsername() { return username; }
    public void setUsername(String u) { this.username = u; }

    public String getPassword() { return password; }
    public void setPassword(String p) { this.password = p; }

    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int s) { this.maxPoolSize = s; }
}
