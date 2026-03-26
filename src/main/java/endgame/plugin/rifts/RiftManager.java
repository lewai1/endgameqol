package endgame.plugin.rifts;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.buuz135.simpleclaims.claim.ClaimManager;
import com.buuz135.simpleclaims.claim.chunk.ChunkInfo;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.portalworld.PortalType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.meta.state.BlockMapMarkersResource;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3i;
import endgame.plugin.rifts.ActiveRift.InstanceState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class RiftManager {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final double ENTER_RADIUS = 2.0F;
    private static final double ENTER_RADIUS_SQ = 4.0F;
    private static final long TELEPORT_COOLDOWN_MS = 5000L;
    private final RiftConfig config;
    private final Map<String, ActiveRift> activePortals = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();
    private final Map<String, java.io.File> instanceLevelOverrideFiles = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> returnPortalKeepAlive = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private volatile boolean running = false;
    private ScheduledFuture<?> proximityTask;

    public RiftManager(@Nonnull RiftConfig config) {
        this.config = config;
    }

    public Map<String, ActiveRift> getActivePortals() {
        return Collections.unmodifiableMap(this.activePortals);
    }

    public int activeCount() {
        return this.activePortals.size();
    }

    public void start() {
        if (!this.running) {
            this.running = true;
            loadPastPortals();
            logMemory("STARTUP");

            // Delay stale cleanup by 15 seconds — at startup, Universe hasn't loaded worlds yet
            // (we saw 0 worlds in logs). The delay gives the engine time to finish loading.
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                logAllWorlds("DELAYED-CLEANUP");
                cleanupStaleInstances();
                logMemory("POST-CLEANUP");
            }, 15, TimeUnit.SECONDS);

            this.scheduleNextRoll();
            this.proximityTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::proximityTick, 1L, 500L, TimeUnit.MILLISECONDS);
            ((HytaleLogger.Api)LOGGER.atInfo()).log("[HyRifts] Spawn loop started (interval=%ds, chance=%.0f%%)", this.config.rollIntervalSeconds, this.config.rollChance * 100.0F);
        }
    }

    public void stop() {
        this.running = false;
        if (this.proximityTask != null) {
            this.proximityTask.cancel(false);
            this.proximityTask = null;
        }
        savePastPortals();
        this.closeAllPortals();
    }

    // ── Debug and Cleanup Helpers ──────────────────────────────────────────────

    private void logMemory(String tag) {
        Runtime rt = Runtime.getRuntime();
        long free = rt.freeMemory() / (1024 * 1024);
        long total = rt.totalMemory() / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        long used = total - free;
        ((HytaleLogger.Api) LOGGER.atInfo()).log(
                "[DEBUG-MEM] [%s] used=%dMB free=%dMB total=%dMB max=%dMB (%.1f%% used)",
                tag, used, free, total, max, (used * 100.0) / max);
    }

    private void logAllWorlds(String tag) {
        try {
            Map<?, ?> worlds = Universe.get().getWorlds();
            ((HytaleLogger.Api) LOGGER.atInfo()).log("[DEBUG-WORLDS] [%s] Total worlds: %d", tag, worlds.size());
            for (Object entry : worlds.entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) entry;
                World w = (World) e.getValue();
                String generatorInfo = "unknown";
                try {
                    var wc = w.getWorldConfig();
                    var sp = wc != null ? wc.getSpawnProvider() : null;
                    generatorInfo = String.format("spawnProvider=%s, deleteOnStart=%s",
                            sp != null ? sp.getClass().getSimpleName() : "NULL",
                            wc != null ? wc.isDeleteOnUniverseStart() : "?");
                } catch (Exception ignored) {}
                ((HytaleLogger.Api) LOGGER.atInfo()).log(
                        "[DEBUG-WORLDS]   - %s (alive=%s) %s",
                        w.getName(), w.isAlive(), generatorInfo);
            }
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("[DEBUG-WORLDS] [%s] Failed to enumerate worlds: %s", tag, e.getMessage());
        }
    }

    private void cleanupStaleInstances() {
        // 1) Request removal of in-memory stale instance worlds
        try {
            Map<?, ?> worlds = Universe.get().getWorlds();
            int cleaned = 0;
            for (Object entry : worlds.entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) entry;
                World w = (World) e.getValue();
                if (w.getName().startsWith("instance-") && w.isAlive()) {
                    boolean tracked = false;
                    for (ActiveRift ap : activePortals.values()) {
                        if (ap.instanceWorld() != null && ap.instanceWorld().getName().equals(w.getName())) {
                            tracked = true;
                            break;
                        }
                    }
                    if (!tracked) {
                        ((HytaleLogger.Api) LOGGER.atWarning()).log(
                                "[HyRifts] Stale instance: %s — requesting removal", w.getName());
                        try {
                            w.execute(() -> InstancesPlugin.safeRemoveInstance(w));
                            cleaned++;
                        } catch (Exception ex) {
                            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                                    "[HyRifts] Failed to remove stale instance %s: %s", w.getName(), ex.getMessage());
                        }
                    }
                }
            }
            if (cleaned > 0) {
                ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Requested removal of %d stale instance world(s)", cleaned);
            }
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Error during stale instance cleanup: %s", e.getMessage());
        }

        // 2) Delete stale instance directories from disk so they don't reload on next boot.
        //    The engine's RemovalSystem often fails (Thread.stop UnsupportedOperationException),
        //    so this is the fallback that prevents accumulation across server restarts.
        deleteStaleInstanceDirectories();
    }

    private void deleteStaleInstanceDirectories() {
        try {
            Path universePath = Universe.get().getPath();
            // World directories live directly under the universe save path
            if (!Files.isDirectory(universePath)) return;

            int deleted = 0;
            try (Stream<Path> entries = Files.list(universePath)) {
                List<Path> instanceDirs = entries
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("instance-"))
                        .toList();

                for (Path dir : instanceDirs) {
                    String dirName = dir.getFileName().toString();
                    // Don't delete directories for instances we're actively tracking
                    boolean tracked = false;
                    for (ActiveRift ap : activePortals.values()) {
                        if (ap.instanceWorld() != null && ap.instanceWorld().getName().equals(dirName)) {
                            tracked = true;
                            break;
                        }
                    }
                    if (!tracked) {
                        try {
                            deleteDirectoryRecursive(dir);
                            deleted++;
                            ((HytaleLogger.Api) LOGGER.atInfo()).log(
                                    "[HyRifts] Deleted stale instance directory: %s", dirName);
                        } catch (Exception ex) {
                            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                                    "[HyRifts] Could not delete instance directory %s: %s", dirName, ex.getMessage());
                        }
                    }
                }
            }
            if (deleted > 0) {
                ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Deleted %d stale instance directory(ies) from disk", deleted);
            }
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Error cleaning instance directories: %s", e.getMessage());
        }
    }

    private static void deleteDirectoryRecursive(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
    }

    // ───────────────────────────────────────────────────────────────────────────

    private void scheduleNextRoll() {
        if (this.running) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(this::diceRoll, (long)this.config.rollIntervalSeconds, TimeUnit.SECONDS);
        }
    }

    @SuppressWarnings("deprecation")
    private void proximityTick() {
        if (this.running && !this.activePortals.isEmpty()) {
            try {
                List<PlayerRef> players = Universe.get().getPlayers();
                if (players == null || players.isEmpty()) return;

                long now = System.currentTimeMillis();

                for(PlayerRef player : players) {
                    try {
                        UUID worldUuid = player.getWorldUuid();
                        if (worldUuid == null) continue;

                        World playerWorld = Universe.get().getWorld(worldUuid);
                        if (playerWorld == null) continue;

                        Transform transform = player.getTransform();
                        if (transform == null) continue;

                        Vector3d pos = transform.getPosition();
                        if (pos == null) continue;

                        UUID playerUuid = player.getUuid();
                        Long lastTp = this.teleportCooldowns.get(playerUuid);
                        boolean canTeleport = (lastTp == null || now - lastTp >= 5000L);

                        for(ActiveRift portal : this.activePortals.values()) {

                            // ==========================================
                            // ENTRANCE: Overworld -> Dungeon
                            // ==========================================
                            if (portal.worldName().equals(playerWorld.getName())) {
                                double portalX = portal.position().getX() + 0.5;
                                double portalY = portal.position().getY();
                                double portalZ = portal.position().getZ() + 0.5;

                                double dx = pos.getX() - portalX;
                                double dy = pos.getY() - portalY;
                                double dz = pos.getZ() - portalZ;
                                double distSq = dx * dx + dy * dy + dz * dz;

                                if (distSq <= 4096.0 && (now % 1500 < 500) && portal.instanceState() != InstanceState.FAILED) {
                                    playerWorld.execute(() -> {
                                        try {
                                            Vector3d particlePos = new Vector3d(portalX, portalY + 3.0, portalZ);
                                            List<Ref<EntityStore>> target = Collections.singletonList(player.getReference());
                                            com.hypixel.hytale.server.core.universe.world.ParticleUtil.spawnParticleEffect(
                                                    "MagicPortal", particlePos, target, playerWorld.getEntityStore().getStore()
                                            );
                                        } catch (Exception ignored) {}
                                    });
                                }

                                // Pre-generate: start instance when player is within 20 blocks
                                if (distSq <= 400.0 && portal.instanceState() == InstanceState.NONE) {
                                    portal.setInstanceState(InstanceState.SPAWNING);
                                    ((HytaleLogger.Api) LOGGER.atInfo()).log(
                                            "[HyRifts] Pre-generating instance for portal %s (player within %.0f blocks)",
                                            portal.portalId(), Math.sqrt(distSq));
                                    playerWorld.execute(() -> this.startInstanceGeneration(playerWorld, portal, player));
                                }

                                if (canTeleport && distSq <= 4.0F) {
                                    ActiveRift.InstanceState state = portal.instanceState();

                                    if (state == InstanceState.READY) {
                                        World instanceWorld = portal.instanceWorld();
                                        if (instanceWorld != null && instanceWorld.isAlive()) {
                                            this.teleportCooldowns.put(playerUuid, now + 10000L);
                                            this.teleportToInstance(player, playerWorld, instanceWorld, portal);
                                        }
                                    } else if (state == InstanceState.SPAWNING) {
                                        this.teleportCooldowns.put(playerUuid, now);
                                        try { player.sendMessage(Message.raw("The rift is stabilizing...").color("#b388ff")); } catch (Exception ignored) {}
                                    }
                                }
                            }
                            // ==========================================
                            // EXIT: Dungeon -> Overworld
                            // ==========================================
                            else if (portal.instanceState() == InstanceState.READY && portal.instanceWorld() != null) {
                                if (playerWorld.getName().equals(portal.instanceWorld().getName())) {

                                    {
                                        // Proximity exit — works for all dungeons (fixed + dynamic)
                                        Vector3d exitPos = portal.returnPortalPos();
                                        RiftDungeonType dt = portal.dungeonType();
                                        double exitX = exitPos != null ? exitPos.getX() : dt.baseX;
                                        double exitY = exitPos != null ? exitPos.getY() : dt.baseY;
                                        double exitZ = exitPos != null ? exitPos.getZ() : dt.baseZ;
                                        double dx = pos.getX() - exitX;
                                        double dz = pos.getZ() - exitZ;
                                        double dy = pos.getY() - exitY;

                                        if (canTeleport && (dx * dx + dz * dz) <= 9.0 && Math.abs(dy) <= 10.0) {
                                            this.teleportCooldowns.put(playerUuid, now);
                                            final UUID capturedWorldUuid = worldUuid;
                                            playerWorld.execute(() -> {
                                                try {
                                                    // Guard: player may have already exited via vanilla portal
                                                    if (!capturedWorldUuid.equals(player.getWorldUuid())) return;
                                                    InstancesPlugin.exitInstance(
                                                            player.getReference(),
                                                            player.getReference().getStore()
                                                    );
                                                    ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Proximity exit: teleported player out of %s", portal.portalId());
                                                } catch (Exception e) {
                                                    ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Exit TP failed: %s", e.getMessage());
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (now % 10000L < 600L) {
                    this.teleportCooldowns.entrySet().removeIf((ex) -> now - ex.getValue() > 30000L);
                }
            } catch (Exception e) {
                ((HytaleLogger.Api)LOGGER.atWarning()).log("[HyRifts] Error in proximity tick: %s", e.getMessage());
            }
        }
    }

    private void teleportToInstance(PlayerRef playerRef, World fromWorld, World targetWorld, ActiveRift portal) {
        // Set cooldown BEFORE teleport so exit proximity check doesn't fire immediately
        teleportCooldowns.put(playerRef.getUuid(), System.currentTimeMillis());

        fromWorld.execute(() -> {
            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) return;

                // Pass null for overrideReturn — the WorldReturnPoint on the InstanceWorldConfig
                // (set during spawnInstance) already points back to the rift. Passing a transform
                // here would OVERWRITE the player's return point with wrong coordinates.
                InstancesPlugin.teleportPlayerToInstance(ref, ref.getStore(), targetWorld, null);
                ((HytaleLogger.Api)LOGGER.atInfo()).log("[HyRifts] Teleported %s into portal %s (%s)", playerRef.getUuid(), portal.portalId(), portal.dungeonType().displayName);
            } catch (Exception e) {
                ((HytaleLogger.Api)LOGGER.atWarning()).log("[HyRifts] Failed to teleport player: %s", e.getMessage());
            }
        });
    }

    private void diceRoll() {
        if (this.running) {
            try {
                this.cleanupExpired();
                if (this.activePortals.size() >= this.config.maxActivePortals) {
                    ((HytaleLogger.Api)LOGGER.atFine()).log("[HyRifts] Max portals active (%d), skipping roll", this.activePortals.size());
                } else if (this.random.nextDouble() < this.config.rollChance) {
                    int count = this.config.minPortalsPerRoll == this.config.maxPortalsPerRoll ? this.config.minPortalsPerRoll : this.config.minPortalsPerRoll + this.random.nextInt(this.config.maxPortalsPerRoll - this.config.minPortalsPerRoll + 1);
                    int remaining = this.config.maxActivePortals - this.activePortals.size();
                    count = Math.min(count, remaining);
                    ((HytaleLogger.Api)LOGGER.atInfo()).log("[HyRifts] Roll succeeded — spawning %d portal(s)", count);

                    for(int i = 0; i < count; ++i) {
                        this.spawnRandomPortal();
                    }
                } else {
                    ((HytaleLogger.Api)LOGGER.atFine()).log("[HyRifts] Roll failed (%.0f%% chance)", this.config.rollChance * 100.0F);
                }
            } catch (Exception e) {
                ((HytaleLogger.Api)LOGGER.atWarning()).log("[HyRifts] Error during dice roll: %s", e.getMessage());
            } finally {
                this.scheduleNextRoll();
            }
        }
    }

    private static final double PORTAL_CLUSTER_RADIUS = 700.0;
    private static final double PORTAL_CLUSTER_RADIUS_SQ = PORTAL_CLUSTER_RADIUS * PORTAL_CLUSTER_RADIUS;

    // Remembers where past portals were so new ones can reuse the spot (persisted across restarts)
    private record PastPortal(String worldName, Vector3d position, RiftDungeonType dungeonType) {}
    private final List<PastPortal> pastPortalLocations = Collections.synchronizedList(new ArrayList<>());
    private static final Path PORTALS_FILE = RiftConfig.CONFIG_DIR.resolve("portals.json");

    private void savePastPortals() {
        try {
            Files.createDirectories(RiftConfig.CONFIG_DIR);
            StringBuilder sb = new StringBuilder("[\n");
            synchronized (pastPortalLocations) {
                for (int i = 0; i < pastPortalLocations.size(); i++) {
                    PastPortal p = pastPortalLocations.get(i);
                    sb.append(String.format(
                            "  {\"world\":\"%s\",\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"dungeon\":\"%s\"}",
                            p.worldName, p.position.getX(), p.position.getY(), p.position.getZ(), p.dungeonType.id));
                    if (i < pastPortalLocations.size() - 1) sb.append(",");
                    sb.append("\n");
                }
            }
            sb.append("]");
            Files.writeString(PORTALS_FILE, sb.toString());
            ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Saved %d past portal locations", pastPortalLocations.size());
        } catch (IOException e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Failed to save portal locations: %s", e.getMessage());
        }
    }

    private void loadPastPortals() {
        if (!Files.exists(PORTALS_FILE)) return;
        try {
            String content = Files.readString(PORTALS_FILE);
            // Simple JSON array parsing — each entry is {"world":"...","x":...,"y":...,"z":...,"dungeon":"..."}
            int count = 0;
            int idx = 0;
            while ((idx = content.indexOf("{", idx)) != -1) {
                int end = content.indexOf("}", idx);
                if (end == -1) break;
                String entry = content.substring(idx, end + 1);
                idx = end + 1;

                String worldName = extractJsonString(entry, "world");
                String dungeonId = extractJsonString(entry, "dungeon");
                double x = extractJsonDouble(entry, "x");
                double y = extractJsonDouble(entry, "y");
                double z = extractJsonDouble(entry, "z");

                if (worldName == null || dungeonId == null) continue;
                RiftDungeonType dtype = RiftDungeonType.fromId(dungeonId);
                if (dtype == null) continue;

                pastPortalLocations.add(new PastPortal(worldName, new Vector3d(x, y, z), dtype));
                count++;
            }
            ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Loaded %d past portal locations from disk", count);
        } catch (IOException e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Failed to load portal locations: %s", e.getMessage());
        }
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
    }

    private static double extractJsonDouble(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return 0;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) end++;
        try { return Double.parseDouble(json.substring(start, end)); }
        catch (NumberFormatException e) { return 0; }
    }

    private void spawnRandomPortal() {
        RiftRank rank = this.rollRank();
        RiftRankSettings rs = this.config.getRankSettings(rank);

        // 1. Try to reopen a past portal location (can spawn near players)
        SpawnCandidate candidate = tryReusePastPortal();
        RiftDungeonType dungeonType;
        int mobLevel;

        if (candidate != null) {
            // Past portal reuse — find which past portal matched
            PastPortal pastMatch = findNearbyPastPortal(candidate.position, candidate.world.getName());
            dungeonType = pastMatch != null ? pastMatch.dungeonType : RiftDungeonType.values()[this.random.nextInt(RiftDungeonType.values().length)];
            mobLevel = this.rollMobLevel(rs);
            ((HytaleLogger.Api) LOGGER.atInfo()).log(
                    "[HyRifts] Reopening past %s rift at %.0f, %.0f, %.0f",
                    dungeonType.displayName, candidate.position.getX(), candidate.position.getY(), candidate.position.getZ());
        } else {
            // 2. No past portal available — find a NEW location (must be far from players)
            candidate = this.findSpawnLocation();
            if (candidate == null) {
                ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Could not find valid spawn location after %d attempts", this.config.maxPlacementAttempts);
                return;
            }

            // Avoid spawning too close to an active portal
            if (isNearActivePortal(candidate.position, candidate.world.getName())) {
                candidate = this.findSpawnLocation();
                if (candidate == null || isNearActivePortal(candidate.position, candidate.world.getName())) {
                    ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Portal spawn cancelled — too close to active portal after reroll");
                    return;
                }
            }

            RiftDungeonType[] dungeonTypes = RiftDungeonType.values();
            dungeonType = dungeonTypes[this.random.nextInt(dungeonTypes.length)];
            mobLevel = this.rollMobLevel(rs);
        }

        String portalId = UUID.randomUUID().toString().substring(0, 8);
        ActiveRift portal = new ActiveRift(portalId, dungeonType, rank, rs, mobLevel, candidate.world.getName(), candidate.position, System.currentTimeMillis(), this.config.portalLifetimeSeconds);
        this.registerLevelOverride(portal, candidate.world.getName());

        this.activePortals.put(portalId, portal);
        this.spawnPortal(candidate.world, candidate.position, portal);

        ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Spawned %s portal [%s] at %.0f, %.0f, %.0f in %s (Lv.%d %s)", dungeonType.displayName, portalId, candidate.position.getX(), candidate.position.getY(), candidate.position.getZ(), candidate.world.getName(), mobLevel, rank.label);
        if (this.config.announcePortals) {
            this.broadcastMessage("A rift in reality has opened", "#b388ff");
        }

        schedulePortalExpiry(portalId, this.config.portalLifetimeSeconds);
    }

    /**
     * Try to find a past portal location that can be reused. Past portals CAN spawn
     * near players (they already existed before). Returns null if no past portal is
     * available or all are occupied by active portals.
     */
    @Nullable
    private SpawnCandidate tryReusePastPortal() {
        if (pastPortalLocations.isEmpty()) return null;

        // Shuffle to avoid always picking the same past portal
        List<PastPortal> shuffled;
        synchronized (pastPortalLocations) {
            shuffled = new ArrayList<>(pastPortalLocations);
        }
        Collections.shuffle(shuffled, random);

        for (PastPortal past : shuffled) {
            // Skip if there's already an active portal at this location
            if (isNearActivePortal(past.position, past.worldName)) continue;

            // Find the world this past portal was in
            World world = null;
            try {
                for (World w : Universe.get().getWorlds().values()) {
                    if (w.getName().equals(past.worldName)) {
                        world = w;
                        break;
                    }
                }
            } catch (Exception e) { continue; }

            if (world == null) continue;

            return new SpawnCandidate(world, past.position);
        }
        return null;
    }

    private boolean isNearActivePortal(Vector3d pos, String worldName) {
        for (ActiveRift p : this.activePortals.values()) {
            if (p.worldName().equals(worldName)) {
                double dx = pos.getX() - p.position().getX();
                double dz = pos.getZ() - p.position().getZ();
                if (dx * dx + dz * dz < PORTAL_CLUSTER_RADIUS_SQ) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private PastPortal findNearbyPastPortal(Vector3d pos, String worldName) {
        for (PastPortal past : pastPortalLocations) {
            if (past.worldName.equals(worldName)) {
                double dx = pos.getX() - past.position.getX();
                double dz = pos.getZ() - past.position.getZ();
                if (dx * dx + dz * dz < PORTAL_CLUSTER_RADIUS_SQ) {
                    return past;
                }
            }
        }
        return null;
    }

    private RiftRank rollRank() {
        int totalWeight = 0;
        for(RiftRank rank : RiftRank.values()) {
            totalWeight += this.config.getRankSettings(rank).weight;
        }
        if (totalWeight <= 0) return RiftRank.E;

        int roll = this.random.nextInt(totalWeight);
        int cumulative = 0;
        for(RiftRank rank : RiftRank.values()) {
            cumulative += this.config.getRankSettings(rank).weight;
            if (roll < cumulative) return rank;
        }
        return RiftRank.E;
    }

    private int rollMobLevel(RiftRankSettings rs) {
        int min = rs.minLevel;
        int max = rs.maxLevel;
        return min >= max ? min : min + this.random.nextInt(max - min + 1);
    }

    private void registerLevelOverride(ActiveRift portal, String worldName) {
        endgame.plugin.EndgameQoL plugin = endgame.plugin.EndgameQoL.getInstance();
        if (plugin == null || !plugin.isEndlessLevelingActive()) return;
        try {
            EndlessLevelingAPI.get().registerMobAreaLevelOverride(portal.levelOverrideId(), worldName, portal.position().getX(), portal.position().getZ(), this.config.mobLevelRadius, portal.mobLevel(), portal.mobLevel());
        } catch (Throwable e) {
            ((HytaleLogger.Api)LOGGER.atWarning()).log("[HyRifts] Failed to register mob level override: %s", e.getMessage());
        }
    }

    private void applyInstanceLevelOverride(ActiveRift portal, World spawnedWorld) {
        endgame.plugin.EndgameQoL plugin = endgame.plugin.EndgameQoL.getInstance();
        if (plugin == null || !plugin.isEndlessLevelingActive()) return;
        try {
            com.airijko.endlessleveling.EndlessLeveling el =
                com.airijko.endlessleveling.EndlessLeveling.getInstance();
            if (el == null) return;

            String worldName = spawnedWorld.getName();
            int level = portal.mobLevel();

            // Write a per-instance world settings file so EL uses FIXED level for this world.
            // An exact world name key scores higher than wildcard keys (e.g. "instance-*"),
            // so this entry overrides the default TIERED config for this specific instance.
            java.io.File folder = el.getFilesManager().getWorldSettingsFolder();
            String safeId = portal.portalId().replaceAll("[^a-zA-Z0-9_-]", "_");
            java.io.File overrideFile = new java.io.File(folder, "hyrift-" + safeId + ".json");

            String json = "{\"World_Overrides\":{\""
                + worldName + "\":{\"Level_Source\":{\"Mode\":\"FIXED\","
                + "\"Fixed_Level\":{\"Level\":\"" + level + "-" + level + "\"}}}}}";

            java.nio.file.Files.writeString(overrideFile.toPath(), json);
            instanceLevelOverrideFiles.put(portal.portalId(), overrideFile);

            // Reload EL world settings to pick up the new file
            el.getMobLevelingManager().reloadConfig();

            ((HytaleLogger.Api)LOGGER.atInfo()).log(
                "[HyRifts] Applied FIXED level=%d EL override for instance world '%s'", level, worldName);
        } catch (Throwable e) {
            ((HytaleLogger.Api)LOGGER.atWarning()).log(
                "[HyRifts] Failed to apply instance level override: %s", e.getMessage());
        }
    }

    @Nullable
    private SpawnCandidate findSpawnLocation() {
        List<PlayerRef> players;
        try {
            players = Universe.get().getPlayers();
        } catch (Exception var20) {
            return null;
        }

        if (players != null && !players.isEmpty()) {
            List<PlayerWorldPos> playerPositions = new ArrayList<>();

            for(PlayerRef player : players) {
                try {
                    UUID worldUuid = player.getWorldUuid();
                    if (worldUuid != null) {
                        World world = Universe.get().getWorld(worldUuid);
                        if (world != null) {
                            Transform transform = player.getTransform();
                            if (transform != null) {
                                Vector3d pos = transform.getPosition();
                                if (pos != null) {
                                    playerPositions.add(new PlayerWorldPos(player, world, pos));
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (playerPositions.isEmpty()) {
                return null;
            } else {
                for(int attempt = 0; attempt < this.config.maxPlacementAttempts; ++attempt) {
                    PlayerWorldPos anchor = playerPositions.get(this.random.nextInt(playerPositions.size()));
                    double angle = this.random.nextDouble() * 2.0F * Math.PI;
                    double dist = (double)this.config.minDistanceFromPlayers + this.random.nextDouble() * (double)(this.config.maxDistanceFromPlayers - this.config.minDistanceFromPlayers);
                    double x = anchor.position.getX() + Math.cos(angle) * dist;
                    double z = anchor.position.getZ() + Math.sin(angle) * dist;
                    World world = anchor.world;
                    int ix = (int)Math.floor(x);
                    int iz = (int)Math.floor(z);

                    try {
                        int surfaceY = this.findSurfaceY(world, ix, iz);
                        if (surfaceY >= 1) {
                            // FIXED: Use the floored integers (ix, iz) instead of the raw doubles (x, z)
                            Vector3d spawnPos = new Vector3d((double)ix, (double)surfaceY + 2.0, (double)iz);

                            if (!this.isTooCloseToAnyPlayer(spawnPos, playerPositions) && !this.isTooCloseToExistingPortal(spawnPos, world.getName()) && this.isSpawnAllowed(world, ix, iz)) {
                                return new SpawnCandidate(world, spawnPos);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return null;
            }
        } else {
            return null;
        }
    }

    private boolean isSpawnAllowed(World world, int blockX, int blockZ) {
        boolean var10000;
        switch (this.config.griefMode) {
            case ENABLED -> var10000 = true;
            case DISABLED -> var10000 = !this.hasPlayerBlocksNearby(world, blockX, blockZ);
            case CLAIMS_PROTECTION -> var10000 = !this.isInsideClaim(world, blockX, blockZ);
            default -> throw new MatchException(null, null);
        }
        return var10000;
    }

    private boolean isInsideClaim(World world, int blockX, int blockZ) {
        try {
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            ClaimManager claimManager = ClaimManager.getInstance();
            if (claimManager == null) {
                return false;
            } else {
                ChunkInfo chunkInfo = claimManager.getChunkRawCoords(world.getName(), chunkX, chunkZ);
                return chunkInfo != null && chunkInfo.getPartyOwner() != null;
            }
        } catch (Exception | NoClassDefFoundError var8) {
            return false;
        }
    }

    private boolean hasPlayerBlocksNearby(World world, int cx, int cz) {
        int r = 10;
        int surfaceY = this.findSurfaceY(world, cx, cz);
        if (surfaceY < 1) {
            return false;
        } else {
            for(int dx = -r; dx <= r; ++dx) {
                for(int dz = -r; dz <= r; ++dz) {
                    for(int dy = -3; dy <= 5; ++dy) {
                        try {
                            BlockType blockType = world.getBlockType(cx + dx, surfaceY + dy, cz + dz);
                            if (blockType != null) {
                                String blockId = blockType.getId();
                                if (blockId != null) {
                                    String lower = blockId.toLowerCase();
                                    for(String keyword : this.config.griefProtectedBlocks) {
                                        if (lower.contains(keyword)) {
                                            return true;
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
            return false;
        }
    }

    private int findSurfaceY(World world, int x, int z) {
        try {
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
            if (chunk == null) return -1;
            int topY = chunk.getHeight(x, z);
            if (topY <= 0) return -1;
            // Scan downward past tree canopy and vegetation to find solid ground
            for (int y = topY; y >= 1; y--) {
                BlockType bt = chunk.getBlockType(x, y, z);
                if (bt == null) continue;
                String id = bt.getId();
                if (id == null) continue;
                String lower = id.toLowerCase();
                if (lower.contains("empty")) continue;
                if (lower.contains("water") || lower.contains("lava")) return -1;
                if (isVegetationBlock(lower)) continue;
                return y;
            }
            return -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static boolean isVegetationBlock(String idLower) {
        return idLower.contains("leaf") || idLower.contains("leaves")
            || idLower.contains("log") || idLower.contains("vine")
            || idLower.contains("bush") || idLower.contains("shrub")
            || idLower.contains("fern") || idLower.contains("flower")
            || idLower.contains("foliage") || idLower.contains("mushroom")
            || idLower.contains("cactus") || idLower.contains("bamboo")
            || idLower.contains("reed");
    }

    private boolean isTooCloseToAnyPlayer(Vector3d pos, List<PlayerWorldPos> players) {
        double minDistSq = (double)this.config.minDistanceFromPlayers * (double)this.config.minDistanceFromPlayers;
        for(PlayerWorldPos pwp : players) {
            double dx = pos.getX() - pwp.position.getX();
            double dz = pos.getZ() - pwp.position.getZ();
            if (dx * dx + dz * dz < minDistSq) {
                return true;
            }
        }
        return false;
    }

    private boolean isTooCloseToExistingPortal(Vector3d pos, String worldName) {
        double minDistSq = this.config.mobLevelRadius * this.config.mobLevelRadius;
        for(ActiveRift p : this.activePortals.values()) {
            if (p.worldName().equals(worldName)) {
                double dx = pos.getX() - p.position().getX();
                double dz = pos.getZ() - p.position().getZ();
                if (dx * dx + dz * dz < minDistSq) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final String PILLAR_BLOCK = "Rock_Chalk";

    private void spawnPortal(World originWorld, Vector3d position, ActiveRift portal) {
        if (portal.instanceState() != InstanceState.NONE) return;

        int x = (int)Math.floor(position.getX());
        int y = (int)Math.floor(position.getY());
        int z = (int)Math.floor(position.getZ());

        originWorld.execute(() -> {
            try {
                // 1. Clear the surrounding area with a 1-block padding (Except the floor)
                // Width: Pillars are at x-4 and x+4. Padded -> dx = -5 to +5
                // Depth: Portal is z to z+1. Padded -> dz = -1 to +2
                // Height: Pillars are 25 high. Padded -> dy = 0 to 25
                for (int dy = 0; dy <= 51; dy++) {
                    for (int dx = -5; dx <= 5; dx++) {
                        for (int dz = -1; dz <= 2; dz++) {
                            originWorld.setBlock(x + dx, y + dy, z + dz, "Empty");
                        }
                    }
                }

                // 2. Build the foundation right under the portal structure (y - 1)
                // Spans from the left pillar to the right pillar, 2 blocks deep
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -1; dz <= 2; dz++) {
                        originWorld.setBlock(x + dx, y - 1, z + dz, PILLAR_BLOCK);
                    }
                }

                // 3. Build the actual side pillars, up to height 25
                for (int dy = 0; dy < 50; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        originWorld.setBlock(x - 4, y + dy, z + dz, PILLAR_BLOCK);
                        originWorld.setBlock(x + 4, y + dy, z + dz, PILLAR_BLOCK);
                    }
                }
            } catch (Exception e) {
                ((HytaleLogger.Api)LOGGER.atWarning()).log("[HyRifts] Failed to clear portal area: %s", e.getMessage());
            }
        });
    }

    private void startInstanceGeneration(World originWorld, ActiveRift portal, @Nullable PlayerRef initiatingPlayer) {
        String portalTypeId = portal.dungeonType().portalTypeId;
        PortalType portalType = (PortalType) PortalType.getAssetMap().getAsset(portalTypeId);

        if (portalType == null || !InstancesPlugin.doesInstanceAssetExist(portalType.getInstanceId())) {
            portal.setInstanceState(InstanceState.FAILED);
            return;
        }

        String instanceId = portalType.getInstanceId();
        Vector3d pos = portal.position();

        // 1. NATIVE EXIT FIX: Pass the offset coordinate natively to spawnInstance!
        // Z - 3.0 places the return point 3 blocks in front of the overworld pillars.
        Transform safeOverworldReturn = new Transform(pos.getX() + 0.5, pos.getY(), pos.getZ() - 3.0);

        ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Generating instance %s ON DEMAND for portal %s...", instanceId, portal.portalId());

        // We pass safeOverworldReturn directly here. The vanilla engine will handle the config routing perfectly!
        InstancesPlugin.get().spawnInstance(instanceId, null, originWorld, safeOverworldReturn)
                .thenAcceptAsync((spawnedWorld) -> {
                    WorldConfig worldConfig = spawnedWorld.getWorldConfig();
                    worldConfig.setDeleteOnUniverseStart(true);
                    worldConfig.setDeleteOnRemove(true);

                    com.hypixel.hytale.builtin.portals.resources.PortalWorld portalWorld =
                            (com.hypixel.hytale.builtin.portals.resources.PortalWorld) spawnedWorld.getEntityStore().getStore().getResource(com.hypixel.hytale.builtin.portals.resources.PortalWorld.getResourceType());

                    if (portalWorld != null) {
                        portalWorld.init(portalType, portal.lifetimeSeconds(), new com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition(portal.lifetimeSeconds()), null);
                    }

                    portal.setInstanceWorld(spawnedWorld);

                    HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                        if (portal.dungeonType().fixedSpawn) {
                            // Fixed spawn (Frozen, Swamp): place our own Portal_Return
                            this.spawnReturnPortal(spawnedWorld, worldConfig, portal)
                                    .thenRunAsync(() -> {
                                        portal.setInstanceState(InstanceState.READY);
                                        applyInstanceLevelOverride(portal, spawnedWorld);
                                        ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Portal %s READY", portal.portalId());
                                        // Proximity tick handles teleport when player steps on portal
                                    }, originWorld)
                                    .exceptionally(retEx -> {
                                        portal.setInstanceState(InstanceState.FAILED);
                                        return null;
                                    });
                        } else {
                            // Dynamic spawn (Golem Void): force-generate edge chunk, place portal
                            this.setupDynamicSpawnPortal(spawnedWorld, worldConfig, portal,
                                    initiatingPlayer, originWorld);
                        }
                    }, 3, TimeUnit.SECONDS);

                }, originWorld)
                .exceptionally(t -> {
                    ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Instance spawn failed for %s: %s", portal.portalId(), t.toString());
                    portal.setInstanceState(InstanceState.FAILED);
                    return null;
                });
    }

    private static final String RETURN_PORTAL_BLOCK = "Portal_Return";

    @Nonnull
    private CompletableFuture<World> spawnReturnPortal(World spawnedWorld, WorldConfig worldConfig, ActiveRift portal) {
        RiftDungeonType dungeonType = portal.dungeonType();
        String portalId = portal.portalId();
        // Use the baseX/Y/Z from RiftDungeonType — these match the SpawnProviderOverride
        // in each PortalType JSON, so the player and the exit portal land in the same place.
        Transform spawnTransform = new Transform(dungeonType.baseX, dungeonType.baseY, dungeonType.baseZ);
        return placeReturnPortal(spawnedWorld, worldConfig, dungeonType, spawnTransform, portalId, portal);
    }

    /**
     * Teleport the initiating player into the instance with a cooldown to prevent
     * the exit proximity check from firing on arrival.
     */
    private void teleportInitiatingPlayer(@Nullable PlayerRef initiatingPlayer,
                                          World originWorld, World instanceWorld, ActiveRift portal) {
        if (initiatingPlayer == null) return;
        teleportCooldowns.put(initiatingPlayer.getUuid(), System.currentTimeMillis() + 8000L);
        originWorld.execute(() -> {
            try {
                Ref<EntityStore> ref = initiatingPlayer.getReference();
                if (ref != null && ref.isValid()) {
                    InstancesPlugin.teleportPlayerToInstance(ref, ref.getStore(), instanceWorld, null);
                }
            } catch (Exception e) {
                ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Entry TP failed: %s", e.getMessage());
            }
        });
    }

    /** Arena-edge spawn points from Golem Void instance.bson (radius 300, 45-degree increments). */
    private static final int[][] GOLEM_VOID_EDGE_POINTS = {
            {300, 0}, {212, 212}, {0, 300}, {-212, 212},
            {-300, 0}, {-212, -212}, {0, -300}, {212, -212}
    };

    /**
     * Dynamic-spawn path (Golem Void): use getChunkAsync to force-generate the edge
     * chunk (no player needed), find ground, place Portal_Return, override spawn
     * provider, then teleport the player directly to the portal.
     */
    private void setupDynamicSpawnPortal(World instanceWorld, WorldConfig worldConfig,
                                         ActiveRift portal, @Nullable PlayerRef initiatingPlayer,
                                         World originWorld) {
        int[] picked = GOLEM_VOID_EDGE_POINTS[random.nextInt(GOLEM_VOID_EDGE_POINTS.length)];
        int edgeX = picked[0];
        int edgeZ = picked[1];
        String portalId = portal.portalId();

        // Calculate yaw to face center (0,0) from the edge point
        float yaw = (float) Math.toDegrees(Math.atan2(-edgeX, edgeZ));

        // Force-generate the edge chunk via getChunkAsync (no player needed)
        long chunkIndex = ChunkUtil.indexChunkFromBlock(edgeX, edgeZ);
        instanceWorld.getChunkAsync(chunkIndex).thenAccept(chunk -> {
            // Chunk generated — now run placement on the world thread
            instanceWorld.execute(() -> {
                try {
                    int groundY = findInstanceGroundY(instanceWorld, edgeX, 200, edgeZ);

                    // If groundY is suspiciously high, terrain may not be fully populated.
                    // Fall back to a safe Y (the void arena floor is ~130-141).
                    if (groundY >= 195) {
                        ((HytaleLogger.Api) LOGGER.atWarning()).log(
                                "[HyRifts] Ground scan at (%d, %d) returned %d — using fallback Y=135",
                                edgeX, edgeZ, groundY);
                        groundY = 135;
                    }

                    Transform spawnTransform = new Transform(
                            new Vector3d(edgeX + 0.5, groundY, edgeZ + 0.5),
                            new Vector3f(0, yaw, 0));

                    // Override spawn provider → Fragment Exit marker + player spawn match
                    worldConfig.setSpawnProvider(new GlobalSpawnProvider(spawnTransform));

                    // Place visible Portal_Return on the ground
                    placeReturnPortalBlocks(instanceWorld, edgeX, groundY, edgeZ);
                    setReturnPortalSpawnPoint(instanceWorld, spawnTransform);
                    portal.setReturnPortalPos(new Vector3d(edgeX, groundY, edgeZ));

                    // Add "Exit Fragment" map marker at the exit portal position
                    addExitFragmentMarker(instanceWorld, edgeX, groundY, edgeZ);

                    ((HytaleLogger.Api) LOGGER.atInfo()).log(
                            "[HyRifts] Dynamic portal for %s at %d, %d, %d (edge %d,%d, yaw=%.0f)",
                            instanceWorld.getName(), edgeX, groundY, edgeZ, edgeX, edgeZ, yaw);

                    // Keep-alive: re-place portal every 3 seconds
                    final int gy = groundY;
                    ScheduledFuture<?> keepAlive = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                        try {
                            if (!instanceWorld.isAlive()) {
                                ScheduledFuture<?> self = returnPortalKeepAlive.remove(portalId);
                                if (self != null) self.cancel(false);
                                return;
                            }
                            instanceWorld.execute(() -> {
                                try {
                                    placeReturnPortalBlocks(instanceWorld, edgeX, gy, edgeZ);
                                } catch (Exception ignored) {}
                            });
                        } catch (Exception ignored) {}
                    }, 3, 3, TimeUnit.SECONDS);
                    ScheduledFuture<?> prev = returnPortalKeepAlive.put(portalId, keepAlive);
                    if (prev != null) prev.cancel(false);

                    // Everything placed — mark ready (proximity tick handles teleport)
                    portal.setInstanceState(InstanceState.READY);
                    applyInstanceLevelOverride(portal, instanceWorld);
                    ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Portal %s READY (dynamic)", portal.portalId());
                } catch (Exception e) {
                    ((HytaleLogger.Api) LOGGER.atWarning()).log(
                            "[HyRifts] setupDynamicSpawnPortal placement failed: %s", e.getMessage());
                    portal.setInstanceState(InstanceState.FAILED);
                }
            });
        }).exceptionally(ex -> {
            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                    "[HyRifts] getChunkAsync failed for (%d, %d): %s", edgeX, edgeZ, ex.getMessage());
            portal.setInstanceState(InstanceState.FAILED);
            return null;
        });
    }

    @Nonnull
    private CompletableFuture<World> placeReturnPortal(World spawnedWorld, WorldConfig worldConfig,
                                                       RiftDungeonType dungeonType, Transform spawnTransform,
                                                       String portalId, ActiveRift portal) {
        Vector3d spawnPoint = spawnTransform.getPosition();
        int spx = (int) spawnPoint.getX();
        int spy = (int) spawnPoint.getY();
        int spz = (int) spawnPoint.getZ();

        // Place the exit portal at the player's spawn point.
        // The Portal_Return is a floor pad, so the player can stand on/near it.
        int portalX = spx;
        int portalZ = spz;

        // Execute directly on the world thread — avoids getChunkAsync→execute deadlock.
        // Instance dungeon chunks are pre-loaded, so getChunkIfInMemory is sufficient.
        CompletableFuture<World> future = new CompletableFuture<>();
        spawnedWorld.execute(() -> {
            try {
                int portalY = findInstanceGroundY(spawnedWorld, portalX, spy, portalZ);

                placeReturnPortalBlocks(spawnedWorld, portalX, portalY, portalZ);

                setReturnPortalSpawnPoint(spawnedWorld, spawnTransform);
                portal.setReturnPortalPos(new Vector3d(portalX, portalY, portalZ));

                ((HytaleLogger.Api) LOGGER.atInfo()).log(
                        "[HyRifts] Spawned return portal for %s at %d, %d, %d (Player spawns at %d, %d, %d)",
                        spawnedWorld.getName(), portalX, portalY, portalZ, spx, spy, spz);

                // Instance chunk management can overwrite runtime-placed blocks.
                // Re-place the portal block every 3 seconds to guarantee visibility.
                ScheduledFuture<?> keepAlive = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(() -> {
                    try {
                        if (!spawnedWorld.isAlive()) {
                            ScheduledFuture<?> self = returnPortalKeepAlive.remove(portalId);
                            if (self != null) self.cancel(false);
                            return;
                        }
                        spawnedWorld.execute(() -> {
                            try {
                                placeReturnPortalBlocks(spawnedWorld, portalX, portalY, portalZ);
                            } catch (Exception ignored) {}
                        });
                    } catch (Exception ignored) {}
                }, 3, 3, TimeUnit.SECONDS);

                // Cancel any previous keep-alive for this portal (shouldn't happen, but be safe)
                ScheduledFuture<?> prev = returnPortalKeepAlive.put(portalId, keepAlive);
                if (prev != null) prev.cancel(false);

                future.complete(spawnedWorld);
            } catch (Exception e) {
                ((HytaleLogger.Api) LOGGER.atWarning()).log(
                        "[HyRifts] Failed to place return portal: %s", e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static void placeReturnPortalBlocks(World world, int portalX, int portalY, int portalZ) {
        // Clear a 3x3x3 area so the multi-block model has space to render
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlock(portalX + dx, portalY + dy, portalZ + dz, "Empty");
                }
            }
        }
        // Place the exit portal (visible platform + particles)
        world.setBlock(portalX, portalY, portalZ, RETURN_PORTAL_BLOCK);
    }

    /**
     * Scan downward from {@code startY} to find the first solid block,
     * then return the Y immediately above it (where the portal should sit).
     * Must be called on the world thread.
     */
    private static int findInstanceGroundY(World world, int x, int startY, int z) {
        try {
            long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
            WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
            if (chunk == null) return startY;

            for (int y = startY; y >= 1; y--) {
                BlockType bt = chunk.getBlockType(x, y, z);
                if (bt == null) continue;
                String id = bt.getId();
                if (id == null) continue;
                String lower = id.toLowerCase();
                if (lower.contains("empty") || lower.contains("air")) continue;
                return y + 1;
            }
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                    "[HyRifts] findInstanceGroundY exception: %s", e.getMessage());
        }
        return startY;
    }

    private void setReturnPortalSpawnPoint(World spawnedWorld, Transform spawnTransform) {
        com.hypixel.hytale.builtin.portals.resources.PortalWorld portalWorld =
                (com.hypixel.hytale.builtin.portals.resources.PortalWorld) spawnedWorld.getEntityStore()
                        .getStore().getResource(com.hypixel.hytale.builtin.portals.resources.PortalWorld.getResourceType());
        if (portalWorld != null) {
            portalWorld.setSpawnPoint(spawnTransform);
        }
    }

    /**
     * Add an "Exit Fragment" map marker at the given position in an instance world.
     * Uses BlockMapMarkersResource to register the marker so it appears on the player's map.
     * Must be called on the world thread.
     */
    private void addExitFragmentMarker(World instanceWorld, int x, int y, int z) {
        try {
            BlockMapMarkersResource resource = (BlockMapMarkersResource) instanceWorld.getChunkStore()
                    .getStore().getResource(BlockMapMarkersResource.getResourceType());
            if (resource != null) {
                resource.addMarker(new Vector3i(x, y, z), "Exit Fragment", "Spawn.png");
                ((HytaleLogger.Api) LOGGER.atInfo()).log(
                        "[HyRifts] Added Exit Fragment map marker at %d, %d, %d in %s",
                        x, y, z, instanceWorld.getName());
            } else {
                ((HytaleLogger.Api) LOGGER.atWarning()).log(
                        "[HyRifts] BlockMapMarkersResource is null for %s — cannot add exit marker",
                        instanceWorld.getName());
            }
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                    "[HyRifts] Failed to add Exit Fragment marker: %s", e.getMessage());
        }
    }

    private void removePortalBlock(World world, Vector3d position) {
        world.execute(() -> {
            try {
                int x = (int)position.getX();
                int y = (int)position.getY();
                int z = (int)position.getZ();

                for (int dy = 0; dy < 5; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            world.setBlock(x + dx, y + dy, z + dz, "Empty");
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public void forceSpawn(@Nullable String dungeonTypeId) {
        RiftDungeonType dungeonType = null;
        if (dungeonTypeId != null && !dungeonTypeId.isBlank()) {
            dungeonType = RiftDungeonType.fromId(dungeonTypeId);
        }

        RiftRank rank = this.rollRank();
        RiftRankSettings rs = this.config.getRankSettings(rank);
        int mobLevel = this.rollMobLevel(rs);

        // Try reusing a past portal location first
        SpawnCandidate candidate = tryReusePastPortal();
        if (candidate != null && dungeonType == null) {
            PastPortal pastMatch = findNearbyPastPortal(candidate.position, candidate.world.getName());
            if (pastMatch != null) dungeonType = pastMatch.dungeonType;
        }

        // Fall back to finding a new location
        if (candidate == null) {
            candidate = this.findSpawnLocation();
            if (candidate == null) {
                ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Force spawn failed — no valid location found");
                return;
            }
            if (isNearActivePortal(candidate.position, candidate.world.getName())) {
                candidate = this.findSpawnLocation();
                if (candidate == null || isNearActivePortal(candidate.position, candidate.world.getName())) {
                    ((HytaleLogger.Api) LOGGER.atInfo()).log("[HyRifts] Force spawn cancelled — too close to active portal after reroll");
                    return;
                }
            }
        }

        if (dungeonType == null) {
            RiftDungeonType[] types = RiftDungeonType.values();
            dungeonType = types[this.random.nextInt(types.length)];
        }

        String portalId = UUID.randomUUID().toString().substring(0, 8);
        ActiveRift portal = new ActiveRift(portalId, dungeonType, rank, rs, mobLevel, candidate.world.getName(), candidate.position, System.currentTimeMillis(), this.config.portalLifetimeSeconds);
        this.registerLevelOverride(portal, candidate.world.getName());

        this.activePortals.put(portalId, portal);
        this.spawnPortal(candidate.world, candidate.position, portal);

        ((HytaleLogger.Api)LOGGER.atInfo()).log("[HyRifts] Force-spawned %s portal [%s] at %.0f, %.0f, %.0f (Lv.%d %s)", dungeonType.displayName, portalId, candidate.position.getX(), candidate.position.getY(), candidate.position.getZ(), mobLevel, rank.label);
        if (this.config.announcePortals) {
            this.broadcastMessage("A rift in reality has opened", "#b388ff");
        }

        schedulePortalExpiry(portalId, this.config.portalLifetimeSeconds);
    }

    public void forceSpawnAt(World world, Vector3d position, @Nullable String dungeonTypeId) {
        RiftDungeonType dungeonType = null;
        if (dungeonTypeId != null && !dungeonTypeId.isBlank()) {
            dungeonType = RiftDungeonType.fromId(dungeonTypeId);
        }

        if (dungeonType == null) {
            RiftDungeonType[] types = RiftDungeonType.values();
            dungeonType = types[this.random.nextInt(types.length)];
        }

        RiftRank rank = this.rollRank();
        RiftRankSettings rs = this.config.getRankSettings(rank);
        int mobLevel = this.rollMobLevel(rs);
        String portalId = UUID.randomUUID().toString().substring(0, 8);
        ActiveRift portal = new ActiveRift(portalId, dungeonType, rank, rs, mobLevel, world.getName(), position, System.currentTimeMillis(), this.config.portalLifetimeSeconds);
        this.registerLevelOverride(portal, world.getName());

        this.activePortals.put(portalId, portal);
        this.spawnPortal(world, position, portal);

        ((HytaleLogger.Api)LOGGER.atInfo()).log("[HyRifts] Force-spawned %s portal [%s] at %.0f, %.0f, %.0f (Lv.%d %s)", dungeonType.displayName, portalId, position.getX(), position.getY(), position.getZ(), mobLevel, rank.label);
        if (this.config.announcePortals) {
            this.broadcastMessage("A rift in reality has opened", "#b388ff");
        }

        schedulePortalExpiry(portalId, this.config.portalLifetimeSeconds);
    }

    @Nullable
    public CompassReading getCompassReading(String worldName, Vector3d playerPos) {
        double range = this.config.riftCompassRange;
        double rangeSq = range * range;
        ActiveRift nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for(ActiveRift portal : this.activePortals.values()) {
            if (portal.worldName().equals(worldName)) {
                double dx = portal.position().getX() - playerPos.getX();
                double dz = portal.position().getZ() - playerPos.getZ();
                double distSq = dx * dx + dz * dz;
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = portal;
                }
            }
        }

        if (nearest != null && !(nearestDistSq > rangeSq)) {
            double dist = Math.sqrt(nearestDistSq);
            double intensity = 1.0F - dist / range;
            double dx = nearest.position().getX() - playerPos.getX();
            double dz = nearest.position().getZ() - playerPos.getZ();
            double angleRad = Math.atan2(dz, dx);
            return new CompassReading(intensity, angleRad, dist, nearest);
        } else {
            return null;
        }
    }

    public void closePortal(String portalId) {
        ActiveRift portal = this.activePortals.remove(portalId);
        if (portal != null) {
            // Remember this location so future portals can reuse it (persisted to disk)
            pastPortalLocations.add(new PastPortal(portal.worldName(), portal.position(), portal.dungeonType()));
            savePastPortals();
            try {
                World world = null;

                for(World w : Universe.get().getWorlds().values()) {
                    if (w.getName().equals(portal.worldName())) {
                        world = w;
                        break;
                    }
                }

                if (world != null) {
                    this.removePortalBlock(world, portal.position());
                }
            } catch (Exception ignored) {}

            // Evacuate any players still inside the instance before tearing it down
            evacuateInstancePlayers(portal);

            // Stop the return portal keep-alive task before removing the instance
            ScheduledFuture<?> keepAlive = returnPortalKeepAlive.remove(portalId);
            if (keepAlive != null) keepAlive.cancel(false);

            // Delay instance removal slightly to let evacuation teleports complete
            World instanceWorld = portal.instanceWorld();
            if (instanceWorld != null && instanceWorld.isAlive()) {
                HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                    try {
                        if (instanceWorld.isAlive()) {
                            instanceWorld.execute(() -> InstancesPlugin.safeRemoveInstance(instanceWorld));
                        }
                    } catch (Exception e) {
                        ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts] Failed to remove instance for portal %s: %s", portalId, e.getMessage());
                    }
                }, 3, TimeUnit.SECONDS);
            }

            endgame.plugin.EndgameQoL elPlugin = endgame.plugin.EndgameQoL.getInstance();
            if (elPlugin != null && elPlugin.isEndlessLevelingActive()) {
                try {
                    EndlessLevelingAPI.get().removeMobAreaLevelOverride(portal.levelOverrideId());
                } catch (Throwable e) {
                    ((HytaleLogger.Api)LOGGER.atWarning()).log("[HyRifts] Failed to remove mob level override for %s: %s", portalId, e.getMessage());
                }
                // Delete the per-instance EL world settings file and reload
                java.io.File overrideFile = instanceLevelOverrideFiles.remove(portalId);
                if (overrideFile != null && overrideFile.exists()) {
                    try {
                        overrideFile.delete();
                        com.airijko.endlessleveling.EndlessLeveling el =
                            com.airijko.endlessleveling.EndlessLeveling.getInstance();
                        if (el != null) el.getMobLevelingManager().reloadConfig();
                    } catch (Throwable e) {
                        ((HytaleLogger.Api)LOGGER.atWarning()).log("[HyRifts] Failed to clean up instance level override for %s: %s", portalId, e.getMessage());
                    }
                }
            }

            ((HytaleLogger.Api)LOGGER.atInfo()).log("[HyRifts] Portal %s closed", portalId);
            if (this.config.announceDespawn) {
                this.broadcastMessage("A rift in reality has closed", "#555555");
            }
        }
    }

    public void closeAllPortals() {
        for(String id : new ArrayList<>(this.activePortals.keySet())) {
            this.closePortal(id);
        }
    }

    private void cleanupExpired() {
        for(Map.Entry<String, ActiveRift> entry : this.activePortals.entrySet()) {
            if (entry.getValue().isExpired()) {
                this.closePortal(entry.getKey());
            }
        }
    }

    /**
     * Schedule the portal expiry: a 1-minute warning to players inside the instance,
     * followed by the actual close (which evacuates players first).
     */
    private void schedulePortalExpiry(String portalId, int lifetimeSeconds) {
        if (lifetimeSeconds <= 0) return;

        // 1-minute warning (only if lifetime > 60s, otherwise warn immediately isn't useful)
        if (lifetimeSeconds > 60) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(
                    () -> warnInstancePlayers(portalId, 60),
                    (long) lifetimeSeconds - 60, TimeUnit.SECONDS);
        }

        // Actual close
        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> this.closePortal(portalId),
                (long) lifetimeSeconds, TimeUnit.SECONDS);
    }

    /** Send a warning message to all players currently inside this portal's instance. */
    private void warnInstancePlayers(String portalId, int secondsRemaining) {
        ActiveRift portal = this.activePortals.get(portalId);
        if (portal == null) return;
        World instanceWorld = portal.instanceWorld();
        if (instanceWorld == null || !instanceWorld.isAlive()) return;

        try {
            Message warning = Message.raw(
                    String.format("The rift is destabilizing! %d seconds remaining...", secondsRemaining))
                    .color("#ff5555");

            for (PlayerRef player : Universe.get().getPlayers()) {
                try {
                    UUID worldUuid = player.getWorldUuid();
                    if (worldUuid == null) continue;
                    World playerWorld = Universe.get().getWorld(worldUuid);
                    if (playerWorld != null && playerWorld.getName().equals(instanceWorld.getName())) {
                        player.sendMessage(warning);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                    "[HyRifts] Failed to warn instance players for %s: %s", portalId, e.getMessage());
        }
    }

    /**
     * Evacuate all players from the portal's instance world back to the overworld
     * using the normal exit path. Called before the instance is removed.
     */
    private void evacuateInstancePlayers(ActiveRift portal) {
        World instanceWorld = portal.instanceWorld();
        if (instanceWorld == null || !instanceWorld.isAlive()) return;

        try {
            Message kickMsg = Message.raw("The rift has collapsed! You have been returned to safety.")
                    .color("#ff8800");

            for (PlayerRef player : Universe.get().getPlayers()) {
                try {
                    UUID worldUuid = player.getWorldUuid();
                    if (worldUuid == null) continue;
                    World playerWorld = Universe.get().getWorld(worldUuid);
                    if (playerWorld != null && playerWorld.getName().equals(instanceWorld.getName())) {
                        player.sendMessage(kickMsg);
                        instanceWorld.execute(() -> {
                            try {
                                InstancesPlugin.exitInstance(
                                        player.getReference(),
                                        player.getReference().getStore()
                                );
                            } catch (Exception e) {
                                ((HytaleLogger.Api) LOGGER.atWarning()).log(
                                        "[HyRifts] Failed to evacuate player: %s", e.getMessage());
                            }
                        });
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                    "[HyRifts] Failed to evacuate players from %s: %s", portal.portalId(), e.getMessage());
        }
    }

    private void broadcastMessage(String text, String color) {
        try {
            Message message = Message.raw(text).color(color);

            for(PlayerRef player : Universe.get().getPlayers()) {
                try {
                    player.sendMessage(message);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            ((HytaleLogger.Api)LOGGER.atWarning()).log("[HyRifts] Failed to broadcast: %s", e.getMessage());
        }
    }

    private record PlayerWorldPos(PlayerRef player, World world, Vector3d position) {}
    private record SpawnCandidate(World world, Vector3d position) {}
    public record CompassReading(double intensity, double angleRad, double distance, ActiveRift portal) {}
}
