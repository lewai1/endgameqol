package endgame.plugin.migration;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.Config;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.AccessoryPouchData;
import endgame.plugin.config.AccessoryPouchStorage;
import endgame.plugin.config.AchievementData;
import endgame.plugin.config.AchievementData.PlayerAchievementState;
import endgame.plugin.config.BestiaryData;
import endgame.plugin.config.BestiaryData.PlayerBestiaryState;
import endgame.plugin.config.BountyData;
import endgame.plugin.config.BountyData.PlayerBountyState;
import endgame.plugin.config.PlayerLocaleStorage;
import endgame.plugin.config.VoidPocketData;
import endgame.plugin.config.VoidPocketStorage;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds references to old monolithic config files for per-player migration.
 *
 * Migration flow:
 *  1. At startup, old configs are loaded via withConfig() (already in memory).
 *  2. On each player connect, PlayerDataEnsureSystem checks dataVersion == 0.
 *  3. If legacy data exists for that UUID -> copy into component -> set version = 1.
 *  4. Old config files remain on disk (backup) until admin runs cleanup.
 *  5. Players who never reconnect: data stays in old files indefinitely (safe).
 *
 * This approach avoids iterating all players upfront — we just look up by UUID on demand.
 * The old Config objects are already in memory from withConfig(), so lookups are O(1).
 */
public class LegacyDataMigration {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Migration");

    private final Config<AchievementData> achievementData;
    private final Config<BestiaryData> bestiaryData;
    private final Config<BountyData> bountyData;
    private final Config<VoidPocketStorage> voidPocketConfig;
    private final Config<AccessoryPouchStorage> accessoryPouchConfig;
    private final Config<PlayerLocaleStorage> playerLocaleConfig;
    private final AtomicInteger migratedCount = new AtomicInteger(0);

    public LegacyDataMigration(Config<AchievementData> achievementData,
                               Config<BestiaryData> bestiaryData,
                               Config<BountyData> bountyData,
                               Config<VoidPocketStorage> voidPocketConfig,
                               Config<AccessoryPouchStorage> accessoryPouchConfig,
                               Config<PlayerLocaleStorage> playerLocaleConfig) {
        this.achievementData = achievementData;
        this.bestiaryData = bestiaryData;
        this.bountyData = bountyData;
        this.voidPocketConfig = voidPocketConfig;
        this.accessoryPouchConfig = accessoryPouchConfig;
        this.playerLocaleConfig = playerLocaleConfig;
    }

    /**
     * Check if there's any legacy data loaded (i.e., old config files exist).
     * Quick heuristic: check if bounty, void pocket, or accessory data has entries.
     */
    public boolean hasLegacyData() {
        try {
            if (bountyData != null && !bountyData.get().getPlayers().isEmpty()) return true;
            if (voidPocketConfig != null && !voidPocketConfig.get().getAllVoidPockets().isEmpty()) return true;
            if (accessoryPouchConfig != null && !accessoryPouchConfig.get().getAllAccessoryPouches().isEmpty()) return true;
            // Achievement/Bestiary don't expose iteration, but if the above have data, migration is needed
        } catch (Exception e) {
            LOGGER.atWarning().log("[Migration] Error checking legacy data: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Migrate legacy data for a single player into their ECS component.
     * Called from PlayerDataEnsureSystem when dataVersion == 0.
     *
     * Looks up each data source by UUID. If data exists, copies it into the component.
     * This is idempotent — if the player has no legacy data, nothing happens.
     *
     * @param uuid player UUID string
     * @param comp the player's ECS component to populate
     */
    public void migratePlayer(String uuid, PlayerEndgameComponent comp) {
        boolean migrated = false;

        // Achievements
        try {
            if (achievementData != null) {
                PlayerAchievementState achState = achievementData.get().getPlayerState(uuid);
                if (achState != null) {
                    comp.setAchievementState(achState);
                    migrated = true;
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[Migration] Failed to migrate achievements for %s: %s", uuid, e.getMessage());
        }

        // Bestiary
        try {
            if (bestiaryData != null) {
                PlayerBestiaryState bestState = bestiaryData.get().getPlayerState(uuid);
                if (bestState != null) {
                    comp.setBestiaryState(bestState);
                    migrated = true;
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[Migration] Failed to migrate bestiary for %s: %s", uuid, e.getMessage());
        }

        // Bounties
        try {
            if (bountyData != null) {
                PlayerBountyState bountyState = bountyData.get().getPlayerState(uuid);
                if (bountyState != null) {
                    comp.setBountyState(bountyState);
                    migrated = true;
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[Migration] Failed to migrate bounties for %s: %s", uuid, e.getMessage());
        }

        // Void Pocket
        try {
            if (voidPocketConfig != null) {
                VoidPocketData vpData = voidPocketConfig.get().getAllVoidPockets().get(uuid);
                if (vpData != null) {
                    comp.setVoidPocketData(vpData);
                    migrated = true;
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[Migration] Failed to migrate void pocket for %s: %s", uuid, e.getMessage());
        }

        // Accessory Pouch
        try {
            if (accessoryPouchConfig != null) {
                AccessoryPouchData apData = accessoryPouchConfig.get().getAllAccessoryPouches().get(uuid);
                if (apData != null) {
                    comp.setAccessoryPouchData(apData);
                    migrated = true;
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[Migration] Failed to migrate accessory pouch for %s: %s", uuid, e.getMessage());
        }

        // Locale
        try {
            if (playerLocaleConfig != null) {
                String locale = playerLocaleConfig.get().getOverride(uuid);
                if (locale != null && !locale.isEmpty()) {
                    comp.setLocale(locale);
                    migrated = true;
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[Migration] Failed to migrate locale for %s: %s", uuid, e.getMessage());
        }

        if (migrated) {
            migratedCount.incrementAndGet();
        }
    }

    public int getMigratedCount() {
        return migratedCount.get();
    }
}
