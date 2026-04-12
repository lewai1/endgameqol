package endgame.plugin.database;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Set;

/**
 * All SQL operations for EndgameQoL player data persistence.
 * Uses universal UPDATE-then-INSERT pattern — works with SQLite, MySQL, MariaDB, and PostgreSQL.
 */
public class EndgameRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Database");

    private final DataSource dataSource;

    public EndgameRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Create tables and indexes if they don't exist. Called once on startup.
     * Also runs migrations for new columns added in v4.0.0.
     */
    public void createTables() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Endgame_PlayerData (
                        uuid VARCHAR(36) PRIMARY KEY,
                        username VARCHAR(64) NOT NULL,
                        bounty_state TEXT,
                        achievement_data TEXT,
                        bestiary_data TEXT,
                        accessory_pouch TEXT,
                        combo_personal_best INT DEFAULT 0,
                        last_updated BIGINT NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS Endgame_Leaderboard (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(64) NOT NULL,
                        best_wave INT NOT NULL,
                        timestamp BIGINT NOT NULL
                    )
                    """);

            try {
                stmt.execute("CREATE INDEX idx_leaderboard_wave ON Endgame_Leaderboard (best_wave)");
            } catch (SQLException ignored) {
                // Index already exists — expected on subsequent startups
            }
            try {
                stmt.execute("CREATE INDEX idx_leaderboard_timestamp ON Endgame_Leaderboard (timestamp)");
            } catch (SQLException ignored) {
                // Index already exists — expected on subsequent startups
            }

            // Migration: add columns for existing databases (pre-4.0.0)
            migrateAddColumn(conn, "Endgame_PlayerData", "achievement_data", "TEXT");
            migrateAddColumn(conn, "Endgame_PlayerData", "bestiary_data", "TEXT");
            migrateAddColumn(conn, "Endgame_PlayerData", "accessory_pouch", "TEXT");
            migrateAddColumn(conn, "Endgame_PlayerData", "combo_personal_best", "INT DEFAULT 0");

            LOGGER.atInfo().log("[Database] Tables and indexes verified/created");
        }
    }

    private static final Set<String> ALLOWED_TABLES = Set.of("Endgame_PlayerData", "Endgame_Leaderboard");
    private static final Set<String> ALLOWED_COLUMNS = Set.of(
            "achievement_data", "bestiary_data", "accessory_pouch", "combo_personal_best",
            "player_name", "best_wave", "timestamp");

    /**
     * Safely add a column if it doesn't exist. Catches the "duplicate column" error
     * which varies across DB engines, so we just try and swallow the failure.
     * Validates table/column names against a whitelist to prevent SQL injection.
     */
    private void migrateAddColumn(Connection conn, String table, String column, String type) {
        if (!ALLOWED_TABLES.contains(table) || !ALLOWED_COLUMNS.contains(column)) {
            LOGGER.atWarning().log("[Database] Rejected migration: invalid table/column %s.%s", table, column);
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            LOGGER.atInfo().log("[Database] Migration: added column %s.%s", table, column);
        } catch (SQLException ignored) {
            // Column already exists — expected for non-migrating databases
        }
    }

    /**
     * Save player data using universal UPDATE-then-INSERT.
     * Safe because all DB writes run on a single-threaded executor.
     */
    public void savePlayerData(PlayerDataSnapshot snapshot) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int rows;
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE Endgame_PlayerData
                    SET username = ?, bounty_state = ?,
                        achievement_data = ?, bestiary_data = ?, accessory_pouch = ?,
                        combo_personal_best = CASE WHEN ? > combo_personal_best THEN ? ELSE combo_personal_best END,
                        last_updated = ?
                    WHERE uuid = ?
                    """)) {
                ps.setString(1, snapshot.username());
                ps.setString(2, snapshot.bountyStateJson());
                ps.setString(3, snapshot.achievementJson());
                ps.setString(4, snapshot.bestiaryJson());
                ps.setString(5, snapshot.accessoryPouchJson());
                ps.setInt(6, snapshot.comboPersonalBest());
                ps.setInt(7, snapshot.comboPersonalBest());
                ps.setLong(8, System.currentTimeMillis());
                ps.setString(9, snapshot.uuid());
                rows = ps.executeUpdate();
            }

            if (rows == 0) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO Endgame_PlayerData
                        (uuid, username, bounty_state,
                         achievement_data, bestiary_data, accessory_pouch, combo_personal_best, last_updated)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    ps.setString(1, snapshot.uuid());
                    ps.setString(2, snapshot.username());
                    ps.setString(3, snapshot.bountyStateJson());
                    ps.setString(4, snapshot.achievementJson());
                    ps.setString(5, snapshot.bestiaryJson());
                    ps.setString(6, snapshot.accessoryPouchJson());
                    ps.setInt(7, snapshot.comboPersonalBest());
                    ps.setLong(8, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * Load player data by UUID.
     */
    @Nullable
    public PlayerDataSnapshot loadPlayerData(String uuid) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT uuid, username, bounty_state, " +
                     "achievement_data, bestiary_data, accessory_pouch, combo_personal_best " +
                     "FROM Endgame_PlayerData WHERE uuid = ?")) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerDataSnapshot(
                            rs.getString("uuid"),
                            rs.getString("username"),
                            rs.getString("bounty_state"),
                            rs.getString("achievement_data"),
                            rs.getString("bestiary_data"),
                            rs.getString("accessory_pouch"),
                            rs.getInt("combo_personal_best")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Upsert leaderboard entry (only updates if better wave).
     * Uses universal UPDATE-then-INSERT pattern.
     */
    public void upsertLeaderboard(String uuid, String name, int bestWave) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            int rows;
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE Endgame_Leaderboard
                    SET player_name = ?,
                        best_wave = CASE WHEN ? > best_wave THEN ? ELSE best_wave END,
                        timestamp = CASE WHEN ? > best_wave THEN ? ELSE timestamp END
                    WHERE player_uuid = ?
                    """)) {
                ps.setString(1, name);
                ps.setInt(2, bestWave);
                ps.setInt(3, bestWave);
                ps.setInt(4, bestWave);
                ps.setLong(5, System.currentTimeMillis());
                ps.setString(6, uuid);
                rows = ps.executeUpdate();
            }

            if (rows == 0) {
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO Endgame_Leaderboard (player_uuid, player_name, best_wave, timestamp)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    ps.setString(1, uuid);
                    ps.setString(2, name);
                    ps.setInt(3, bestWave);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
        }
    }
}
