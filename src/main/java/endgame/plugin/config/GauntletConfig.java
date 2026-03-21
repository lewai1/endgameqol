package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class GauntletConfig {

    @Nonnull
    public static final BuilderCodec<GauntletConfig> CODEC = BuilderCodec
            .builder(GauntletConfig.class, GauntletConfig::new)
            .append(new KeyedCodec<Boolean>("Enabled", Codec.BOOLEAN),
                    (c, v) -> c.enabled = v != null ? v : false, c -> c.enabled).add()
            .append(new KeyedCodec<Integer>("ScalingPercent", Codec.INTEGER),
                    (c, v) -> c.setScalingPercent(v != null ? v : 10), c -> c.scalingPercent).add()
            .append(new KeyedCodec<Integer>("BuffCount", Codec.INTEGER),
                    (c, v) -> c.setBuffCount(v != null ? v : 3), c -> c.buffCount).add()
            .build();

    private boolean enabled = false;
    private int scalingPercent = 10;
    private int buffCount = 3;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    public int getScalingPercent() { return scalingPercent; }
    public void setScalingPercent(int p) { this.scalingPercent = Math.max(0, Math.min(100, p)); }

    public int getBuffCount() { return buffCount; }
    public void setBuffCount(int c) { this.buffCount = Math.max(1, Math.min(5, c)); }
}
