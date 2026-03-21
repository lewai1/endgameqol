package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration EndgameQoL — v4.0 nested format.
 *
 * All settings are organized into sub-config objects but serialized as ONE JSON file.
 * Backward-compatible delegation getters ensure zero changes needed in consumer files.
 */
public class EndgameConfig {

    /**
     * Legacy CODEC — reads the old flat format (pre-4.0). Used only by ConfigMigration.
     */
    @Nonnull
    public static final BuilderCodec<EndgameConfig> LEGACY_CODEC = buildLegacyCodec();

    @Nonnull
    public static final BuilderCodec<EndgameConfig> CODEC = BuilderCodec
            .builder(EndgameConfig.class, EndgameConfig::new)
            .append(new KeyedCodec<String>("Version", Codec.STRING),
                    (c, v) -> c.version = "4.1.3", c -> c.version).add()
            .append(new KeyedCodec<DifficultyConfig>("Difficulty", DifficultyConfig.CODEC),
                    (c, v) -> { if (v != null) c.difficulty = v; }, c -> c.difficulty).add()
            .append(new KeyedCodec<Map<String, BossConfig>>("Bosses",
                    new MapCodec<>(BossConfig.CODEC, HashMap::new, false)),
                    (c, v) -> { if (v != null) c.bossConfigs.putAll(v); },
                    c -> c.bossConfigs).add()
            .append(new KeyedCodec<WeaponsConfig>("Weapons", WeaponsConfig.CODEC),
                    (c, v) -> { if (v != null) c.weapons = v; }, c -> c.weapons).add()
            .append(new KeyedCodec<ArmorConfig>("Armor", ArmorConfig.CODEC),
                    (c, v) -> { if (v != null) c.armor = v; }, c -> c.armor).add()
            .append(new KeyedCodec<ComboConfig>("Combo", ComboConfig.CODEC),
                    (c, v) -> { if (v != null) c.combo = v; }, c -> c.combo).add()
            .append(new KeyedCodec<GauntletConfig>("Gauntlet", GauntletConfig.CODEC),
                    (c, v) -> { if (v != null) c.gauntlet = v; }, c -> c.gauntlet).add()
            .append(new KeyedCodec<BountyConfig>("Bounty", BountyConfig.CODEC),
                    (c, v) -> { if (v != null) c.bounty = v; }, c -> c.bounty).add()
            .append(new KeyedCodec<CraftingConfig>("Crafting", CraftingConfig.CODEC),
                    (c, v) -> { if (v != null) c.crafting = v; }, c -> c.crafting).add()
            .append(new KeyedCodec<MiscConfig>("Misc", MiscConfig.CODEC),
                    (c, v) -> { if (v != null) c.misc = v; }, c -> c.misc).add()
            .build();

    // === SUB-CONFIG INSTANCES ===

    private String version = "4.1.3";
    private DifficultyConfig difficulty = new DifficultyConfig();
    private final Map<String, BossConfig> bossConfigs = new ConcurrentHashMap<>();
    private WeaponsConfig weapons = new WeaponsConfig();
    private ArmorConfig armor = new ArmorConfig();
    private ComboConfig combo = new ComboConfig();
    private GauntletConfig gauntlet = new GauntletConfig();
    private BountyConfig bounty = new BountyConfig();
    private CraftingConfig crafting = new CraftingConfig();
    private MiscConfig misc = new MiscConfig();

    public EndgameConfig() {
        initDefaultBossConfigs();
    }

    // === SUB-CONFIG ACCESSORS ===

    public String getVersion() { return version; }
    public DifficultyConfig difficulty() { return difficulty; }
    public WeaponsConfig weapons() { return weapons; }
    public ArmorConfig armor() { return armor; }
    public ComboConfig combo() { return combo; }
    public GauntletConfig gauntlet() { return gauntlet; }
    public BountyConfig bounty() { return bounty; }
    public CraftingConfig crafting() { return crafting; }
    public MiscConfig misc() { return misc; }

    // ========================================================================
    // BOSS CONFIG (data-driven map — not in a sub-config, stays at top level)
    // ========================================================================

    private void initDefaultBossConfigs() {
        for (BossType bt : BossType.values()) {
            bossConfigs.computeIfAbsent(bt.getConfigKey(), k -> {
                BossConfig bc = new BossConfig();
                bc.setPlayerScaling(bt.getDefaultPlayerScaling());
                bc.setXpReward(bt.getDefaultXpReward());
                return bc;
            });
        }
    }

    @Nonnull
    public BossConfig getBossConfig(@Nonnull BossType bossType) {
        return bossConfigs.computeIfAbsent(bossType.getConfigKey(), k -> {
            BossConfig bc = new BossConfig();
            bc.setPlayerScaling(bossType.getDefaultPlayerScaling());
            bc.setXpReward(bossType.getDefaultXpReward());
            return bc;
        });
    }

    @Nonnull
    public BossConfig getBossConfig(@Nonnull String bossTypeKey) {
        BossConfig bc = bossConfigs.get(bossTypeKey);
        if (bc != null) return bc;
        try {
            BossType bt = BossType.valueOf(bossTypeKey);
            return getBossConfig(bt);
        } catch (IllegalArgumentException e) {
            return bossConfigs.computeIfAbsent(bossTypeKey, k -> new BossConfig());
        }
    }

    public Map<String, BossConfig> getAllBossConfigs() { return bossConfigs; }

    public int getEffectiveBossHealth(@Nonnull BossType bossType) {
        float diffMult = bossType.isBoss()
                ? (difficulty.isAffectsBosses() ? difficulty.getEffectiveHealthMultiplier() : 1.0f)
                : (difficulty.isAffectsMobs() ? difficulty.getEffectiveHealthMultiplier() : 1.0f);
        return getBossConfig(bossType).getEffectiveHealth(
                bossType.getDefaultHealth(), diffMult, bossType.isBoss());
    }

    public float getEffectiveBossDamageMultiplier(@Nonnull BossType bossType) {
        float groupMult = bossType.isBoss() ? difficulty.getBossDamageMultiplier() : difficulty.getMobDamageMultiplier();
        return getBossConfig(bossType).getEffectiveDamageMultiplier(groupMult);
    }

    // ========================================================================
    // DELEGATION GETTERS — backward compat for all 35 consumer files
    // Zero consumer changes needed.
    // ========================================================================

    // --- Difficulty ---
    public Difficulty getDifficulty() { return difficulty.getDifficulty(); }
    public DifficultyConfig getDifficultyConfig() { return difficulty; }
    public void setDifficulty(Difficulty d) { difficulty.setDifficulty(d); }
    public String getDifficultyString() { return difficulty.getDifficultyString(); }
    public void setDifficultyString(String s) { difficulty.setDifficultyString(s); }
    public float getCustomHealthMultiplier() { return difficulty.getCustomHealthMultiplier(); }
    public void setCustomHealthMultiplier(float m) { difficulty.setCustomHealthMultiplier(m); }
    public float getCustomDamageMultiplier() { return difficulty.getCustomDamageMultiplier(); }
    public void setCustomDamageMultiplier(float m) { difficulty.setCustomDamageMultiplier(m); }
    public float getEffectiveHealthMultiplier() { return difficulty.getEffectiveHealthMultiplier(); }
    public float getEffectiveDamageMultiplier() { return difficulty.getEffectiveDamageMultiplier(); }
    public boolean isDifficultyAffectsBosses() { return difficulty.isAffectsBosses(); }
    public void setDifficultyAffectsBosses(boolean a) { difficulty.setAffectsBosses(a); }
    public boolean isDifficultyAffectsMobs() { return difficulty.isAffectsMobs(); }
    public void setDifficultyAffectsMobs(boolean a) { difficulty.setAffectsMobs(a); }
    public float getBossDamageMultiplier() { return difficulty.getBossDamageMultiplier(); }
    public float getMobDamageMultiplier() { return difficulty.getMobDamageMultiplier(); }
    public float getDamageMultiplier() { return difficulty.getEffectiveDamageMultiplier(); }

    // --- Weapons: Hedera Daggers ---
    public boolean isEnableHederaDaggerPoison() { return weapons.isHederaPoisonEnabled(); }
    public void setEnableHederaDaggerPoison(boolean e) { weapons.setHederaPoisonEnabled(e); }
    public float getHederaDaggerPoisonDamage() { return weapons.getHederaPoisonDamage(); }
    public void setHederaDaggerPoisonDamage(float d) { weapons.setHederaPoisonDamage(d); }
    public int getHederaDaggerPoisonTicks() { return weapons.getHederaPoisonTicks(); }
    public void setHederaDaggerPoisonTicks(int t) { weapons.setHederaPoisonTicks(t); }
    public boolean isEnableHederaDaggerLifesteal() { return weapons.isHederaLifestealEnabled(); }
    public void setEnableHederaDaggerLifesteal(boolean e) { weapons.setHederaLifestealEnabled(e); }
    public float getHederaDaggerLifestealPercent() { return weapons.getHederaLifestealPercent(); }
    public void setHederaDaggerLifestealPercent(float p) { weapons.setHederaLifestealPercent(p); }

    // --- Weapons: Prisma Mirage ---
    public boolean isPrismaMirageEnabled() { return weapons.isPrismaMirageEnabled(); }
    public void setPrismaMirageEnabled(boolean e) { weapons.setPrismaMirageEnabled(e); }
    public int getPrismaMirageCooldownMs() { return weapons.getPrismaMirageCooldownMs(); }
    public void setPrismaMirageCooldownMs(int ms) { weapons.setPrismaMirageCooldownMs(ms); }
    public int getPrismaMirageLifetimeMs() { return weapons.getPrismaMirageLifetimeMs(); }
    public void setPrismaMirageLifetimeMs(int ms) { weapons.setPrismaMirageLifetimeMs(ms); }

    // --- Weapons: Void Mark ---
    public boolean isVoidMarkEnabled() { return weapons.isVoidMarkEnabled(); }
    public void setVoidMarkEnabled(boolean e) { weapons.setVoidMarkEnabled(e); }
    public int getVoidMarkDurationMs() { return weapons.getVoidMarkDurationMs(); }
    public void setVoidMarkDurationMs(int ms) { weapons.setVoidMarkDurationMs(ms); }
    public boolean isVoidMarkExecutionEnabled() { return weapons.isVoidMarkExecutionEnabled(); }
    public void setVoidMarkExecutionEnabled(boolean e) { weapons.setVoidMarkExecutionEnabled(e); }
    public float getVoidMarkExecutionThreshold() { return weapons.getVoidMarkExecutionThreshold(); }
    public void setVoidMarkExecutionThreshold(float t) { weapons.setVoidMarkExecutionThreshold(t); }
    public float getVoidMarkExecutionMultiplier() { return weapons.getVoidMarkExecutionMultiplier(); }
    public void setVoidMarkExecutionMultiplier(float m) { weapons.setVoidMarkExecutionMultiplier(m); }

    // --- Weapons: Dagger Blink ---
    public boolean isDaggerBlinkEnabled() { return weapons.isDaggerBlinkEnabled(); }
    public void setDaggerBlinkEnabled(boolean e) { weapons.setDaggerBlinkEnabled(e); }
    public float getDaggerBlinkDistance() { return weapons.getDaggerBlinkDistance(); }
    public void setDaggerBlinkDistance(float d) { weapons.setDaggerBlinkDistance(d); }

    // --- Weapons: Dagger Trail ---
    public boolean isDaggerTrailEnabled() { return weapons.isDaggerTrailEnabled(); }
    public void setDaggerTrailEnabled(boolean e) { weapons.setDaggerTrailEnabled(e); }
    public float getDaggerTrailDamage() { return weapons.getDaggerTrailDamage(); }
    public void setDaggerTrailDamage(float d) { weapons.setDaggerTrailDamage(d); }

    // --- Weapons: Blazefist Burn ---
    public boolean isBlazefistBurnEnabled() { return weapons.isBlazefistBurnEnabled(); }
    public void setBlazefistBurnEnabled(boolean e) { weapons.setBlazefistBurnEnabled(e); }
    public float getBlazefistBurnDamage() { return weapons.getBlazefistBurnDamage(); }
    public void setBlazefistBurnDamage(float d) { weapons.setBlazefistBurnDamage(d); }
    public int getBlazefistBurnTicks() { return weapons.getBlazefistBurnTicks(); }
    public void setBlazefistBurnTicks(int t) { weapons.setBlazefistBurnTicks(t); }

    // --- Weapons: Dagger Vanish ---
    public int getDaggerVanishCooldownMs() { return weapons.getDaggerVanishCooldownMs(); }
    public void setDaggerVanishCooldownMs(int ms) { weapons.setDaggerVanishCooldownMs(ms); }
    public int getDaggerVanishInvulnerabilityMs() { return weapons.getDaggerVanishInvulnerabilityMs(); }
    public void setDaggerVanishInvulnerabilityMs(int ms) { weapons.setDaggerVanishInvulnerabilityMs(ms); }

    // --- Armor: Mana Regen ---
    public boolean isManaRegenArmorEnabled() { return armor.isManaRegenEnabled(); }
    public void setManaRegenArmorEnabled(boolean e) { armor.setManaRegenEnabled(e); }
    public float getManaRegenMithrilPerPiece() { return armor.getManaRegenMithrilPerPiece(); }
    public void setManaRegenMithrilPerPiece(float v) { armor.setManaRegenMithrilPerPiece(v); }
    public float getManaRegenOnyxiumPerPiece() { return armor.getManaRegenOnyxiumPerPiece(); }
    public void setManaRegenOnyxiumPerPiece(float v) { armor.setManaRegenOnyxiumPerPiece(v); }
    public float getManaRegenPrismaPerPiece() { return armor.getManaRegenPrismaPerPiece(); }
    public void setManaRegenPrismaPerPiece(float v) { armor.setManaRegenPrismaPerPiece(v); }

    // --- Armor: HP Regen ---
    public boolean isArmorHPRegenEnabled() { return armor.isHPRegenEnabled(); }
    public void setArmorHPRegenEnabled(boolean e) { armor.setHPRegenEnabled(e); }
    public float getArmorHPRegenDelaySec() { return armor.getHpRegenDelaySec(); }
    public void setArmorHPRegenDelaySec(float v) { armor.setHpRegenDelaySec(v); }
    public float getArmorHPRegenOnyxiumPerPiece() { return armor.getHpRegenOnyxiumPerPiece(); }
    public void setArmorHPRegenOnyxiumPerPiece(float v) { armor.setHpRegenOnyxiumPerPiece(v); }
    public float getArmorHPRegenPrismaPerPiece() { return armor.getHpRegenPrismaPerPiece(); }
    public void setArmorHPRegenPrismaPerPiece(float v) { armor.setHpRegenPrismaPerPiece(v); }

    // --- Combo ---
    public boolean isComboEnabled() { return combo.isEnabled(); }
    public void setComboEnabled(boolean e) { combo.setEnabled(e); }
    public float getComboTimerSeconds() { return combo.getTimerSeconds(); }
    public void setComboTimerSeconds(float s) { combo.setTimerSeconds(s); }
    public float getComboDamageX2() { return combo.getComboDamageX2(); }
    public void setComboDamageX2(float m) { combo.setComboDamageX2(m); }
    public float getComboDamageX3() { return combo.getComboDamageX3(); }
    public void setComboDamageX3(float m) { combo.setComboDamageX3(m); }
    public float getComboDamageX4() { return combo.getComboDamageX4(); }
    public void setComboDamageX4(float m) { combo.setComboDamageX4(m); }
    public float getComboDamageFrenzy() { return combo.getComboDamageFrenzy(); }
    public void setComboDamageFrenzy(float m) { combo.setComboDamageFrenzy(m); }
    public boolean isComboTierEffectsEnabled() { return combo.isTierEffectsEnabled(); }
    public void setComboTierEffectsEnabled(boolean e) { combo.setTierEffectsEnabled(e); }
    public boolean isComboDecayEnabled() { return combo.isDecayEnabled(); }
    public void setComboDecayEnabled(boolean e) { combo.setDecayEnabled(e); }

    // --- Gauntlet ---
    public boolean isGauntletEnabled() { return gauntlet.isEnabled(); }
    public void setGauntletEnabled(boolean e) { gauntlet.setEnabled(e); }
    public int getGauntletScalingPercent() { return gauntlet.getScalingPercent(); }
    public void setGauntletScalingPercent(int p) { gauntlet.setScalingPercent(p); }
    public int getGauntletBuffCount() { return gauntlet.getBuffCount(); }
    public void setGauntletBuffCount(int c) { gauntlet.setBuffCount(c); }

    // --- Bounty ---
    public boolean isBountyEnabled() { return bounty.isEnabled(); }
    public void setBountyEnabled(boolean e) { bounty.setEnabled(e); }
    public int getBountyRefreshHours() { return bounty.getRefreshHours(); }
    public void setBountyRefreshHours(int h) { bounty.setRefreshHours(h); }
    public boolean isBountyStreakEnabled() { return bounty.isStreakEnabled(); }
    public void setBountyStreakEnabled(boolean e) { bounty.setStreakEnabled(e); }
    public boolean isBountyWeeklyEnabled() { return bounty.isWeeklyEnabled(); }
    public void setBountyWeeklyEnabled(boolean e) { bounty.setWeeklyEnabled(e); }

    // --- Crafting ---
    public boolean isEnableGliderCrafting() { return crafting.isEnableGliderCrafting(); }
    public void setEnableGliderCrafting(boolean e) { crafting.setEnableGliderCrafting(e); }
    public boolean isEnableMithrilOreCrafting() { return crafting.isEnableMithrilOreCrafting(); }
    public void setEnableMithrilOreCrafting(boolean e) { crafting.setEnableMithrilOreCrafting(e); }
    public boolean isEnablePortalKeyTaiga() { return crafting.isEnablePortalKeyTaiga(); }
    public void setEnablePortalKeyTaiga(boolean e) { crafting.setEnablePortalKeyTaiga(e); }
    public boolean isEnablePortalKeyHederasLair() { return crafting.isEnablePortalKeyHederasLair(); }
    public void setEnablePortalKeyHederasLair(boolean e) { crafting.setEnablePortalKeyHederasLair(e); }
    public boolean isEnablePortalHedera() { return crafting.isEnablePortalHedera(); }
    public void setEnablePortalHedera(boolean e) { crafting.setEnablePortalHedera(e); }
    public boolean isEnablePortalGolemVoid() { return crafting.isEnablePortalGolemVoid(); }
    public void setEnablePortalGolemVoid(boolean e) { crafting.setEnablePortalGolemVoid(e); }

    // --- OrbisGuard ---
    public boolean isOrbisGuardEnabled() { return misc.isOrbisGuardEnabled(); }
    public void setOrbisGuardEnabled(boolean e) { misc.setOrbisGuardEnabled(e); }
    public String getOrbisGuardAutoDetected() { return misc.getOrbisGuardAutoDetected(); }
    public void setOrbisGuardAutoDetected(String s) { misc.setOrbisGuardAutoDetected(s); }
    public boolean isOrbisGuardAutoDetectPending() { return misc.isOrbisGuardAutoDetectPending(); }
    public boolean isOgBlockBuild() { return misc.isOgBlockBuild(); }
    public void setOgBlockBuild(boolean e) { misc.setOgBlockBuild(e); }
    public boolean isOgBlockPvp() { return misc.isOgBlockPvp(); }
    public void setOgBlockPvp(boolean e) { misc.setOgBlockPvp(e); }
    public String getOgBlockedCommands() { return misc.getOgBlockedCommands(); }
    public void setOgBlockedCommands(String s) { misc.setOgBlockedCommands(s); }

    // --- Misc ---
    public boolean isPvpEnabled() { return misc.isPvpEnabled(); }
    public void setPvpEnabled(boolean e) { misc.setPvpEnabled(e); }
    public boolean isEnableDungeonBlockProtection() { return misc.isEnableDungeonBlockProtection(); }
    public void setEnableDungeonBlockProtection(boolean e) { misc.setEnableDungeonBlockProtection(e); }
    public boolean isWardenTrialEnabled() { return misc.isWardenTrialEnabled(); }
    public void setWardenTrialEnabled(boolean e) { misc.setWardenTrialEnabled(e); }
    public float getMinionSpawnRadius() { return misc.getMinionSpawnRadius(); }
    public void setMinionSpawnRadius(float r) { misc.setMinionSpawnRadius(r); }
    public float getEyeVoidHealthMultiplier() { return misc.getEyeVoidHealthMultiplier(); }
    public void setEyeVoidHealthMultiplier(float m) { misc.setEyeVoidHealthMultiplier(m); }
    public boolean isRPGLevelingEnabled() { return misc.isRPGLevelingEnabled(); }
    public void setRPGLevelingEnabled(boolean e) { misc.setRPGLevelingEnabled(e); }
    public String getRPGLevelingAutoDetected() { return misc.getRPGLevelingAutoDetected(); }
    public void setRPGLevelingAutoDetected(String s) { misc.setRPGLevelingAutoDetected(s); }
    public boolean isRPGLevelingAutoDetectPending() { return misc.isRPGLevelingAutoDetectPending(); }
    public boolean isEndlessLevelingEnabled() { return misc.isEndlessLevelingEnabled(); }
    public void setEndlessLevelingEnabled(boolean e) { misc.setEndlessLevelingEnabled(e); }
    public String getEndlessLevelingAutoDetected() { return misc.getEndlessLevelingAutoDetected(); }
    public void setEndlessLevelingAutoDetected(String s) { misc.setEndlessLevelingAutoDetected(s); }
    public boolean isEndlessLevelingAutoDetectPending() { return misc.isEndlessLevelingAutoDetectPending(); }
    public float getElXpShareRange() { return misc.getElXpShareRange(); }
    public void setElXpShareRange(float r) { misc.setElXpShareRange(r); }
    public int getElGauntletXpBase() { return misc.getElGauntletXpBase(); }
    public void setElGauntletXpBase(int v) { misc.setElGauntletXpBase(v); }
    public int getElWardenXpBase() { return misc.getElWardenXpBase(); }
    public void setElWardenXpBase(int v) { misc.setElWardenXpBase(v); }
    public int getElAchievementXp() { return misc.getElAchievementXp(); }
    public void setElAchievementXp(int v) { misc.setElAchievementXp(v); }
    public boolean isAccessoriesEnabled() { return misc.isAccessoriesEnabled(); }
    public void setAccessoriesEnabled(boolean e) { misc.setAccessoriesEnabled(e); }

    // --- Boss Target Switch ---
    public boolean isBossTargetSwitchEnabled() { return misc.isBossTargetSwitchEnabled(); }
    public void setBossTargetSwitchEnabled(boolean e) { misc.setBossTargetSwitchEnabled(e); }
    public int getBossTargetSwitchIntervalMs() { return misc.getBossTargetSwitchIntervalMs(); }
    public void setBossTargetSwitchIntervalMs(int ms) { misc.setBossTargetSwitchIntervalMs(ms); }

    // --- Prisma Armor Vulnerability ---
    public boolean isPrismaArmorVulnerabilityEnabled() { return misc.isPrismaArmorVulnerabilityEnabled(); }
    public void setPrismaArmorVulnerabilityEnabled(boolean e) { misc.setPrismaArmorVulnerabilityEnabled(e); }

    // --- Prisma Weapon Boss Block ---
    public boolean isPrismaWeaponBossBlockEnabled() { return misc.isPrismaWeaponBossBlockEnabled(); }
    public void setPrismaWeaponBossBlockEnabled(boolean e) { misc.setPrismaWeaponBossBlockEnabled(e); }

    // --- Vorthak ---
    public boolean isVorthakEnabled() { return misc.isVorthakEnabled(); }
    public void setVorthakEnabled(boolean e) { misc.setVorthakEnabled(e); }

    // --- Shared Boss Kill Credit ---
    public boolean isSharedBossKillCredit() { return misc.isSharedBossKillCredit(); }
    public void setSharedBossKillCredit(boolean e) { misc.setSharedBossKillCredit(e); }

    // --- Warden Trial Wave Timers (per tier, 0-indexed) ---
    public int getWardenTrialTimer(int tierIndex) { return misc.getWardenTrialTimer(tierIndex); }
    public void setWardenTrialTimer(int tierIndex, int seconds) { misc.setWardenTrialTimer(tierIndex, seconds); }

    // --- Frost Dragon ---
    public long getFrostDragonBoltCooldownMs() { return misc.getFrostDragonBoltCooldownMs(); }
    public long getFrostDragonNovaCooldownMs() { return misc.getFrostDragonNovaCooldownMs(); }
    public int getFrostDragonSpiritMaxCount() { return misc.getFrostDragonSpiritMaxCount(); }
    public int getFrostDragonNovaBoltCount() { return misc.getFrostDragonNovaBoltCount(); }

    // --- Gauntlet ---
    public float getGauntletVampiricHealPercent() { return misc.getGauntletVampiricHealPercent(); }

    // ========================================================================
    // LEGACY CODEC — reads old flat format for migration
    // ========================================================================

    private static BuilderCodec<EndgameConfig> buildLegacyCodec() {
        return BuilderCodec
                .builder(EndgameConfig.class, EndgameConfig::new)
                .append(new KeyedCodec<String>("Difficulty", Codec.STRING),
                        (c, v) -> c.setDifficultyString(v), EndgameConfig::getDifficultyString).add()
                .append(new KeyedCodec<Boolean>("DifficultyAffectsBosses", Codec.BOOLEAN),
                        (c, v) -> c.setDifficultyAffectsBosses(v != null ? v : true), c -> c.isDifficultyAffectsBosses()).add()
                .append(new KeyedCodec<Boolean>("DifficultyAffectsMobs", Codec.BOOLEAN),
                        (c, v) -> c.setDifficultyAffectsMobs(v != null ? v : true), c -> c.isDifficultyAffectsMobs()).add()
                .append(new KeyedCodec<Float>("CustomHealthMultiplier", Codec.FLOAT),
                        (c, v) -> c.setCustomHealthMultiplier(v != null ? v : 1.0f), c -> c.getCustomHealthMultiplier()).add()
                .append(new KeyedCodec<Float>("CustomDamageMultiplier", Codec.FLOAT),
                        (c, v) -> c.setCustomDamageMultiplier(v != null ? v : 1.0f), c -> c.getCustomDamageMultiplier()).add()
                .append(new KeyedCodec<Boolean>("EnableDungeonBlockProtection", Codec.BOOLEAN),
                        (c, v) -> c.setEnableDungeonBlockProtection(v != null ? v : true), c -> c.isEnableDungeonBlockProtection()).add()
                .append(new KeyedCodec<Boolean>("EnableGliderCrafting", Codec.BOOLEAN),
                        (c, v) -> c.setEnableGliderCrafting(v != null ? v : true), EndgameConfig::isEnableGliderCrafting).add()
                .append(new KeyedCodec<Boolean>("EnablePortalKeyTaiga", Codec.BOOLEAN),
                        (c, v) -> c.setEnablePortalKeyTaiga(v != null ? v : false), EndgameConfig::isEnablePortalKeyTaiga).add()
                .append(new KeyedCodec<Boolean>("EnablePortalKeyHederasLair", Codec.BOOLEAN),
                        (c, v) -> c.setEnablePortalKeyHederasLair(v != null ? v : false), EndgameConfig::isEnablePortalKeyHederasLair).add()
                .append(new KeyedCodec<Boolean>("EnableMithrilOreCrafting", Codec.BOOLEAN),
                        (c, v) -> c.setEnableMithrilOreCrafting(v != null ? v : false), c -> c.isEnableMithrilOreCrafting()).add()
                .append(new KeyedCodec<Boolean>("EnablePortalHedera", Codec.BOOLEAN),
                        (c, v) -> c.setEnablePortalHedera(v != null ? v : true), c -> c.isEnablePortalHedera()).add()
                .append(new KeyedCodec<Boolean>("EnablePortalGolemVoid", Codec.BOOLEAN),
                        (c, v) -> c.setEnablePortalGolemVoid(v != null ? v : true), c -> c.isEnablePortalGolemVoid()).add()
                .append(new KeyedCodec<Boolean>("EnableHederaDaggerPoison", Codec.BOOLEAN),
                        (c, v) -> c.setEnableHederaDaggerPoison(v != null ? v : true), c -> c.isEnableHederaDaggerPoison()).add()
                .append(new KeyedCodec<Float>("HederaDaggerPoisonDamage", Codec.FLOAT),
                        (c, v) -> c.setHederaDaggerPoisonDamage(v != null ? v : 5.0f), c -> c.getHederaDaggerPoisonDamage()).add()
                .append(new KeyedCodec<Integer>("HederaDaggerPoisonTicks", Codec.INTEGER),
                        (c, v) -> c.setHederaDaggerPoisonTicks(v != null ? v : 4), c -> c.getHederaDaggerPoisonTicks()).add()
                .append(new KeyedCodec<Boolean>("EnableHederaDaggerLifesteal", Codec.BOOLEAN),
                        (c, v) -> c.setEnableHederaDaggerLifesteal(v != null ? v : true), c -> c.isEnableHederaDaggerLifesteal()).add()
                .append(new KeyedCodec<Float>("HederaDaggerLifestealPercent", Codec.FLOAT),
                        (c, v) -> c.setHederaDaggerLifestealPercent(v != null ? v : 0.08f), c -> c.getHederaDaggerLifestealPercent()).add()
                .append(new KeyedCodec<Float>("MinionSpawnRadius", Codec.FLOAT),
                        (c, v) -> c.setMinionSpawnRadius(v != null ? v : 12.0f), c -> c.getMinionSpawnRadius()).add()
                .append(new KeyedCodec<Float>("EyeVoidHealthMultiplier", Codec.FLOAT),
                        (c, v) -> c.setEyeVoidHealthMultiplier(v != null ? v : 1.5f), c -> c.getEyeVoidHealthMultiplier()).add()
                .append(new KeyedCodec<Boolean>("PvpEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setPvpEnabled(v != null ? v : false), c -> c.isPvpEnabled()).add()
                .append(new KeyedCodec<Boolean>("ManaRegenArmorEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setManaRegenArmorEnabled(v != null ? v : true), c -> c.isManaRegenArmorEnabled()).add()
                .append(new KeyedCodec<Float>("ManaRegenMithrilPerPiece", Codec.FLOAT),
                        (c, v) -> c.setManaRegenMithrilPerPiece(v != null ? v : 0.5f), c -> c.getManaRegenMithrilPerPiece()).add()
                .append(new KeyedCodec<Float>("ManaRegenOnyxiumPerPiece", Codec.FLOAT),
                        (c, v) -> c.setManaRegenOnyxiumPerPiece(v != null ? v : 0.75f), c -> c.getManaRegenOnyxiumPerPiece()).add()
                .append(new KeyedCodec<Float>("ManaRegenPrismaPerPiece", Codec.FLOAT),
                        (c, v) -> c.setManaRegenPrismaPerPiece(v != null ? v : 1.0f), c -> c.getManaRegenPrismaPerPiece()).add()
                .append(new KeyedCodec<Boolean>("RPGLevelingEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setRPGLevelingEnabled(v != null ? v : false), c -> c.isRPGLevelingEnabled()).add()
                .append(new KeyedCodec<String>("RPGLevelingAutoDetected", Codec.STRING),
                        (c, v) -> c.setRPGLevelingAutoDetected(v != null ? v : "pending"), c -> c.getRPGLevelingAutoDetected()).add()
                .append(new KeyedCodec<Boolean>("EndlessLevelingEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setEndlessLevelingEnabled(v != null ? v : false), c -> c.isEndlessLevelingEnabled()).add()
                .append(new KeyedCodec<String>("EndlessLevelingAutoDetected", Codec.STRING),
                        (c, v) -> c.setEndlessLevelingAutoDetected(v != null ? v : "pending"), c -> c.getEndlessLevelingAutoDetected()).add()
                .append(new KeyedCodec<Map<String, BossConfig>>("BossConfigs",
                        new MapCodec<>(BossConfig.CODEC, HashMap::new, false)),
                        (c, v) -> { if (v != null) c.bossConfigs.putAll(v); }, c -> c.bossConfigs).add()
                .append(new KeyedCodec<Boolean>("PrismaMirageEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setPrismaMirageEnabled(v != null ? v : true), c -> c.isPrismaMirageEnabled()).add()
                .append(new KeyedCodec<Integer>("PrismaMirageCooldownMs", Codec.INTEGER),
                        (c, v) -> c.setPrismaMirageCooldownMs(v != null ? v : 15000), c -> c.getPrismaMirageCooldownMs()).add()
                .append(new KeyedCodec<Integer>("PrismaMirageLifetimeMs", Codec.INTEGER),
                        (c, v) -> c.setPrismaMirageLifetimeMs(v != null ? v : 5000), c -> c.getPrismaMirageLifetimeMs()).add()
                .append(new KeyedCodec<Boolean>("VoidMarkEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setVoidMarkEnabled(v != null ? v : true), c -> c.isVoidMarkEnabled()).add()
                .append(new KeyedCodec<Integer>("VoidMarkDurationMs", Codec.INTEGER),
                        (c, v) -> c.setVoidMarkDurationMs(v != null ? v : 10000), c -> c.getVoidMarkDurationMs()).add()
                .append(new KeyedCodec<Boolean>("VoidMarkExecutionEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setVoidMarkExecutionEnabled(v != null ? v : true), c -> c.isVoidMarkExecutionEnabled()).add()
                .append(new KeyedCodec<Float>("VoidMarkExecutionThreshold", Codec.FLOAT),
                        (c, v) -> c.setVoidMarkExecutionThreshold(v != null ? v : 0.25f), c -> c.getVoidMarkExecutionThreshold()).add()
                .append(new KeyedCodec<Float>("VoidMarkExecutionMultiplier", Codec.FLOAT),
                        (c, v) -> c.setVoidMarkExecutionMultiplier(v != null ? v : 3.0f), c -> c.getVoidMarkExecutionMultiplier()).add()
                .append(new KeyedCodec<Boolean>("DaggerBlinkEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setDaggerBlinkEnabled(v != null ? v : true), c -> c.isDaggerBlinkEnabled()).add()
                .append(new KeyedCodec<Float>("DaggerBlinkDistance", Codec.FLOAT),
                        (c, v) -> c.setDaggerBlinkDistance(v != null ? v : 12.0f), c -> c.getDaggerBlinkDistance()).add()
                .append(new KeyedCodec<Boolean>("ArmorHPRegenEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setArmorHPRegenEnabled(v != null ? v : true), c -> c.isArmorHPRegenEnabled()).add()
                .append(new KeyedCodec<Float>("ArmorHPRegenDelaySec", Codec.FLOAT),
                        (c, v) -> c.setArmorHPRegenDelaySec(v != null ? v : 15.0f), c -> c.getArmorHPRegenDelaySec()).add()
                .append(new KeyedCodec<Float>("ArmorHPRegenOnyxiumPerPiece", Codec.FLOAT),
                        (c, v) -> c.setArmorHPRegenOnyxiumPerPiece(v != null ? v : 0.5f), c -> c.getArmorHPRegenOnyxiumPerPiece()).add()
                .append(new KeyedCodec<Float>("ArmorHPRegenPrismaPerPiece", Codec.FLOAT),
                        (c, v) -> c.setArmorHPRegenPrismaPerPiece(v != null ? v : 0.75f), c -> c.getArmorHPRegenPrismaPerPiece()).add()
                .append(new KeyedCodec<Boolean>("DaggerTrailEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setDaggerTrailEnabled(v != null ? v : true), c -> c.isDaggerTrailEnabled()).add()
                .append(new KeyedCodec<Float>("DaggerTrailDamage", Codec.FLOAT),
                        (c, v) -> c.setDaggerTrailDamage(v != null ? v : 15.0f), c -> c.getDaggerTrailDamage()).add()
                .append(new KeyedCodec<Boolean>("BlazefistBurnEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setBlazefistBurnEnabled(v != null ? v : true), c -> c.isBlazefistBurnEnabled()).add()
                .append(new KeyedCodec<Float>("BlazefistBurnDamage", Codec.FLOAT),
                        (c, v) -> c.setBlazefistBurnDamage(v != null ? v : 50.0f), c -> c.getBlazefistBurnDamage()).add()
                .append(new KeyedCodec<Integer>("BlazefistBurnTicks", Codec.INTEGER),
                        (c, v) -> c.setBlazefistBurnTicks(v != null ? v : 3), c -> c.getBlazefistBurnTicks()).add()
                .append(new KeyedCodec<Boolean>("EnableWardenTrial", Codec.BOOLEAN),
                        (c, v) -> c.setWardenTrialEnabled(v != null ? v : true), c -> c.isWardenTrialEnabled()).add()
                .append(new KeyedCodec<Boolean>("ComboEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setComboEnabled(v != null ? v : true), c -> c.isComboEnabled()).add()
                .append(new KeyedCodec<Float>("ComboTimerSeconds", Codec.FLOAT),
                        (c, v) -> c.setComboTimerSeconds(v != null ? v : 5.0f), c -> c.getComboTimerSeconds()).add()
                .append(new KeyedCodec<Float>("ComboDamageX2", Codec.FLOAT),
                        (c, v) -> c.setComboDamageX2(v != null ? v : 1.10f), c -> c.getComboDamageX2()).add()
                .append(new KeyedCodec<Float>("ComboDamageX3", Codec.FLOAT),
                        (c, v) -> c.setComboDamageX3(v != null ? v : 1.25f), c -> c.getComboDamageX3()).add()
                .append(new KeyedCodec<Float>("ComboDamageX4", Codec.FLOAT),
                        (c, v) -> c.setComboDamageX4(v != null ? v : 1.50f), c -> c.getComboDamageX4()).add()
                .append(new KeyedCodec<Float>("ComboDamageFrenzy", Codec.FLOAT),
                        (c, v) -> c.setComboDamageFrenzy(v != null ? v : 2.00f), c -> c.getComboDamageFrenzy()).add()
                .append(new KeyedCodec<Boolean>("GauntletEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setGauntletEnabled(v != null ? v : false), c -> c.isGauntletEnabled()).add()
                .append(new KeyedCodec<Integer>("GauntletScalingPercent", Codec.INTEGER),
                        (c, v) -> c.setGauntletScalingPercent(v != null ? v : 10), c -> c.getGauntletScalingPercent()).add()
                .append(new KeyedCodec<Integer>("GauntletBuffCount", Codec.INTEGER),
                        (c, v) -> c.setGauntletBuffCount(v != null ? v : 3), c -> c.getGauntletBuffCount()).add()
                .append(new KeyedCodec<Boolean>("BountyEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setBountyEnabled(v != null ? v : true), c -> c.isBountyEnabled()).add()
                .append(new KeyedCodec<Integer>("BountyRefreshHours", Codec.INTEGER),
                        (c, v) -> c.setBountyRefreshHours(v != null ? v : 24), c -> c.getBountyRefreshHours()).add()
                .append(new KeyedCodec<Boolean>("BountyStreakEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setBountyStreakEnabled(v != null ? v : true), c -> c.isBountyStreakEnabled()).add()
                .append(new KeyedCodec<Boolean>("ComboTierEffectsEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setComboTierEffectsEnabled(v != null ? v : true), c -> c.isComboTierEffectsEnabled()).add()
                .append(new KeyedCodec<Boolean>("ComboDecayEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setComboDecayEnabled(v != null ? v : true), c -> c.isComboDecayEnabled()).add()
                .append(new KeyedCodec<Boolean>("BountyWeeklyEnabled", Codec.BOOLEAN),
                        (c, v) -> c.setBountyWeeklyEnabled(v != null ? v : true), c -> c.isBountyWeeklyEnabled()).add()
                .build();
    }
}
