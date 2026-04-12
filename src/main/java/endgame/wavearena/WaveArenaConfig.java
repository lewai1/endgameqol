package endgame.wavearena;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Arena configuration. Created programmatically via Builder or parsed from JSON.
 * Supports fixed wave composition (Warden Trials) and generated pool mode (Portal Wave).
 */
public class WaveArenaConfig {

    private final String id;
    private final String displayName;
    private final String displayColor;
    private final int waveCount;
    private final int timeLimitSeconds;
    private final float spawnRadius;
    private final int intervalSeconds;
    private final int countdownSeconds;
    private final int mobLevel;
    @Nullable private final String rewardDropTable;
    private final int xpReward;
    private final String xpSource;
    private final boolean xpPerWave;
    @Nullable private final List<WaveDef> waves;
    @Nullable private final List<PoolEntry> mobPool;
    private final int baseCount;
    private final float countScaling;
    private final int bossEveryN;
    private final List<String> instanceBlacklist;
    private final String blockedMessage;
    @Nullable private final String bountyHook;
    private final int bountyTier;
    @Nullable private final String zoneParticleId;
    private final float zoneParticleScale;
    private final double zoneParticleYOffset;
    private final long zoneParticleIntervalMs;

    private WaveArenaConfig(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.displayColor = b.displayColor;
        this.waveCount = b.waveCount;
        this.timeLimitSeconds = b.timeLimitSeconds;
        this.spawnRadius = b.spawnRadius;
        this.intervalSeconds = b.intervalSeconds;
        this.countdownSeconds = b.countdownSeconds;
        this.mobLevel = b.mobLevel;
        this.rewardDropTable = b.rewardDropTable;
        this.xpReward = b.xpReward;
        this.xpSource = b.xpSource;
        this.xpPerWave = b.xpPerWave;
        this.waves = b.waves;
        this.mobPool = b.mobPool;
        this.baseCount = b.baseCount;
        this.countScaling = b.countScaling;
        this.bossEveryN = b.bossEveryN;
        this.instanceBlacklist = b.instanceBlacklist;
        this.blockedMessage = b.blockedMessage;
        this.bountyHook = b.bountyHook;
        this.bountyTier = b.bountyTier;
        this.zoneParticleId = b.zoneParticleId;
        this.zoneParticleScale = b.zoneParticleScale;
        this.zoneParticleYOffset = b.zoneParticleYOffset;
        this.zoneParticleIntervalMs = b.zoneParticleIntervalMs;
    }

    public static Builder builder(String id) { return new Builder(id); }

    public boolean isFixedMode() { return waves != null && !waves.isEmpty(); }
    public boolean isGeneratedMode() { return mobPool != null && !mobPool.isEmpty(); }

    @Nonnull public String getId() { return id; }
    @Nonnull public String getDisplayName() { return displayName; }
    @Nonnull public String getDisplayColor() { return displayColor; }
    public int getWaveCount() { return waveCount; }
    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public float getSpawnRadius() { return spawnRadius; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public int getCountdownSeconds() { return countdownSeconds; }
    public int getMobLevel() { return mobLevel; }
    @Nullable public String getRewardDropTable() { return rewardDropTable; }
    public int getXpReward() { return xpReward; }
    @Nonnull public String getXpSource() { return xpSource; }
    public boolean isXpPerWave() { return xpPerWave; }
    @Nonnull public List<String> getInstanceBlacklist() { return instanceBlacklist; }
    @Nonnull public String getBlockedMessage() { return blockedMessage; }
    @Nullable public String getBountyHook() { return bountyHook; }
    public int getBountyTier() { return bountyTier; }
    @Nullable public String getZoneParticleId() { return zoneParticleId; }
    public float getZoneParticleScale() { return zoneParticleScale; }
    public double getZoneParticleYOffset() { return zoneParticleYOffset; }
    public long getZoneParticleIntervalMs() { return zoneParticleIntervalMs; }

    @Nonnull
    public WaveDef getWaveForIndex(int waveIndex) {
        if (isFixedMode() && waveIndex < waves.size()) return waves.get(waveIndex);
        if (isGeneratedMode()) return generateWave(waveIndex);
        return new WaveDef(List.of());
    }

    private WaveDef generateWave(int waveIndex) {
        int mobCount = Math.round(baseCount * (float) Math.pow(countScaling, waveIndex));
        boolean isBossWave = bossEveryN > 0 && (waveIndex + 1) % bossEveryN == 0;

        List<PoolEntry> eligible = new ArrayList<>();
        List<PoolEntry> bosses = new ArrayList<>();
        for (PoolEntry e : mobPool) {
            if (waveIndex + 1 >= e.minWave) {
                if (e.boss) bosses.add(e); else eligible.add(e);
            }
        }

        List<WaveDef.MobEntry> mobs = new ArrayList<>();
        if (isBossWave && !bosses.isEmpty()) {
            PoolEntry boss = pickWeighted(bosses);
            mobs.add(new WaveDef.MobEntry(boss.type, 1));
            mobCount = Math.max(1, mobCount - 1);
        }

        if (!eligible.isEmpty()) {
            int totalWeight = eligible.stream().mapToInt(e -> e.weight).sum();
            for (int i = 0; i < mobCount; i++) {
                PoolEntry pick = pickWeightedWithTotal(eligible, totalWeight);
                boolean merged = false;
                for (int j = 0; j < mobs.size(); j++) {
                    if (mobs.get(j).type().equals(pick.type)) {
                        mobs.set(j, new WaveDef.MobEntry(pick.type, mobs.get(j).count() + 1));
                        merged = true;
                        break;
                    }
                }
                if (!merged) mobs.add(new WaveDef.MobEntry(pick.type, 1));
            }
        }
        return new WaveDef(mobs);
    }

    private PoolEntry pickWeighted(List<PoolEntry> pool) {
        int total = pool.stream().mapToInt(e -> e.weight).sum();
        return pickWeightedWithTotal(pool, total);
    }

    private PoolEntry pickWeightedWithTotal(List<PoolEntry> pool, int totalWeight) {
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cum = 0;
        for (PoolEntry e : pool) {
            cum += e.weight;
            if (roll < cum) return e;
        }
        return pool.getLast();
    }

    public record PoolEntry(String type, int weight, int minWave, boolean boss) {}

    public static class Builder {
        private final String id;
        private String displayName = "";
        private String displayColor = "#ffffff";
        private int waveCount = 5;
        private int timeLimitSeconds = 300;
        private float spawnRadius = 6.0f;
        private int intervalSeconds = 8;
        private int countdownSeconds = 3;
        private int mobLevel = -1;
        private String rewardDropTable;
        private int xpReward = 0;
        private String xpSource = "";
        private boolean xpPerWave = false;
        private List<WaveDef> waves;
        private List<PoolEntry> mobPool;
        private int baseCount = 4;
        private float countScaling = 1.15f;
        private int bossEveryN = 0;
        private List<String> instanceBlacklist = List.of();
        private String blockedMessage = "You cannot start this arena here.";
        private String bountyHook;
        private int bountyTier = 0;
        private String zoneParticleId;
        private float zoneParticleScale = 16.0f;
        private double zoneParticleYOffset = -0.3;
        private long zoneParticleIntervalMs = 1500;

        Builder(String id) { this.id = id; }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder displayColor(String v) { this.displayColor = v; return this; }
        public Builder waveCount(int v) { this.waveCount = v; return this; }
        public Builder timeLimitSeconds(int v) { this.timeLimitSeconds = v; return this; }
        public Builder spawnRadius(float v) { this.spawnRadius = v; return this; }
        public Builder intervalSeconds(int v) { this.intervalSeconds = v; return this; }
        public Builder countdownSeconds(int v) { this.countdownSeconds = v; return this; }
        public Builder mobLevel(int v) { this.mobLevel = v; return this; }
        public Builder rewardDropTable(String v) { this.rewardDropTable = v; return this; }
        public Builder xpReward(int v) { this.xpReward = v; return this; }
        public Builder xpSource(String v) { this.xpSource = v; return this; }
        public Builder xpPerWave(boolean v) { this.xpPerWave = v; return this; }
        public Builder waves(List<WaveDef> v) { this.waves = v; return this; }
        public Builder mobPool(List<PoolEntry> v) { this.mobPool = v; return this; }
        public Builder baseCount(int v) { this.baseCount = v; return this; }
        public Builder countScaling(float v) { this.countScaling = v; return this; }
        public Builder bossEveryN(int v) { this.bossEveryN = v; return this; }
        public Builder instanceBlacklist(List<String> v) { this.instanceBlacklist = v; return this; }
        public Builder blockedMessage(String v) { this.blockedMessage = v; return this; }
        public Builder bountyHook(String v) { this.bountyHook = v; return this; }
        public Builder bountyTier(int v) { this.bountyTier = v; return this; }
        public Builder zoneParticleId(String v) { this.zoneParticleId = v; return this; }
        public Builder zoneParticleScale(float v) { this.zoneParticleScale = v; return this; }
        public Builder zoneParticleYOffset(double v) { this.zoneParticleYOffset = v; return this; }
        public Builder zoneParticleIntervalMs(long v) { this.zoneParticleIntervalMs = v; return this; }

        public WaveArenaConfig build() { return new WaveArenaConfig(this); }
    }
}
