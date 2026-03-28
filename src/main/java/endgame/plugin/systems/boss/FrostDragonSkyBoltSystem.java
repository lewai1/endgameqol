package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.boss.GenericBossManager;

import com.hypixel.hytale.component.RemoveReason;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Phase-based frost dragon ranged attack system.
 *
 * Sky Bolt Barrage — rains ice projectiles from above players:
 *   Phase 1 (>70% HP): 2 bolts, 10s cooldown
 *   Phase 2 (70-40% HP): 4 bolts, 7s cooldown
 *   Phase 3 (<40% HP): 6 bolts, 5s cooldown + alternating ring pattern
 *
 * Frost Nova — outward ring of slower ice projectiles (Phase 2+):
 *   10 projectiles in a circle, 25s cooldown, strong knockback
 */
public class FrostDragonSkyBoltSystem extends EntityTickingSystem<EntityStore> {

    private static final long CHECK_INTERVAL_MS = 200;
    private static final long STATE_CLEANUP_INTERVAL_MS = 60000;
    private static final long STATE_STALE_THRESHOLD_MS = 60000; // 1 minute
    private static final double BOLT_HEIGHT = 35.0;
    private static final double COMBAT_RANGE = 80.0;
    private static final double COMBAT_RANGE_SQ = COMBAT_RANGE * COMBAT_RANGE;
    private static final double MAX_VERTICAL_DISTANCE = 50.0;
    private static final long BOLT_STAGGER_MS = 150;
    private static final String ENDGAME_DRAGON_TYPE_ID = "Endgame_Dragon_Frost";
    private static final String BOLT_CONFIG_ID = "Projectile_Config_Endgame_Dragon_Frost_Ice";
    private static final String NOVA_CONFIG_ID = "Projectile_Config_Endgame_Dragon_Frost_Nova";

    private static final float PHASE2_THRESHOLD = 0.70f;
    private static final float PHASE3_THRESHOLD = 0.40f;

    // Phase-based bolt counts (hardcoded ratios, base cooldown from config)
    private static final int[] PHASE_BOLT_COUNT = {2, 4, 6};

    // Warning telegraph (ground rings before bolts land)
    private static final long WARNING_DELAY_MS = 1000;
    private static final String WARNING_PARTICLE = "Rings_Rings_Ice";
    private static final String WARNING_PARTICLE_BLAST = "Ice_Blast";
    private static final String NOVA_LAUNCH_PARTICLE = "Ice_Blast";
    private static final String NOVA_LAUNCH_PARTICLE_2 = "Hedera_Scream";

    // Spirit spawning (replaces NPC role EnableSpawn)
    private static final String SPIRIT_NPC_ID = "Spirit_Frost";
    private static final long SPIRIT_SPAWN_MIN_MS = 20000;
    private static final long SPIRIT_SPAWN_MAX_MS = 30000;
    private static final int SPIRIT_SPAWN_MIN_COUNT = 2;
    private static final int SPIRIT_SPAWN_MAX_COUNT = 3;
    private static final double SPIRIT_SPAWN_RADIUS = 12.0;

    private static final Query<EntityStore> QUERY = Query.and(
            TransformComponent.getComponentType(),
            NPCEntity.getComponentType());

    private final EndgameQoL plugin;
    private volatile GenericBossManager genericBossManager;

    // Per-dragon state — keyed by Ref directly (equals/hashCode stable, toString is NOT)
    private final ConcurrentHashMap<Ref<EntityStore>, DragonState> dragonStates = new ConcurrentHashMap<>();
    private volatile long lastStateCleanupTime = 0;

    // Cached configs (lazy-loaded)
    private volatile ProjectileConfig cachedBoltConfig;
    private volatile ProjectileConfig cachedNovaConfig;
    private volatile boolean boltConfigFailed = false;
    private volatile boolean novaConfigFailed = false;

    // Player list cache with 2s TTL (avoids O(n) Universe.get().getPlayers() every tick per dragon)
    private volatile List<PlayerRef> cachedPlayers;
    private volatile long playerCacheTime = 0;
    private static final long PLAYER_CACHE_TTL_MS = 2000;

    /**
     * Tracks per-dragon barrage and nova state.
     */
    private static class DragonState {
        volatile long lastCheckTime;
        volatile long lastBarrageStartTime;
        volatile long lastNovaTime;
        volatile int barrageCount; // alternating counter for ring pattern in phase 3

        // Active barrage state
        volatile boolean barrageActive;
        volatile long barrageStartedAt;
        volatile int barrageBoltsTotal;
        volatile int barrageBoltsFired;
        volatile List<Vector3d> barragePositions; // pre-computed spawn positions (sky level)
        volatile boolean isRingBarrage;

        // Warning telegraph state
        volatile boolean warningPhase;
        volatile long warningStartTime;
        // Spirit spawning state
        volatile int spiritsSpawned;
        volatile long lastSpiritSpawnTime;
        volatile long nextSpiritCooldown;
        final CopyOnWriteArrayList<Ref<EntityStore>> spiritRefs = new CopyOnWriteArrayList<>();

        DragonState() {
            this.lastCheckTime = 0;
            this.lastBarrageStartTime = 0;
            this.lastNovaTime = 0;
            this.barrageCount = 0;
            this.barrageActive = false;
            this.warningPhase = false;
            this.spiritsSpawned = 0;
            this.lastSpiritSpawnTime = 0;
            this.nextSpiritCooldown = ThreadLocalRandom.current().nextLong(SPIRIT_SPAWN_MIN_MS, SPIRIT_SPAWN_MAX_MS);
        }
    }

    public FrostDragonSkyBoltSystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    public void setGenericBossManager(GenericBossManager manager) {
        this.genericBossManager = manager;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        String typeId = npc.getNPCTypeId();
        if (!ENDGAME_DRAGON_TYPE_ID.equals(typeId)) return;

        Ref<EntityStore> dragonRef = archetypeChunk.getReferenceTo(index);
        if (dragonRef == null || !dragonRef.isValid()) return;

        long now = System.currentTimeMillis();

        // Periodic cleanup of stale dragon states (dragons that died/despawned)
        if (now - lastStateCleanupTime > STATE_CLEANUP_INTERVAL_MS) {
            lastStateCleanupTime = now;
            dragonStates.entrySet().removeIf(e -> {
                if (now - e.getValue().lastCheckTime > STATE_STALE_THRESHOLD_MS) {
                    despawnSpirits(e.getValue(), commandBuffer);
                    return true;
                }
                return false;
            });
        }
        DragonState state = dragonStates.computeIfAbsent(dragonRef, k -> new DragonState());

        // Rate limit checks
        if ((now - state.lastCheckTime) < CHECK_INTERVAL_MS) return;
        state.lastCheckTime = now;

        // If a barrage is active, handle warning phase or fire bolts
        if (state.barrageActive) {
            if (state.warningPhase) {
                if (now - state.warningStartTime >= WARNING_DELAY_MS) {
                    state.warningPhase = false;
                    state.barrageStartedAt = now; // reset stagger timer after warning
                } else {
                    return; // still showing warning telegraph
                }
            }
            tickBarrage(state, dragonRef, commandBuffer, now);
            return;
        }

        // Get phase from GenericBossManager if available, otherwise calculate locally
        int phase;
        if (genericBossManager != null) {
            int managerPhase = genericBossManager.getCurrentPhase(dragonRef);
            if (managerPhase > 0) {
                phase = managerPhase - 1; // GenericBossManager uses 1-based, this system uses 0-based
            } else {
                float hpRatio = getDragonHpRatio(archetypeChunk, index, store, dragonRef);
                phase = getPhase(hpRatio);
            }
        } else {
            float hpRatio = getDragonHpRatio(archetypeChunk, index, store, dragonRef);
            phase = getPhase(hpRatio);
        }

        // Get dragon position
        TransformComponent dragonTransform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (dragonTransform == null) return;
        Vector3d dragonPos = dragonTransform.getPosition();
        if (dragonPos == null) return;

        // Spirit spawning — capped at plugin.getConfig().get().getFrostDragonSpiritMaxCount() per dragon lifetime
        tickSpiritSpawn(state, store, commandBuffer, dragonPos, now);

        // Find nearest player
        PlayerTarget target = findNearestPlayer(store, dragonPos);
        if (target == null) return;

        // Check Frost Nova first (Phase 2+, separate cooldown)
        long novaCooldown = plugin.getConfig().get().getFrostDragonNovaCooldownMs();
        if (phase >= 1 && (now - state.lastNovaTime) >= novaCooldown) {
            ProjectileConfig novaConfig = getNovaConfig();
            if (novaConfig != null) {
                fireNovaRing(dragonRef, commandBuffer, dragonPos, novaConfig);
                state.lastNovaTime = now;

                plugin.getLogger().atFine().log(
                        "[FrostDragonSkyBolt] Nova ring fired at phase %d", phase + 1);
                return; // Don't start a barrage in the same tick as a nova
            }
        }

        // Sky bolt barrage — only from phase 2+ (avoid premature triggering in phase 1)
        if (phase < 1) return;

        // Check barrage cooldown
        long baseCooldown = plugin.getConfig().get().getFrostDragonBoltCooldownMs();
        long cooldown = switch (phase) {
            case 0 -> baseCooldown;
            case 1 -> (long) (baseCooldown * 0.7);
            default -> (long) (baseCooldown * 0.5);
        };
        if ((now - state.lastBarrageStartTime) < cooldown) return;

        // Resolve bolt config
        ProjectileConfig boltConfig = getBoltConfig();
        if (boltConfig == null) return;

        // Start a new barrage
        int boltCount = PHASE_BOLT_COUNT[phase];
        boolean isRing = (phase == 2) && (state.barrageCount % 2 == 1); // every other barrage in phase 3

        List<Vector3d> positions;
        if (isRing) {
            positions = computeRingPositions(dragonPos, boltCount);
        } else {
            positions = computeSpreadPositions(target.position, boltCount, phase);
        }

        // Compute ground-level impact positions for warning telegraph
        List<Vector3d> groundPositions = new ArrayList<>(boltCount);
        for (Vector3d skyPos : positions) {
            groundPositions.add(new Vector3d(skyPos.x, skyPos.y - BOLT_HEIGHT, skyPos.z));
        }

        state.barrageActive = true;
        state.barrageStartedAt = now;
        state.barrageBoltsTotal = boltCount;
        state.barrageBoltsFired = 0;
        state.barragePositions = positions;
        state.isRingBarrage = isRing;
        state.lastBarrageStartTime = now;
        state.barrageCount++;

        // Start warning phase — show ground rings before bolts fall
        state.warningPhase = true;
        state.warningStartTime = now;
        spawnWarningParticles(store, groundPositions);

        plugin.getLogger().atFine().log(
                "[FrostDragonSkyBolt] Barrage started: %d bolts, phase %d, ring=%b, dist=%.1f (warning telegraphed)",
                boltCount, phase + 1, isRing, target.distance);
    }

    /**
     * Fire the next bolt in an active barrage with stagger delay.
     */
    private void tickBarrage(DragonState state, Ref<EntityStore> dragonRef,
                             CommandBuffer<EntityStore> commandBuffer, long now) {
        if (!state.barrageActive) return;
        if (state.barrageBoltsFired >= state.barrageBoltsTotal) {
            state.barrageActive = false;
            return;
        }

        // Check stagger timing (150ms between bolts)
        long elapsed = now - state.barrageStartedAt;
        int expectedFired = (int) (elapsed / BOLT_STAGGER_MS) + 1;
        if (expectedFired <= state.barrageBoltsFired) return; // not time yet

        ProjectileConfig config = getBoltConfig();
        if (config == null) {
            state.barrageActive = false;
            return;
        }

        // Fire bolts that should have been fired by now
        while (state.barrageBoltsFired < expectedFired && state.barrageBoltsFired < state.barrageBoltsTotal) {
            Vector3d spawnPos = state.barragePositions.get(state.barrageBoltsFired);
            Vector3d direction;

            if (state.isRingBarrage) {
                // Ring bolts go straight down
                direction = new Vector3d(0, -1, 0);
            } else {
                // Sky bolts go straight down
                direction = new Vector3d(0, -1, 0);
            }

            try {
                ProjectileModule.get().spawnProjectile(dragonRef, commandBuffer, config, spawnPos, direction);
            } catch (Exception e) {
                plugin.getLogger().atWarning().withCause(e).log(
                        "[FrostDragonSkyBolt] Failed to spawn bolt %d",
                        state.barrageBoltsFired);
            }
            state.barrageBoltsFired++;
        }

        if (state.barrageBoltsFired >= state.barrageBoltsTotal) {
            state.barrageActive = false;
        }
    }

    /**
     * Fire a ring of nova projectiles outward from the dragon.
     */
    private void fireNovaRing(Ref<EntityStore> dragonRef, CommandBuffer<EntityStore> commandBuffer,
                               Vector3d dragonPos, ProjectileConfig novaConfig) {
        // Nova launch burst VFX at dragon position
        Store<EntityStore> store = dragonRef.getStore();
        if (store != null) {
            List<Ref<EntityStore>> viewers = collectNearbyPlayerRefs(store, dragonPos);
            if (!viewers.isEmpty()) {
                // Use viewer's store — players may be in a different instance store than the dragon
                Store<EntityStore> particleStore = viewers.getFirst().getStore();
                try {
                    ParticleUtil.spawnParticleEffect(NOVA_LAUNCH_PARTICLE,
                            dragonPos.x, dragonPos.y, dragonPos.z,
                            0f, 0f, 0f, 3f, null, null, viewers, particleStore);
                    ParticleUtil.spawnParticleEffect(NOVA_LAUNCH_PARTICLE_2, dragonPos, viewers, particleStore);
                } catch (Exception e) {
                    plugin.getLogger().atWarning().withCause(e).log(
                            "[FrostDragonSkyBolt] Failed to spawn nova launch VFX");
                }
            }
        }

        Vector3d spawnPos = new Vector3d(dragonPos.x, dragonPos.y + 2, dragonPos.z);
        int novaBoltCount = plugin.getConfig().get().getFrostDragonNovaBoltCount();
        for (int i = 0; i < novaBoltCount; i++) {
            double angle = (2 * Math.PI / novaBoltCount) * i;
            Vector3d dir = new Vector3d(Math.cos(angle), 0.3, Math.sin(angle));
            // Normalize
            double len = Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
            dir = new Vector3d(dir.x / len, dir.y / len, dir.z / len);

            try {
                ProjectileModule.get().spawnProjectile(dragonRef, commandBuffer, novaConfig, spawnPos, dir);
            } catch (Exception e) {
                plugin.getLogger().atWarning().withCause(e).log(
                        "[FrostDragonSkyBolt] Failed to spawn nova bolt %d", i);
            }
        }
    }

    /**
     * Spawn Spirit_Frost NPCs near the dragon on a timer, capped at plugin.getConfig().get().getFrostDragonSpiritMaxCount().
     */
    private void tickSpiritSpawn(DragonState state, Store<EntityStore> store,
                                  CommandBuffer<EntityStore> commandBuffer,
                                  Vector3d dragonPos, long now) {
        if (state.spiritsSpawned >= plugin.getConfig().get().getFrostDragonSpiritMaxCount()) return;
        if ((now - state.lastSpiritSpawnTime) < state.nextSpiritCooldown) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int remaining = plugin.getConfig().get().getFrostDragonSpiritMaxCount() - state.spiritsSpawned;
        int count = Math.min(rng.nextInt(SPIRIT_SPAWN_MIN_COUNT, SPIRIT_SPAWN_MAX_COUNT + 1), remaining);

        // Pre-compute spawn positions (rng not safe to use inside deferred callback)
        List<Vector3d> spawnPositions = new ArrayList<>(count);
        List<Vector3f> spawnRotations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble(0, 2 * Math.PI);
            double dist = rng.nextDouble(5.0, SPIRIT_SPAWN_RADIUS);
            spawnPositions.add(new Vector3d(
                    dragonPos.x + dist * Math.cos(angle),
                    dragonPos.y + 1.0,
                    dragonPos.z + dist * Math.sin(angle)));
            spawnRotations.add(new Vector3f(0, (float) Math.toDegrees(angle), 0));
        }

        // Defer NPC spawning — store is locked during system tick
        final int spawnCount = count;
        commandBuffer.run((s) -> {
            int spawned = 0;
            for (int i = 0; i < spawnCount; i++) {
                try {
                    var result = NPCPlugin.get().spawnNPC(s, SPIRIT_NPC_ID, null,
                            spawnPositions.get(i), spawnRotations.get(i));
                    if (result != null && result.left() != null) {
                        state.spiritRefs.add(result.left());
                    }
                    spawned++;
                } catch (Exception e) {
                    plugin.getLogger().atWarning().withCause(e).log(
                            "[FrostDragonSkyBolt] Failed to spawn spirit");
                }
            }
            state.spiritsSpawned += spawned;

            if (spawned > 0) {
                plugin.getLogger().atFine().log(
                        "[FrostDragonSkyBolt] Spawned %d spirits (%d/%d total)",
                        spawned, state.spiritsSpawned, plugin.getConfig().get().getFrostDragonSpiritMaxCount());
            }
        });

        state.lastSpiritSpawnTime = now;
        state.nextSpiritCooldown = rng.nextLong(SPIRIT_SPAWN_MIN_MS, SPIRIT_SPAWN_MAX_MS);
    }

    /**
     * Spawn warning particles (ice rings) at ground-level impact positions.
     * Players see the rings ~1s before bolts land.
     */
    private void spawnWarningParticles(Store<EntityStore> store, List<Vector3d> groundPositions) {
        if (groundPositions.isEmpty()) return;

        // Collect nearby player refs for particle visibility
        Vector3d center = groundPositions.getFirst();
        List<Ref<EntityStore>> playerRefs = collectNearbyPlayerRefs(store, center);
        if (playerRefs.isEmpty()) return;

        // Use the player's store for particles — players may be in an instance store
        // different from the tick store. ParticleUtil validates refs against the passed store.
        Store<EntityStore> particleStore = playerRefs.getFirst().getStore();

        for (Vector3d pos : groundPositions) {
            try {
                ParticleUtil.spawnParticleEffect(WARNING_PARTICLE, pos, playerRefs, particleStore);
                ParticleUtil.spawnParticleEffect(WARNING_PARTICLE_BLAST, pos, playerRefs, particleStore);
            } catch (Exception e) {
                plugin.getLogger().atWarning().withCause(e).log(
                        "[FrostDragonSkyBolt] Failed to spawn warning particle");
            }
        }
    }

    /**
     * Get player list with 2s TTL cache to avoid repeated Universe.get().getPlayers() calls.
     */
    private List<PlayerRef> getPlayers() {
        long now = System.currentTimeMillis();
        if (now - playerCacheTime > PLAYER_CACHE_TTL_MS || cachedPlayers == null) {
            cachedPlayers = new ArrayList<>(Universe.get().getPlayers());
            playerCacheTime = now;
        }
        return cachedPlayers;
    }

    /**
     * Collect refs of players within combat range for particle visibility.
     */
    private List<Ref<EntityStore>> collectNearbyPlayerRefs(Store<EntityStore> store, Vector3d center) {
        List<Ref<EntityStore>> refs = new ArrayList<>();
        for (PlayerRef playerRef : getPlayers()) {
            if (playerRef == null) continue;
            Ref<EntityStore> pRef = playerRef.getReference();
            if (pRef == null || !pRef.isValid()) continue;

            Store<EntityStore> pStore = pRef.getStore();
            if (pStore == null) continue;

            TransformComponent pt;
            try {
                pt = pStore.getComponent(pRef, TransformComponent.getComponentType());
            } catch (Exception e) {
                continue;
            }
            if (pt == null) continue;

            Vector3d pos = pt.getPosition();
            if (pos == null) continue;

            double dx = pos.x - center.x;
            double dy = Math.abs(pos.y - center.y);
            double dz = pos.z - center.z;
            // Only include players within horizontal range AND vertical range
            if (dy <= MAX_VERTICAL_DISTANCE && dx * dx + dz * dz <= COMBAT_RANGE * COMBAT_RANGE) {
                refs.add(pRef);
            }
        }
        return refs;
    }

    /**
     * Compute spread positions above a player for sky bolt barrage.
     * Bolts are offset ±2-4 blocks randomly from the target.
     */
    private List<Vector3d> computeSpreadPositions(Vector3d playerPos, int count, int phase) {
        List<Vector3d> positions = new ArrayList<>(count);
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        // First bolt always directly above player
        positions.add(new Vector3d(playerPos.x, playerPos.y + BOLT_HEIGHT, playerPos.z));
        // Remaining bolts spread around
        double spreadRange = 2.0 + phase * 1.5; // Phase 1: ±2, Phase 2: ±3.5, Phase 3: ±5
        for (int i = 1; i < count; i++) {
            double offsetX = rng.nextDouble(-spreadRange, spreadRange);
            double offsetZ = rng.nextDouble(-spreadRange, spreadRange);
            positions.add(new Vector3d(
                    playerPos.x + offsetX,
                    playerPos.y + BOLT_HEIGHT,
                    playerPos.z + offsetZ));
        }
        return positions;
    }

    /**
     * Compute ring positions above the dragon for ring barrage pattern (Phase 3).
     * Bolts spawn in a circle at radius 10 from the dragon, high up.
     */
    private List<Vector3d> computeRingPositions(Vector3d dragonPos, int count) {
        List<Vector3d> positions = new ArrayList<>(count);
        double radius = 10.0;
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            positions.add(new Vector3d(
                    dragonPos.x + radius * Math.cos(angle),
                    dragonPos.y + BOLT_HEIGHT,
                    dragonPos.z + radius * Math.sin(angle)));
        }
        return positions;
    }

    /**
     * Get the dragon's current HP ratio (0.0 to 1.0).
     * Returns 1.0 if HP cannot be determined.
     */
    private float getDragonHpRatio(ArchetypeChunk<EntityStore> chunk, int index,
                                    Store<EntityStore> store, Ref<EntityStore> dragonRef) {
        try {
            ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
            if (statType == null) return 1.0f;

            EntityStatMap statMap = store.getComponent(dragonRef, statType);
            if (statMap == null) return 1.0f;

            EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
            if (healthValue == null) return 1.0f;

            float current = healthValue.get();
            float max = healthValue.getMax();
            if (max <= 0) return 1.0f;

            return Math.max(0f, Math.min(1f, current / max));
        } catch (Exception e) {
            return 1.0f;
        }
    }

    /**
     * Determine phase index from HP ratio.
     * Returns 0 (phase 1), 1 (phase 2), or 2 (phase 3).
     */
    private int getPhase(float hpRatio) {
        if (hpRatio <= PHASE3_THRESHOLD) return 2;
        if (hpRatio <= PHASE2_THRESHOLD) return 1;
        return 0;
    }

    /**
     * Find nearest player within combat range.
     */
    @Nullable
    private PlayerTarget findNearestPlayer(Store<EntityStore> store, Vector3d dragonPos) {
        PlayerRef nearestPlayer = null;
        double nearestDistSq = Double.MAX_VALUE;
        Vector3d nearestPos = null;

        for (PlayerRef playerRef : getPlayers()) {
            if (playerRef == null) continue;
            Ref<EntityStore> pRef = playerRef.getReference();
            if (pRef == null || !pRef.isValid()) continue;

            Store<EntityStore> pStore = pRef.getStore();
            if (pStore == null) continue;

            TransformComponent pt;
            try {
                pt = pStore.getComponent(pRef, TransformComponent.getComponentType());
            } catch (Exception e) {
                continue;
            }
            if (pt == null) continue;

            Vector3d playerPos = pt.getPosition();
            if (playerPos == null) continue;

            double dx = playerPos.x - dragonPos.x;
            double dy = Math.abs(playerPos.y - dragonPos.y);
            double dz = playerPos.z - dragonPos.z;
            double distSq = dx * dx + dz * dz;

            // Skip players too far vertically (e.g., at dungeon entrance while dragon is at bottom)
            if (dy > MAX_VERTICAL_DISTANCE) continue;

            if (distSq <= COMBAT_RANGE_SQ && distSq < nearestDistSq) {
                nearestPlayer = playerRef;
                nearestDistSq = distSq;
                nearestPos = playerPos;
            }
        }

        if (nearestPlayer == null || nearestPos == null) return null;
        return new PlayerTarget(nearestPlayer, nearestPos, Math.sqrt(nearestDistSq));
    }

    private record PlayerTarget(PlayerRef playerRef, Vector3d position, double distance) {}

    @Nullable
    private ProjectileConfig getBoltConfig() {
        if (cachedBoltConfig != null) return cachedBoltConfig;
        if (boltConfigFailed) return null;
        try {
            cachedBoltConfig = ProjectileConfig.getAssetMap().getAsset(BOLT_CONFIG_ID);
            if (cachedBoltConfig == null) {
                boltConfigFailed = true;
                plugin.getLogger().atWarning().log(
                        "[FrostDragonSkyBolt] ProjectileConfig '%s' not found — sky bolts disabled", BOLT_CONFIG_ID);
            }
        } catch (Exception e) {
            boltConfigFailed = true;
            plugin.getLogger().atWarning().withCause(e).log(
                    "[FrostDragonSkyBolt] Failed to resolve bolt config");
        }
        return cachedBoltConfig;
    }

    @Nullable
    private ProjectileConfig getNovaConfig() {
        if (cachedNovaConfig != null) return cachedNovaConfig;
        if (novaConfigFailed) return null;
        try {
            cachedNovaConfig = ProjectileConfig.getAssetMap().getAsset(NOVA_CONFIG_ID);
            if (cachedNovaConfig == null) {
                novaConfigFailed = true;
                plugin.getLogger().atWarning().log(
                        "[FrostDragonSkyBolt] ProjectileConfig '%s' not found — frost nova disabled", NOVA_CONFIG_ID);
            }
        } catch (Exception e) {
            novaConfigFailed = true;
            plugin.getLogger().atWarning().withCause(e).log(
                    "[FrostDragonSkyBolt] Failed to resolve nova config");
        }
        return cachedNovaConfig;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    /**
     * Despawn all spirits associated with a dragon state.
     */
    private void despawnSpirits(DragonState state, CommandBuffer<EntityStore> commandBuffer) {
        if (state.spiritRefs.isEmpty()) return;
        int count = 0;
        for (Ref<EntityStore> spiritRef : state.spiritRefs) {
            if (spiritRef != null && spiritRef.isValid()) {
                try {
                    commandBuffer.removeEntity(spiritRef, RemoveReason.REMOVE);
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().atFine().log(
                            "[FrostDragonSkyBolt] Failed to despawn spirit");
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().atFine().log(
                    "[FrostDragonSkyBolt] Despawned %d orphaned spirits", count);
        }
        state.spiritRefs.clear();
    }

    /**
     * Force clear all tracking state. Called on plugin shutdown.
     */
    public void forceClear() {
        dragonStates.clear();
        cachedBoltConfig = null;
        cachedNovaConfig = null;
        boltConfigFailed = false;
        novaConfigFailed = false;
        cachedPlayers = null;
        playerCacheTime = 0;
    }
}
