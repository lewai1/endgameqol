package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class BountyConfig {

    @Nonnull
    public static final BuilderCodec<BountyConfig> CODEC = BuilderCodec
            .builder(BountyConfig.class, BountyConfig::new)
            .append(new KeyedCodec<Boolean>("Enabled", Codec.BOOLEAN),
                    (c, v) -> c.enabled = v != null ? v : true, c -> c.enabled).add()
            .append(new KeyedCodec<Integer>("RefreshHours", Codec.INTEGER),
                    (c, v) -> c.setRefreshHours(v != null ? v : 24), c -> c.refreshHours).add()
            .append(new KeyedCodec<Boolean>("StreakEnabled", Codec.BOOLEAN),
                    (c, v) -> c.streakEnabled = v != null ? v : true, c -> c.streakEnabled).add()
            .append(new KeyedCodec<Boolean>("WeeklyEnabled", Codec.BOOLEAN),
                    (c, v) -> c.weeklyEnabled = v != null ? v : true, c -> c.weeklyEnabled).add()
            .build();

    private boolean enabled = true;
    private int refreshHours = 24;
    private boolean streakEnabled = true;
    private boolean weeklyEnabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }

    public int getRefreshHours() { return refreshHours; }
    public void setRefreshHours(int h) { this.refreshHours = Math.max(1, Math.min(168, h)); }

    public boolean isStreakEnabled() { return streakEnabled; }
    public void setStreakEnabled(boolean e) { this.streakEnabled = e; }

    public boolean isWeeklyEnabled() { return weeklyEnabled; }
    public void setWeeklyEnabled(boolean e) { this.weeklyEnabled = e; }
}
