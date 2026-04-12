package endgame.plugin.systems.boss;

import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.managers.boss.GolemVoidBossManager;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;

import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tick system that applies damage to players in the Golem Void danger zone.
 * Runs every tick and checks player distance to active bosses.
 */
public class DangerZoneTickSystem extends EntityTickingSystem<EntityStore> {

    private final EndgameQoL plugin;
    private final GolemVoidBossManager bossManager;
    private final GenericBossManager genericBossManager;
    private final EnrageTracker enrageTracker;

    // Track last cleanup time to periodically clean lastDamageTime map
    private volatile long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 60000; // 1 minute

    // Configuration
    public static final float DANGER_ZONE_RADIUS = 8.0f;
    // Single threshold with hysteresis to avoid gap where boss bar gets stuck
    // Show boss bar when within this distance, hide when beyond
    public static final float BOSS_BAR_DISTANCE_THRESHOLD = 75.0f;
    // Small hysteresis to prevent flickering at boundary
    public static final float BOSS_BAR_HYSTERESIS = 5.0f;
    public static final float TICK_DAMAGE = 3.0f;
    public static final long DAMAGE_INTERVAL_MS = 1000; // 1 second between damage ticks

    // Pre-computed squared thresholds (avoids Math.sqrt() in hot paths)
    private static final double DANGER_ZONE_RADIUS_SQ = DANGER_ZONE_RADIUS * DANGER_ZONE_RADIUS;
    private static final double BOSS_BAR_SHOW_THRESHOLD_SQ = BOSS_BAR_DISTANCE_THRESHOLD * BOSS_BAR_DISTANCE_THRESHOLD;
    private static final double BOSS_BAR_HIDE_THRESHOLD_SQ =
            (BOSS_BAR_DISTANCE_THRESHOLD + BOSS_BAR_HYSTERESIS) * (BOSS_BAR_DISTANCE_THRESHOLD + BOSS_BAR_HYSTERESIS);

    // Track boss bar state per player to implement hysteresis (Golem Void)
    private final Map<UUID, Boolean> playerBossBarState = new ConcurrentHashMap<>();

    // Track boss bar state per player+boss for generic bosses
    // Outer key = playerUuid, Inner key = bossRef directly (Ref has stable equals/hashCode)
    private final Map<UUID, Map<Ref<EntityStore>, Boolean>> genericBossBarState = new ConcurrentHashMap<>();

    // Track last damage time per entity (by player UUID)
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();

    // Frame-rate guard for bossManager.tick() to prevent per-entity spam (per-world)
    private final ConcurrentHashMap<Store<EntityStore>, Long> lastBossTickTimes = new ConcurrentHashMap<>();
    private static final long BOSS_TICK_INTERVAL_MS = 200;


    // Query for players with transform and stats
    private static final Query<EntityStore> QUERY = Query.and(
            TransformComponent.getComponentType(),
            EntityStatMap.getComponentType(),
            Player.getComponentType());

    public DangerZoneTickSystem(EndgameQoL plugin, GolemVoidBossManager bossManager,
                                GenericBossManager genericBossManager, EnrageTracker enrageTracker) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.genericBossManager = genericBossManager;
        this.enrageTracker = enrageTracker;
        plugin.getLogger().atFine().log("[DangerZoneTickSystem] Initialized (radius: %.1f, damage: %.1f)",
                DANGER_ZONE_RADIUS, TICK_DAMAGE);
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Periodic cleanup to prevent memory leak
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            cleanup();
        }

        if (bossManager == null)
            return;

        // Cache config once per tick to avoid repeated indirection
        endgame.plugin.config.EndgameConfig config = plugin.getConfig().get();

        // Call bossManager.tick() with per-store frame-rate guard to prevent per-entity spam
        Long lastBossTime = lastBossTickTimes.get(store);
        if (lastBossTime == null || now - lastBossTime >= BOSS_TICK_INTERVAL_MS) {
            lastBossTickTimes.put(store, now);
            bossManager.tick(store);
            if (genericBossManager != null) {
                genericBossManager.tick(store);
            }
            if (enrageTracker != null) {
                enrageTracker.tick(now);
            }
        }

        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        if (playerRef == null || !playerRef.isValid())
            return;

        // Get player position
        TransformComponent playerTransform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (playerTransform == null)
            return;

        Vector3d playerPos = playerTransform.getPosition();
        if (playerPos == null)
            return;

        long damageNow = now;

        // O(1) direct component lookups — replaces O(n) Universe.getPlayers() loop
        UUID playerUuid = endgame.plugin.utils.EntityUtils.getUuid(archetypeChunk, index);
        if (playerUuid == null) return;
        PlayerRef matchingPlayerRef = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (matchingPlayerRef == null) return;

        // Golem Void boss bar proximity + danger zone damage
        Map<Ref<EntityStore>, GolemVoidBossManager.GolemVoidState> activeBosses = bossManager.getActiveBosses();
        if (activeBosses.isEmpty()) {
            // Self-healing: clear stale Golem Void boss bar state when no bosses exist
            if (!playerBossBarState.isEmpty()) {
                playerBossBarState.clear();
            }
        } else {
            // Check distance to each active Golem Void boss
            for (Map.Entry<Ref<EntityStore>, GolemVoidBossManager.GolemVoidState> entry : activeBosses.entrySet()) {
                Ref<EntityStore> bossRef = entry.getKey();
                GolemVoidBossManager.GolemVoidState state = entry.getValue();

                if (!bossRef.isValid())
                    continue;

                // Skip if boss is in a different store (different world)
                if (bossRef.getStore() != store)
                    continue;

                // Get boss position safely
                TransformComponent bossTransform;
                try {
                    bossTransform = store.getComponent(bossRef, TransformComponent.getComponentType());
                } catch (Exception e) {
                    continue; // Skip this boss if component access fails
                }
                if (bossTransform == null)
                    continue;

                Vector3d bossPos = bossTransform.getPosition();
                if (bossPos == null)
                    continue;

                // Calculate horizontal distance squared (avoids Math.sqrt())
                double dx = playerPos.x - bossPos.x;
                double dz = playerPos.z - bossPos.z;
                double distanceSq = dx * dx + dz * dz;

                // Hysteresis prevents flickering at threshold boundary
                Boolean currentlyShowing = playerBossBarState.get(playerUuid);
                boolean wasShowing = currentlyShowing != null && currentlyShowing;

                if (!wasShowing && distanceSq <= BOSS_BAR_SHOW_THRESHOLD_SQ) {
                    bossManager.showBossBarToPlayer(matchingPlayerRef, store);
                    playerBossBarState.put(playerUuid, true);
                } else if (wasShowing && distanceSq > BOSS_BAR_HIDE_THRESHOLD_SQ) {
                    bossManager.hideBossBarForPlayer(matchingPlayerRef);
                    playerBossBarState.put(playerUuid, false);
                }

                // Check if player is in danger zone - progressive damage from configurable start phase
                int startPhase = config.getBossConfig(endgame.plugin.utils.BossType.GOLEM_VOID).getDangerZoneStartPhase();
                if (state.currentPhase >= startPhase && distanceSq <= DANGER_ZONE_RADIUS_SQ) {
                    Long lastDamage = lastDamageTime.get(playerUuid);

                    if (lastDamage == null || (damageNow - lastDamage) >= DAMAGE_INTERVAL_MS) {
                        // Progressive damage scaling by phase
                        float phaseMult = switch (state.currentPhase) {
                            case 1 -> 0.5f;
                            case 2 -> 1.0f;
                            case 3 -> 2.0f;
                            default -> 0f;
                        };
                        float damageAmount = TICK_DAMAGE * phaseMult;

                        if (damageAmount > 0) {
                            @SuppressWarnings("deprecation")
                            Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.OUT_OF_WORLD, damageAmount);
                            DamageSystems.executeDamage(playerRef, commandBuffer, damage);

                            lastDamageTime.put(playerUuid, damageNow);

                            plugin.getLogger().atFine().log(
                                    "[DangerZone] Player in zone (dist: %.1f, dmg: %.1f, phase: %d, mult: %.1fx)",
                                    Math.sqrt(distanceSq), damageAmount, state.currentPhase, phaseMult);
                        }
                    }
                    break; // Only damage once per tick
                }
            }
        }

        // Generic boss bar proximity (Frost Dragon, Hedera, elites)
        if (genericBossManager != null && genericBossManager.getActiveBosses().isEmpty()) {
            // Self-healing: clear stale generic boss bar state when no generic bosses exist
            if (!genericBossBarState.isEmpty()) {
                genericBossBarState.clear();
            }
        }
        if (genericBossManager != null && matchingPlayerRef != null) {
            for (Map.Entry<Ref<EntityStore>, GenericBossManager.GenericBossState> entry
                    : genericBossManager.getActiveBosses().entrySet()) {
                Ref<EntityStore> gBossRef = entry.getKey();
                if (!gBossRef.isValid()) continue;
                if (gBossRef.getStore() != store) continue;

                TransformComponent gBossTransform;
                try {
                    gBossTransform = store.getComponent(gBossRef, TransformComponent.getComponentType());
                } catch (Exception e) {
                    continue;
                }
                if (gBossTransform == null) continue;
                Vector3d gBossPos = gBossTransform.getPosition();
                if (gBossPos == null) continue;

                double gdx = playerPos.x - gBossPos.x;
                double gdz = playerPos.z - gBossPos.z;
                double gDistanceSq = gdx * gdx + gdz * gdz;

                Map<Ref<EntityStore>, Boolean> playerBossMap =
                        genericBossBarState.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>());
                Boolean gShowing = playerBossMap.get(gBossRef);
                boolean gWasShowing = gShowing != null && gShowing;

                if (!gWasShowing && gDistanceSq <= BOSS_BAR_SHOW_THRESHOLD_SQ) {
                    genericBossManager.showBossBarToPlayer(matchingPlayerRef, gBossRef, store);
                    playerBossMap.put(gBossRef, true);
                } else if (gWasShowing && gDistanceSq > BOSS_BAR_HIDE_THRESHOLD_SQ) {
                    genericBossManager.hideBossBarForPlayer(matchingPlayerRef);
                    playerBossMap.put(gBossRef, false);
                }
            }
        }
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null; // Run in default group (synchronous) to safely access other entities
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        lastDamageTime.entrySet().removeIf(e -> (now - e.getValue()) > 60000);

        // Clear Golem Void boss bar state when no bosses are active
        if (bossManager.getActiveBosses().isEmpty() && !playerBossBarState.isEmpty()) {
            playerBossBarState.clear();
        }

        // Clear generic boss bar state when no generic bosses are active
        if (genericBossManager != null && genericBossManager.getActiveBosses().isEmpty()
                && !genericBossBarState.isEmpty()) {
            genericBossBarState.clear();
        }

        // Periodic cleanup of invalid Ref keys from genericBossBarState (prevents slow memory leak)
        if (!genericBossBarState.isEmpty()) {
            genericBossBarState.values().forEach(innerMap ->
                    innerMap.keySet().removeIf(ref -> !ref.isValid()));
            genericBossBarState.values().removeIf(Map::isEmpty);
        }

        // EndgameSpawnNPC interaction stale caster cleanup
        endgame.plugin.systems.npc.EndgameSpawnNPCInteraction.cleanupStaleEntries();

        // Command rate limit cleanup
        endgame.plugin.utils.CommandRateLimit.cleanup();
    }

    /**
     * Clear all tracking state for a specific player.
     * Called on disconnect or world leave to prevent stale hysteresis entries.
     */
    public void clearPlayerState(UUID playerUuid) {
        if (playerUuid == null) return;
        playerBossBarState.remove(playerUuid);
        lastDamageTime.remove(playerUuid);
        genericBossBarState.remove(playerUuid);
    }

    /**
     * Force clear all tracking state. Called on plugin shutdown or boss death.
     */
    public void forceClear() {
        lastDamageTime.clear();
        playerBossBarState.clear();
        genericBossBarState.clear();
        lastBossTickTimes.clear();
    }
}
