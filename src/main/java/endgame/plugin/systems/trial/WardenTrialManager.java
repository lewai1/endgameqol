package endgame.plugin.systems.trial;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.LabelBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages Warden Challenge wave encounters on a per-player basis.
 * Supports 4 difficulty tiers (I–IV) with tier-specific wave compositions and rewards.
 *
 * State machine:
 * IDLE → COUNTDOWN(3s) → SPAWNING(poll-wait) → ACTIVE → WAVE_CLEAR → INTERVAL(8s) → SPAWNING → ... → COMPLETED / FAILED
 *
 * Uses Ref<EntityStore> directly as map keys (equals/hashCode), matching GolemVoidBossManager pattern.
 * NEVER use ref.toString() as key — toString() is not stable across different Ref instances for the same entity.
 */
public class WardenTrialManager {

    // === State machine phases ===
    public enum TrialPhase {
        COUNTDOWN, SPAWNING, ACTIVE, WAVE_CLEAR, INTERVAL, COMPLETED, FAILED
    }

    // === Wave definition ===
    private record WaveDef(String[] npcTypes, int[] counts) {
        int totalEnemies() {
            int total = 0;
            for (int c : counts) total += c;
            return total;
        }
    }

    // Tier I (Adamantite level) — basic overworld mobs, intro ranged variety
    private static final WaveDef[] TIER_1_WAVES = {
        new WaveDef(new String[]{"Goblin_Scrapper", "Skeleton_Archer"}, new int[]{3, 2}),
        new WaveDef(new String[]{"Skeleton_Soldier", "Skeleton_Mage", "Spider"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Hyena", "Goblin_Lobber", "Skeleton_Ranger"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Outlander_Brute", "Skeleton_Archmage", "Skeleton_Soldier"}, new int[]{1, 1, 2}),
        new WaveDef(new String[]{"Toad_Rhino", "Outlander_Hunter", "Skeleton_Knight"}, new int[]{1, 2, 2})
    };

    // Tier II (Mithril level) — ranged-heavy, Final boss: Endgame_Goblin_Duke
    private static final WaveDef[] TIER_2_WAVES = {
        new WaveDef(new String[]{"Trork_Brawler", "Skeleton_Ranger", "Trork_Hunter"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Outlander_Marauder", "Outlander_Stalker", "Skeleton_Archmage"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Tiger_Sabertooth", "Trork_Sentry", "Skeleton_Mage"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Endgame_Saurian_Warrior", "Endgame_Ghoul", "Outlander_Sorcerer"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Endgame_Goblin_Duke", "Endgame_Saurian_Hunter", "Skeleton_Burnt_Wizard", "Endgame_Werewolf"}, new int[]{1, 1, 1, 1})
    };

    // Tier III (Onyxium level) — sand/burnt skeletons, Final boss: Endgame_Necromancer_Void
    private static final WaveDef[] TIER_3_WAVES = {
        new WaveDef(new String[]{"Endgame_Saurian_Rogue", "Skeleton_Sand_Mage", "Endgame_Ghoul"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Endgame_Werewolf", "Skeleton_Burnt_Gunner", "Skeleton_Burnt_Wizard"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Endgame_Goblin_Duke", "Endgame_Saurian_Warrior", "Skeleton_Sand_Archmage", "Skeleton_Burnt_Gunner"}, new int[]{1, 1, 1, 1}),
        new WaveDef(new String[]{"Endgame_Shadow_Knight", "Skeleton_Burnt_Gunner", "Golem_Eye_Void"}, new int[]{2, 2, 1}),
        new WaveDef(new String[]{"Endgame_Necromancer_Void", "Endgame_Shadow_Knight", "Skeleton_Sand_Archmage", "Skeleton_Burnt_Gunner"}, new int[]{1, 1, 1, 2})
    };

    // Tier IV (Prisma level) — elite ranged + bosses, Final boss: Endgame_Shadow_Knight
    private static final WaveDef[] TIER_4_WAVES = {
        new WaveDef(new String[]{"Endgame_Goblin_Duke", "Endgame_Necromancer_Void", "Skeleton_Burnt_Gunner", "Skeleton_Burnt_Wizard"}, new int[]{1, 1, 2, 1}),
        new WaveDef(new String[]{"Alpha_Rex", "Endgame_Werewolf", "Skeleton_Burnt_Wizard", "Golem_Eye_Void"}, new int[]{1, 1, 2, 1}),
        new WaveDef(new String[]{"Endgame_Necromancer_Void", "Alpha_Rex", "Skeleton_Sand_Archmage", "Golem_Eye_Void"}, new int[]{1, 1, 2, 2}),
        new WaveDef(new String[]{"Alpha_Rex", "Endgame_Goblin_Duke", "Skeleton_Burnt_Gunner"}, new int[]{2, 1, 2}),
        new WaveDef(new String[]{"Endgame_Shadow_Knight", "Alpha_Rex", "Skeleton_Sand_Archmage", "Skeleton_Burnt_Gunner", "Golem_Eye_Void", "Endgame_Necromancer_Void"}, new int[]{1, 1, 1, 1, 1, 1})
    };

    private static final WaveDef[][] TIER_WAVES = {TIER_1_WAVES, TIER_2_WAVES, TIER_3_WAVES, TIER_4_WAVES};
    private static final String[] TIER_ROMAN = {"I", "II", "III", "IV"};
    // RPG Leveling forced mob levels per tier (overrides zone-based calculation)
    private static final int[] TIER_MOB_LEVELS = {60, 70, 80, 90};
    private static final long COUNTDOWN_MS = 3000;
    private static final long INTERVAL_MS = 8000;
    private static final float SPAWN_RADIUS = 6.0f;
    private static final long HUD_REFRESH_MS = 200;
    private static final long SPAWN_TIMEOUT_MS = 5000;
    private static final long STUCK_CHECK_INTERVAL_MS = 5000;
    private static final long STUCK_CHECK_GRACE_MS = 10000; // don't check stuck until 10s into wave
    private static final double STUCK_Y_THRESHOLD = 40.0; // below spawn Y
    private static final long WAVE_TIMEOUT_MS = 600_000; // 10 minutes
    private static final long ZONE_PARTICLE_INTERVAL_MS = 1500;
    // Custom .particlesystem referencing vanilla spawners (Fire_AoE_Spawn_Circle) at 4 heights.
    // Custom system loads on client via asset pack; vanilla spawner IDs resolve normally.
    private static final String ZONE_PARTICLE = "Warden_Trial_Zone";
    private static final float ZONE_PARTICLE_SCALE = 16.0f;
    private static final double ZONE_PARTICLE_Y_OFFSET = -0.3;
    private static final int PROGRESS_BAR_WIDTH = 300;
    private static final int TOTAL_HUD_WIDTH = 380;

    // Wave difficulty colors (indexed 0-4 matching wave index)
    private static final String[] WAVE_COLORS = {"#55ff55", "#ffff55", "#ffaa33", "#ff5555", "#cc55ff"};
    private static final String[] WAVE_BAR_COLORS = {"#44cc44", "#cccc44", "#cc8833", "#cc4444", "#aa44cc"};
    private static final String[] WAVE_DIFFICULTY_NAMES = {"Easy", "Moderate", "Hard", "Deadly", "EXTREME"};

    // === Per-player trial state ===
    public static class TrialState {
        final UUID playerUuid;
        final PlayerRef playerRef;
        final Vector3d position;
        final int tier; // 1-4
        volatile int currentWave; // 0-indexed
        volatile TrialPhase phase;
        // Use Ref directly as key — equals()/hashCode() are stable, toString() is NOT
        final Set<Ref<EntityStore>> aliveNpcRefs = ConcurrentHashMap.newKeySet();
        volatile long phaseStartTime;
        volatile int totalEnemiesInWave;
        volatile int totalKilledInWave;
        volatile boolean spawnRequested;
        long spawnRequestTime;
        volatile HyUIHud hud;
        volatile boolean hudRemoved;
        volatile boolean pendingHudRemoval;
        volatile boolean playerAlive = true;
        long lastZoneParticleTime;
        long lastStuckCheckTime;
        boolean zoneParticleLoggedOnce;

        TrialState(UUID playerUuid, PlayerRef playerRef, Vector3d position, int tier) {
            this.playerUuid = playerUuid;
            this.playerRef = playerRef;
            this.position = new Vector3d(position);
            this.tier = Math.max(1, Math.min(4, tier));
            this.currentWave = 0;
            this.phase = TrialPhase.COUNTDOWN;
            this.phaseStartTime = System.currentTimeMillis();
            this.totalEnemiesInWave = 0;
            this.totalKilledInWave = 0;
            this.spawnRequested = false;
            this.hudRemoved = false;
            this.pendingHudRemoval = false;
        }
    }

    // === Fields ===
    private final EndgameQoL plugin;
    private final ConcurrentHashMap<UUID, TrialState> activeTrials = new ConcurrentHashMap<>();
    // Global reverse lookup: NPC ref → player UUID (for death system)
    // Uses Ref directly as key — equals()/hashCode() match same entity across different Ref instances
    private final ConcurrentHashMap<Ref<EntityStore>, UUID> npcToPlayerMap = new ConcurrentHashMap<>();

    public WardenTrialManager(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    // === Public API ===

    public boolean hasActiveTrial(UUID playerUuid) {
        return activeTrials.containsKey(playerUuid);
    }

    public int getActiveTrialCount() {
        return activeTrials.size();
    }

    public boolean isTrackedNpc(Ref<EntityStore> ref) {
        return npcToPlayerMap.containsKey(ref);
    }

    /**
     * Start a new challenge for the given player at the specified tier (1-4).
     */
    public void startTrial(UUID playerUuid, PlayerRef playerRef, Ref<EntityStore> ref,
                           Vector3d position, Store<EntityStore> store, int tier) {
        if (activeTrials.containsKey(playerUuid)) return;

        TrialState state = new TrialState(playerUuid, playerRef, position, tier);
        activeTrials.put(playerUuid, state);

        plugin.getLogger().atFine().log("[WardenChallenge] Tier %s started for %s at %.0f, %.0f, %.0f",
                TIER_ROMAN[state.tier - 1], playerUuid, position.x, position.y, position.z);

        showTrialHud(state);
        sendMessage(playerRef, "#FFD700",
                "[Warden Challenge " + TIER_ROMAN[state.tier - 1] + "] Prepare yourself! Challenge begins in 3 seconds...");
    }

    /**
     * Called by WardenTrialDeathSystem when a tracked NPC dies.
     * Uses Ref directly (not toString()) for reliable equality matching.
     */
    public void onEnemyDeath(Ref<EntityStore> npcRef) {
        UUID playerUuid = npcToPlayerMap.remove(npcRef);
        if (playerUuid == null) return;

        TrialState state = activeTrials.get(playerUuid);
        if (state == null) return;

        // Synchronize compound check-then-act to prevent race between death system and tick thread
        synchronized (state) {
            state.aliveNpcRefs.remove(npcRef);
            state.totalKilledInWave++;

            plugin.getLogger().atFine().log("[WardenChallenge] Enemy died for %s, remaining: %d/%d",
                    playerUuid, state.aliveNpcRefs.size(), state.totalEnemiesInWave);

            // Check if wave is clear
            if (state.aliveNpcRefs.isEmpty() && state.phase == TrialPhase.ACTIVE) {
                state.phase = TrialPhase.WAVE_CLEAR;
                state.phaseStartTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * Tick all active trials. Called from WardenTrialTickSystem with rate limiting.
     */
    public void tick(Store<EntityStore> store) {
        if (activeTrials.isEmpty()) return;

        // Process deferred HUD removals first (NEVER call hud.remove() inside onRefresh)
        for (TrialState state : activeTrials.values()) {
            if (state.pendingHudRemoval) {
                state.pendingHudRemoval = false;
                state.hudRemoved = true;
                if (state.hud != null) {
                    try { state.hud.remove(); } catch (Exception | NoClassDefFoundError ignored) {}
                    state.hud = null;
                }
            }
        }

        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, TrialState> entry : activeTrials.entrySet()) {
            TrialState state = entry.getValue();
            try {
                tickTrial(state, store, now);
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[WardenChallenge] Error ticking trial for %s: %s",
                        state.playerUuid, e.getMessage());
            }
        }
    }

    /**
     * Fail a trial for the given player (death, disconnect, leave world, etc.)
     * Despawns all remaining trial NPCs.
     */
    public void failTrial(UUID playerUuid) {
        TrialState state = activeTrials.remove(playerUuid);
        if (state == null) return;

        state.phase = TrialPhase.FAILED;

        // Collect NPC refs to despawn, then clean up tracking
        List<Ref<EntityStore>> npcsToRemove = new ArrayList<>(state.aliveNpcRefs);
        for (Ref<EntityStore> ref : npcsToRemove) {
            npcToPlayerMap.remove(ref);
        }
        state.aliveNpcRefs.clear();

        // Despawn remaining NPCs on world thread
        // Use each NPC's own world (not the player's) to handle disconnect cases
        // where the player's world ref is already null
        if (!npcsToRemove.isEmpty()) {
            // Group NPCs by world to minimize world.execute() calls
            Map<World, List<Ref<EntityStore>>> npcsByWorld = new HashMap<>();
            for (Ref<EntityStore> npcRef : npcsToRemove) {
                try {
                    if (!npcRef.isValid()) continue;
                    World npcWorld = npcRef.getStore().getExternalData().getWorld();
                    if (npcWorld != null) {
                        npcsByWorld.computeIfAbsent(npcWorld, k -> new ArrayList<>()).add(npcRef);
                    }
                } catch (Exception e) {
                    plugin.getLogger().atFine().log(
                            "[WardenChallenge] Failed to resolve NPC world: %s", e.getMessage());
                }
            }

            for (Map.Entry<World, List<Ref<EntityStore>>> worldEntry : npcsByWorld.entrySet()) {
                World world = worldEntry.getKey();
                List<Ref<EntityStore>> refs = worldEntry.getValue();
                if (!world.isAlive()) continue;
                world.execute(() -> {
                    for (Ref<EntityStore> npcRef : refs) {
                        try {
                            if (npcRef.isValid()) {
                                Store<EntityStore> npcStore = npcRef.getStore();
                                npcStore.removeEntity(npcRef,
                                        EntityStore.REGISTRY.newHolder(), RemoveReason.REMOVE);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().atFine().log(
                                    "[WardenChallenge] Failed to despawn NPC: %s", e.getMessage());
                        }
                    }
                });
            }
            plugin.getLogger().atFine().log(
                    "[WardenChallenge] Despawned %d remaining NPCs for %s",
                    npcsToRemove.size(), playerUuid);
        }

        hideTrialHud(state);

        sendMessage(state.playerRef, "#FF5555",
                "[Warden Challenge " + TIER_ROMAN[state.tier - 1] + "] Challenge failed!");
        plugin.getLogger().atFine().log("[WardenChallenge] Tier %s failed for %s",
                TIER_ROMAN[state.tier - 1], playerUuid);
    }

    /**
     * Shutdown cleanup — despawn all trial NPCs and clear all state.
     */
    public void forceClear() {
        // Despawn all tracked trial NPCs before clearing state
        for (TrialState state : activeTrials.values()) {
            hideTrialHud(state);
            for (Ref<EntityStore> npcRef : state.aliveNpcRefs) {
                try {
                    if (!npcRef.isValid()) continue;
                    World npcWorld = npcRef.getStore().getExternalData().getWorld();
                    if (npcWorld != null) {
                        npcWorld.execute(() -> {
                            try {
                                if (npcRef.isValid()) {
                                    npcRef.getStore().removeEntity(npcRef,
                                            EntityStore.REGISTRY.newHolder(), RemoveReason.REMOVE);
                                }
                            } catch (Exception ignored) {}
                        });
                    }
                } catch (Exception ignored) {}
            }
            state.aliveNpcRefs.clear();
        }
        activeTrials.clear();
        npcToPlayerMap.clear();
    }

    // === State machine ===

    private void tickTrial(TrialState state, Store<EntityStore> store, long now) {
        long elapsed = now - state.phaseStartTime;
        int totalWaves = TIER_WAVES[state.tier - 1].length;
        String prefix = "[Warden Challenge " + TIER_ROMAN[state.tier - 1] + "]";

        // Spawn zone boundary particle periodically (expires naturally after 4s)
        if (now - state.lastZoneParticleTime >= ZONE_PARTICLE_INTERVAL_MS) {
            state.lastZoneParticleTime = now;
            spawnZoneParticle(state, store);
        }

        switch (state.phase) {
            case COUNTDOWN -> {
                if (elapsed >= COUNTDOWN_MS) {
                    state.phase = TrialPhase.SPAWNING;
                    state.phaseStartTime = now;
                    state.spawnRequested = false;
                    sendMessage(state.playerRef, "#FF5555",
                            prefix + " Wave " + (state.currentWave + 1) + "/" + totalWaves + " — Fight!");
                }
            }
            case SPAWNING -> {
                if (!state.spawnRequested) {
                    state.spawnRequested = true;
                    state.spawnRequestTime = now;
                    spawnWave(state, store);
                } else if (!state.aliveNpcRefs.isEmpty()) {
                    state.spawnRequested = false;
                    state.phase = TrialPhase.ACTIVE;
                    state.phaseStartTime = now;
                } else if (now - state.spawnRequestTime > SPAWN_TIMEOUT_MS) {
                    plugin.getLogger().atWarning().log("[WardenChallenge] Spawn timeout for %s tier %d wave %d",
                            state.playerUuid, state.tier, state.currentWave + 1);
                    failTrial(state.playerUuid);
                }
            }
            case ACTIVE -> {
                updatePlayerAlive(state);
                if (!state.playerAlive) {
                    failTrial(state.playerUuid);
                    return;
                }
                // Periodic stuck NPC cleanup (skip during grace period to let NPCs land)
                if (elapsed >= STUCK_CHECK_GRACE_MS
                        && now - state.lastStuckCheckTime >= STUCK_CHECK_INTERVAL_MS) {
                    state.lastStuckCheckTime = now;
                    cleanupStuckNpcs(state);
                }
                // Per-tier wave time limit — fail the trial if exceeded
                int timeLimitSec = plugin.getConfig().get().getWardenTrialTimer(state.tier - 1);
                if (timeLimitSec > 0 && elapsed >= timeLimitSec * 1000L && !state.aliveNpcRefs.isEmpty()) {
                    sendMessage(state.playerRef, "#FF5555",
                            prefix + " Time's up! Wave " + (state.currentWave + 1) + " not cleared in " + timeLimitSec + "s.");
                    plugin.getLogger().atFine().log(
                            "[WardenChallenge] Wave %d timed out for %s (%d NPCs remaining, limit %ds) — failing",
                            state.currentWave + 1, state.playerUuid, state.aliveNpcRefs.size(), timeLimitSec);
                    failTrial(state.playerUuid);
                    return;
                }
                // Fallback safety net (10 min hard limit for stuck NPCs)
                if (elapsed >= WAVE_TIMEOUT_MS && !state.aliveNpcRefs.isEmpty()) {
                    plugin.getLogger().atWarning().log(
                            "[WardenChallenge] Wave %d hard-timed out for %s (%d NPCs remaining) — forcing clear",
                            state.currentWave + 1, state.playerUuid, state.aliveNpcRefs.size());
                    forceWaveClear(state);
                }
            }
            case WAVE_CLEAR -> {
                state.currentWave++;
                if (state.currentWave >= totalWaves) {
                    completeTrial(state, store);
                } else {
                    state.phase = TrialPhase.INTERVAL;
                    state.phaseStartTime = now;
                    state.totalKilledInWave = 0;
                    sendMessage(state.playerRef, "#55FF55",
                            prefix + " Wave cleared! Next wave in 8 seconds...");
                    rebuildHud(state);
                }
            }
            case INTERVAL -> {
                if (elapsed >= INTERVAL_MS) {
                    state.phase = TrialPhase.SPAWNING;
                    state.phaseStartTime = now;
                    state.spawnRequested = false;
                    sendMessage(state.playerRef, "#FF5555",
                            prefix + " Wave " + (state.currentWave + 1) + "/" + totalWaves + " — Fight!");
                }
            }
            case COMPLETED, FAILED -> {
                activeTrials.remove(state.playerUuid);
            }
        }
    }

    // === Spawning ===

    private void spawnWave(TrialState state, Store<EntityStore> store) {
        WaveDef wave = TIER_WAVES[state.tier - 1][state.currentWave];
        state.aliveNpcRefs.clear();
        state.totalEnemiesInWave = wave.totalEnemies();
        state.totalKilledInWave = 0;

        World world = findWorldForPlayer(state.playerRef);
        if (world == null) {
            plugin.getLogger().atWarning().log("[WardenChallenge] Cannot find world for player %s", state.playerUuid);
            failTrial(state.playerUuid);
            return;
        }

        if (!world.isAlive()) return;
        world.execute(() -> {
            try {
                // Resolve the player's actual store on the world thread —
                // the 'store' parameter from tick() belongs to the default world,
                // but the player may be in an instance world
                Ref<EntityStore> playerRef = state.playerRef.getReference();
                if (playerRef == null || !playerRef.isValid()) return;
                Store<EntityStore> playerStore = playerRef.getStore();

                for (int typeIdx = 0; typeIdx < wave.npcTypes().length; typeIdx++) {
                    String npcType = wave.npcTypes()[typeIdx];
                    int count = wave.counts()[typeIdx];

                    for (int i = 0; i < count; i++) {
                        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                        double radius = SPAWN_RADIUS * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
                        double spawnX = state.position.x + Math.cos(angle) * radius;
                        double spawnZ = state.position.z + Math.sin(angle) * radius;
                        Vector3d spawnPos = new Vector3d(spawnX, state.position.y, spawnZ);

                        Vector3f rotation = new Vector3f(0, (float) (ThreadLocalRandom.current().nextDouble() * 360.0), 0);

                        var spawnResult = NPCPlugin.get().spawnNPC(
                                playerStore, npcType, null, spawnPos, rotation);

                        if (spawnResult != null) {
                            Ref<EntityStore> npcRef = spawnResult.left();
                            state.aliveNpcRefs.add(npcRef);
                            npcToPlayerMap.put(npcRef, state.playerUuid);

                            // Prevent Hytale's automatic despawn system from removing trial mobs
                            NPCEntity npcEntity = playerStore.getComponent(npcRef, NPCEntity.getComponentType());
                            if (npcEntity != null) {
                                npcEntity.setDespawning(false);
                            }

                            // Force RPG Leveling mob level per tier (prevents zone-based exploit)
                            if (plugin.isRPGLevelingActive()) {
                                plugin.getRpgLevelingBridge().setMobLevel(
                                        playerStore, npcRef, TIER_MOB_LEVELS[state.tier - 1]);
                            }

                            plugin.getLogger().atFine().log("[WardenChallenge] Spawned %s for wave %d",
                                    npcType, state.currentWave + 1);
                        } else {
                            plugin.getLogger().atWarning().log("[WardenChallenge] Failed to spawn %s for wave %d",
                                    npcType, state.currentWave + 1);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[WardenChallenge] Error spawning wave %d: %s",
                        state.currentWave + 1, e.getMessage());
            }
        });
    }

    // === Completion ===

    private void completeTrial(TrialState state, Store<EntityStore> store) {
        state.phase = TrialPhase.COMPLETED;
        activeTrials.remove(state.playerUuid);

        // Defensive cleanup: remove any remaining refs from global map
        // (should already be empty via onEnemyDeath, but guards against edge cases)
        for (Ref<EntityStore> ref : state.aliveNpcRefs) {
            npcToPlayerMap.remove(ref);
        }
        state.aliveNpcRefs.clear();

        hideTrialHud(state);
        giveRewards(state, store);

        sendMessage(state.playerRef, "#55FF55",
                "[Warden Challenge " + TIER_ROMAN[state.tier - 1] + "] CHALLENGE COMPLETE! Rewards added to your inventory.");

        // Award XP on trial completion (tier * base = 150, 300, 450, 600)
        int wardenXpBase = plugin.getConfig().get().getElWardenXpBase();
        int trialXp = state.tier * wardenXpBase;
        try {
            if (plugin.isRPGLevelingActive()) {
                plugin.getRpgLevelingBridge().addXP(state.playerUuid, trialXp, "WARDEN_TRIAL");
            }
            if (plugin.isEndlessLevelingActive()) {
                plugin.getEndlessLevelingBridge().addXP(state.playerUuid, trialXp, "WARDEN_TRIAL");
            }
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[WardenChallenge] Failed to award XP: %s", e.getMessage());
        }

        // Bounty hook: notify bounty manager of trial completion
        try {
            var bountyManager = plugin.getBountyManager();
            if (bountyManager != null) {
                bountyManager.onTrialComplete(state.playerUuid, state.tier);
            }
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[WardenChallenge] Could not notify bounty of trial complete: %s", e.getMessage());
        }

        plugin.getLogger().atFine().log("[WardenChallenge] Tier %s completed for %s",
                TIER_ROMAN[state.tier - 1], state.playerUuid);
    }

    private void giveRewards(TrialState state, Store<EntityStore> store) {
        World world = findWorldForPlayer(state.playerRef);
        if (world == null || !world.isAlive()) return;

        world.execute(() -> {
            try {
                Ref<EntityStore> playerRef = state.playerRef.getReference();
                if (playerRef == null || !playerRef.isValid()) return;

                Store<EntityStore> playerStore = playerRef.getStore();
                Player player = playerStore.getComponent(playerRef, Player.getComponentType());
                if (player == null) return;

                String dropTable = "Endgame_Drop_Warden_Challenge_" + TIER_ROMAN[state.tier - 1];
                List<ItemStack> rewards = ItemModule.get().getRandomItemDrops(dropTable);
                if (rewards == null || rewards.isEmpty()) {
                    plugin.getLogger().atWarning().log("[WardenChallenge] No rewards generated from %s", dropTable);
                    return;
                }

                for (ItemStack item : rewards) {
                    ItemStackTransaction transaction = player.giveItem(item, playerRef, playerStore);
                    ItemStack remainder = transaction.getRemainder();
                    if (remainder != null && !remainder.isEmpty()) {
                        // Inventory full — drop overflow on the ground
                        ItemUtils.dropItem(playerRef, remainder, playerStore);
                    }
                }

                plugin.getLogger().atFine().log("[WardenChallenge] Gave %d reward items (tier %s) to %s",
                        rewards.size(), TIER_ROMAN[state.tier - 1], state.playerUuid);
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[WardenChallenge] Error giving rewards: %s", e.getMessage());
            }
        });
    }

    // === HyUI HUD ===

    private void showTrialHud(TrialState state) {
        try {
            String html = buildTrialHudHtml(state);
            state.hudRemoved = false;
            state.pendingHudRemoval = false;
            state.hud = HudBuilder.hudForPlayer(state.playerRef)
                    .fromHtml(html)
                    .withRefreshRate(HUD_REFRESH_MS)
                    .onRefresh(h -> {
                        if (state.hudRemoved) return;
                        TrialState current = activeTrials.get(state.playerUuid);
                        if (current == null || current.hud != h) {
                            // Trial ended or HUD replaced — defer removal, NEVER call h.remove() here
                            state.pendingHudRemoval = true;
                            return;
                        }
                        doRefreshHud(current, h);
                    })
                    .show();
        } catch (NoClassDefFoundError e) {
            // HyUI not installed — trial works without HUD
            plugin.getLogger().atFine().log("[WardenChallenge] HyUI not available, running without HUD");
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[WardenChallenge] Failed to show HUD: %s", e.getMessage());
        }
    }

    /**
     * Rebuild HUD with new wave colors. Called on wave transitions.
     * Since HyUI can't change CSS colors dynamically, we remove + recreate.
     */
    private void rebuildHud(TrialState state) {
        state.hudRemoved = true;
        if (state.hud != null) {
            try { state.hud.remove(); } catch (Exception | NoClassDefFoundError ignored) {}
            state.hud = null;
        }
        showTrialHud(state);
    }

    private void doRefreshHud(TrialState state, HyUIHud hud) {
        try {
            int totalWaves = TIER_WAVES[state.tier - 1].length;
            int waveIdx = Math.min(state.currentWave, totalWaves - 1);
            int waveNum = waveIdx + 1;
            int killed = state.totalKilledInWave;
            int total = state.totalEnemiesInWave;
            float progress = total > 0 ? Math.max(0f, Math.min(1f, (float) killed / total)) : 0f;
            int barFillWidth = Math.round(PROGRESS_BAR_WIDTH * progress);

            long now = System.currentTimeMillis();
            long elapsed = now - state.phaseStartTime;

            String killText;
            String statusText;

            switch (state.phase) {
                case COUNTDOWN -> {
                    long remaining = Math.max(0, COUNTDOWN_MS - elapsed);
                    int secs = (int) Math.ceil(remaining / 1000.0);
                    killText = "Preparing...";
                    statusText = "Starting in " + secs + "...";
                }
                case SPAWNING -> {
                    killText = "Summoning enemies...";
                    statusText = "Spawning...";
                }
                case ACTIVE -> {
                    killText = killed + " / " + total + " defeated";
                    int timeLimitSec = plugin.getConfig().get().getWardenTrialTimer(state.tier - 1);
                    if (timeLimitSec > 0) {
                        long remainingMs = Math.max(0, timeLimitSec * 1000L - elapsed);
                        int remainingSec = (int) Math.ceil(remainingMs / 1000.0);
                        statusText = remainingSec <= 10
                                ? "\u00A7c" + remainingSec + "s remaining!"
                                : remainingSec + "s remaining";
                    } else {
                        statusText = "FIGHT!";
                    }
                }
                case WAVE_CLEAR -> {
                    killText = total + " / " + total + " defeated";
                    statusText = "Wave cleared!";
                }
                case INTERVAL -> {
                    long remaining = Math.max(0, INTERVAL_MS - elapsed);
                    int secs = (int) Math.ceil(remaining / 1000.0);
                    killText = "Prepare yourself...";
                    statusText = "Next wave in " + secs + "s...";
                }
                default -> {
                    killText = "";
                    statusText = "";
                }
            }

            final String fWaveText = "Wave " + waveNum + " / " + totalWaves;
            final String fDiffText = WAVE_DIFFICULTY_NAMES[waveIdx];
            final String fKillText = killText;
            final String fStatusText = statusText;
            final int fBarWidth = barFillWidth;

            try { hud.getById("wave-info", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fWaveText)); } catch (Exception ignored) {}
            try { hud.getById("difficulty-label", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fDiffText)); } catch (Exception ignored) {}
            try { hud.getById("kill-text", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fKillText)); } catch (Exception ignored) {}
            try { hud.getById("status-text", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fStatusText)); } catch (Exception ignored) {}
            try { hud.getById("progress-bar-fill", GroupBuilder.class)
                    .ifPresent(b -> b.withAnchor(new HyUIAnchor()
                            .setWidth(fBarWidth).setHeight(12).setLeft(0).setTop(0))); } catch (Exception ignored) {}
        } catch (Exception e) {
            // Non-fatal — HUD refresh errors should not crash the trial
        }
    }

    private void hideTrialHud(TrialState state) {
        state.hudRemoved = true;
        state.pendingHudRemoval = false;
        if (state.hud != null) {
            try { state.hud.remove(); } catch (Exception | NoClassDefFoundError ignored) {}
            state.hud = null;
        }
    }

    private String buildTrialHudHtml(TrialState state) {
        int totalWaves = TIER_WAVES[state.tier - 1].length;
        int waveIdx = Math.min(state.currentWave, totalWaves - 1);
        String waveColor = WAVE_COLORS[waveIdx];
        String barColor = WAVE_BAR_COLORS[waveIdx];
        String diffName = WAVE_DIFFICULTY_NAMES[waveIdx];
        int waveNum = waveIdx + 1;
        String title = "WARDEN CHALLENGE " + TIER_ROMAN[state.tier - 1];

        return String.format("""
            <style>
                #trial-container {
                    vertical-align: center;
                    anchor-right: 16;
                    anchor-width: %d;
                    anchor-height: 130;
                    padding: 8;
                    layout-mode: Top;
                    background-color: rgba(10, 10, 18, 0.75);
                    border-radius: 6;
                }
                #trial-title {
                    font-size: 16;
                    font-weight: bold;
                    color: #FFD700;
                    text-align: center;
                    anchor-height: 20;
                    anchor-width: 100%%;
                }
                #wave-info {
                    font-size: 13;
                    font-weight: bold;
                    color: %s;
                    text-align: center;
                    anchor-height: 18;
                    anchor-width: 100%%;
                    margin-top: 2;
                }
                #difficulty-label {
                    font-size: 11;
                    color: %s;
                    text-align: center;
                    anchor-height: 14;
                    anchor-width: 100%%;
                    margin-top: 1;
                }
                #progress-bar-bg {
                    anchor-width: %d;
                    anchor-height: 12;
                    background-color: #1a1a2a;
                    margin-top: 6;
                    horizontal-align: center;
                }
                #progress-bar-fill {
                    anchor-width: 0;
                    anchor-height: 12;
                    background-color: %s;
                    anchor-left: 0;
                    anchor-top: 0;
                }
                #kill-text {
                    font-size: 11;
                    color: #cccccc;
                    text-align: center;
                    anchor-height: 14;
                    anchor-width: 100%%;
                    margin-top: 4;
                }
                #status-text {
                    font-size: 12;
                    font-weight: bold;
                    color: #ffff55;
                    text-align: center;
                    anchor-height: 16;
                    anchor-width: 100%%;
                    margin-top: 2;
                }
            </style>
            <div id="trial-container">
                <p id="trial-title">%s</p>
                <p id="wave-info">Wave %d / %d</p>
                <p id="difficulty-label">%s</p>
                <div id="progress-bar-bg">
                    <div id="progress-bar-fill"></div>
                </div>
                <p id="kill-text">Preparing...</p>
                <p id="status-text">Starting in 3...</p>
            </div>
            """,
            TOTAL_HUD_WIDTH,
            waveColor,
            waveColor,
            PROGRESS_BAR_WIDTH,
            barColor,
            title,
            waveNum, totalWaves,
            diffName
        );
    }

    // === Stuck NPC handling ===

    /**
     * Check all alive NPC refs for stale/stuck entities:
     * - Invalid refs (entity already removed by engine, e.g. void death)
     * - NPCs that fell far below the spawn position
     * Force-removes them and counts as killed.
     */
    private void cleanupStuckNpcs(TrialState state) {
        List<Ref<EntityStore>> toRemove = new ArrayList<>();

        for (Ref<EntityStore> npcRef : state.aliveNpcRefs) {
            // Check 1: ref no longer valid (entity removed by engine)
            if (!npcRef.isValid()) {
                toRemove.add(npcRef);
                continue;
            }

            // Check 2: NPC fell below spawn Y threshold
            try {
                Store<EntityStore> npcStore = npcRef.getStore();
                TransformComponent transform = npcStore.getComponent(npcRef, TransformComponent.getComponentType());
                if (transform != null) {
                    double npcY = transform.getPosition().y;
                    if (npcY < state.position.y - STUCK_Y_THRESHOLD) {
                        toRemove.add(npcRef);
                    }
                }
            } catch (Exception ignored) {
                // Can't read position — skip, don't assume stuck (may be mid-spawn)
            }
        }

        if (toRemove.isEmpty()) return;

        plugin.getLogger().atWarning().log("[WardenChallenge] Cleaning up %d stuck/fallen NPCs for %s",
                toRemove.size(), state.playerUuid);

        synchronized (state) {
            for (Ref<EntityStore> ref : toRemove) {
                state.aliveNpcRefs.remove(ref);
                state.totalKilledInWave++;
                npcToPlayerMap.remove(ref);

                // Force-remove entity if still valid
                if (ref.isValid()) {
                    try {
                        World npcWorld = ref.getStore().getExternalData().getWorld();
                        if (npcWorld != null && npcWorld.isAlive()) {
                            npcWorld.execute(() -> {
                                try {
                                    if (ref.isValid()) {
                                        ref.getStore().removeEntity(ref,
                                                EntityStore.REGISTRY.newHolder(), RemoveReason.REMOVE);
                                    }
                                } catch (Exception ignored) {}
                            });
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Check if wave is now clear
            if (state.aliveNpcRefs.isEmpty() && state.phase == TrialPhase.ACTIVE) {
                state.phase = TrialPhase.WAVE_CLEAR;
                state.phaseStartTime = System.currentTimeMillis();
            }
        }
    }

    /**
     * Force-clear a wave that timed out. Despawns remaining NPCs and advances.
     */
    private void forceWaveClear(TrialState state) {
        List<Ref<EntityStore>> remaining = new ArrayList<>(state.aliveNpcRefs);
        synchronized (state) {
            for (Ref<EntityStore> ref : remaining) {
                state.aliveNpcRefs.remove(ref);
                state.totalKilledInWave++;
                npcToPlayerMap.remove(ref);
            }
            state.phase = TrialPhase.WAVE_CLEAR;
            state.phaseStartTime = System.currentTimeMillis();
        }

        // Despawn on world thread
        for (Ref<EntityStore> ref : remaining) {
            if (!ref.isValid()) continue;
            try {
                World npcWorld = ref.getStore().getExternalData().getWorld();
                if (npcWorld != null && npcWorld.isAlive()) {
                    npcWorld.execute(() -> {
                        try {
                            if (ref.isValid()) {
                                ref.getStore().removeEntity(ref,
                                        EntityStore.REGISTRY.newHolder(), RemoveReason.REMOVE);
                            }
                        } catch (Exception ignored) {}
                    });
                }
            } catch (Exception ignored) {}
        }

        sendMessage(state.playerRef, "#FFAA00",
                "[Warden Challenge] Remaining enemies cleared (timeout).");
    }

    // === Helpers ===

    private void spawnZoneParticle(TrialState state, Store<EntityStore> store) {
        try {
            // Resolve the player's actual store (may be in an instance, not default world)
            Ref<EntityStore> trialPlayerRef = state.playerRef.getReference();
            if (trialPlayerRef == null || !trialPlayerRef.isValid()) return;
            Store<EntityStore> playerStore = trialPlayerRef.getStore();

            // Particle must be spawned on the player's world thread (instance-safe)
            World playerWorld = playerStore.getExternalData().getWorld();
            if (playerWorld == null) return;

            playerWorld.execute(() -> {
                try {
                    // Re-check validity inside world thread
                    Ref<EntityStore> pRef = state.playerRef.getReference();
                    if (pRef == null || !pRef.isValid()) return;
                    Store<EntityStore> pStore = pRef.getStore();

                    // Collect viewers from the player's store (matches instance world)
                    List<Ref<EntityStore>> viewers = new ArrayList<>();
                    for (PlayerRef p : Universe.get().getPlayers()) {
                        if (p == null) continue;
                        Ref<EntityStore> ref = p.getReference();
                        if (ref == null || !ref.isValid()) continue;
                        if (ref.getStore() != pStore) continue;
                        viewers.add(ref);
                    }
                    if (!viewers.isEmpty()) {
                        double x = state.position.x;
                        double y = state.position.y + ZONE_PARTICLE_Y_OFFSET;
                        double z = state.position.z;
                        ParticleUtil.spawnParticleEffect(ZONE_PARTICLE,
                                x, y, z,
                                0f, 0f, 0f, ZONE_PARTICLE_SCALE,
                                null, null, viewers, pStore);
                        if (!state.zoneParticleLoggedOnce) {
                            state.zoneParticleLoggedOnce = true;
                            plugin.getLogger().atFine().log(
                                    "[WardenChallenge] Zone particle spawned: %s at %.0f,%.0f,%.0f scale=%.1f viewers=%d",
                                    ZONE_PARTICLE, x, y, z, ZONE_PARTICLE_SCALE, viewers.size());
                        }
                    } else if (!state.zoneParticleLoggedOnce) {
                        state.zoneParticleLoggedOnce = true;
                        plugin.getLogger().atWarning().log(
                                "[WardenChallenge] Zone particle: no viewers found in player store!");
                    }
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[WardenChallenge] Zone particle error: %s", e.getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[WardenChallenge] Zone particle error: %s", e.getMessage());
        }
    }

    /**
     * Schedule an async health check on the player's world thread.
     * Result is cached in state.playerAlive (volatile) and read next tick.
     * This avoids "Assert not in thread" when the player is in an instance world.
     */
    private void updatePlayerAlive(TrialState state) {
        try {
            Ref<EntityStore> playerRef = state.playerRef.getReference();
            if (playerRef == null || !playerRef.isValid()) {
                state.playerAlive = false;
                return;
            }

            Store<EntityStore> playerStore = playerRef.getStore();
            World playerWorld = playerStore.getExternalData().getWorld();
            if (playerWorld == null) return;

            playerWorld.execute(() -> {
                try {
                    Ref<EntityStore> pRef = state.playerRef.getReference();
                    if (pRef == null || !pRef.isValid()) {
                        state.playerAlive = false;
                        return;
                    }
                    Store<EntityStore> pStore = pRef.getStore();
                    EntityStatMap statMap = pStore.getComponent(pRef, EntityStatMap.getComponentType());
                    if (statMap == null) return;

                    EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                    if (health != null) {
                        state.playerAlive = health.get() > 0;
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            // Can't schedule check, keep last known state
        }
    }

    private World findWorldForPlayer(PlayerRef playerRef) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null) return null;
            return ref.getStore().getExternalData().getWorld();
        } catch (Exception e) {
            return null;
        }
    }

    private void sendMessage(PlayerRef playerRef, String color, String text) {
        try {
            playerRef.sendMessage(Message.raw(text).color(color));
        } catch (Exception e) {
            // Non-fatal
        }
    }
}
