package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

/**
 * Per-boss configuration. One instance per BossType, stored in EndgameConfig.bossConfigs map.
 *
 * All values default to 0, meaning "use BossType defaults × difficulty".
 */
public class BossConfig {

    private static final int MINIMUM_BOSS_HEALTH = 100;
    private static final int MINIMUM_MOB_HEALTH = 10;
    private static final int MAXIMUM_HEALTH = 1000000;

    // Health override: 0 = use BossType.defaultHealth × difficulty
    private int healthOverride = 0;
    // Per-boss damage multiplier: 0 = use group difficulty multiplier
    private float damageMultiplier = 0f;
    // Player scaling: % HP per extra player (0 = no scaling)
    private int playerScaling = 0;
    // XP reward for RPG Leveling integration (0 = disabled)
    private int xpReward = 0;
    // Golem-specific: danger zone start phase (1-3)
    private int dangerZoneStartPhase = 1;
    private float phase2Threshold = 0.66f;
    private float phase3Threshold = 0.33f;
    // Phase transition invulnerability duration (ms, 0 = disabled)
    private long phaseInvulnerabilityMs = 3000L;
    // Minion spawn counts per phase transition
    private int phase2MinionCount = 2;
    private int phase3MinionCount = 4;

    // Enrage system
    private boolean enrageEnabled = true;
    private float enrageDamageThreshold = 200.0f;
    private long enrageWindowMs = 5000L;
    private long enrageDurationMs = 8000L;
    private float enrageDamageMultiplier = 1.5f;
    private long enrageCooldownMs = 15000L;

    @Nonnull
    public static final BuilderCodec<BossConfig> CODEC = BuilderCodec
            .builder(BossConfig.class, BossConfig::new)
            .append(new KeyedCodec<Integer>("HealthOverride", Codec.INTEGER),
                    (bc, value) -> bc.setHealthOverride(value != null ? value : 0),
                    bc -> bc.healthOverride)
            .add()
            .append(new KeyedCodec<Float>("DamageMultiplier", Codec.FLOAT),
                    (bc, value) -> bc.setDamageMultiplier(value != null ? value : 0f),
                    bc -> bc.damageMultiplier)
            .add()
            .append(new KeyedCodec<Integer>("PlayerScaling", Codec.INTEGER),
                    (bc, value) -> bc.setPlayerScaling(value != null ? value : 0),
                    bc -> bc.playerScaling)
            .add()
            .append(new KeyedCodec<Integer>("XpReward", Codec.INTEGER),
                    (bc, value) -> bc.setXpReward(value != null ? value : 0),
                    bc -> bc.xpReward)
            .add()
            .append(new KeyedCodec<Integer>("DangerZoneStartPhase", Codec.INTEGER),
                    (bc, value) -> bc.setDangerZoneStartPhase(value != null ? value : 1),
                    bc -> bc.dangerZoneStartPhase)
            .add()
            .append(new KeyedCodec<Float>("Phase2Threshold", Codec.FLOAT),
                    (bc, value) -> bc.phase2Threshold = value != null ? Math.max(0.01f, Math.min(0.99f, value)) : 0.66f,
                    bc -> bc.phase2Threshold)
            .add()
            .append(new KeyedCodec<Float>("Phase3Threshold", Codec.FLOAT),
                    (bc, value) -> bc.phase3Threshold = value != null ? Math.max(0.01f, Math.min(0.99f, value)) : 0.33f,
                    bc -> bc.phase3Threshold)
            .add()
            .append(new KeyedCodec<Long>("PhaseInvulnerabilityMs", Codec.LONG),
                    (bc, value) -> bc.phaseInvulnerabilityMs = value != null ? Math.max(0L, Math.min(10000L, value)) : 3000L,
                    bc -> bc.phaseInvulnerabilityMs)
            .add()
            .append(new KeyedCodec<Integer>("Phase2MinionCount", Codec.INTEGER),
                    (bc, value) -> bc.phase2MinionCount = value != null ? Math.max(0, Math.min(10, value)) : 2,
                    bc -> bc.phase2MinionCount)
            .add()
            .append(new KeyedCodec<Integer>("Phase3MinionCount", Codec.INTEGER),
                    (bc, value) -> bc.phase3MinionCount = value != null ? Math.max(0, Math.min(10, value)) : 4,
                    bc -> bc.phase3MinionCount)
            .add()
            .append(new KeyedCodec<Boolean>("EnrageEnabled", Codec.BOOLEAN),
                    (bc, value) -> bc.setEnrageEnabled(value != null ? value : true),
                    bc -> bc.enrageEnabled)
            .add()
            .append(new KeyedCodec<Float>("EnrageDamageThreshold", Codec.FLOAT),
                    (bc, value) -> bc.setEnrageDamageThreshold(value != null ? value : 200.0f),
                    bc -> bc.enrageDamageThreshold)
            .add()
            .append(new KeyedCodec<Long>("EnrageWindowMs", Codec.LONG),
                    (bc, value) -> bc.setEnrageWindowMs(value != null ? value : 5000L),
                    bc -> bc.enrageWindowMs)
            .add()
            .append(new KeyedCodec<Long>("EnrageDurationMs", Codec.LONG),
                    (bc, value) -> bc.setEnrageDurationMs(value != null ? value : 8000L),
                    bc -> bc.enrageDurationMs)
            .add()
            .append(new KeyedCodec<Float>("EnrageDamageMultiplier", Codec.FLOAT),
                    (bc, value) -> bc.setEnrageDamageMultiplier(value != null ? value : 1.5f),
                    bc -> bc.enrageDamageMultiplier)
            .add()
            .append(new KeyedCodec<Long>("EnrageCooldownMs", Codec.LONG),
                    (bc, value) -> bc.setEnrageCooldownMs(value != null ? value : 15000L),
                    bc -> bc.enrageCooldownMs)
            .add()
            .build();

    public BossConfig() {
    }

    // === HEALTH ===

    public int getHealthOverride() {
        return healthOverride;
    }

    public void setHealthOverride(int health) {
        this.healthOverride = Math.max(0, Math.min(MAXIMUM_HEALTH, health));
    }

    /**
     * Compute effective health for this boss given its default and difficulty context.
     *
     * @param defaultHealth   BossType.getDefaultHealth()
     * @param difficultyMult  effective health multiplier from difficulty settings
     * @param isBoss          true for bosses, false for mobs (determines min HP)
     */
    public int getEffectiveHealth(int defaultHealth, float difficultyMult, boolean isBoss) {
        int minHealth = isBoss ? MINIMUM_BOSS_HEALTH : MINIMUM_MOB_HEALTH;
        if (healthOverride > 0) {
            return Math.max(minHealth, healthOverride);
        }
        int health = Math.round(defaultHealth * difficultyMult);
        return Math.max(minHealth, health);
    }

    public int getHealthOverrideRaw() {
        return healthOverride;
    }

    // === DAMAGE ===

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float multiplier) {
        this.damageMultiplier = Math.max(0, Math.min(10.0f, multiplier));
    }

    /**
     * Get effective damage multiplier. Returns custom if set (>0), otherwise group fallback.
     */
    public float getEffectiveDamageMultiplier(float groupMultiplier) {
        return damageMultiplier > 0 ? damageMultiplier : groupMultiplier;
    }

    public float getDamageMultiplierRaw() {
        return damageMultiplier;
    }

    // === PLAYER SCALING ===

    public int getPlayerScaling() {
        return playerScaling;
    }

    public void setPlayerScaling(int scaling) {
        this.playerScaling = Math.max(0, Math.min(1000, scaling));
    }

    // === XP REWARD ===

    public int getXpReward() {
        return xpReward;
    }

    public void setXpReward(int xp) {
        this.xpReward = Math.max(0, Math.min(1000000, xp));
    }

    // === DANGER ZONE (Golem-specific) ===

    public int getDangerZoneStartPhase() {
        return dangerZoneStartPhase;
    }

    public void setDangerZoneStartPhase(int phase) {
        this.dangerZoneStartPhase = Math.max(1, Math.min(3, phase));
    }

    // === PHASE THRESHOLDS ===

    public float getPhase2Threshold() { return phase2Threshold; }
    public float getPhase3Threshold() { return phase3Threshold; }
    public long getPhaseInvulnerabilityMs() { return phaseInvulnerabilityMs; }
    public int getPhase2MinionCount() { return phase2MinionCount; }
    public int getPhase3MinionCount() { return phase3MinionCount; }

    // === ENRAGE ===

    public boolean isEnrageEnabled() {
        return enrageEnabled;
    }

    public void setEnrageEnabled(boolean enabled) {
        this.enrageEnabled = enabled;
    }

    public float getEnrageDamageThreshold() {
        return enrageDamageThreshold;
    }

    public void setEnrageDamageThreshold(float threshold) {
        this.enrageDamageThreshold = Math.max(1.0f, Math.min(10000.0f, threshold));
    }

    public long getEnrageWindowMs() {
        return enrageWindowMs;
    }

    public void setEnrageWindowMs(long windowMs) {
        this.enrageWindowMs = Math.max(1000L, Math.min(30000L, windowMs));
    }

    public long getEnrageDurationMs() {
        return enrageDurationMs;
    }

    public void setEnrageDurationMs(long durationMs) {
        this.enrageDurationMs = Math.max(1000L, Math.min(60000L, durationMs));
    }

    public float getEnrageDamageMultiplier() {
        return enrageDamageMultiplier;
    }

    public void setEnrageDamageMultiplier(float multiplier) {
        this.enrageDamageMultiplier = Math.max(1.0f, Math.min(5.0f, multiplier));
    }

    public long getEnrageCooldownMs() {
        return enrageCooldownMs;
    }

    public void setEnrageCooldownMs(long cooldownMs) {
        this.enrageCooldownMs = Math.max(0L, Math.min(120000L, cooldownMs));
    }
}
