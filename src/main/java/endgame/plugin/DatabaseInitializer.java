package endgame.plugin;

import com.hypixel.hytale.server.core.util.Config;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.AccessoryPouchData;
import endgame.plugin.config.AchievementData;
import endgame.plugin.config.BestiaryData;
import endgame.plugin.config.BountyData;
import endgame.plugin.database.DatabaseConfig;
import endgame.plugin.database.DatabaseSyncService;
import endgame.plugin.database.DataSourceFactory;
import endgame.plugin.database.EndgameRepository;
import endgame.plugin.database.PlayerDataSnapshot;

import java.util.UUID;

/**
 * Manages database initialization and player data sync/serialization.
 * Extracted from EndgameQoL to reduce main class size.
 */
public class DatabaseInitializer {

    private final EndgameQoL plugin;
    private DatabaseSyncService databaseSyncService;

    public DatabaseInitializer(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the database sync service. Called from setup().
     */
    public void initialize(Config<DatabaseConfig> databaseConfig) {
        DatabaseConfig dbCfg = databaseConfig.get();
        if (dbCfg.isEnabled()) {
            try {
                var ds = DataSourceFactory.create(dbCfg);
                var repo = new EndgameRepository(ds);
                this.databaseSyncService = new DatabaseSyncService(repo);
                this.databaseSyncService.initialize();
            } catch (Exception e) {
                plugin.getLogger().atWarning().withCause(e).log("[EndgameQoL] Database init failed (non-critical)");
                this.databaseSyncService = new DatabaseSyncService(null);
            }
        } else {
            this.databaseSyncService = new DatabaseSyncService(null);
        }
    }

    /**
     * Sync player data to the database (async). Called on disconnect.
     * Reads all per-player data from the ECS component.
     */
    public void syncPlayerToDatabase(String uuid, String username) {
        try {
            // Read all per-player data from ECS component
            PlayerEndgameComponent comp = plugin.getPlayerComponent(UUID.fromString(uuid));

            // Bounty state
            String bountyJson = null;
            if (comp != null) {
                var state = comp.getBountyState();
                if (state != null) {
                    bountyJson = serializeBountyState(state);
                }
            }

            // Achievements
            String achievementJson = null;
            if (comp != null) {
                var achState = comp.getAchievementState();
                if (achState != null) {
                    achievementJson = serializeAchievements(achState);
                }
            }

            // Bestiary
            String bestiaryJson = null;
            if (comp != null) {
                var bestState = comp.getBestiaryState();
                if (bestState != null) {
                    bestiaryJson = serializeBestiary(bestState);
                }
            }

            // Accessory pouch
            String accessoryPouchJson = null;
            if (comp != null) {
                var pouch = comp.getAccessoryPouchData();
                if (pouch.getOccupiedCount() > 0) {
                    accessoryPouchJson = serializeAccessoryPouch(pouch);
                }
            }

            // Combo personal best
            int comboPersonalBest = comp != null ? comp.getComboPersonalBest() : 0;

            databaseSyncService.syncAsync(new PlayerDataSnapshot(
                    uuid, username, bountyJson,
                    achievementJson, bestiaryJson, accessoryPouchJson, comboPersonalBest));
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[Database] Failed to build snapshot for %s: %s", uuid, e.getMessage());
        }
    }

    private String serializeBountyState(BountyData.PlayerBountyState state) {
        var sb = new StringBuilder("{");
        sb.append("\"lastRefresh\":").append(state.getLastRefreshTimestamp());
        sb.append(",\"streakClaimed\":").append(state.isStreakClaimed());
        sb.append(",\"totalCompleted\":").append(state.getTotalBountiesCompleted());
        sb.append(",\"bounties\":[");
        var bounties = state.getBounties();
        for (int i = 0; i < bounties.size(); i++) {
            if (i > 0) sb.append(",");
            var b = bounties.get(i);
            sb.append("{\"id\":\"").append(b.getTemplateId())
              .append("\",\"p\":").append(b.getProgress())
              .append(",\"t\":").append(b.getTarget())
              .append(",\"c\":").append(b.isCompleted())
              .append(",\"cl\":").append(b.isClaimed()).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String serializeAchievements(AchievementData.PlayerAchievementState state) {
        var sb = new StringBuilder("{\"completed\":{");
        boolean first = true;
        for (var e : state.getCompleted().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("},\"progress\":{");
        first = true;
        for (var e : state.getProgressMap().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("},\"claimed\":{");
        first = true;
        for (var e : state.getClaimed().entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    private String serializeBestiary(BestiaryData.PlayerBestiaryState state) {
        var sb = new StringBuilder("{\"discoveryMilestone\":");
        sb.append(state.getClaimedDiscoveryMilestone());
        sb.append(",\"entries\":{");
        boolean first = true;
        for (var e : state.getEntries().entrySet()) {
            if (!first) sb.append(",");
            var npc = e.getValue();
            sb.append("\"").append(e.getKey()).append("\":{")
              .append("\"k\":").append(npc.getKillCount())
              .append(",\"d\":").append(npc.isDiscovered())
              .append(",\"m\":").append(npc.getClaimedMilestone())
              .append("}");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    private String serializeAccessoryPouch(AccessoryPouchData pouch) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < AccessoryPouchData.MAX_SLOTS; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(pouch.getItemId(i))
              .append("\",\"n\":").append(pouch.getCount(i))
              .append(",\"d\":").append(pouch.getDurability(i)).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    public DatabaseSyncService getDatabaseSyncService() {
        return databaseSyncService;
    }

    /**
     * Shut down the database sync service. Called from EndgameQoL.shutdown().
     */
    public void shutdown() {
        if (this.databaseSyncService != null) {
            this.databaseSyncService.shutdown();
            plugin.getLogger().atInfo().log("[EndgameQoL] Database sync service shut down");
        }
    }
}
