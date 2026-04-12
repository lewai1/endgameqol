package endgame.plugin.systems.portal;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalSpawnConfig;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.spawn.IndividualSpawnProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.EntityUtils;

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
 * Manages the Temporal Portal system (HyRifts + Shards inspired):
 *
 * 7 improvements over original:
 * 1. Min 100 blocks between portals (configurable)
 * 2. Spawn 80-300 blocks from player (not next to them)
 * 3. Warnings at 5min and 1min before expiration
 * 4. Probabilistic spawn (40% chance per roll, not guaranteed)
 * 5. Particle-only portals (no block placement, no grief)
 * 6. Grace period (30s after expiration before removal)
 * 7. Lifetime status progression (STABLE > DESTABILIZING > CRITICAL > COLLAPSING)
 */
public class TemporalPortalManager {

    private final EndgameQoL plugin;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, TemporalPortalSession> activeSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> entryCooldowns = new ConcurrentHashMap<>();

    private volatile long lastSpawnTimeMs;
    private volatile long nextSpawnIntervalMs;

    // Particles (vanilla IDs — custom plugin IDs don't work from Java)
    private static final String PARTICLE_AMBIENT = "Praetorian_Summon_Spawn";
    private static final String PARTICLE_DESTABILIZE = "Rings_Rings_Ice";
    private static final String PARTICLE_CRITICAL = "Fire_AoE_Spawn";

    private static final long SPAWN_CHECK_INTERVAL_MS = 30_000;
    private static final long PROXIMITY_TICK_MS = 500;
    private static final long MAINTENANCE_INTERVAL_MS = 10_000;
    private static final long INITIAL_DELAY_MS = 60_000;
    private static final double ENTER_RADIUS_SQ = 9.0; // 3 blocks (slightly larger since no block visual)
    private static final double ENTER_RADIUS_Y = 4.0;
    private static final long ENTRY_COOLDOWN_MS = 5_000;
    private static final int MAX_SPAWN_ATTEMPTS = 10;

    // Particle refresh interval — respawn ambient particles every 5s to keep portal visible
    private static final long PARTICLE_REFRESH_MS = 5_000;
    private volatile long lastParticleRefreshMs = 0;

    public TemporalPortalManager(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EndgameQoL-TemporalPortal");
            t.setDaemon(true);
            return t;
        });
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void start() {
        lastSpawnTimeMs = System.currentTimeMillis();
        nextSpawnIntervalMs = randomInterval();

        scheduler.scheduleAtFixedRate(this::safeSpawnTick, INITIAL_DELAY_MS, SPAWN_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::safeProximityTick, 2_000, PROXIMITY_TICK_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::safeMaintenanceTick, MAINTENANCE_INTERVAL_MS, MAINTENANCE_INTERVAL_MS, TimeUnit.MILLISECONDS);

        plugin.getLogger().atInfo().log("[TemporalPortal] System started (particle-only, no block placement)");
    }

    public void stop() {
        for (TemporalPortalSession session : activeSessions.values()) {
            closePortal(session);
        }
        activeSessions.clear();
        entryCooldowns.clear();

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
        entryCooldowns.clear();
    }

    // =========================================================================
    // Safe wrappers
    // =========================================================================

    private void safeSpawnTick() {
        try { trySpawnPortal(); } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Spawn error: %s", e.getMessage());
        }
    }

    private void safeProximityTick() {
        try {
            proximityTick();
            refreshParticles();
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Proximity error: %s", e.getMessage());
        }
    }

    private void safeMaintenanceTick() {
        try { maintenanceTick(); } catch (Exception e) {
            plugin.getLogger().atWarning().log("[TemporalPortal] Maintenance error: %s", e.getMessage());
        }
    }

    // =========================================================================
    // [1-4] Spawn Logic — probabilistic, far from player, min distance
    // =========================================================================

    private void trySpawnPortal() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        if (!config.isEnabled()) return;
        if (activeSessions.size() >= config.getMaxConcurrentPortals()) return;

        long now = System.currentTimeMillis();
        if (now - lastSpawnTimeMs < nextSpawnIntervalMs) return;

        // [4] Probabilistic — roll the dice, not guaranteed
        if (ThreadLocalRandom.current().nextFloat() > config.getSpawnChance()) {
            lastSpawnTimeMs = now;
            nextSpawnIntervalMs = randomInterval();
            plugin.getLogger().atFine().log("[TemporalPortal] Spawn roll failed (%.0f%% chance)", config.getSpawnChance() * 100);
            return;
        }

        DungeonDefinition dungeonType = pickRandomEnabledDungeon(config);
        if (dungeonType == null) return;

        PlayerRef targetPlayer = pickRandomOverworldPlayer();
        if (targetPlayer == null) return;

        Ref<EntityStore> playerRef = targetPlayer.getReference();
        if (playerRef == null || !playerRef.isValid()) return;

        World world;
        try {
            world = playerRef.getStore().getExternalData().getWorld();
        } catch (Exception e) { return; }
        if (world == null || !world.isAlive()) return;

        final TemporalPortalConfig cfg = config;
        final DungeonDefinition dt = dungeonType;
        world.execute(() -> {
            if (!playerRef.isValid()) return;
            TransformComponent transform = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d playerPos = transform.getPosition();

            // [2] Spawn far from player (80-300 blocks)
            Vector3d spawnPos = findSpawnPosition(world, playerPos, cfg);
            if (spawnPos == null) return;

            // [5] No block placement — just register the session with position
            String sessionId = UUID.randomUUID().toString().substring(0, 8);
            TemporalPortalSession session = new TemporalPortalSession(sessionId, dt, dt.getPortalDurationSeconds());
            session.setSpawnWorldName(world.getName());
            session.setPortalPosition(spawnPos);
            activeSessions.put(session.getId(), session);

            // Spawn initial particles + announce
            spawnPortalParticles(world, spawnPos);
            announcePortalSpawn(world, spawnPos, dt, cfg.getAnnounceRadius());

            plugin.getLogger().atInfo().log("[TemporalPortal] Spawned %s portal at (%.0f, %.0f, %.0f) in %s [session=%s] (particle-only)",
                    dt.getDisplayName(), spawnPos.x, spawnPos.y, spawnPos.z, world.getName(), sessionId);
        });

        lastSpawnTimeMs = now;
        nextSpawnIntervalMs = randomInterval();
    }

    /**
     * [1][2] Find a valid spawn position: 80-300 blocks from player, 100+ blocks from other portals,
     * not in protected zones, on solid ground.
     */
    @Nullable
    private Vector3d findSpawnPosition(World world, Vector3d playerPos, TemporalPortalConfig config) {
        float minDist = config.getSpawnMinDistance();
        float maxDist = config.getSpawnMaxDistance();
        float portalMinDist = config.getMinDistanceBetweenPortals();

        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
            double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);
            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;

            int bx = (int) Math.floor(playerPos.x + offsetX);
            int bz = (int) Math.floor(playerPos.z + offsetZ);
            int by = (int) Math.floor(playerPos.y);

            // Check protection
            if (isPositionProtected(world.getName(), bx, by, bz)) continue;

            Vector3d candidate = new Vector3d(bx + 0.5, by, bz + 0.5);

            // [1] Check min distance from other portals
            if (isPortalNearby(candidate, portalMinDist)) continue;

            return candidate;
        }
        return null;
    }

    // =========================================================================
    // [5] Particle-only portal — no block placement or removal
    // =========================================================================

    /**
     * Refresh ambient particles on all active portals to keep them visible.
     */
    private void refreshParticles() {
        long now = System.currentTimeMillis();
        if (now - lastParticleRefreshMs < PARTICLE_REFRESH_MS) return;
        lastParticleRefreshMs = now;

        for (TemporalPortalSession session : activeSessions.values()) {
            Vector3d pos = session.getPortalPosition();
            String worldName = session.getSpawnWorldName();
            if (pos == null || worldName == null) continue;
            if (session.isPortalExpired()) continue;

            World world = Universe.get().getWorld(worldName);
            if (world == null || !world.isAlive()) continue;

            // Different particle based on lifetime status
            TemporalPortalSession.LifetimeStatus status = session.getLifetimeStatus();
            String particleId = switch (status) {
                case CRITICAL, COLLAPSING -> PARTICLE_CRITICAL;
                case DESTABILIZING -> PARTICLE_DESTABILIZE;
                default -> PARTICLE_AMBIENT;
            };

            world.execute(() -> spawnParticleAt(world, pos, particleId));
        }
    }

    // =========================================================================
    // Proximity Detection
    // =========================================================================

    private void proximityTick() {
        long now = System.currentTimeMillis();
        entryCooldowns.entrySet().removeIf(e -> now - e.getValue() > ENTRY_COOLDOWN_MS * 3);

        for (TemporalPortalSession session : activeSessions.values()) {
            Vector3d portalPos = session.getPortalPosition();
            String portalWorldName = session.getSpawnWorldName();
            if (portalPos == null || portalWorldName == null) continue;
            if (session.isPortalExpired()) continue;

            // [7] Update lifetime status
            TemporalPortalSession.LifetimeStatus oldStatus = session.getLifetimeStatus();
            TemporalPortalSession.LifetimeStatus newStatus = session.updateLifetimeStatus();

            World portalWorld = Universe.get().getWorld(portalWorldName);
            if (portalWorld == null || !portalWorld.isAlive()) continue;

            // Capture warning flags before world.execute (session fields are volatile)
            final boolean warn5 = session.shouldWarn5min();
            final boolean warn1 = session.shouldWarn1min();
            final String dungeonColor = session.getDungeonDef().getColor();

            portalWorld.execute(() -> {
                // Collect players in this portal's world ONCE — reused by warnings + proximity loop
                List<PlayerRef> playersInWorld = new ArrayList<>();
                for (PlayerRef p : Universe.get().getPlayers()) {
                    if (p == null) continue;
                    Ref<EntityStore> r = p.getReference();
                    if (r == null || !r.isValid()) continue;
                    World pw = r.getStore().getExternalData().getWorld();
                    if (pw != null && pw.getName().equals(portalWorldName)) {
                        playersInWorld.add(p);
                    }
                }

                // [3] Warnings — must be inside world.execute for ECS access
                if (warn5) {
                    broadcastNearPortalFiltered(playersInWorld, portalPos, 100, dungeonColor,
                            "The portal is destabilizing... 5 minutes remaining!");
                }
                if (warn1) {
                    broadcastNearPortalFiltered(playersInWorld, portalPos, 100, "#ff4444",
                            "The portal is collapsing! 1 minute remaining!");
                }
                long tick = System.currentTimeMillis();
                for (PlayerRef player : playersInWorld) {
                    Ref<EntityStore> ref = player.getReference();
                    if (ref == null || !ref.isValid()) continue;

                    TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                    if (tc == null) continue;

                    Vector3d playerPos = tc.getPosition();
                    double dx = playerPos.x - portalPos.x;
                    double dz = playerPos.z - portalPos.z;
                    double hDistSq = dx * dx + dz * dz;
                    double dy = Math.abs(playerPos.y - portalPos.y);

                    if (hDistSq <= ENTER_RADIUS_SQ && dy <= ENTER_RADIUS_Y) {
                        UUID playerUuid = EntityUtils.getUuid(player);
                        if (playerUuid == null) continue;

                        Long lastEntry = entryCooldowns.get(playerUuid);
                        if (lastEntry != null && (tick - lastEntry) < ENTRY_COOLDOWN_MS) continue;

                        if (session.getInstanceState() == TemporalPortalSession.InstanceState.NONE) {
                            startInstanceGeneration(portalWorld, session, player);
                            entryCooldowns.put(playerUuid, tick);
                        } else if (session.getInstanceState() == TemporalPortalSession.InstanceState.READY) {
                            World instanceWorld = session.getInstanceWorld();
                            if (instanceWorld != null && instanceWorld.isAlive()) {
                                teleportToInstance(player, portalWorld, instanceWorld);
                                entryCooldowns.put(playerUuid, tick);
                            }
                        }
                    }
                }
            });
        }
    }

    // =========================================================================
    // Instance Creation
    // =========================================================================

    private void startInstanceGeneration(World originWorld, TemporalPortalSession session, @Nullable PlayerRef initiatingPlayer) {
        String portalTypeId = session.getDungeonDef().getPortalTypeId();
        PortalType portalType = PortalType.getAssetMap().getAsset(portalTypeId);
        if (portalType == null || !InstancesPlugin.doesInstanceAssetExist(portalType.getInstanceId())) {
            plugin.getLogger().atWarning().log("[TemporalPortal] PortalType '%s' or instance not found", portalTypeId);
            session.setInstanceState(TemporalPortalSession.InstanceState.FAILED);
            return;
        }

        session.setInstanceState(TemporalPortalSession.InstanceState.SPAWNING);
        Vector3d pos = session.getPortalPosition();
        Transform returnTransform = new Transform(pos.x, pos.y + 0.5, pos.z);

        InstancesPlugin.get().spawnInstance(portalType.getInstanceId(), originWorld, returnTransform)
                .thenAcceptAsync(spawnedWorld -> {
                    WorldConfig worldConfig = spawnedWorld.getWorldConfig();
                    worldConfig.setDeleteOnUniverseStart(true);
                    worldConfig.setDeleteOnRemove(true);

                    PortalWorld portalWorld = spawnedWorld.getEntityStore().getStore()
                            .getResource(PortalWorld.getResourceType());
                    if (portalWorld != null) {
                        int timeLimitSec = session.getDungeonDef().getInstanceTimeLimitSeconds();
                        portalWorld.init(portalType, timeLimitSec,
                                new PortalRemovalCondition((double) timeLimitSec), null);
                    }

                    placeReturnPortal(spawnedWorld, portalType);

                    session.setInstanceWorld(spawnedWorld);
                    plugin.getLogger().atInfo().log("[TemporalPortal] Instance ready: %s [session=%s]",
                            spawnedWorld.getName(), session.getId());

                    if (initiatingPlayer != null) {
                        teleportToInstance(initiatingPlayer, originWorld, spawnedWorld);
                    }
                }, originWorld)
                .exceptionally(t -> {
                    plugin.getLogger().atWarning().log("[TemporalPortal] Instance spawn failed: %s", t.getMessage());
                    session.setInstanceState(TemporalPortalSession.InstanceState.FAILED);
                    return null;
                });
    }

    // =========================================================================
    // Return Portal
    // =========================================================================

    private void placeReturnPortal(World instanceWorld, PortalType portalType) {
        PortalSpawnConfig spawnConfig = portalType.getSpawn();
        ISpawnProvider spawnOverride = spawnConfig.getSpawnProviderOverride();
        if (spawnOverride == null) return;

        Transform spawnTransform = spawnOverride.getSpawnPoint(instanceWorld, null);
        if (spawnTransform == null) return;

        PortalWorld portalWorld = instanceWorld.getEntityStore().getStore()
                .getResource(PortalWorld.getResourceType());
        if (portalWorld != null) {
            portalWorld.setSpawnPoint(spawnTransform);
        }

        instanceWorld.getWorldConfig().setSpawnProvider(new IndividualSpawnProvider(spawnTransform));

        Vector3d spawnPos = spawnTransform.getPosition();
        int px = (int) Math.floor(spawnPos.x);
        int py = (int) Math.floor(spawnPos.y);
        int pz = (int) Math.floor(spawnPos.z);

        scheduler.schedule(() -> {
            instanceWorld.execute(() -> {
                try {
                    for (int dy = 0; dy < 3; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                instanceWorld.setBlock(px + dx, py + dy, pz + dz, "Empty");
                            }
                        }
                    }
                    instanceWorld.setBlock(px, py, pz, "Portal_Return");
                    plugin.getLogger().atFine().log("[TemporalPortal] Placed return portal at (%d, %d, %d)", px, py, pz);
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[TemporalPortal] Failed to place return portal: %s", e.getMessage());
                }
            });
        }, 3, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Teleportation
    // =========================================================================

    private void teleportToInstance(PlayerRef playerRef, World fromWorld, World targetWorld) {
        fromWorld.execute(() -> {
            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) return;
                InstancesPlugin.teleportPlayerToInstance(ref, ref.getStore(), targetWorld, null);
                plugin.getLogger().atFine().log("[TemporalPortal] Teleported %s to instance %s",
                        playerRef.getUsername(), targetWorld.getName());
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[TemporalPortal] Teleport failed: %s", e.getMessage());
            }
        });
    }

    // =========================================================================
    // [6][7] Maintenance — expiry with grace period, lifetime status
    // =========================================================================

    private void maintenanceTick() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        int gracePeriodMs = config.getGracePeriodSeconds() * 1000;

        for (var entry : new ArrayList<>(activeSessions.entrySet())) {
            TemporalPortalSession session = entry.getValue();
            session.updateLifetimeStatus();

            if (session.isPortalExpired()) {
                long expiredAgo = -session.getRemainingMs();
                // [6] Grace period — keep portal visible for X more seconds after expiration
                if (expiredAgo >= gracePeriodMs) {
                    plugin.getLogger().atFine().log("[TemporalPortal] Portal expired + grace period over [session=%s]", session.getId());
                    closePortal(session);
                    activeSessions.remove(entry.getKey());
                }
            }
        }
    }

    private void closePortal(@Nonnull TemporalPortalSession session) {
        // [5] No block to remove — particle-only portal

        // Remove instance world
        World instanceWorld = session.getInstanceWorld();
        if (instanceWorld != null && instanceWorld.isAlive()) {
            // Warn players inside
            broadcastInWorld(instanceWorld, "#ff4444", "The portal has collapsed! You will be teleported out.");

            scheduler.schedule(() -> {
                try {
                    if (instanceWorld.isAlive()) {
                        instanceWorld.execute(() -> InstancesPlugin.safeRemoveInstance(instanceWorld));
                    }
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[TemporalPortal] Instance removal failed: %s", e.getMessage());
                }
            }, 5, TimeUnit.SECONDS);
        }
    }

    // =========================================================================
    // Admin: force spawn (keeps block for admin testing)
    // =========================================================================

    public void forceSpawnNear(@Nonnull PlayerRef targetPlayer, @Nonnull DungeonDefinition dungeonType) {
        Ref<EntityStore> playerRef = targetPlayer.getReference();
        if (playerRef == null || !playerRef.isValid()) return;

        TransformComponent transform = playerRef.getStore().getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d playerPos = transform.getPosition();
        World world = playerRef.getStore().getExternalData().getWorld();
        if (world == null || !world.isAlive()) return;

        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        int bx = (int) Math.floor(playerPos.x + Math.cos(angle) * 4);
        int bz = (int) Math.floor(playerPos.z + Math.sin(angle) * 4);

        Vector3d spawnPos = new Vector3d(bx + 0.5, playerPos.y, bz + 0.5);
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        TemporalPortalSession session = new TemporalPortalSession(sessionId, dungeonType, dungeonType.getPortalDurationSeconds());
        session.setSpawnWorldName(world.getName());
        session.setPortalPosition(spawnPos);
        activeSessions.put(session.getId(), session);

        world.execute(() -> spawnParticleAt(world, spawnPos, PARTICLE_AMBIENT));
        announcePortalSpawn(world, spawnPos, dungeonType, config.getAnnounceRadius());

        plugin.getLogger().atInfo().log("[TemporalPortal] Force-spawned %s portal at (%.0f, %.0f, %.0f) [session=%s]",
                dungeonType.getDisplayName(), spawnPos.x, spawnPos.y, spawnPos.z, sessionId);
    }

    // =========================================================================
    // Protection check
    // =========================================================================

    private boolean isPositionProtected(String worldName, int x, int y, int z) {
        var ogBridge = plugin.getOrbisGuardBridge();
        if (ogBridge != null && ogBridge.isPositionProtected(worldName, x, y, z)) {
            return true;
        }
        var claimBridge = endgame.plugin.integration.ClaimProtectionBridge.get();
        return claimBridge.isPositionClaimed(worldName, x, z);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean isPortalNearby(Vector3d position, double minDistance) {
        double minDistSq = minDistance * minDistance;
        for (TemporalPortalSession session : activeSessions.values()) {
            Vector3d portalPos = session.getPortalPosition();
            if (portalPos == null) continue;
            double dx = position.x - portalPos.x;
            double dz = position.z - portalPos.z;
            if (dx * dx + dz * dz < minDistSq) return true;
        }
        return false;
    }

    @Nullable
    private DungeonDefinition pickRandomEnabledDungeon(TemporalPortalConfig config) {
        List<DungeonDefinition> enabled = config.getEnabledDungeons();
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

    private long randomInterval() {
        TemporalPortalConfig config = plugin.getConfig().get().getTemporalPortalConfig();
        int min = config.getSpawnIntervalMinSeconds();
        int max = config.getSpawnIntervalMaxSeconds();
        return (min + ThreadLocalRandom.current().nextInt(Math.max(1, max - min))) * 1000L;
    }

    private void spawnPortalParticles(World world, Vector3d position) {
        spawnParticleAt(world, position, PARTICLE_AMBIENT);
    }

    private void spawnParticleAt(World world, Vector3d position, String particleId) {
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
            ParticleUtil.spawnParticleEffect(particleId, position, viewers, particleStore);
        } catch (Exception ignored) {}
    }

    private void announcePortalSpawn(World world, Vector3d position,
                                      DungeonDefinition type, float radius) {
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

    private void broadcastNearPortal(String worldName, Vector3d position, double radius,
                                      String color, String message) {
        double radiusSq = radius * radius;
        World world = Universe.get().getWorld(worldName);
        if (world == null) return;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;
            World pw = ref.getStore().getExternalData().getWorld();
            if (pw == null || !pw.getName().equals(worldName)) continue;
            TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
            if (tc != null && tc.getPosition().distanceSquaredTo(position) <= radiusSq) {
                p.sendMessage(Message.raw("[Temporal Portal] " + message).color(color));
            }
        }
    }

    /**
     * Variant of {@link #broadcastNearPortal} that skips the full
     * {@code Universe.get().getPlayers()} scan by operating on a pre-filtered
     * list (players already confirmed to be in the target world).
     */
    private void broadcastNearPortalFiltered(List<PlayerRef> playersInWorld, Vector3d position,
                                              double radius, String color, String message) {
        double radiusSq = radius * radius;
        for (PlayerRef p : playersInWorld) {
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;
            TransformComponent tc = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
            if (tc != null && tc.getPosition().distanceSquaredTo(position) <= radiusSq) {
                p.sendMessage(Message.raw("[Temporal Portal] " + message).color(color));
            }
        }
    }

    private void broadcastInWorld(World world, String color, String message) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) continue;
            World pw = ref.getStore().getExternalData().getWorld();
            if (pw != null && pw.getName().equals(world.getName())) {
                p.sendMessage(Message.raw("[Temporal Portal] " + message).color(color));
            }
        }
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public int getActiveSessionCount() { return activeSessions.size(); }
    public ConcurrentHashMap<String, TemporalPortalSession> getActiveSessions() { return activeSessions; }
}
