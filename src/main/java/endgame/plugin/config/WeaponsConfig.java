package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class WeaponsConfig {

    @Nonnull
    public static final BuilderCodec<WeaponsConfig> CODEC = BuilderCodec
            .builder(WeaponsConfig.class, WeaponsConfig::new)
            // Hedera Daggers
            .append(new KeyedCodec<Boolean>("HederaPoisonEnabled", Codec.BOOLEAN),
                    (c, v) -> c.hederaPoisonEnabled = v != null ? v : true, c -> c.hederaPoisonEnabled).add()
            .append(new KeyedCodec<Float>("HederaPoisonDamage", Codec.FLOAT),
                    (c, v) -> c.setHederaPoisonDamage(v != null ? v : 5.0f), c -> c.hederaPoisonDamage).add()
            .append(new KeyedCodec<Integer>("HederaPoisonTicks", Codec.INTEGER),
                    (c, v) -> c.setHederaPoisonTicks(v != null ? v : 4), c -> c.hederaPoisonTicks).add()
            // Hedera Boss Poison (different from dagger poison — boss attack values)
            .append(new KeyedCodec<Float>("HederaBossPoisonDamage", Codec.FLOAT),
                    (c, v) -> c.hederaBossPoisonDamage = v != null ? Math.max(0.1f, Math.min(50.0f, v)) : 8.0f, c -> c.hederaBossPoisonDamage).add()
            .append(new KeyedCodec<Integer>("HederaBossPoisonTicks", Codec.INTEGER),
                    (c, v) -> c.hederaBossPoisonTicks = v != null ? Math.max(1, Math.min(20, v)) : 5, c -> c.hederaBossPoisonTicks).add()
            .append(new KeyedCodec<Boolean>("HederaLifestealEnabled", Codec.BOOLEAN),
                    (c, v) -> c.hederaLifestealEnabled = v != null ? v : true, c -> c.hederaLifestealEnabled).add()
            .append(new KeyedCodec<Float>("HederaLifestealPercent", Codec.FLOAT),
                    (c, v) -> c.setHederaLifestealPercent(v != null ? v : 0.08f), c -> c.hederaLifestealPercent).add()
            // Prisma Mirage
            .append(new KeyedCodec<Boolean>("PrismaMirageEnabled", Codec.BOOLEAN),
                    (c, v) -> c.prismaMirageEnabled = v != null ? v : true, c -> c.prismaMirageEnabled).add()
            .append(new KeyedCodec<Integer>("PrismaMirageCooldownMs", Codec.INTEGER),
                    (c, v) -> c.setPrismaMirageCooldownMs(v != null ? v : 15000), c -> c.prismaMirageCooldownMs).add()
            .append(new KeyedCodec<Integer>("PrismaMirageLifetimeMs", Codec.INTEGER),
                    (c, v) -> c.setPrismaMirageLifetimeMs(v != null ? v : 5000), c -> c.prismaMirageLifetimeMs).add()
            // Void Mark
            .append(new KeyedCodec<Boolean>("VoidMarkEnabled", Codec.BOOLEAN),
                    (c, v) -> c.voidMarkEnabled = v != null ? v : true, c -> c.voidMarkEnabled).add()
            .append(new KeyedCodec<Integer>("VoidMarkDurationMs", Codec.INTEGER),
                    (c, v) -> c.setVoidMarkDurationMs(v != null ? v : 10000), c -> c.voidMarkDurationMs).add()
            .append(new KeyedCodec<Boolean>("VoidMarkExecutionEnabled", Codec.BOOLEAN),
                    (c, v) -> c.voidMarkExecutionEnabled = v != null ? v : true, c -> c.voidMarkExecutionEnabled).add()
            .append(new KeyedCodec<Float>("VoidMarkExecutionThreshold", Codec.FLOAT),
                    (c, v) -> c.setVoidMarkExecutionThreshold(v != null ? v : 0.25f), c -> c.voidMarkExecutionThreshold).add()
            .append(new KeyedCodec<Float>("VoidMarkExecutionMultiplier", Codec.FLOAT),
                    (c, v) -> c.setVoidMarkExecutionMultiplier(v != null ? v : 3.0f), c -> c.voidMarkExecutionMultiplier).add()
            // Dagger Blink
            .append(new KeyedCodec<Boolean>("DaggerBlinkEnabled", Codec.BOOLEAN),
                    (c, v) -> c.daggerBlinkEnabled = v != null ? v : true, c -> c.daggerBlinkEnabled).add()
            .append(new KeyedCodec<Float>("DaggerBlinkDistance", Codec.FLOAT),
                    (c, v) -> c.setDaggerBlinkDistance(v != null ? v : 12.0f), c -> c.daggerBlinkDistance).add()
            // Dagger Trail
            .append(new KeyedCodec<Boolean>("DaggerTrailEnabled", Codec.BOOLEAN),
                    (c, v) -> c.daggerTrailEnabled = v != null ? v : true, c -> c.daggerTrailEnabled).add()
            .append(new KeyedCodec<Float>("DaggerTrailDamage", Codec.FLOAT),
                    (c, v) -> c.setDaggerTrailDamage(v != null ? v : 15.0f), c -> c.daggerTrailDamage).add()
            // Blazefist Burn
            .append(new KeyedCodec<Boolean>("BlazefistBurnEnabled", Codec.BOOLEAN),
                    (c, v) -> c.blazefistBurnEnabled = v != null ? v : true, c -> c.blazefistBurnEnabled).add()
            .append(new KeyedCodec<Float>("BlazefistBurnDamage", Codec.FLOAT),
                    (c, v) -> c.setBlazefistBurnDamage(v != null ? v : 50.0f), c -> c.blazefistBurnDamage).add()
            .append(new KeyedCodec<Integer>("BlazefistBurnTicks", Codec.INTEGER),
                    (c, v) -> c.setBlazefistBurnTicks(v != null ? v : 3), c -> c.blazefistBurnTicks).add()
            // Dagger Vanish
            .append(new KeyedCodec<Integer>("DaggerVanishCooldownMs", Codec.INTEGER),
                    (c, v) -> c.setDaggerVanishCooldownMs(v != null ? v : 10000), c -> c.daggerVanishCooldownMs).add()
            .append(new KeyedCodec<Integer>("DaggerVanishInvulnerabilityMs", Codec.INTEGER),
                    (c, v) -> c.setDaggerVanishInvulnerabilityMs(v != null ? v : 2000), c -> c.daggerVanishInvulnerabilityMs).add()
            .build();

    // Hedera Daggers
    private boolean hederaPoisonEnabled = true;
    private float hederaPoisonDamage = 5.0f;
    private int hederaPoisonTicks = 4;
    // Hedera Boss Poison
    private float hederaBossPoisonDamage = 8.0f;
    private int hederaBossPoisonTicks = 5;
    private boolean hederaLifestealEnabled = true;
    private float hederaLifestealPercent = 0.08f;
    // Prisma Mirage
    private boolean prismaMirageEnabled = true;
    private int prismaMirageCooldownMs = 15000;
    private int prismaMirageLifetimeMs = 5000;
    // Void Mark
    private boolean voidMarkEnabled = true;
    private int voidMarkDurationMs = 10000;
    private boolean voidMarkExecutionEnabled = true;
    private float voidMarkExecutionThreshold = 0.25f;
    private float voidMarkExecutionMultiplier = 3.0f;
    // Dagger Blink
    private boolean daggerBlinkEnabled = true;
    private float daggerBlinkDistance = 12.0f;
    // Dagger Trail
    private boolean daggerTrailEnabled = true;
    private float daggerTrailDamage = 15.0f;
    // Blazefist Burn
    private boolean blazefistBurnEnabled = true;
    private float blazefistBurnDamage = 50.0f;
    private int blazefistBurnTicks = 3;
    // Dagger Vanish
    private int daggerVanishCooldownMs = 10000;
    private int daggerVanishInvulnerabilityMs = 2000;

    // === HEDERA DAGGERS ===

    public boolean isHederaPoisonEnabled() { return hederaPoisonEnabled; }
    public void setHederaPoisonEnabled(boolean e) { this.hederaPoisonEnabled = e; }

    public float getHederaPoisonDamage() { return hederaPoisonDamage; }
    public void setHederaPoisonDamage(float d) { this.hederaPoisonDamage = Math.max(0.1f, Math.min(50.0f, d)); }

    public int getHederaPoisonTicks() { return hederaPoisonTicks; }
    public void setHederaPoisonTicks(int t) { this.hederaPoisonTicks = Math.max(1, Math.min(20, t)); }

    public boolean isHederaLifestealEnabled() { return hederaLifestealEnabled; }
    public void setHederaLifestealEnabled(boolean e) { this.hederaLifestealEnabled = e; }

    public float getHederaLifestealPercent() { return hederaLifestealPercent; }
    public void setHederaLifestealPercent(float p) { this.hederaLifestealPercent = Math.max(0.01f, Math.min(0.50f, p)); }

    // === HEDERA BOSS POISON ===

    public float getHederaBossPoisonDamage() { return hederaBossPoisonDamage; }
    public int getHederaBossPoisonTicks() { return hederaBossPoisonTicks; }

    // === PRISMA MIRAGE ===

    public boolean isPrismaMirageEnabled() { return prismaMirageEnabled; }
    public void setPrismaMirageEnabled(boolean e) { this.prismaMirageEnabled = e; }

    public int getPrismaMirageCooldownMs() { return prismaMirageCooldownMs; }
    public void setPrismaMirageCooldownMs(int ms) { this.prismaMirageCooldownMs = Math.max(1000, Math.min(60000, ms)); }

    public int getPrismaMirageLifetimeMs() { return prismaMirageLifetimeMs; }
    public void setPrismaMirageLifetimeMs(int ms) { this.prismaMirageLifetimeMs = Math.max(1000, Math.min(30000, ms)); }

    // === VOID MARK ===

    public boolean isVoidMarkEnabled() { return voidMarkEnabled; }
    public void setVoidMarkEnabled(boolean e) { this.voidMarkEnabled = e; }

    public int getVoidMarkDurationMs() { return voidMarkDurationMs; }
    public void setVoidMarkDurationMs(int ms) { this.voidMarkDurationMs = Math.max(1000, Math.min(60000, ms)); }

    public boolean isVoidMarkExecutionEnabled() { return voidMarkExecutionEnabled; }
    public void setVoidMarkExecutionEnabled(boolean e) { this.voidMarkExecutionEnabled = e; }

    public float getVoidMarkExecutionThreshold() { return voidMarkExecutionThreshold; }
    public void setVoidMarkExecutionThreshold(float t) { this.voidMarkExecutionThreshold = Math.max(0.05f, Math.min(0.75f, t)); }

    public float getVoidMarkExecutionMultiplier() { return voidMarkExecutionMultiplier; }
    public void setVoidMarkExecutionMultiplier(float m) { this.voidMarkExecutionMultiplier = Math.max(1.0f, Math.min(10.0f, m)); }

    // === DAGGER BLINK ===

    public boolean isDaggerBlinkEnabled() { return daggerBlinkEnabled; }
    public void setDaggerBlinkEnabled(boolean e) { this.daggerBlinkEnabled = e; }

    public float getDaggerBlinkDistance() { return daggerBlinkDistance; }
    public void setDaggerBlinkDistance(float d) { this.daggerBlinkDistance = Math.max(3.0f, Math.min(30.0f, d)); }

    // === DAGGER TRAIL ===

    public boolean isDaggerTrailEnabled() { return daggerTrailEnabled; }
    public void setDaggerTrailEnabled(boolean e) { this.daggerTrailEnabled = e; }

    public float getDaggerTrailDamage() { return daggerTrailDamage; }
    public void setDaggerTrailDamage(float d) { this.daggerTrailDamage = Math.max(1.0f, Math.min(100.0f, d)); }

    // === BLAZEFIST BURN ===

    public boolean isBlazefistBurnEnabled() { return blazefistBurnEnabled; }
    public void setBlazefistBurnEnabled(boolean e) { this.blazefistBurnEnabled = e; }

    public float getBlazefistBurnDamage() { return blazefistBurnDamage; }
    public void setBlazefistBurnDamage(float d) { this.blazefistBurnDamage = Math.max(1.0f, Math.min(200.0f, d)); }

    public int getBlazefistBurnTicks() { return blazefistBurnTicks; }
    public void setBlazefistBurnTicks(int t) { this.blazefistBurnTicks = Math.max(1, Math.min(10, t)); }

    // === DAGGER VANISH ===

    public int getDaggerVanishCooldownMs() { return daggerVanishCooldownMs; }
    public void setDaggerVanishCooldownMs(int ms) { this.daggerVanishCooldownMs = Math.max(1000, Math.min(60000, ms)); }

    public int getDaggerVanishInvulnerabilityMs() { return daggerVanishInvulnerabilityMs; }
    public void setDaggerVanishInvulnerabilityMs(int ms) { this.daggerVanishInvulnerabilityMs = Math.max(500, Math.min(10000, ms)); }
}
