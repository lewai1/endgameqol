package endgame.plugin.config;

import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * One-time migration from the old flat EndgameConfig format (pre-4.0) to the
 * new nested sub-config format. Detects the old format by the absence of a
 * "Version" key, reads all values with the LEGACY_CODEC, populates the live
 * config (which uses the new nested CODEC), and saves.
 *
 * Two-phase migration:
 * 1. backupOldFormatIfNeeded() — called in constructor BEFORE withConfig(),
 *    renames old-format file so the new CODEC doesn't crash on it.
 * 2. restoreFromBackupIfNeeded() — called in setup() AFTER withConfig(),
 *    reads the backup with LEGACY_CODEC and copies values into the live config.
 */
public class ConfigMigration {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Migration");
    private static final String BACKUP_NAME = "EndgameConfig.v3.bak.json";

    /**
     * Phase 1: Called in constructor BEFORE withConfig().
     * If the config file is in old flat format (no "Version" key),
     * rename it to a backup so withConfig() creates fresh defaults.
     */
    public static void backupOldFormatIfNeeded(Path dataDir) {
        Path file = dataDir.resolve("EndgameConfig.json");
        if (!Files.exists(file)) return;

        try {
            String content = Files.readString(file);
            if (content.contains("\"Version\"")) return; // already new format

            Path backup = dataDir.resolve(BACKUP_NAME);
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.atInfo().log("Old flat config backed up to %s for migration", BACKUP_NAME);
        } catch (Exception e) {
            LOGGER.atWarning().log("Pre-migration backup failed: %s", e.getMessage());
        }
    }

    /**
     * Phase 2: Called in setup() AFTER withConfig().
     * If a backup exists, read it with LEGACY_CODEC and copy values into the live config.
     */
    public static void restoreFromBackupIfNeeded(Path dataDir, Config<EndgameConfig> config) {
        Path backup = dataDir.resolve(BACKUP_NAME);
        if (!Files.exists(backup)) return;

        try {
            LOGGER.atInfo().log("Migrating old flat config → v4.0 nested format...");

            EndgameConfig legacy = RawJsonReader.readSync(backup, EndgameConfig.LEGACY_CODEC, LOGGER);
            if (legacy == null) {
                LOGGER.atWarning().log("Failed to read legacy config backup — using defaults");
                return;
            }

            EndgameConfig live = config.get();
            copyLegacyToLive(legacy, live);
            config.save();

            Files.delete(backup);
            LOGGER.atInfo().log("Config migration complete! Backup removed.");
        } catch (Exception e) {
            LOGGER.atWarning().log("Config migration failed (non-critical, defaults used): %s", e.getMessage());
        }
    }

    /**
     * Copy all field values from a legacy-read config into the live config.
     * Uses delegation setters so values flow into the correct sub-configs.
     */
    private static void copyLegacyToLive(EndgameConfig legacy, EndgameConfig live) {
        // Difficulty
        live.setDifficulty(legacy.getDifficulty());
        live.setDifficultyAffectsBosses(legacy.isDifficultyAffectsBosses());
        live.setDifficultyAffectsMobs(legacy.isDifficultyAffectsMobs());
        live.setCustomHealthMultiplier(legacy.getCustomHealthMultiplier());
        live.setCustomDamageMultiplier(legacy.getCustomDamageMultiplier());

        // Weapons: Hedera
        live.setEnableHederaDaggerPoison(legacy.isEnableHederaDaggerPoison());
        live.setHederaDaggerPoisonDamage(legacy.getHederaDaggerPoisonDamage());
        live.setHederaDaggerPoisonTicks(legacy.getHederaDaggerPoisonTicks());
        live.setEnableHederaDaggerLifesteal(legacy.isEnableHederaDaggerLifesteal());
        live.setHederaDaggerLifestealPercent(legacy.getHederaDaggerLifestealPercent());

        // Weapons: Prisma Mirage
        live.setPrismaMirageEnabled(legacy.isPrismaMirageEnabled());
        live.setPrismaMirageCooldownMs(legacy.getPrismaMirageCooldownMs());
        live.setPrismaMirageLifetimeMs(legacy.getPrismaMirageLifetimeMs());

        // Weapons: Void Mark
        live.setVoidMarkEnabled(legacy.isVoidMarkEnabled());
        live.setVoidMarkDurationMs(legacy.getVoidMarkDurationMs());
        live.setVoidMarkExecutionEnabled(legacy.isVoidMarkExecutionEnabled());
        live.setVoidMarkExecutionThreshold(legacy.getVoidMarkExecutionThreshold());
        live.setVoidMarkExecutionMultiplier(legacy.getVoidMarkExecutionMultiplier());

        // Weapons: Dagger Blink
        live.setDaggerBlinkEnabled(legacy.isDaggerBlinkEnabled());
        live.setDaggerBlinkDistance(legacy.getDaggerBlinkDistance());

        // Weapons: Dagger Trail
        live.setDaggerTrailEnabled(legacy.isDaggerTrailEnabled());
        live.setDaggerTrailDamage(legacy.getDaggerTrailDamage());

        // Weapons: Blazefist Burn
        live.setBlazefistBurnEnabled(legacy.isBlazefistBurnEnabled());
        live.setBlazefistBurnDamage(legacy.getBlazefistBurnDamage());
        live.setBlazefistBurnTicks(legacy.getBlazefistBurnTicks());

        // Armor: Mana Regen
        live.setManaRegenArmorEnabled(legacy.isManaRegenArmorEnabled());
        live.setManaRegenMithrilPerPiece(legacy.getManaRegenMithrilPerPiece());
        live.setManaRegenOnyxiumPerPiece(legacy.getManaRegenOnyxiumPerPiece());
        live.setManaRegenPrismaPerPiece(legacy.getManaRegenPrismaPerPiece());

        // Armor: HP Regen
        live.setArmorHPRegenEnabled(legacy.isArmorHPRegenEnabled());
        live.setArmorHPRegenDelaySec(legacy.getArmorHPRegenDelaySec());
        live.setArmorHPRegenOnyxiumPerPiece(legacy.getArmorHPRegenOnyxiumPerPiece());
        live.setArmorHPRegenPrismaPerPiece(legacy.getArmorHPRegenPrismaPerPiece());

        // Combo
        live.setComboEnabled(legacy.isComboEnabled());
        live.setComboTimerSeconds(legacy.getComboTimerSeconds());
        live.setComboDamageX2(legacy.getComboDamageX2());
        live.setComboDamageX3(legacy.getComboDamageX3());
        live.setComboDamageX4(legacy.getComboDamageX4());
        live.setComboDamageFrenzy(legacy.getComboDamageFrenzy());
        live.setComboTierEffectsEnabled(legacy.isComboTierEffectsEnabled());
        live.setComboDecayEnabled(legacy.isComboDecayEnabled());

        // Gauntlet
        live.setGauntletEnabled(legacy.isGauntletEnabled());
        live.setGauntletScalingPercent(legacy.getGauntletScalingPercent());
        live.setGauntletBuffCount(legacy.getGauntletBuffCount());

        // Bounty
        live.setBountyEnabled(legacy.isBountyEnabled());
        live.setBountyRefreshHours(legacy.getBountyRefreshHours());
        live.setBountyStreakEnabled(legacy.isBountyStreakEnabled());
        live.setBountyWeeklyEnabled(legacy.isBountyWeeklyEnabled());

        // Crafting
        live.setEnableGliderCrafting(legacy.isEnableGliderCrafting());
        live.setEnableMithrilOreCrafting(legacy.isEnableMithrilOreCrafting());
        live.setEnablePortalKeyTaiga(legacy.isEnablePortalKeyTaiga());
        live.setEnablePortalKeyHederasLair(legacy.isEnablePortalKeyHederasLair());
        live.setEnablePortalHedera(legacy.isEnablePortalHedera());
        live.setEnablePortalGolemVoid(legacy.isEnablePortalGolemVoid());

        // Misc
        live.setPvpEnabled(legacy.isPvpEnabled());
        live.setEnableDungeonBlockProtection(legacy.isEnableDungeonBlockProtection());
        live.setWardenTrialEnabled(legacy.isWardenTrialEnabled());
        live.setMinionSpawnRadius(legacy.getMinionSpawnRadius());
        live.setEyeVoidHealthMultiplier(legacy.getEyeVoidHealthMultiplier());
        live.setRPGLevelingEnabled(legacy.isRPGLevelingEnabled());
        live.setRPGLevelingAutoDetected(legacy.getRPGLevelingAutoDetected());
        live.setEndlessLevelingEnabled(legacy.isEndlessLevelingEnabled());
        live.setEndlessLevelingAutoDetected(legacy.getEndlessLevelingAutoDetected());

        // Boss configs (copy map directly)
        for (var entry : legacy.getAllBossConfigs().entrySet()) {
            live.getAllBossConfigs().put(entry.getKey(), entry.getValue());
        }
    }
}
