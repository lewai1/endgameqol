package endgame.plugin.systems.portal;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.npc.NPCPlugin;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Temporal Portal system: spawns temporary portals near players,
 * creates dungeon instances, handles teleportation and cleanup.
 *
 * Thread-safe: uses ConcurrentHashMap, volatile fields, and world.execute().
 * Pattern: ScheduledExecutor (same as ForgottenTempleWatcher).
 */
public class TemporalPortalManager {

    private final EndgameQoL plugin;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, TemporalPortalSession> activeSessions = new ConcurrentHashMap<>();

    private volatile long lastSpawnTimeMs;
    private volatile long nextSpawnIntervalMs;

    private static final String PORTAL_NPC_ID = "Endgame_Temporal_Portal";
    private static final String PARTICLE_SPAWN = "Praetorian_Summon_Spawn";
    private static final long SPAWN_CHECK_INTERVAL_MS = 30_000; // Check every 30s
    private static final long MAINTENANCE_INTERVAL_MS = 60_000; // Cleanup every 60s
    private static final long INITIAL_DELAY_MS = 60_000; // 60s warmup

    public TemporalPortalManager(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void start() {
        lastSpawnTimeMs = System.currentTimeMillis();
        nextSpawnIntervalMs = randomInterval();

        scheduler.scheduleAtFixedRate(() -> {
            try { trySpawnPortal(); } catch (Exception e) {
                plugin.getLogger().atWarning().log("[TemporalPortal] Spawn error: %s", e.getMessage());
            }
        }, INITIAL_DELAY_MS, SPAWN_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            try { maintenanceTick(); } catch (Exception e) {
                plugin.getLogger().atWarning().log("[TemporalPortal] Maintenance error: %s", e.getMessage());
            }
        }, MAINTENANCE_INTERVAL_MS, MAINTENANCE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        plugin.getLogger().atInfo().log("[TemporalPortal] System started");
    }

    public void stop() {
        // Cleanup all portal entities
        for (TemporalPortalSession session : activeSessions.values()) {
            removePortalEntity(session);
            removeReturnPortal(session);
            teleportAllPlayersOut(session);
        }
        activeSessions.clear();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        plugin.getLogger().atInfo().log("[TemporalPortal] System stopped");
    }

    public void forceClear() {
        activeSessions.clear();
    }

    // =========================================================================
    // Spawn Logic
    // =========================================================================

    private void trySpawnPortal() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        if (!config.isEnabled()) return;
        if (activeSessions.size() >= config.getMaxConcurrentPortals()) return;

        long now = System.currentTimeMillis();
        if (now - lastSpawnTimeMs < nextSpawnIntervalMs) return;

        // Pick a random enabled dungeon type
        TemporalPortalSession.DungeonType dungeonType = pickRandomEnabledDungeon(config);
        if (dungeonType == null) return;

        // Pick a random overworld player
        PlayerRef targetPlayer = pickRandomOverworldPlayer();
        if (targetPlayer == null) return;

        // Get player position
        Ref<EntityStore> playerRef = targetPlayer.getReference();
        if (playerRef == null || !playerRef.isValid()) return;

        TransformComponent transform = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d playerPos = transform.getPosition();
        World world = playerRef.getStore().getExternalData().getWorld();
        if (world == null || !world.isAlive()) return;

        // Compute spawn position (offset from player)
        float radius = config.getSpawnOffsetRadius();
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        double offsetX = Math.cos(angle) * (3 + ThreadLocalRandom.current().nextDouble() * (radius - 3));
        double offsetZ = Math.sin(angle) * (3 + ThreadLocalRandom.current().nextDouble() * (radius - 3));
        Vector3d spawnPos = new Vector3d(
                playerPos.x + offsetX,
                playerPos.y + 1,
                playerPos.z + offsetZ);

        // Create session
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        TemporalPortalSession session = new TemporalPortalSession(sessionId, dungeonType);

        // Spawn on world thread
        spawnPortalEntity(session, world, spawnPos, config);

        lastSpawnTimeMs = now;
        nextSpawnIntervalMs = randomInterval();
    }

    private void spawnPortalEntity(TemporalPortalSession session, World world,
                                    Vector3d position, TemporalPortalConfig config) {
        world.execute(() -> {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                Vector3f rotation = new Vector3f(0, 0, 0);

                NPCPlugin.get().spawnNPC(store, PORTAL_NPC_ID, null, position, rotation);

                // We can't easily get the Ref from spawnNPC return, so we track by session
                session.setSpawnWorldName(world.getName());
                session.setPortalPosition(position);
                activeSessions.put(session.getId(), session);

                plugin.getLogger().atInfo().log("[TemporalPortal] Spawned %s portal at (%.0f, %.0f, %.0f) in %s [session=%s]",
                        session.getDungeonType().getDisplayName(),
                        position.x, position.y, position.z,
                        world.getName(), session.getId());

                // Announce to nearby players
                announcePortalSpawn(world, position, session.getDungeonType(), config.getAnnounceRadius());

                // Spawn particles
                spawnPortalParticles(world, position);

            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[TemporalPortal] Failed to spawn portal: %s", e.getMessage());
            }
        });
    }

    // =========================================================================
    // Player Interaction (called from BuilderActionEnterTemporalPortal)
    // =========================================================================

    public void onPlayerInteract(@Nonnull UUID playerUuid, @Nonnull Ref<EntityStore> portalNpcRef) {
        // Find session by portal position (NPC ref matching)
        TemporalPortalSession session = findSessionNearPortal(portalNpcRef);
        if (session == null) {
            plugin.getLogger().atFine().log("[TemporalPortal] No session found for portal interaction");
            return;
        }

        PlayerRef playerRef = findPlayerRef(playerUuid);
        if (playerRef == null) return;

        session.recordPlayerActivity();

        if (!session.isInstanceReady()) {
            createInstanceWorld(session, () -> teleportPlayerIn(playerRef, session));
        } else {
            teleportPlayerIn(playerRef, session);
        }
    }

    // =========================================================================
    // Instance Management
    // =========================================================================

    private void createInstanceWorld(TemporalPortalSession session, Runnable onReady) {
        String instanceId = session.getDungeonType().getInstanceId();
        String worldName = "temporal-portal-" + instanceId.toLowerCase() + "-" + session.getId();

        try {
            var future = Universe.get().addWorld(worldName, null, instanceId);
            future.whenComplete((world, error) -> {
                if (error != null) {
                    plugin.getLogger().atWarning().log("[TemporalPortal] Failed to create instance %s: %s",
                            worldName, error.getMessage());
                    return;
                }
                if (world != null) {
                    world.getWorldConfig().setDeleteOnRemove(true);
                    world.getWorldConfig().markChanged();
                    session.setInstanceWorldName(worldName);
                    session.setInstanceReady(true);

                    plugin.getLogger().atInfo().log("[TemporalPortal] Instance created: %s", worldName);
                    onReady.run();
                }
            });
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Error creating instance: %s", e.getMessage());
        }
    }

    private void teleportPlayerIn(@Nonnull PlayerRef playerRef, @Nonnull TemporalPortalSession session) {
        String worldName = session.getInstanceWorldName();
        if (worldName == null) return;

        World instanceWorld = Universe.get().getWorld(worldName);
        if (instanceWorld == null || !instanceWorld.isAlive()) return;

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;

        // Teleport to instance spawn point (center of world)
        Vector3d spawnPos = new Vector3d(0, 80, 0); // TODO: read from instance config
        try {
            var teleportType = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType();
            var teleport = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.createForPlayer(
                    instanceWorld, spawnPos, new Vector3f(0, 0, 0));
            ref.getStore().addComponent(ref, teleportType, teleport);

            UUID uuid = endgame.plugin.utils.EntityUtils.getUuid(playerRef);
            if (uuid != null) {
                session.getPlayersInside().add(uuid);
                session.recordPlayerActivity();
            }

            plugin.getLogger().atInfo().log("[TemporalPortal] Teleported player into %s [session=%s]",
                    worldName, session.getId());
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Teleport failed: %s", e.getMessage());
        }
    }

    private void teleportPlayerOut(@Nonnull PlayerRef playerRef, @Nonnull TemporalPortalSession session) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;

        // Return to portal spawn position in overworld
        String overworldName = session.getSpawnWorldName();
        Vector3d returnPos = session.getPortalPosition();
        if (overworldName == null || returnPos == null) return;

        World overworld = Universe.get().getWorld(overworldName);
        if (overworld == null || !overworld.isAlive()) {
            // Fallback: first available world
            overworld = new ArrayList<>(Universe.get().getWorlds().values()).getFirst();
        }

        try {
            var teleportType = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType();
            var teleport = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.createForPlayer(
                    overworld, returnPos, new Vector3f(0, 0, 0));
            ref.getStore().addComponent(ref, teleportType, teleport);

            UUID uuid = endgame.plugin.utils.EntityUtils.getUuid(playerRef);
            if (uuid != null) {
                session.getPlayersInside().remove(uuid);
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Return teleport failed: %s", e.getMessage());
        }
    }

    private void teleportAllPlayersOut(@Nonnull TemporalPortalSession session) {
        for (UUID uuid : new ArrayList<>(session.getPlayersInside())) {
            PlayerRef playerRef = findPlayerRef(uuid);
            if (playerRef != null) {
                teleportPlayerOut(playerRef, session);
            }
        }
        session.getPlayersInside().clear();
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    private void maintenanceTick() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        long now = System.currentTimeMillis();

        for (var entry : new ArrayList<>(activeSessions.entrySet())) {
            TemporalPortalSession session = entry.getValue();

            // 0. Warnings (30s before expiry)
            if (!session.hasWarnedPortalExpiry() &&
                    session.isPortalAboutToExpire(config.getPortalDurationSeconds())) {
                session.setWarnedPortalExpiry(true);
                plugin.getLogger().atFine().log("[TemporalPortal] Portal expiring soon [session=%s]", session.getId());
            }
            if (!session.hasWarnedInstanceExpiry() && session.isInstanceReady() &&
                    session.isInstanceAboutToExpire(config.getInstanceTimeLimitSeconds())) {
                session.setWarnedInstanceExpiry(true);
                plugin.getLogger().atInfo().log("[TemporalPortal] Instance closing in 30s [session=%s]", session.getId());
                sendMessageToPlayersInside(session,
                        Message.raw("[Temporal Portal] The dungeon is closing in 30 seconds!").color("#ff5555"));
            }

            // 1. Instance time limit reached → eject all players
            if (session.isInstanceReady() &&
                    session.isInstanceTimeLimitReached(config.getInstanceTimeLimitSeconds())) {
                plugin.getLogger().atInfo().log("[TemporalPortal] Instance time limit reached [session=%s]", session.getId());
                sendMessageToPlayersInside(session,
                        Message.raw("[Temporal Portal] The dungeon has collapsed! Returning to overworld...").color("#ff5555"));
                teleportAllPlayersOut(session);
                destroyInstance(session);
                removePortalEntity(session);
                activeSessions.remove(entry.getKey());
                continue;
            }

            // 2. Portal NPC expired (overworld, visual only)
            if (session.isPortalExpired(config.getPortalDurationSeconds()) && session.getPortalPosition() != null) {
                removePortalEntity(session);
                // Don't remove session — instance may still have players
            }

            // 3. Instance idle (no players for too long)
            if (session.isInstanceReady() && session.getPlayersInside().isEmpty() &&
                    session.isInstanceIdle(config.getInstanceIdleTimeoutMinutes())) {
                plugin.getLogger().atInfo().log("[TemporalPortal] Instance idle timeout [session=%s]", session.getId());
                destroyInstance(session);
                activeSessions.remove(entry.getKey());
                continue;
            }

            // 4. No instance + portal expired → fully cleanup
            if (!session.isInstanceReady() &&
                    session.isPortalExpired(config.getPortalDurationSeconds())) {
                removePortalEntity(session);
                activeSessions.remove(entry.getKey());
            }
        }
    }

    private void removePortalEntity(@Nonnull TemporalPortalSession session) {
        // Portal NPC will naturally despawn when its chunk is unloaded or via kill
        // For now we just clear the tracking — the NPC is stationary and invulnerable
        // TODO: actively remove entity via store.removeEntity() if we track the ref
        session.setPortalEntityRef(null);
    }

    private void removeReturnPortal(@Nonnull TemporalPortalSession session) {
        session.setReturnPortalRef(null);
    }

    private void destroyInstance(@Nonnull TemporalPortalSession session) {
        String worldName = session.getInstanceWorldName();
        if (worldName == null) return;

        try {
            World world = Universe.get().getWorld(worldName);
            if (world != null && world.isAlive()) {
                Universe.get().removeWorld(world.getName());
                plugin.getLogger().atInfo().log("[TemporalPortal] Destroyed instance: %s", worldName);
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Failed to destroy instance %s: %s",
                    worldName, e.getMessage());
        }
        session.setInstanceWorldName(null);
        session.setInstanceReady(false);
    }

    private void sendMessageToPlayersInside(@Nonnull TemporalPortalSession session, @Nonnull Message message) {
        for (UUID uuid : session.getPlayersInside()) {
            PlayerRef p = findPlayerRef(uuid);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    // =========================================================================
    // Player disconnect/leave cleanup
    // =========================================================================

    public void onPlayerDisconnect(@Nonnull UUID playerUuid) {
        for (TemporalPortalSession session : activeSessions.values()) {
            session.getPlayersInside().remove(playerUuid);
        }
    }

    public void onPlayerLeaveInstanceWorld(@Nonnull UUID playerUuid, @Nonnull String worldName) {
        for (TemporalPortalSession session : activeSessions.values()) {
            if (worldName.equals(session.getInstanceWorldName())) {
                session.getPlayersInside().remove(playerUuid);
                session.recordPlayerActivity(); // Reset idle timer
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Nullable
    private TemporalPortalSession.DungeonType pickRandomEnabledDungeon(TemporalPortalConfig config) {
        List<TemporalPortalSession.DungeonType> enabled = new ArrayList<>();
        if (config.isFrozenDungeonEnabled()) enabled.add(TemporalPortalSession.DungeonType.FROZEN_DUNGEON);
        if (config.isSwampDungeonEnabled()) enabled.add(TemporalPortalSession.DungeonType.SWAMP_DUNGEON);
        if (enabled.isEmpty()) return null;
        return enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
    }

    @Nullable
    private PlayerRef pickRandomOverworldPlayer() {
        List<PlayerRef> candidates = new ArrayList<>();
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;
            World w = ref.getStore().getExternalData().getWorld();
            if (w != null && !w.getName().toLowerCase().contains("instance-")
                    && !w.getName().toLowerCase().contains("temporal-portal-")) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) return null;
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    @Nullable
    private TemporalPortalSession findSessionNearPortal(@Nonnull Ref<EntityStore> portalNpcRef) {
        // Get portal NPC position
        Store<EntityStore> store = portalNpcRef.getStore();
        TransformComponent transform = store.getComponent(portalNpcRef, TransformComponent.getComponentType());
        if (transform == null) return null;
        Vector3d npcPos = transform.getPosition();

        // Find session with closest portal position (within 3 blocks)
        for (TemporalPortalSession session : activeSessions.values()) {
            Vector3d portalPos = session.getPortalPosition();
            if (portalPos != null) {
                double distSq = npcPos.distanceSquaredTo(portalPos);
                if (distSq < 9.0) { // 3 blocks
                    return session;
                }
            }
        }
        return null;
    }

    @Nullable
    private PlayerRef findPlayerRef(@Nonnull UUID uuid) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            UUID pUuid = endgame.plugin.utils.EntityUtils.getUuid(p);
            if (uuid.equals(pUuid)) return p;
        }
        return null;
    }

    private long randomInterval() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        int min = config.getSpawnIntervalMinSeconds();
        int max = config.getSpawnIntervalMaxSeconds();
        return (min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min))) * 1000L;
    }

    private void announcePortalSpawn(World world, Vector3d position,
                                      TemporalPortalSession.DungeonType type, float radius) {
        double radiusSq = radius * radius;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;

            World pw = ref.getStore().getExternalData().getWorld();
            if (pw == null || !pw.getName().equals(world.getName())) continue;

            TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) continue;

            if (tc.getPosition().distanceSquaredTo(position) <= radiusSq) {
                p.sendMessage(Message.join(
                        Message.raw("[Temporal Portal] ").color(type.getColor()),
                        Message.raw("A " + type.getDisplayName() + " portal has appeared nearby!").color("#ffffff")
                ));
            }
        }
    }

    private void spawnPortalParticles(World world, Vector3d position) {
        List<Ref<EntityStore>> viewers = new ArrayList<>();
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;
            World pw = ref.getStore().getExternalData().getWorld();
            if (pw != null && pw.getName().equals(world.getName())) {
                viewers.add(ref);
            }
        }
        if (viewers.isEmpty()) return;

        try {
            Store<EntityStore> particleStore = viewers.getFirst().getStore();
            ParticleUtil.spawnParticleEffect(PARTICLE_SPAWN, position, viewers, particleStore);
        } catch (Exception e) {
            // Silently ignore particle failures
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public int getActiveSessionCount() { return activeSessions.size(); }

    public ConcurrentHashMap<String, TemporalPortalSession> getActiveSessions() { return activeSessions; }
}
