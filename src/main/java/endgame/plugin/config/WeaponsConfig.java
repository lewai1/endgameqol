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
            // Blazefist Burn
            .append(new KeyedCodec<Boolean>("BlazefistBurnEnabled", Codec.BOOLEAN),
                    (c, v) -> c.blazefistBurnEnabled = v != null ? v : true, c -> c.blazefistBurnEnabled).add()
            .append(new KeyedCodec<Float>("BlazefistBurnDamage", Codec.FLOAT),
                    (c, v) -> c.setBlazefistBurnDamage(v != null ? v : 50.0f), c -> c.blazefistBurnDamage).add()
            .append(new KeyedCodec<Integer>("BlazefistBurnTicks", Codec.INTEGER),
                    (c, v) -> c.setBlazefistBurnTicks(v != null ? v : 3), c -> c.blazefistBurnTicks).add()
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
    // Blazefist Burn
    private boolean blazefistBurnEnabled = true;
    private float blazefistBurnDamage = 50.0f;
    private int blazefistBurnTicks = 3;

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

    // === BLAZEFIST BURN ===

    public boolean isBlazefistBurnEnabled() { return blazefistBurnEnabled; }
    public void setBlazefistBurnEnabled(boolean e) { this.blazefistBurnEnabled = e; }

    public float getBlazefistBurnDamage() { return blazefistBurnDamage; }
    public void setBlazefistBurnDamage(float d) { this.blazefistBurnDamage = Math.max(1.0f, Math.min(200.0f, d)); }

    public int getBlazefistBurnTicks() { return blazefistBurnTicks; }
    public void setBlazefistBurnTicks(int t) { this.blazefistBurnTicks = Math.max(1, Math.min(10, t)); }
}
