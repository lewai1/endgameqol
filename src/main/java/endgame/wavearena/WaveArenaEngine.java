package endgame.wavearena;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core wave arena state machine. Manages per-player arena sessions.
 * Zero dependencies on EndgameQoL — all mod-specific behavior injected via {@link WaveArenaCallbacks}.
 */
public class WaveArenaEngine {

    private static final HytaleLogger LOGGER = HytaleLogger.get("WaveArena");

    public enum Phase {
        COUNTDOWN, SPAWNING, ACTIVE, WAVE_CLEAR, INTERVAL, COMPLETED, FAILED
    }

    public static class ArenaSession {
        public final UUID playerUuid;
        public final PlayerRef playerRef;
        public final Vector3d spawnPosition;
        public final WaveArenaConfig config;
        volatile int currentWave;
        volatile Phase phase;
        final Set<Ref<EntityStore>> aliveNpcs = ConcurrentHashMap.newKeySet();
        volatile long phaseStartTime;
        volatile int totalEnemiesInWave;
        volatile int totalKilledInWave;
        volatile boolean spawnRequested;
        long spawnRequestTime;
        volatile boolean playerAlive = true;
        long lastZoneParticleTime;

        ArenaSession(UUID playerUuid, PlayerRef playerRef, Vector3d position, WaveArenaConfig config) {
            this.playerUuid = playerUuid;
            this.playerRef = playerRef;
            this.spawnPosition = new Vector3d(position);
            this.config = config;
            this.currentWave = 0;
            this.phase = Phase.COUNTDOWN;
            this.phaseStartTime = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<UUID, ArenaSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Ref<EntityStore>, UUID> npcToPlayerMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WaveArenaConfig> registeredConfigs = new ConcurrentHashMap<>();
    private final List<WaveArenaCallbacks> callbacks = new ArrayList<>();
    private volatile MobLevelProvider mobLevelProvider;

    private static final long SPAWN_TIMEOUT_MS = 5000;
    private static final long WAVE_HARD_TIMEOUT_MS = 600_000;

    // === Config registration ===

    public void registerConfig(@Nonnull WaveArenaConfig config) {
        registeredConfigs.put(config.getId(), config);
        LOGGER.atInfo().log("[WaveArena] Registered arena: %s (%d waves)", config.getId(), config.getWaveCount());
    }

    @Nullable
    public WaveArenaConfig getConfig(@Nonnull String arenaId) {
        return registeredConfigs.get(arenaId);
    }

    public Collection<String> getRegisteredIds() {
        return registeredConfigs.keySet();
    }

    // === Callbacks ===

    public void addCallbacks(@Nonnull WaveArenaCallbacks cb) {
        callbacks.add(cb);
    }

    public void setMobLevelProvider(@Nullable MobLevelProvider provider) {
        this.mobLevelProvider = provider;
    }

    // === Lifecycle ===

    public boolean startArena(@Nonnull UUID playerUuid, @Nonnull PlayerRef playerRef,
                               @Nonnull Vector3d position, @Nonnull String arenaId, @Nonnull World world) {
        if (activeSessions.containsKey(playerUuid)) return false;

        WaveArenaConfig config = registeredConfigs.get(arenaId);
        if (config == null) {
            LOGGER.atWarning().log("[WaveArena] Unknown arena: %s", arenaId);
            return false;
        }

        ArenaSession session = new ArenaSession(playerUuid, playerRef, position, config);
        activeSessions.put(playerUuid, session);

        LOGGER.atFine().log("[WaveArena] Started %s for %s", arenaId, playerUuid);
        for (WaveArenaCallbacks cb : callbacks) {
            cb.onCountdown(playerUuid, arenaId, config.getCountdownSeconds());
        }
        return true;
    }

    public boolean isInArena(@Nonnull UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    @Nullable
    public ArenaSession getSession(@Nonnull UUID playerUuid) {
        return activeSessions.get(playerUuid);
    }

    public void failArena(@Nonnull UUID playerUuid) {
        failArena(playerUuid, WaveArenaCallbacks.FailReason.MANUAL);
    }

    public void failArena(@Nonnull UUID playerUuid, @Nonnull WaveArenaCallbacks.FailReason reason) {
        ArenaSession session = activeSessions.remove(playerUuid);
        if (session == null) return;

        session.phase = Phase.FAILED;
        despawnAllNpcs(session);
        for (WaveArenaCallbacks cb : callbacks) {
            cb.onArenaFailed(playerUuid, session.config.getId(), session.currentWave, reason);
            cb.onCleanup(playerUuid);
        }
        LOGGER.atFine().log("[WaveArena] %s failed for %s (reason: %s)", session.config.getId(), playerUuid, reason);
    }

    public boolean isTrackedNpc(@Nonnull Ref<EntityStore> ref) {
        return npcToPlayerMap.containsKey(ref);
    }

    // === Death tracking ===

    public void onNpcDeath(@Nonnull Ref<EntityStore> npcRef) {
        UUID playerUuid = npcToPlayerMap.remove(npcRef);
        if (playerUuid == null) return;

        ArenaSession session = activeSessions.get(playerUuid);
        if (session == null) return;

        synchronized (session) {
            session.aliveNpcs.remove(npcRef);
            session.totalKilledInWave++;

            for (WaveArenaCallbacks cb : callbacks) {
                cb.onMobKilled(npcRef, playerUuid, session.config.getId());
            }

            if (session.aliveNpcs.isEmpty() && session.phase == Phase.ACTIVE) {
                session.phase = Phase.WAVE_CLEAR;
                session.phaseStartTime = System.currentTimeMillis();
            }
        }
    }

    // === Tick ===

    public void tick(Store<EntityStore> store) {
        if (activeSessions.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (ArenaSession session : activeSessions.values()) {
            try {
                tickSession(session, store, now);
            } catch (Exception e) {
                LOGGER.atWarning().log("[WaveArena] Error ticking %s for %s: %s",
                        session.config.getId(), session.playerUuid, e.getMessage());
            }
        }
    }

    private void tickSession(ArenaSession session, Store<EntityStore> store, long now) {
        long elapsed = now - session.phaseStartTime;
        WaveArenaConfig config = session.config;
        int totalWaves = config.getWaveCount();

        // Zone boundary particle (visual ring around spawn area)
        if (config.getZoneParticleId() != null
                && now - session.lastZoneParticleTime >= config.getZoneParticleIntervalMs()) {
            session.lastZoneParticleTime = now;
            spawnZoneParticle(session, store);
        }

        switch (session.phase) {
            case COUNTDOWN -> {
                if (elapsed >= config.getCountdownSeconds() * 1000L) {
                    session.phase = Phase.SPAWNING;
                    session.phaseStartTime = now;
                    session.spawnRequested = false;
                    for (WaveArenaCallbacks cb : callbacks) {
                        cb.onWaveStart(session.playerUuid, session.config.getId(),
                                session.currentWave, totalWaves);
                    }
                }
            }
            case SPAWNING -> {
                if (!session.spawnRequested) {
                    session.spawnRequested = true;
                    session.spawnRequestTime = now;
                    spawnWave(session);
                } else if (!session.aliveNpcs.isEmpty()) {
                    session.spawnRequested = false;
                    session.phase = Phase.ACTIVE;
                    session.phaseStartTime = now;
                } else if (now - session.spawnRequestTime > SPAWN_TIMEOUT_MS) {
                    LOGGER.atWarning().log("[WaveArena] Spawn timeout for %s", session.playerUuid);
                    failArena(session.playerUuid, WaveArenaCallbacks.FailReason.MANUAL);
                }
            }
            case ACTIVE -> {
                updatePlayerAlive(session);
                if (!session.playerAlive) {
                    failArena(session.playerUuid, WaveArenaCallbacks.FailReason.PLAYER_DEATH);
                    return;
                }
                if (config.getTimeLimitSeconds() > 0
                        && elapsed >= config.getTimeLimitSeconds() * 1000L
                        && !session.aliveNpcs.isEmpty()) {
                    failArena(session.playerUuid, WaveArenaCallbacks.FailReason.TIMEOUT);
                    return;
                }
                if (elapsed >= WAVE_HARD_TIMEOUT_MS && !session.aliveNpcs.isEmpty()) {
                    LOGGER.atWarning().log("[WaveArena] Hard timeout for %s wave %d",
                            session.playerUuid, session.currentWave + 1);
                    forceWaveClear(session);
                }
            }
            case WAVE_CLEAR -> {
                session.currentWave++;
                for (WaveArenaCallbacks cb : callbacks) {
                    cb.onWaveClear(session.playerUuid, session.config.getId(),
                            session.currentWave - 1, totalWaves);
                }
                if (session.currentWave >= totalWaves) {
                    completeArena(session, store);
                } else {
                    session.phase = Phase.INTERVAL;
                    session.phaseStartTime = now;
                    session.totalKilledInWave = 0;
                }
            }
            case INTERVAL -> {
                if (elapsed >= config.getIntervalSeconds() * 1000L) {
                    session.phase = Phase.SPAWNING;
                    session.phaseStartTime = now;
                    session.spawnRequested = false;
                    for (WaveArenaCallbacks cb : callbacks) {
                        cb.onWaveStart(session.playerUuid, session.config.getId(),
                                session.currentWave, totalWaves);
                    }
                }
            }
            case COMPLETED, FAILED -> activeSessions.remove(session.playerUuid);
        }
    }

    // === Spawning ===

    private void spawnWave(ArenaSession session) {
        WaveDef wave = session.config.getWaveForIndex(session.currentWave);
        session.aliveNpcs.clear();
        session.totalEnemiesInWave = wave.totalEnemies();
        session.totalKilledInWave = 0;

        World world = findWorld(session.playerRef);
        if (world == null || !world.isAlive()) {
            failArena(session.playerUuid, WaveArenaCallbacks.FailReason.MANUAL);
            return;
        }

        world.execute(() -> {
            try {
                Ref<EntityStore> playerRefEcs = session.playerRef.getReference();
                if (playerRefEcs == null || !playerRefEcs.isValid()) return;
                Store<EntityStore> playerStore = playerRefEcs.getStore();

                for (WaveDef.MobEntry mob : wave.getMobs()) {
                    for (int i = 0; i < mob.count(); i++) {
                        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                        double radius = session.config.getSpawnRadius()
                                * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
                        Vector3d pos = new Vector3d(
                                session.spawnPosition.x + Math.cos(angle) * radius,
                                session.spawnPosition.y,
                                session.spawnPosition.z + Math.sin(angle) * radius);
                        Vector3f rot = new Vector3f(0, (float) (ThreadLocalRandom.current().nextDouble() * 360), 0);

                        var result = NPCPlugin.get().spawnNPC(playerStore, mob.type(), null, pos, rot);
                        if (result != null) {
                            Ref<EntityStore> npcRef = result.left();
                            session.aliveNpcs.add(npcRef);
                            npcToPlayerMap.put(npcRef, session.playerUuid);

                            NPCEntity npc = playerStore.getComponent(npcRef, NPCEntity.getComponentType());
                            if (npc != null) npc.setDespawning(false);

                            // Mob level: provider first, then config fallback
                            int level = -1;
                            MobLevelProvider provider = mobLevelProvider;
                            if (provider != null) {
                                level = provider.getMobLevel(session.config.getId(),
                                        session.currentWave, mob.type());
                            }
                            if (level <= 0 && session.config.getMobLevel() > 0) {
                                level = session.config.getMobLevel();
                            }

                            for (WaveArenaCallbacks cb : callbacks) {
                                cb.onMobSpawned(npcRef, mob.type(), session.playerUuid,
                                        session.config.getId());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.atWarning().log("[WaveArena] Error spawning wave %d: %s",
                        session.currentWave + 1, e.getMessage());
            }
        });
    }

    // === Completion ===

    private void completeArena(ArenaSession session, Store<EntityStore> store) {
        session.phase = Phase.COMPLETED;
        activeSessions.remove(session.playerUuid);

        for (Ref<EntityStore> ref : session.aliveNpcs) {
            npcToPlayerMap.remove(ref);
        }
        session.aliveNpcs.clear();

        for (WaveArenaCallbacks cb : callbacks) {
            cb.onArenaCompleted(session.playerUuid, session.config.getId(), session.currentWave);
            cb.onCleanup(session.playerUuid);
        }

        LOGGER.atFine().log("[WaveArena] %s completed for %s (%d waves)",
                session.config.getId(), session.playerUuid, session.currentWave);
    }

    // === Zone particle ===

    private void spawnZoneParticle(ArenaSession session, Store<EntityStore> store) {
        try {
            Ref<EntityStore> playerRefEcs = session.playerRef.getReference();
            if (playerRefEcs == null || !playerRefEcs.isValid()) return;
            Store<EntityStore> playerStore = playerRefEcs.getStore();
            World playerWorld = playerStore.getExternalData().getWorld();
            if (playerWorld == null || !playerWorld.isAlive()) return;

            playerWorld.execute(() -> {
                try {
                    Ref<EntityStore> pRef = session.playerRef.getReference();
                    if (pRef == null || !pRef.isValid()) return;
                    Store<EntityStore> pStore = pRef.getStore();

                    List<Ref<EntityStore>> viewers = new ArrayList<>();
                    for (PlayerRef p : Universe.get().getPlayers()) {
                        if (p == null) continue;
                        Ref<EntityStore> ref = p.getReference();
                        if (ref == null || !ref.isValid()) continue;
                        if (ref.getStore() != pStore) continue;
                        viewers.add(ref);
                    }
                    if (!viewers.isEmpty()) {
                        WaveArenaConfig cfg = session.config;
                        double x = session.spawnPosition.x;
                        double y = session.spawnPosition.y + cfg.getZoneParticleYOffset();
                        double z = session.spawnPosition.z;
                        LOGGER.atFine().log("[WaveArena] Spawning zone particle %s at %.0f,%.0f,%.0f scale=%.1f viewers=%d",
                                cfg.getZoneParticleId(), x, y, z, cfg.getZoneParticleScale(), viewers.size());
                        ParticleUtil.spawnParticleEffect(cfg.getZoneParticleId(),
                                x, y, z, 0f, 0f, 0f, cfg.getZoneParticleScale(),
                                null, null, viewers, pStore);
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().log("[WaveArena] Zone particle error: %s", e.getMessage());
                }
            });
        } catch (Exception ignored) {}
    }

    // === Helpers ===

    private void despawnAllNpcs(ArenaSession session) {
        List<Ref<EntityStore>> refs = new ArrayList<>(session.aliveNpcs);
        for (Ref<EntityStore> ref : refs) npcToPlayerMap.remove(ref);
        session.aliveNpcs.clear();

        Map<World, List<Ref<EntityStore>>> byWorld = new HashMap<>();
        for (Ref<EntityStore> ref : refs) {
            try {
                if (!ref.isValid()) continue;
                World w = ref.getStore().getExternalData().getWorld();
                if (w != null) byWorld.computeIfAbsent(w, k -> new ArrayList<>()).add(ref);
            } catch (Exception ignored) {}
        }

        for (Map.Entry<World, List<Ref<EntityStore>>> entry : byWorld.entrySet()) {
            World w = entry.getKey();
            if (!w.isAlive()) continue;
            w.execute(() -> {
                for (Ref<EntityStore> ref : entry.getValue()) {
                    try {
                        if (ref.isValid()) {
                            ref.getStore().removeEntity(ref,
                                    EntityStore.REGISTRY.newHolder(), RemoveReason.REMOVE);
                        }
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void forceWaveClear(ArenaSession session) {
        despawnAllNpcs(session);
        session.phase = Phase.WAVE_CLEAR;
        session.phaseStartTime = System.currentTimeMillis();
    }

    private void updatePlayerAlive(ArenaSession session) {
        try {
            Ref<EntityStore> ref = session.playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                session.playerAlive = false;
                return;
            }
            var statMap = ref.getStore().getComponent(ref,
                    com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType());
            if (statMap != null) {
                var hpValue = statMap.get(
                        com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes.getHealth());
                session.playerAlive = hpValue != null && hpValue.get() > 0;
            }
        } catch (Exception e) {
            session.playerAlive = false;
        }
    }

    @Nullable
    private World findWorld(PlayerRef playerRef) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return null;
            return ref.getStore().getExternalData().getWorld();
        } catch (Exception e) {
            return null;
        }
    }

    public void forceClear() {
        for (ArenaSession session : activeSessions.values()) {
            despawnAllNpcs(session);
            for (WaveArenaCallbacks cb : callbacks) cb.onCleanup(session.playerUuid);
        }
        activeSessions.clear();
        npcToPlayerMap.clear();
    }
}
