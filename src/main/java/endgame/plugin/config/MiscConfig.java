package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class MiscConfig {

    @Nonnull
    public static final BuilderCodec<MiscConfig> CODEC = BuilderCodec
            .builder(MiscConfig.class, MiscConfig::new)
            .append(new KeyedCodec<Boolean>("PvpEnabled", Codec.BOOLEAN),
                    (c, v) -> c.pvpEnabled = v != null ? v : false, c -> c.pvpEnabled).add()
            .append(new KeyedCodec<Boolean>("EnableDungeonBlockProtection", Codec.BOOLEAN),
                    (c, v) -> c.enableDungeonBlockProtection = v != null ? v : true, c -> c.enableDungeonBlockProtection).add()
            .append(new KeyedCodec<Boolean>("EnableWardenTrial", Codec.BOOLEAN),
                    (c, v) -> c.enableWardenTrial = v != null ? v : true, c -> c.enableWardenTrial).add()
            .append(new KeyedCodec<Float>("MinionSpawnRadius", Codec.FLOAT),
                    (c, v) -> c.setMinionSpawnRadius(v != null ? v : 12.0f), c -> c.minionSpawnRadius).add()
            .append(new KeyedCodec<Float>("EyeVoidHealthMultiplier", Codec.FLOAT),
                    (c, v) -> c.setEyeVoidHealthMultiplier(v != null ? v : 1.5f), c -> c.eyeVoidHealthMultiplier).add()
            .append(new KeyedCodec<Boolean>("RPGLevelingEnabled", Codec.BOOLEAN),
                    (c, v) -> c.rpgLevelingEnabled = v != null ? v : false, c -> c.rpgLevelingEnabled).add()
            .append(new KeyedCodec<String>("RPGLevelingAutoDetected", Codec.STRING),
                    (c, v) -> c.rpgLevelingAutoDetected = v != null ? v : "pending", c -> c.rpgLevelingAutoDetected).add()
            .append(new KeyedCodec<Boolean>("EndlessLevelingEnabled", Codec.BOOLEAN),
                    (c, v) -> c.endlessLevelingEnabled = v != null ? v : false, c -> c.endlessLevelingEnabled).add()
            .append(new KeyedCodec<String>("EndlessLevelingAutoDetected", Codec.STRING),
                    (c, v) -> c.endlessLevelingAutoDetected = v != null ? v : "pending", c -> c.endlessLevelingAutoDetected).add()
            .append(new KeyedCodec<Float>("EndlessLevelingXpShareRange", Codec.FLOAT),
                    (c, v) -> c.setElXpShareRange(v != null ? v : 30.0f), c -> c.elXpShareRange).add()
            .append(new KeyedCodec<Integer>("EndlessLevelingGauntletXpBase", Codec.INTEGER),
                    (c, v) -> c.setElGauntletXpBase(v != null ? v : 50), c -> c.elGauntletXpBase).add()
            .append(new KeyedCodec<Integer>("EndlessLevelingWardenXpBase", Codec.INTEGER),
                    (c, v) -> c.setElWardenXpBase(v != null ? v : 150), c -> c.elWardenXpBase).add()
            .append(new KeyedCodec<Integer>("EndlessLevelingAchievementXp", Codec.INTEGER),
                    (c, v) -> c.setElAchievementXp(v != null ? v : 50), c -> c.elAchievementXp).add()
            .append(new KeyedCodec<Boolean>("OrbisGuardEnabled", Codec.BOOLEAN),
                    (c, v) -> c.orbisGuardEnabled = v != null ? v : false, c -> c.orbisGuardEnabled).add()
            .append(new KeyedCodec<String>("OrbisGuardAutoDetected", Codec.STRING),
                    (c, v) -> c.orbisGuardAutoDetected = v != null ? v : "pending", c -> c.orbisGuardAutoDetected).add()
            .append(new KeyedCodec<Boolean>("OrbisGuardBlockBuild", Codec.BOOLEAN),
                    (c, v) -> c.ogBlockBuild = v != null ? v : true, c -> c.ogBlockBuild).add()
            .append(new KeyedCodec<Boolean>("OrbisGuardBlockPvp", Codec.BOOLEAN),
                    (c, v) -> c.ogBlockPvp = v != null ? v : true, c -> c.ogBlockPvp).add()
            .append(new KeyedCodec<String>("OrbisGuardBlockedCommands", Codec.STRING),
                    (c, v) -> c.ogBlockedCommands = v != null ? v : "/spawn, /sethome, /tpa", c -> c.ogBlockedCommands).add()
            .append(new KeyedCodec<Boolean>("AccessoriesEnabled", Codec.BOOLEAN),
                    (c, v) -> c.accessoriesEnabled = v != null ? v : true, c -> c.accessoriesEnabled).add()
            .append(new KeyedCodec<Boolean>("BossTargetSwitchEnabled", Codec.BOOLEAN),
                    (c, v) -> c.bossTargetSwitchEnabled = v != null ? v : true, c -> c.bossTargetSwitchEnabled).add()
            .append(new KeyedCodec<Integer>("BossTargetSwitchIntervalMs", Codec.INTEGER),
                    (c, v) -> c.setBossTargetSwitchIntervalMs(v != null ? v : 8000), c -> c.bossTargetSwitchIntervalMs).add()
            .append(new KeyedCodec<Boolean>("PrismaArmorVulnerabilityEnabled", Codec.BOOLEAN),
                    (c, v) -> c.prismaArmorVulnerabilityEnabled = v != null ? v : false, c -> c.prismaArmorVulnerabilityEnabled).add()
            .append(new KeyedCodec<Boolean>("PrismaWeaponBossBlockEnabled", Codec.BOOLEAN),
                    (c, v) -> c.prismaWeaponBossBlockEnabled = v != null ? v : false, c -> c.prismaWeaponBossBlockEnabled).add()
            .append(new KeyedCodec<Boolean>("VorthakEnabled", Codec.BOOLEAN),
                    (c, v) -> c.vorthakEnabled = v != null ? v : true, c -> c.vorthakEnabled).add()
            .append(new KeyedCodec<Boolean>("SharedBossKillCredit", Codec.BOOLEAN),
                    (c, v) -> c.sharedBossKillCredit = v != null ? v : true, c -> c.sharedBossKillCredit).add()
            .append(new KeyedCodec<Integer>("WardenTrialTimerTier1", Codec.INTEGER),
                    (c, v) -> c.setWardenTrialTimer(0, v != null ? v : 270), c -> c.wardenTrialTimers[0]).add()
            .append(new KeyedCodec<Integer>("WardenTrialTimerTier2", Codec.INTEGER),
                    (c, v) -> c.setWardenTrialTimer(1, v != null ? v : 360), c -> c.wardenTrialTimers[1]).add()
            .append(new KeyedCodec<Integer>("WardenTrialTimerTier3", Codec.INTEGER),
                    (c, v) -> c.setWardenTrialTimer(2, v != null ? v : 450), c -> c.wardenTrialTimers[2]).add()
            .append(new KeyedCodec<Integer>("WardenTrialTimerTier4", Codec.INTEGER),
                    (c, v) -> c.setWardenTrialTimer(3, v != null ? v : 540), c -> c.wardenTrialTimers[3]).add()
            .append(new KeyedCodec<Long>("FrostDragonBoltCooldownMs", Codec.LONG),
                    (c, v) -> c.frostDragonBoltCooldownMs = v != null ? Math.max(1000L, Math.min(60000L, v)) : 10000L, c -> c.frostDragonBoltCooldownMs).add()
            .append(new KeyedCodec<Long>("FrostDragonNovaCooldownMs", Codec.LONG),
                    (c, v) -> c.frostDragonNovaCooldownMs = v != null ? Math.max(5000L, Math.min(120000L, v)) : 25000L, c -> c.frostDragonNovaCooldownMs).add()
            .append(new KeyedCodec<Integer>("FrostDragonSpiritMaxCount", Codec.INTEGER),
                    (c, v) -> c.frostDragonSpiritMaxCount = v != null ? Math.max(0, Math.min(10, v)) : 4, c -> c.frostDragonSpiritMaxCount).add()
            .append(new KeyedCodec<Integer>("FrostDragonNovaBoltCount", Codec.INTEGER),
                    (c, v) -> c.frostDragonNovaBoltCount = v != null ? Math.max(1, Math.min(20, v)) : 10, c -> c.frostDragonNovaBoltCount).add()
            .append(new KeyedCodec<Float>("GauntletVampiricHealPercent", Codec.FLOAT),
                    (c, v) -> c.gauntletVampiricHealPercent = v != null ? Math.max(0f, Math.min(0.5f, v)) : 0.05f, c -> c.gauntletVampiricHealPercent).add()
            .build();

    private boolean pvpEnabled = false;
    private boolean enableDungeonBlockProtection = true;
    private boolean enableWardenTrial = true;
    private float minionSpawnRadius = 12.0f;
    private float eyeVoidHealthMultiplier = 1.5f;
    private boolean rpgLevelingEnabled = false;
    private String rpgLevelingAutoDetected = "pending";
    private boolean endlessLevelingEnabled = false;
    private String endlessLevelingAutoDetected = "pending";
    private float elXpShareRange = 30.0f;
    private int elGauntletXpBase = 50;
    private int elWardenXpBase = 150;
    private int elAchievementXp = 50;
    private boolean orbisGuardEnabled = false;
    private String orbisGuardAutoDetected = "pending";
    private boolean ogBlockBuild = true;
    private boolean ogBlockPvp = true;
    private String ogBlockedCommands = "/spawn, /sethome, /tpa";
    private boolean accessoriesEnabled = true;
    private boolean bossTargetSwitchEnabled = true;
    private int bossTargetSwitchIntervalMs = 8000;
    private boolean prismaArmorVulnerabilityEnabled = false;
    private boolean prismaWeaponBossBlockEnabled = false;
    private boolean vorthakEnabled = true;
    private boolean sharedBossKillCredit = true;
    // Per-tier wave time limits (seconds). 0 = disabled for that tier.
    private final int[] wardenTrialTimers = {270, 360, 450, 540};
    // Frost Dragon sky bolt system
    private long frostDragonBoltCooldownMs = 10000L;
    private long frostDragonNovaCooldownMs = 25000L;
    private int frostDragonSpiritMaxCount = 4;
    private int frostDragonNovaBoltCount = 10;
    // Gauntlet
    private float gauntletVampiricHealPercent = 0.05f;

    // === PVP ===

    public boolean isPvpEnabled() { return pvpEnabled; }
    public void setPvpEnabled(boolean e) { this.pvpEnabled = e; }

    // === DUNGEON ===

    public boolean isEnableDungeonBlockProtection() { return enableDungeonBlockProtection; }
    public void setEnableDungeonBlockProtection(boolean e) { this.enableDungeonBlockProtection = e; }

    // === WARDEN TRIAL ===

    public boolean isWardenTrialEnabled() { return enableWardenTrial; }
    public void setWardenTrialEnabled(boolean e) { this.enableWardenTrial = e; }

    // === MINION ===

    public float getMinionSpawnRadius() { return minionSpawnRadius; }
    public void setMinionSpawnRadius(float r) { this.minionSpawnRadius = Math.max(1.0f, Math.min(50.0f, r)); }

    // === EYE VOID ===

    public float getEyeVoidHealthMultiplier() { return eyeVoidHealthMultiplier; }
    public void setEyeVoidHealthMultiplier(float m) { this.eyeVoidHealthMultiplier = Math.max(0.1f, Math.min(10.0f, m)); }

    // === RPG LEVELING ===

    public boolean isRPGLevelingEnabled() { return rpgLevelingEnabled; }
    public void setRPGLevelingEnabled(boolean e) { this.rpgLevelingEnabled = e; }

    public String getRPGLevelingAutoDetected() { return rpgLevelingAutoDetected; }
    public void setRPGLevelingAutoDetected(String s) { this.rpgLevelingAutoDetected = s != null ? s : "pending"; }

    public boolean isRPGLevelingAutoDetectPending() { return !"done".equals(rpgLevelingAutoDetected); }

    // === ENDLESS LEVELING ===

    public boolean isEndlessLevelingEnabled() { return endlessLevelingEnabled; }
    public void setEndlessLevelingEnabled(boolean e) { this.endlessLevelingEnabled = e; }

    public String getEndlessLevelingAutoDetected() { return endlessLevelingAutoDetected; }
    public void setEndlessLevelingAutoDetected(String s) { this.endlessLevelingAutoDetected = s != null ? s : "pending"; }

    public boolean isEndlessLevelingAutoDetectPending() { return !"done".equals(endlessLevelingAutoDetected); }

    public float getElXpShareRange() { return elXpShareRange; }
    public void setElXpShareRange(float r) { this.elXpShareRange = Math.max(5.0f, Math.min(100.0f, r)); }

    public int getElGauntletXpBase() { return elGauntletXpBase; }
    public void setElGauntletXpBase(int v) { this.elGauntletXpBase = Math.max(0, Math.min(500, v)); }

    public int getElWardenXpBase() { return elWardenXpBase; }
    public void setElWardenXpBase(int v) { this.elWardenXpBase = Math.max(0, Math.min(1000, v)); }

    public int getElAchievementXp() { return elAchievementXp; }
    public void setElAchievementXp(int v) { this.elAchievementXp = Math.max(0, Math.min(500, v)); }

    // === ORBISGUARD ===

    public boolean isOrbisGuardEnabled() { return orbisGuardEnabled; }
    public void setOrbisGuardEnabled(boolean e) { this.orbisGuardEnabled = e; }

    public String getOrbisGuardAutoDetected() { return orbisGuardAutoDetected; }
    public void setOrbisGuardAutoDetected(String s) { this.orbisGuardAutoDetected = s != null ? s : "pending"; }

    public boolean isOrbisGuardAutoDetectPending() { return !"done".equals(orbisGuardAutoDetected); }

    public boolean isOgBlockBuild() { return ogBlockBuild; }
    public void setOgBlockBuild(boolean e) { this.ogBlockBuild = e; }

    public boolean isOgBlockPvp() { return ogBlockPvp; }
    public void setOgBlockPvp(boolean e) { this.ogBlockPvp = e; }

    public String getOgBlockedCommands() { return ogBlockedCommands; }
    public void setOgBlockedCommands(String s) { this.ogBlockedCommands = s != null ? s : ""; }

    // === ACCESSORIES ===

    public boolean isAccessoriesEnabled() { return accessoriesEnabled; }
    public void setAccessoriesEnabled(boolean e) { this.accessoriesEnabled = e; }

    // === BOSS TARGET SWITCH ===

    public boolean isBossTargetSwitchEnabled() { return bossTargetSwitchEnabled; }
    public void setBossTargetSwitchEnabled(boolean e) { this.bossTargetSwitchEnabled = e; }

    public int getBossTargetSwitchIntervalMs() { return bossTargetSwitchIntervalMs; }
    public void setBossTargetSwitchIntervalMs(int ms) { this.bossTargetSwitchIntervalMs = Math.max(2000, Math.min(30000, ms)); }

    // === PRISMA ARMOR VULNERABILITY ===

    public boolean isPrismaArmorVulnerabilityEnabled() { return prismaArmorVulnerabilityEnabled; }
    public void setPrismaArmorVulnerabilityEnabled(boolean e) { this.prismaArmorVulnerabilityEnabled = e; }

    // === PRISMA WEAPON BOSS BLOCK ===

    public boolean isPrismaWeaponBossBlockEnabled() { return prismaWeaponBossBlockEnabled; }
    public void setPrismaWeaponBossBlockEnabled(boolean e) { this.prismaWeaponBossBlockEnabled = e; }

    // === VORTHAK ===

    public boolean isVorthakEnabled() { return vorthakEnabled; }
    public void setVorthakEnabled(boolean e) { this.vorthakEnabled = e; }

    // === SHARED BOSS KILL CREDIT ===

    public boolean isSharedBossKillCredit() { return sharedBossKillCredit; }
    public void setSharedBossKillCredit(boolean e) { this.sharedBossKillCredit = e; }

    // === WARDEN TRIAL WAVE TIMERS (per tier) ===

    public int getWardenTrialTimer(int tierIndex) {
        if (tierIndex < 0 || tierIndex >= 4) return 0;
        return wardenTrialTimers[tierIndex];
    }
    public void setWardenTrialTimer(int tierIndex, int seconds) {
        if (tierIndex >= 0 && tierIndex < 4) {
            wardenTrialTimers[tierIndex] = Math.max(0, Math.min(600, seconds));
        }
    }

    // === FROST DRAGON ===

    public long getFrostDragonBoltCooldownMs() { return frostDragonBoltCooldownMs; }
    public long getFrostDragonNovaCooldownMs() { return frostDragonNovaCooldownMs; }
    public int getFrostDragonSpiritMaxCount() { return frostDragonSpiritMaxCount; }
    public int getFrostDragonNovaBoltCount() { return frostDragonNovaBoltCount; }

    // === GAUNTLET VAMPIRIC ===

    public float getGauntletVampiricHealPercent() { return gauntletVampiricHealPercent; }
}
