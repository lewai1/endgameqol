package endgame.wavearena;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

/**
 * Public API for the WaveArena framework.
 * Other mods call these static methods to interact with the wave system.
 */
public final class WaveArenaAPI {

    private static volatile WaveArenaEngine engine;

    private WaveArenaAPI() {}

    public static void init(@Nonnull WaveArenaEngine instance) {
        engine = instance;
    }

    public static boolean isAvailable() {
        return engine != null;
    }

    @Nullable
    public static WaveArenaEngine getEngine() {
        return engine;
    }

    public static boolean startArena(@Nonnull UUID playerUuid, @Nonnull PlayerRef playerRef,
                                      @Nonnull Vector3d position, @Nonnull String arenaId,
                                      @Nonnull World world) {
        WaveArenaEngine e = engine;
        return e != null && e.startArena(playerUuid, playerRef, position, arenaId, world);
    }

    public static boolean isInArena(@Nonnull UUID playerUuid) {
        WaveArenaEngine e = engine;
        return e != null && e.isInArena(playerUuid);
    }

    public static void failArena(@Nonnull UUID playerUuid) {
        WaveArenaEngine e = engine;
        if (e != null) e.failArena(playerUuid, WaveArenaCallbacks.FailReason.DISCONNECT);
    }

    @Nullable
    public static WaveArenaConfig getArenaConfig(@Nonnull String arenaId) {
        WaveArenaEngine e = engine;
        return e != null ? e.getConfig(arenaId) : null;
    }

    @Nonnull
    public static Collection<String> getRegisteredArenaIds() {
        WaveArenaEngine e = engine;
        return e != null ? e.getRegisteredIds() : java.util.List.of();
    }

    public static void registerCallbacks(@Nonnull WaveArenaCallbacks callbacks) {
        WaveArenaEngine e = engine;
        if (e != null) e.addCallbacks(callbacks);
    }

    public static void setMobLevelProvider(@Nullable MobLevelProvider provider) {
        WaveArenaEngine e = engine;
        if (e != null) e.setMobLevelProvider(provider);
    }
}
