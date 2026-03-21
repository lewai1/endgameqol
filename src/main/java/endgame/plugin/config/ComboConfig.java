package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class ComboConfig {

    @Nonnull
    public static final BuilderCodec<ComboConfig> CODEC = BuilderCodec
            .builder(ComboConfig.class, ComboConfig::new)
            .append(new KeyedCodec<Boolean>("Enabled", Codec.BOOLEAN),
                    (c, v) -> c.enabled = v != null ? v : true, c -> c.enabled).add()
            .append(new KeyedCodec<Float>("TimerSeconds", Codec.FLOAT),
                    (c, v) -> c.setTimerSeconds(v != null ? v : 5.0f), c -> c.timerSeconds).add()
            .append(new KeyedCodec<Float>("DamageX2", Codec.FLOAT),
                    (c, v) -> c.setComboDamageX2(v != null ? v : 1.10f), c -> c.comboDamageX2).add()
            .append(new KeyedCodec<Float>("DamageX3", Codec.FLOAT),
                    (c, v) -> c.setComboDamageX3(v != null ? v : 1.25f), c -> c.comboDamageX3).add()
            .append(new KeyedCodec<Float>("DamageX4", Codec.FLOAT),
                    (c, v) -> c.setComboDamageX4(v != null ? v : 1.50f), c -> c.comboDamageX4).add()
            .append(new KeyedCodec<Float>("DamageFrenzy", Codec.FLOAT),
                    (c, v) -> c.setComboDamageFrenzy(v != null ? v : 2.00f), c -> c.comboDamageFrenzy).add()
            .append(new KeyedCodec<Boolean>("TierEffectsEnabled", Codec.BOOLEAN),
                    (c, v) -> c.tierEffectsEnabled = v != null ? v : true, c -> c.tierEffectsEnabled).add()
            .append(new KeyedCodec<Boolean>("DecayEnabled", Codec.BOOLEAN),
                    (c, v) -> c.decayEnabled = v != null ? v : true, c -> c.decayEnabled).add()
            .build();

    private boolean enabled = true;
    private float timerSeconds = 5.0f;
    private float comboDamageX2 = 1.10f;
    private float comboDamageX3 = 1.25f;
    private float comboDamageX4 = 1.50f;
    private float comboDamageFrenzy = 2.00f;
    private boolean tierEffectsEnabled = true;
    private boolean decayEnabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    public float getTimerSeconds() { return timerSeconds; }
    public void setTimerSeconds(float s) { this.timerSeconds = Math.max(1.0f, Math.min(30.0f, s)); }

    public float getComboDamageX2() { return comboDamageX2; }
    public void setComboDamageX2(float m) { this.comboDamageX2 = Math.max(1.0f, Math.min(5.0f, m)); }

    public float getComboDamageX3() { return comboDamageX3; }
    public void setComboDamageX3(float m) { this.comboDamageX3 = Math.max(1.0f, Math.min(5.0f, m)); }

    public float getComboDamageX4() { return comboDamageX4; }
    public void setComboDamageX4(float m) { this.comboDamageX4 = Math.max(1.0f, Math.min(5.0f, m)); }

    public float getComboDamageFrenzy() { return comboDamageFrenzy; }
    public void setComboDamageFrenzy(float m) { this.comboDamageFrenzy = Math.max(1.0f, Math.min(10.0f, m)); }

    public boolean isTierEffectsEnabled() { return tierEffectsEnabled; }
    public void setTierEffectsEnabled(boolean e) { this.tierEffectsEnabled = e; }

    public boolean isDecayEnabled() { return decayEnabled; }
    public void setDecayEnabled(boolean e) { this.decayEnabled = e; }
}
