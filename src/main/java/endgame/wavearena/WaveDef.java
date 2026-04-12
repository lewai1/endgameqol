package endgame.wavearena;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Defines a single wave: a list of mob entries (type + count).
 */
public class WaveDef {

    public record MobEntry(String type, int count) {}

    private final List<MobEntry> mobs;

    public WaveDef(@Nonnull List<MobEntry> mobs) {
        this.mobs = mobs;
    }

    @Nonnull
    public List<MobEntry> getMobs() { return mobs; }

    public int totalEnemies() {
        int total = 0;
        for (MobEntry m : mobs) total += m.count();
        return total;
    }
}
