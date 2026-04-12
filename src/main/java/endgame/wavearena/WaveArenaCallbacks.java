package endgame.wavearena;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Callback interface for wave arena lifecycle events.
 * Consumers (EndgameQoL, other mods) implement this to hook into arena events
 * without the framework depending on mod-specific classes.
 */
public interface WaveArenaCallbacks {

    void onCountdown(@Nonnull UUID playerUuid, @Nonnull String arenaId, int secondsRemaining);

    void onWaveStart(@Nonnull UUID playerUuid, @Nonnull String arenaId, int waveIndex, int totalWaves);

    void onWaveClear(@Nonnull UUID playerUuid, @Nonnull String arenaId, int waveIndex, int totalWaves);

    void onArenaCompleted(@Nonnull UUID playerUuid, @Nonnull String arenaId, int wavesCleared);

    void onArenaFailed(@Nonnull UUID playerUuid, @Nonnull String arenaId, int wavesCleared, @Nonnull FailReason reason);

    void onMobSpawned(@Nonnull Ref<EntityStore> npcRef, @Nonnull String npcType, @Nonnull UUID ownerUuid, @Nonnull String arenaId);

    void onMobKilled(@Nonnull Ref<EntityStore> npcRef, @Nonnull UUID killerUuid, @Nonnull String arenaId);

    default void onCleanup(@Nonnull UUID playerUuid) {}

    enum FailReason {
        PLAYER_DEATH,
        TIMEOUT,
        DISCONNECT,
        MANUAL
    }
}
