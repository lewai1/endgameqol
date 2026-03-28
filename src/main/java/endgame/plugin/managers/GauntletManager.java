package endgame.plugin.managers;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.npc.NPCPlugin;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.config.GauntletLeaderboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages The Gauntlet — infinite escalating wave mode.
 * Forked from WardenTrialManager with key differences:
 * - Infinite waves (no fixed count)
 * - Cumulative scaling per wave
 * - Buff selection between boss waves
 * - Persistent leaderboard
 */
public class GauntletManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Gauntlet");

    // === Gauntlet Phases ===
    public enum GauntletPhase {
        COUNTDOWN, SPAWNING, ACTIVE, WAVE_CLEAR, BUFF_SELECT, INTERVAL, FAILED
    }

    // === G2: Buff system with categories and trade-offs ===
    public enum BuffCategory { POWER, TACTICAL, UTILITY, CURSED }

    public enum GauntletBuff {
        // Power
        WRATH("+25% Damage", true, BuffCategory.POWER),
        FORTIFY("+20% Max HP + Full Heal", true, BuffCategory.POWER),
        SWIFTNESS("+15% Speed", true, BuffCategory.POWER),
        // Tactical
        VAMPIRIC_TOUCH("3% Lifesteal", true, BuffCategory.TACTICAL),
        GLASS_CANNON("+50% Dmg, -25% Max HP", true, BuffCategory.TACTICAL),
        BERSERKER_RAGE("+10% Dmg per 10% Missing HP (cap +60%)", false, BuffCategory.TACTICAL),
        // Utility
        COMBO_SURGE("+5s Combo Timer + Combo Persists Between Waves", true, BuffCategory.UTILITY),
        ARSENAL("Random Endgame Weapon Drop", false, BuffCategory.UTILITY),
        LAST_STAND("Survive 1 Lethal Hit, Heal 30%", false, BuffCategory.UTILITY),
        // Cursed (wave 10+)
        BLOOD_PACT("+40% Dmg, -5% Max HP Per Wave", false, BuffCategory.CURSED),
        UNSTABLE_POWER("3x Dmg Every 3rd Hit", false, BuffCategory.CURSED);

        private final String description;
        private final boolean stacks;
        private final BuffCategory category;

        GauntletBuff(String description, boolean stacks, BuffCategory category) {
            this.description = description;
            this.stacks = stacks;
            this.category = category;
        }

        public String getDescription() { return description; }
        public boolean isStackable() { return stacks; }
        public BuffCategory getCategory() { return category; }
    }

    // === G1: Wave Modifiers ===
    public enum WaveModifier {
        NONE("Standard", ""),
        SWARM("SWARM", "+50% count, -50% HP each"),
        ARMORED("ARMORED", "Enemies take -30% dmg, -20% speed"),
        BERSERK("BERSERK", "Enemies deal +50% dmg, -25% HP"),
        HUNTERS("HUNTERS", "+2 extra ranged enemies"),
        VAMPIRIC("VAMPIRIC", "Enemies heal 5% of damage dealt");

        private final String name;
        private final String description;

        WaveModifier(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
    }

    // === G3: Tiered enemy pools (dynamic wave composition) ===
    private record WaveDef(String[] npcTypes, int[] counts) {
        int totalEnemies() {
            int total = 0;
            for (int c : counts) total += c;
            return total;
        }
    }

    // Tier 1: Waves 1-5 (easy enemies)
    private static final String[] TIER_1 = {
        "Trork_Brawler", "Skeleton_Ranger", "Skeleton_Soldier", "Goblin_Lobber", "Spider"
    };
    // Tier 2: Waves 6-10 (medium enemies)
    private static final String[] TIER_2 = {
        "Endgame_Saurian_Warrior", "Endgame_Ghoul", "Endgame_Werewolf",
        "Skeleton_Burnt_Wizard", "Skeleton_Knight"
    };
    // Tier 3: Waves 11-15 (hard enemies)
    private static final String[] TIER_3 = {
        "Endgame_Shadow_Knight", "Endgame_Necromancer_Void", "Endgame_Yeti",
        "Skeleton_Burnt_Gunner", "Skeleton_Sand_Archmage"
    };
    // Tier 4: Waves 16+ (elite enemies)
    private static final String[] TIER_4 = {
        "Endgame_Goblin_Duke", "Zombie_Aberrant", "Scarak_Broodmother"
    };
    // Ranged enemies (for HUNTERS modifier)
    private static final String[] RANGED_POOL = {
        "Skeleton_Ranger", "Skeleton_Burnt_Gunner", "Goblin_Lobber", "Skeleton_Sand_Mage"
    };

    // Boss waves (every 5th wave)
    private static final WaveDef[] BOSS_WAVE_5 = {
        new WaveDef(new String[]{"Alpha_Rex", "Endgame_Saurian_Warrior"}, new int[]{1, 2}),
        new WaveDef(new String[]{"Endgame_Goblin_Duke", "Trork_Brawler"}, new int[]{1, 2}),
        new WaveDef(new String[]{"Endgame_Necromancer_Void", "Golem_Eye_Void"}, new int[]{1, 2}),
    };

    private static final WaveDef[] BOSS_WAVE_10 = {
        new WaveDef(new String[]{"Zombie_Aberrant", "Endgame_Shadow_Knight", "Skeleton_Burnt_Gunner"}, new int[]{1, 1, 1}),
        new WaveDef(new String[]{"Alpha_Rex", "Alpha_Rex", "Endgame_Werewolf"}, new int[]{1, 1, 1}),
    };

    // Wave 20+ dual boss waves
    private static final WaveDef[] BOSS_WAVE_20 = {
        new WaveDef(new String[]{"Zombie_Aberrant", "Alpha_Rex"}, new int[]{1, 1}),
        new WaveDef(new String[]{"Endgame_Goblin_Duke", "Endgame_Necromancer_Void", "Endgame_Shadow_Knight"}, new int[]{1, 1, 1}),
    };

    private static final long COUNTDOWN_MS = 3000;
    private static final long INTERVAL_MS = 8000;
    private static final long BUFF_SELECT_TIMEOUT_MS = 30000;
    private static final float SPAWN_RADIUS = 6.0f;
    private static final long HUD_REFRESH_MS = 200;
    private static final long SPAWN_TIMEOUT_MS = 5000;
    private static final long STUCK_CHECK_INTERVAL_MS = 2000;
    private static final double STUCK_Y_THRESHOLD = 20.0;
    private static final long WAVE_TIMEOUT_MS = 600_000;

    // === Per-player gauntlet state ===
    public static class GauntletState {
        final UUID playerUuid;
        final PlayerRef playerRef;
        final Vector3d position;
        final AtomicInteger currentWave = new AtomicInteger(); // 1-indexed
        volatile GauntletPhase phase;
        final Set<Ref<EntityStore>> aliveNpcRefs = ConcurrentHashMap.newKeySet();
        volatile long phaseStartTime;
        volatile int totalEnemiesInWave;
        final AtomicInteger totalKilledInWave = new AtomicInteger();
        volatile boolean spawnRequested;
        long spawnRequestTime;
        volatile HyUIHud hud;
        volatile boolean hudRemoved;
        volatile boolean pendingHudRemoval;
        volatile boolean playerAlive = true;
        volatile boolean fewerEnemiesActive = false;
        // Buff system
        final Map<GauntletBuff, Integer> activeBuffs = new ConcurrentHashMap<>();
        volatile GauntletBuff[] offeredBuffs;
        volatile GauntletBuff selectedBuff;
        volatile boolean secondWindUsed = false;
        // G1: Wave modifier
        volatile WaveModifier currentModifier = WaveModifier.NONE;
        // G2: Unstable Power hit counter
        final AtomicInteger hitCounter = new AtomicInteger(0);
        // G2: Blood Pact waves survived
        final AtomicInteger bloodPactWaves = new AtomicInteger(0);
        // Stuck NPC cleanup
        long lastStuckCheckTime;

        GauntletState(UUID playerUuid, PlayerRef playerRef, Vector3d position) {
            this.playerUuid = playerUuid;
            this.playerRef = playerRef;
            this.position = new Vector3d(position);
            this.currentWave.set(0);
            this.phase = GauntletPhase.COUNTDOWN;
            this.phaseStartTime = System.currentTimeMillis();
            this.totalEnemiesInWave = 0;
            this.totalKilledInWave.set(0);
            this.spawnRequested = false;
            this.hudRemoved = false;
            this.pendingHudRemoval = false;
        }

        int getBuffStacks(GauntletBuff buff) {
            return activeBuffs.getOrDefault(buff, 0);
        }
    }

    // === Fields ===
    private final EndgameQoL plugin;
    private final Config<GauntletLeaderboard> leaderboard;
    private final ComboMeterManager comboManager;
    private final ConcurrentHashMap<UUID, GauntletState> activeGauntlets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Ref<EntityStore>, UUID> npcToPlayerMap = new ConcurrentHashMap<>();

    // Bounty hook
    public interface BountyHook {
        void onGauntletWave(UUID playerUuid, int wave);
    }
    private volatile BountyHook bountyHook;
    public void setBountyHook(BountyHook hook) { this.bountyHook = hook; }

    public GauntletManager(EndgameQoL plugin, Config<GauntletLeaderboard> leaderboard, ComboMeterManager comboManager) {
        this.plugin = plugin;
        this.leaderboard = leaderboard;
        this.comboManager = comboManager;
    }

    // === Public API ===

    public boolean hasActiveGauntlet(UUID playerUuid) {
        return activeGauntlets.containsKey(playerUuid);
    }

    public int getActiveGauntletCount() {
        return activeGauntlets.size();
    }

    public boolean isTrackedNpc(Ref<EntityStore> ref) {
        return npcToPlayerMap.containsKey(ref);
    }

    public void startGauntlet(UUID playerUuid, PlayerRef playerRef, Ref<EntityStore> ref,
                               Vector3d position, Store<EntityStore> store) {
        if (activeGauntlets.containsKey(playerUuid)) return;

        GauntletState state = new GauntletState(playerUuid, playerRef, position);
        activeGauntlets.put(playerUuid, state);

        LOGGER.atFine().log("[Gauntlet] Started for %s at %.0f, %.0f, %.0f",
                playerUuid, position.x, position.y, position.z);

        showGauntletHud(state);
        sendMessage(playerRef, "#FFD700", "[The Gauntlet] Prepare yourself! Starting in 3 seconds...");
    }

    public void onEnemyDeath(Ref<EntityStore> npcRef) {
        UUID playerUuid = npcToPlayerMap.remove(npcRef);
        if (playerUuid == null) return;

        GauntletState state = activeGauntlets.get(playerUuid);
        if (state == null) return;

        synchronized (state) {
            state.aliveNpcRefs.remove(npcRef);
            state.totalKilledInWave.incrementAndGet();

            LOGGER.atFine().log("[Gauntlet] Enemy died for %s, remaining: %d/%d",
                    playerUuid, state.aliveNpcRefs.size(), state.totalEnemiesInWave);

            if (state.aliveNpcRefs.isEmpty() && state.phase == GauntletPhase.ACTIVE) {
                state.phase = GauntletPhase.WAVE_CLEAR;
                state.phaseStartTime = System.currentTimeMillis();
            }
        }
    }

    public void tick(Store<EntityStore> store) {
        if (activeGauntlets.isEmpty()) return;

        // Process deferred HUD removals
        for (GauntletState state : activeGauntlets.values()) {
            if (state.pendingHudRemoval) {
                state.pendingHudRemoval = false;
                state.hudRemoved = true;
                if (state.hud != null) {
                    try { state.hud.remove(); } catch (Exception | NoClassDefFoundError ignored) {}
                    state.hud = null;
                }
            }
        }

        // Prune orphan entries from npcToPlayerMap (NPCs that despawned without triggering onEnemyDeath)
        npcToPlayerMap.entrySet().removeIf(e -> !e.getKey().isValid());

        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, GauntletState> entry : activeGauntlets.entrySet()) {
            GauntletState state = entry.getValue();
            try {
                tickGauntlet(state, store, now);
            } catch (Exception e) {
                LOGGER.atWarning().log("[Gauntlet] Error ticking for %s: %s",
                        state.playerUuid, e.getMessage());
            }
        }
    }

    public void failGauntlet(UUID playerUuid) {
        GauntletState state = activeGauntlets.remove(playerUuid);
        if (state == null) return;

        state.phase = GauntletPhase.FAILED;

        // Save score to leaderboard
        if (state.currentWave.get() > 0) {
            String playerName = findPlayerName(state.playerRef);
            boolean madeBoard = leaderboard.get().submitScore(playerName, playerUuid.toString(), state.currentWave.get());
            leaderboard.save();
            if (madeBoard) {
                sendMessage(state.playerRef, "#FFD700",
                        "[The Gauntlet] New record! Wave " + state.currentWave.get() + " — check /gauntlet top");
            }
            // Sync to database
            var dbService = plugin.getDatabaseSyncService();
            if (dbService != null && dbService.isEnabled()) {
                dbService.updateLeaderboardAsync(playerUuid.toString(), playerName, state.currentWave.get());
            }
        }

        // Despawn remaining NPCs
        List<Ref<EntityStore>> npcsToRemove = new ArrayList<>(state.aliveNpcRefs);
        for (Ref<EntityStore> ref : npcsToRemove) {
            npcToPlayerMap.remove(ref);
        }
        state.aliveNpcRefs.clear();

        despawnNpcs(npcsToRemove);
        hideGauntletHud(state);

        // Reset combo timer bonus from COMBO_SURGE
        if (comboManager != null) {
            comboManager.resetComboTimerBonus(playerUuid);
        }

        sendMessage(state.playerRef, "#FF5555",
                "[The Gauntlet] Defeated at Wave " + state.currentWave.get() + "!");
        LOGGER.atFine().log("[Gauntlet] Failed at wave %d for %s", state.currentWave.get(), playerUuid);

        // Notify bounty hook
        BountyHook hook = this.bountyHook;
        if (hook != null && state.currentWave.get() > 0) {
            hook.onGauntletWave(playerUuid, state.currentWave.get());
        }
    }

    public void forceClear() {
        for (GauntletState state : activeGauntlets.values()) {
            hideGauntletHud(state);
            List<Ref<EntityStore>> npcs = new ArrayList<>(state.aliveNpcRefs);
            despawnNpcs(npcs);
            state.aliveNpcRefs.clear();
        }
        activeGauntlets.clear();
        npcToPlayerMap.clear();
    }

    // === Damage/buff queries (called by GauntletDamageBoostSystem) ===

    public float getPlayerDamageMultiplier(UUID playerUuid) {
        GauntletState state = activeGauntlets.get(playerUuid);
        if (state == null) return 1.0f;

        float mult = 1.0f;

        // G2: Wrath (+25% per stack)
        int wrathStacks = state.getBuffStacks(GauntletBuff.WRATH);
        if (wrathStacks > 0) mult += 0.25f * wrathStacks;

        // G2: Glass Cannon (+50% per stack)
        int glassCannon = state.getBuffStacks(GauntletBuff.GLASS_CANNON);
        if (glassCannon > 0) mult += 0.50f * glassCannon;

        // G2: Blood Pact (+40% flat)
        if (state.activeBuffs.containsKey(GauntletBuff.BLOOD_PACT)) {
            mult += 0.40f;
        }

        // G2: Berserker Rage (+10% per 10% missing HP, cap +60%)
        if (state.activeBuffs.containsKey(GauntletBuff.BERSERKER_RAGE)) {
            float missingHpPct = getMissingHpPercent(state);
            float berserkerBonus = Math.min(0.60f, (float) Math.floor(missingHpPct * 10) * 0.10f);
            mult += berserkerBonus;
        }

        // G2: Unstable Power (3x every 3rd hit) — counter advanced by onPlayerHit()
        if (state.activeBuffs.containsKey(GauntletBuff.UNSTABLE_POWER)) {
            if (state.hitCounter.get() > 0 && state.hitCounter.get() % 3 == 0) {
                mult *= 3.0f;
            }
        }

        return mult;
    }

    /**
     * Advance the hit counter for Unstable Power buff. Call once per damage event.
     */
    public void onPlayerHit(UUID playerUuid) {
        GauntletState state = activeGauntlets.get(playerUuid);
        if (state != null && state.activeBuffs.containsKey(GauntletBuff.UNSTABLE_POWER)) {
            state.hitCounter.incrementAndGet();
        }
    }

    public float getWaveDamageMultiplier(UUID playerUuid) {
        GauntletState state = activeGauntlets.get(playerUuid);
        if (state == null) return 1.0f;

        EndgameConfig config = plugin.getConfig().get();
        float scalingPercent = config.getGauntletScalingPercent();
        return 1.0f + (state.currentWave.get() * scalingPercent / 100f);
    }

    public float getLifestealPercent(UUID playerUuid) {
        GauntletState state = activeGauntlets.get(playerUuid);
        if (state == null) return 0f;
        // G2: Vampiric Touch (3% per stack)
        int stacks = state.getBuffStacks(GauntletBuff.VAMPIRIC_TOUCH);
        return stacks > 0 ? 0.03f * stacks : 0f;
    }

    public void checkSecondWind(UUID playerUuid, Damage damage) {
        GauntletState state = activeGauntlets.get(playerUuid);
        if (state == null) return;

        int swStacks = state.getBuffStacks(GauntletBuff.LAST_STAND);
        if (swStacks <= 0 || state.secondWindUsed) return;

        // Check if this damage would kill the player
        try {
            PlayerRef pRef = state.playerRef;
            Ref<EntityStore> ref = pRef.getReference();
            if (ref == null || !ref.isValid()) return;

            Store<EntityStore> store = ref.getStore();
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) return;

            EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
            if (health == null) return;

            if (health.get() - damage.getAmount() <= 0) {
                // Activate Second Wind
                damage.setAmount(0);
                state.secondWindUsed = true;
                state.activeBuffs.remove(GauntletBuff.LAST_STAND);

                // Heal 30%
                float maxHp = health.getMax();
                statMap.addStatValue(DefaultEntityStatTypes.getHealth(), maxHp * 0.3f);

                sendMessage(pRef, "#FFD700", "[The Gauntlet] SECOND WIND! Survived lethal hit!");
                LOGGER.atFine().log("[Gauntlet] Second Wind activated for %s", playerUuid);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[Gauntlet] Second Wind check error: %s", e.getMessage());
        }
    }

    /**
     * G1: Get the wave modifier damage multiplier for enemies.
     * BERSERK: enemies deal +50% dmg. ARMORED: enemies HP stays same but -30% incoming.
     */
    public float getModifierEnemyDamageMult(UUID playerUuid) {
        GauntletState state = activeGauntlets.get(playerUuid);
        if (state == null) return 1.0f;
        if (state.currentModifier == WaveModifier.BERSERK) return 1.5f;
        return 1.0f;
    }

    /**
     * G1: Get the wave modifier damage reduction for ARMORED enemies.
     * Returns the multiplier on incoming damage (0.7 = 30% reduction).
     */
    public float getModifierEnemyDefenseMult(UUID playerUuid) {
        GauntletState state = activeGauntlets.get(playerUuid);
        if (state == null) return 1.0f;
        if (state.currentModifier == WaveModifier.ARMORED) return 0.7f;
        return 1.0f;
    }

    /**
     * G1: Check if VAMPIRIC modifier is active (enemies heal 5% of damage dealt).
     */
    public boolean isVampiricWave(UUID playerUuid) {
        GauntletState state = activeGauntlets.get(playerUuid);
        if (state == null) return false;
        return state.currentModifier == WaveModifier.VAMPIRIC;
    }

    private float getMissingHpPercent(GauntletState state) {
        try {
            Ref<EntityStore> ref = state.playerRef.getReference();
            if (ref == null || !ref.isValid()) return 0f;
            Store<EntityStore> s = ref.getStore();
            EntityStatMap statMap = s.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) return 0f;
            EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
            if (health == null || health.getMax() <= 0) return 0f;
            return 1.0f - (health.get() / health.getMax());
        } catch (Exception e) {
            return 0f;
        }
    }

    public List<GauntletLeaderboard.LeaderboardEntry> getLeaderboardTop(int n) {
        return leaderboard.get().getTop(n);
    }

    public int getLeaderboardEntryCount() {
        return leaderboard.get().getEntryCount();
    }

    // === State machine ===

    private void tickGauntlet(GauntletState state, Store<EntityStore> store, long now) {
        long elapsed = now - state.phaseStartTime;

        switch (state.phase) {
            case COUNTDOWN -> {
                if (elapsed >= COUNTDOWN_MS) {
                    state.currentWave.set(1);
                    state.phase = GauntletPhase.SPAWNING;
                    state.phaseStartTime = now;
                    state.spawnRequested = false;
                    sendMessage(state.playerRef, "#FF5555",
                            "[The Gauntlet] Wave " + state.currentWave.get() + " — Fight!");
                }
            }
            case SPAWNING -> {
                if (!state.spawnRequested) {
                    state.spawnRequested = true;
                    state.spawnRequestTime = now;
                    spawnWave(state, store);
                } else if (!state.aliveNpcRefs.isEmpty()) {
                    state.spawnRequested = false;
                    state.phase = GauntletPhase.ACTIVE;
                    state.phaseStartTime = now;
                } else if (now - state.spawnRequestTime > SPAWN_TIMEOUT_MS) {
                    LOGGER.atWarning().log("[Gauntlet] Spawn timeout for %s wave %d",
                            state.playerUuid, state.currentWave.get());
                    failGauntlet(state.playerUuid);
                }
            }
            case ACTIVE -> {
                updatePlayerAlive(state);
                if (!state.playerAlive) {
                    failGauntlet(state.playerUuid);
                    return;
                }
                // Periodic stuck NPC cleanup
                if (now - state.lastStuckCheckTime >= STUCK_CHECK_INTERVAL_MS) {
                    state.lastStuckCheckTime = now;
                    cleanupStuckNpcs(state);
                }
                // Wave timeout safety net
                if (elapsed >= WAVE_TIMEOUT_MS && !state.aliveNpcRefs.isEmpty()) {
                    LOGGER.atWarning().log("[Gauntlet] Wave %d timed out for %s (%d NPCs remaining) — forcing clear",
                            state.currentWave.get(), state.playerUuid, state.aliveNpcRefs.size());
                    forceWaveClear(state);
                }
            }
            case WAVE_CLEAR -> {
                // Notify bounty of wave clear
                BountyHook hook = this.bountyHook;
                if (hook != null) {
                    hook.onGauntletWave(state.playerUuid, state.currentWave.get());
                }

                // Give rewards on milestone waves
                if (state.currentWave.get() % 5 == 0) {
                    giveWaveRewards(state, store);
                }

                state.currentWave.incrementAndGet();

                sendMessage(state.playerRef, "#55FF55",
                        "[The Gauntlet] Wave " + (state.currentWave.get() - 1) + " cleared!");

                // Boss wave every 5th: offer buffs
                if ((state.currentWave.get() - 1) % 5 == 0 && state.currentWave.get() > 1) {
                    state.phase = GauntletPhase.BUFF_SELECT;
                    state.phaseStartTime = now;
                    state.selectedBuff = null;
                    offerBuffSelection(state, store);
                } else {
                    state.phase = GauntletPhase.INTERVAL;
                    state.phaseStartTime = now;
                    state.totalKilledInWave.set(0);
                    rebuildHud(state);
                }
            }
            case BUFF_SELECT -> {
                if (state.selectedBuff != null) {
                    applyBuff(state, state.selectedBuff, store);
                    state.selectedBuff = null;
                    state.phase = GauntletPhase.INTERVAL;
                    state.phaseStartTime = now;
                    state.totalKilledInWave.set(0);
                    rebuildHud(state);
                } else if (elapsed >= BUFF_SELECT_TIMEOUT_MS) {
                    // Auto-select first buff
                    if (state.offeredBuffs != null && state.offeredBuffs.length > 0) {
                        applyBuff(state, state.offeredBuffs[0], store);
                    }
                    state.phase = GauntletPhase.INTERVAL;
                    state.phaseStartTime = now;
                    state.totalKilledInWave.set(0);
                    rebuildHud(state);
                }
            }
            case INTERVAL -> {
                if (elapsed >= INTERVAL_MS) {
                    state.phase = GauntletPhase.SPAWNING;
                    state.phaseStartTime = now;
                    state.spawnRequested = false;
                    state.fewerEnemiesActive = false;
                    sendMessage(state.playerRef, "#FF5555",
                            "[The Gauntlet] Wave " + state.currentWave.get() + " — Fight!");
                }
            }
            case FAILED -> {
                activeGauntlets.remove(state.playerUuid);
            }
        }
    }

    // === Spawning ===

    private void spawnWave(GauntletState state, Store<EntityStore> store) {
        // G1: Roll wave modifier for non-boss waves
        state.currentModifier = rollWaveModifier(state.currentWave.get());
        if (state.currentModifier != WaveModifier.NONE) {
            sendMessage(state.playerRef, "#FFAA33",
                    "[The Gauntlet] Wave " + state.currentWave.get() + " — " + state.currentModifier.getName() + ": " + state.currentModifier.getDescription());
        }

        WaveDef wave = getWaveDef(state.currentWave.get());
        state.aliveNpcRefs.clear();
        state.totalEnemiesInWave = wave.totalEnemies();
        state.totalKilledInWave.set(0);

        World world = findWorldForPlayer(state.playerRef);
        if (world == null) {
            LOGGER.atWarning().log("[Gauntlet] Cannot find world for player %s", state.playerUuid);
            failGauntlet(state.playerUuid);
            return;
        }

        // G3: Dynamic spawn radius scales with wave
        float spawnRadius = Math.min(12f, SPAWN_RADIUS + state.currentWave.get() / 5f);
        // G1: SWARM modifier increases count
        boolean isSwarm = state.currentModifier == WaveModifier.SWARM;
        // G1: HUNTERS modifier adds ranged enemies
        boolean isHunters = state.currentModifier == WaveModifier.HUNTERS;

        if (!world.isAlive()) return;
        world.execute(() -> {
            try {
                Ref<EntityStore> playerRef = state.playerRef.getReference();
                if (playerRef == null || !playerRef.isValid()) return;
                Store<EntityStore> playerStore = playerRef.getStore();

                int actualTotal = 0;
                for (int typeIdx = 0; typeIdx < wave.npcTypes().length; typeIdx++) {
                    String npcType = wave.npcTypes()[typeIdx];
                    int count = wave.counts()[typeIdx];
                    if (isSwarm) count = (int)(count * 1.5f);

                    for (int i = 0; i < count; i++) {
                        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                        double radius = spawnRadius * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5);
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
                            actualTotal++;
                        }
                    }
                }

                // G1: HUNTERS — spawn 2 extra ranged enemies
                if (isHunters) {
                    for (int i = 0; i < 2; i++) {
                        String rangedNpc = RANGED_POOL[ThreadLocalRandom.current().nextInt(RANGED_POOL.length)];
                        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
                        double radius = spawnRadius * (0.6 + ThreadLocalRandom.current().nextDouble() * 0.4);
                        Vector3d spawnPos = new Vector3d(
                                state.position.x + Math.cos(angle) * radius,
                                state.position.y,
                                state.position.z + Math.sin(angle) * radius);
                        Vector3f rotation = new Vector3f(0, (float) (ThreadLocalRandom.current().nextDouble() * 360.0), 0);
                        var sr = NPCPlugin.get().spawnNPC(playerStore, rangedNpc, null, spawnPos, rotation);
                        if (sr != null) {
                            state.aliveNpcRefs.add(sr.left());
                            npcToPlayerMap.put(sr.left(), state.playerUuid);
                            actualTotal++;
                        }
                    }
                }

                state.totalEnemiesInWave = actualTotal;
                LOGGER.atFine().log("[Gauntlet] Spawned %d enemies for wave %d (modifier=%s)",
                        actualTotal, state.currentWave.get(), state.currentModifier.name());
            } catch (Exception e) {
                LOGGER.atWarning().log("[Gauntlet] Error spawning wave %d: %s", state.currentWave.get(), e.getMessage());
            }
        });
    }

    private WaveDef getWaveDef(int wave) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Boss wave every 5th
        if (wave % 5 == 0) {
            if (wave >= 20 && wave % 10 == 0) {
                return BOSS_WAVE_20[rng.nextInt(BOSS_WAVE_20.length)];
            }
            if (wave % 10 == 0) {
                return BOSS_WAVE_10[rng.nextInt(BOSS_WAVE_10.length)];
            }
            return BOSS_WAVE_5[rng.nextInt(BOSS_WAVE_5.length)];
        }

        // G3: Dynamic wave composition from tiered pools
        String[] primaryPool;
        String[] secondaryPool;
        int baseCount;

        if (wave <= 5) {
            primaryPool = TIER_1;
            secondaryPool = TIER_1;
            baseCount = 4 + rng.nextInt(2); // 4-5
        } else if (wave <= 10) {
            primaryPool = TIER_2;
            secondaryPool = TIER_1;
            baseCount = 5 + rng.nextInt(2); // 5-6
        } else if (wave <= 15) {
            primaryPool = TIER_3;
            secondaryPool = TIER_2;
            baseCount = 5 + rng.nextInt(3); // 5-7
        } else {
            primaryPool = TIER_4;
            secondaryPool = TIER_3;
            baseCount = 4 + rng.nextInt(3); // 4-6
        }

        // Mix: ~60% primary, ~40% secondary
        int primaryCount = Math.max(1, (int)(baseCount * 0.6));
        int secondaryCount = baseCount - primaryCount;

        List<String> types = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        // Pick random types from pools
        String primary = primaryPool[rng.nextInt(primaryPool.length)];
        types.add(primary);
        counts.add(primaryCount);

        if (secondaryCount > 0) {
            String secondary = secondaryPool[rng.nextInt(secondaryPool.length)];
            // Avoid duplicate
            if (secondary.equals(primary) && secondaryPool.length > 1) {
                secondary = secondaryPool[(Arrays.asList(secondaryPool).indexOf(primary) + 1) % secondaryPool.length];
            }
            types.add(secondary);
            counts.add(secondaryCount);
        }

        // 10% Nemesis chance: 1 next-tier enemy as mini-boss
        if (wave > 5 && rng.nextFloat() < 0.10f) {
            String[] nemesisPool;
            if (wave <= 10) nemesisPool = TIER_3;
            else if (wave <= 15) nemesisPool = TIER_4;
            else nemesisPool = TIER_4; // highest tier as nemesis
            types.add(nemesisPool[rng.nextInt(nemesisPool.length)]);
            counts.add(1);
        }

        return new WaveDef(
                types.toArray(new String[0]),
                counts.stream().mapToInt(Integer::intValue).toArray()
        );
    }

    // G1: Roll a random wave modifier for non-boss waves
    private WaveModifier rollWaveModifier(int wave) {
        if (wave % 5 == 0) return WaveModifier.NONE; // Boss waves never modified
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        float roll = rng.nextFloat();
        // 40% NONE, 12% each for the 5 modifiers
        if (roll < 0.40f) return WaveModifier.NONE;
        if (roll < 0.52f) return WaveModifier.SWARM;
        if (roll < 0.64f) return WaveModifier.ARMORED;
        if (roll < 0.76f) return WaveModifier.BERSERK;
        if (roll < 0.88f) return WaveModifier.HUNTERS;
        return WaveModifier.VAMPIRIC;
    }

    // === Buff system ===

    private void offerBuffSelection(GauntletState state, Store<EntityStore> store) {
        EndgameConfig config = plugin.getConfig().get();
        int buffCount = config.getGauntletBuffCount();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        GauntletBuff[] allBuffs = GauntletBuff.values();
        List<GauntletBuff> available = new ArrayList<>();
        for (GauntletBuff b : allBuffs) {
            // Remove non-stackable buffs already owned
            if (!b.isStackable() && state.activeBuffs.containsKey(b)) continue;
            // G2: Cursed buffs only available wave 10+
            if (b.getCategory() == BuffCategory.CURSED && state.currentWave.get() < 10) continue;
            available.add(b);
        }

        // G2: Selection rules — at least 2 different categories, max 1 cursed
        List<GauntletBuff> offered = new ArrayList<>();
        Set<BuffCategory> usedCategories = new HashSet<>();
        int cursedCount = 0;

        Collections.shuffle(available, rng);
        for (GauntletBuff b : available) {
            if (offered.size() >= buffCount) break;
            if (b.getCategory() == BuffCategory.CURSED && cursedCount >= 1) continue;
            offered.add(b);
            usedCategories.add(b.getCategory());
            if (b.getCategory() == BuffCategory.CURSED) cursedCount++;
        }

        // If only 1 category, try to replace last with a different one
        if (usedCategories.size() < 2 && offered.size() >= 2) {
            BuffCategory current = offered.get(0).getCategory();
            for (GauntletBuff b : available) {
                if (b.getCategory() != current && !offered.contains(b)) {
                    if (b.getCategory() == BuffCategory.CURSED && cursedCount >= 1) continue;
                    offered.set(offered.size() - 1, b);
                    break;
                }
            }
        }

        GauntletBuff[] offeredArray = offered.toArray(new GauntletBuff[0]);
        state.offeredBuffs = offeredArray;

        // Show buff selection page
        showBuffSelectPage(state, offeredArray, store);
    }

    private void showBuffSelectPage(GauntletState state, GauntletBuff[] buffs, Store<EntityStore> store) {
        try {
            PlayerRef playerRef = state.playerRef;
            Ref<EntityStore> pRef = playerRef.getReference();
            if (pRef == null || !pRef.isValid()) return;

            StringBuilder sb = new StringBuilder();
            sb.append("""
                <style>
                    .buff-container { anchor-width: 500; layout-mode: top; padding: 16; }
                    .buff-title { font-size: 18; font-weight: bold; color: #FFD700; anchor-height: 30; text-align: center; anchor-width: 100%; }
                    .buff-subtitle { font-size: 12; color: #888888; anchor-height: 20; text-align: center; anchor-width: 100%; margin-bottom: 12; }
                    .buff-card { layout-mode: left; vertical-align: center; padding: 12; background-color: #1e1e2e; margin-bottom: 8; anchor-height: 50; }
                    .buff-desc { font-size: 13; color: #ffffff; flex-weight: 1; padding-left: 12; }
                    @BuffBtnBg { background-color: #2d5a2d; }
                    @BuffBtnHoverBg { background-color: #3a7a3a; }
                    @BuffBtnPressedBg { background-color: #1e4a1e; }
                    @BuffBtnLabel { font-size: 12; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
                    @BuffBtnHoverLabel { font-size: 12; color: #ffff88; font-weight: bold; horizontal-align: center; vertical-align: center; }
                </style>
                <div class="page-overlay" style="layout-mode: center; vertical-align: middle; horizontal-align: center;">
                    <div class="decorated-container buff-container" data-hyui-title="Choose Your Buff">
                        <div class="container-contents" style="layout-mode: top; padding: 10;">
                            <p class="buff-title">WAVE CLEAR BONUS</p>
                            <p class="buff-subtitle">Choose a buff to help you in the next waves</p>
                """);

            for (int i = 0; i < buffs.length; i++) {
                sb.append(String.format("""
                    <div class="buff-card">
                        <p class="buff-desc">%s</p>
                        <button id="buff_%d" class="custom-textbutton"
                            data-hyui-default-bg="@BuffBtnBg"
                            data-hyui-hovered-bg="@BuffBtnHoverBg"
                            data-hyui-pressed-bg="@BuffBtnPressedBg"
                            data-hyui-default-label-style="@BuffBtnLabel"
                            data-hyui-hovered-label-style="@BuffBtnHoverLabel"
                            style="anchor-height: 32; anchor-width: 100;">Select</button>
                    </div>
                    """, buffs[i].getDescription(), i));
            }

            sb.append("""
                        </div>
                    </div>
                </div>
                """);

            var builder = PageBuilder.pageForPlayer(playerRef)
                    .fromHtml(sb.toString());

            for (int i = 0; i < buffs.length; i++) {
                final int buffIdx = i;
                builder.addEventListener("buff_" + i, CustomUIEventBindingType.Activating, (data, ctx) -> {
                    if (state.offeredBuffs != null && buffIdx < state.offeredBuffs.length) {
                        state.selectedBuff = state.offeredBuffs[buffIdx];
                    }
                });
            }

            builder.open(pRef.getStore());
        } catch (Exception e) {
            LOGGER.atWarning().log("[Gauntlet] Failed to show buff page: %s", e.getMessage());
            // Auto-select first buff on error
            if (buffs.length > 0) {
                state.selectedBuff = buffs[0];
            }
        }
    }

    private void applyBuff(GauntletState state, GauntletBuff buff, Store<EntityStore> store) {
        if (buff.isStackable()) {
            state.activeBuffs.merge(buff, 1, Integer::sum);
        } else {
            state.activeBuffs.put(buff, 1);
        }

        String buffColor = buff.getCategory() == BuffCategory.CURSED ? "#FF5555" : "#55FF55";
        sendMessage(state.playerRef, buffColor,
                "[The Gauntlet] Buff acquired: " + buff.getDescription());

        // Immediate effects
        switch (buff) {
            case FORTIFY -> {
                // Full heal (max HP boost is tracked via buff stacks for damage reduction)
                healPlayer(state, 1.0f);
            }
            case COMBO_SURGE -> {
                if (comboManager != null) {
                    comboManager.addComboTimerBonus(state.playerUuid, 5.0f);
                }
            }
            case ARSENAL -> {
                // Drop a random endgame weapon on the ground
                dropRandomWeapon(state, store);
            }
            case GLASS_CANNON -> {
                // Trade-off: -25% current HP as cost
                try {
                    Ref<EntityStore> ref = state.playerRef.getReference();
                    if (ref != null && ref.isValid()) {
                        EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                        if (statMap != null) {
                            EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                            if (health != null) {
                                float dmg = health.getMax() * 0.25f;
                                statMap.addStatValue(DefaultEntityStatTypes.getHealth(), -dmg);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            default -> {}
        }

        LOGGER.atFine().log("[Gauntlet] Buff %s applied for %s (stacks: %d)",
                buff.name(), state.playerUuid, state.getBuffStacks(buff));
    }

    private void dropRandomWeapon(GauntletState state, Store<EntityStore> store) {
        // Use a drop table for the weapon — gives random endgame weapon via existing system
        World world = findWorldForPlayer(state.playerRef);
        if (world == null || !world.isAlive()) return;
        world.execute(() -> {
            try {
                Ref<EntityStore> pRef = state.playerRef.getReference();
                if (pRef == null || !pRef.isValid()) return;
                // Use gauntlet 10 drops as source for a random weapon
                List<ItemStack> items = ItemModule.get().getRandomItemDrops("Endgame_Drop_Reward_10");
                if (items != null) {
                    for (ItemStack item : items) {
                        ItemUtils.dropItem(pRef, item, pRef.getStore());
                    }
                }
            } catch (Exception e) {
                LOGGER.atFine().log("[Gauntlet] Arsenal drop error: %s", e.getMessage());
            }
        });
    }

    // === Rewards ===

    private void giveWaveRewards(GauntletState state, Store<EntityStore> store) {
        World world = findWorldForPlayer(state.playerRef);
        if (world == null) return;

        // G4: Expanded reward tiers
        String dropTable;
        if (state.currentWave.get() >= 30) {
            dropTable = "Endgame_Drop_Reward_30";
        } else if (state.currentWave.get() >= 25) {
            dropTable = "Endgame_Drop_Reward_25";
        } else if (state.currentWave.get() >= 20) {
            dropTable = "Endgame_Drop_Reward_20";
        } else if (state.currentWave.get() >= 15) {
            dropTable = "Endgame_Drop_Reward_15";
        } else if (state.currentWave.get() >= 10) {
            dropTable = "Endgame_Drop_Reward_10";
        } else {
            dropTable = "Endgame_Drop_Reward_5";
        }

        // Award XP on milestone waves (wave/5 * base = 50, 100, 150...)
        int gauntletXpBase = plugin.getConfig().get().getElGauntletXpBase();
        int milestoneXp = (state.currentWave.get() / 5) * gauntletXpBase;
        if (milestoneXp > 0) {
            if (plugin.isRPGLevelingActive()) {
                try { plugin.getRpgLevelingBridge().addXP(state.playerUuid, milestoneXp, "GAUNTLET_WAVE"); }
                catch (Exception ignored) {}
            }
            if (plugin.isEndlessLevelingActive()) {
                try { plugin.getEndlessLevelingBridge().addXP(state.playerUuid, milestoneXp, "GAUNTLET_WAVE"); }
                catch (Exception ignored) {}
            }
        }

        // G2: Blood Pact — lose 5% current HP per wave survived as cost
        if (state.activeBuffs.containsKey(GauntletBuff.BLOOD_PACT)) {
            state.bloodPactWaves.incrementAndGet();
            try {
                Ref<EntityStore> ref = state.playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    EntityStatMap statMap = ref.getStore().getComponent(ref, EntityStatMap.getComponentType());
                    if (statMap != null) {
                        EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                        if (health != null) {
                            float hpCost = health.getMax() * 0.05f;
                            statMap.addStatValue(DefaultEntityStatTypes.getHealth(), -hpCost);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        final String dt = dropTable;
        if (!world.isAlive()) return;
        world.execute(() -> {
            try {
                Ref<EntityStore> playerRef = state.playerRef.getReference();
                if (playerRef == null || !playerRef.isValid()) return;

                Store<EntityStore> playerStore = playerRef.getStore();
                Player player = playerStore.getComponent(playerRef, Player.getComponentType());
                if (player == null) return;

                List<ItemStack> rewards = ItemModule.get().getRandomItemDrops(dt);
                if (rewards == null || rewards.isEmpty()) return;

                for (ItemStack item : rewards) {
                    ItemStackTransaction transaction = player.giveItem(item, playerRef, playerStore);
                    ItemStack remainder = transaction.getRemainder();
                    if (remainder != null && !remainder.isEmpty()) {
                        ItemUtils.dropItem(playerRef, remainder, playerStore);
                    }
                }

                LOGGER.atFine().log("[Gauntlet] Gave rewards from %s to %s", dt, state.playerUuid);
            } catch (Exception e) {
                LOGGER.atWarning().log("[Gauntlet] Error giving rewards: %s", e.getMessage());
            }
        });
    }

    // === HyUI HUD ===

    private void showGauntletHud(GauntletState state) {
        try {
            String html = buildGauntletHudHtml(state);
            state.hudRemoved = false;
            state.pendingHudRemoval = false;
            state.hud = HudBuilder.hudForPlayer(state.playerRef)
                    .fromHtml(html)
                    .withRefreshRate(HUD_REFRESH_MS)
                    .onRefresh(h -> {
                        if (state.hudRemoved) return;
                        GauntletState current = activeGauntlets.get(state.playerUuid);
                        if (current == null || current.hud != h) {
                            state.pendingHudRemoval = true;
                            return;
                        }
                        doRefreshHud(current, h);
                    })
                    .show();
        } catch (NoClassDefFoundError e) {
            LOGGER.atFine().log("[Gauntlet] HyUI not available, running without HUD");
        } catch (Exception e) {
            LOGGER.atWarning().log("[Gauntlet] Failed to show HUD: %s", e.getMessage());
        }
    }

    private void rebuildHud(GauntletState state) {
        state.hudRemoved = true;
        if (state.hud != null) {
            try { state.hud.remove(); } catch (Exception | NoClassDefFoundError ignored) {}
            state.hud = null;
        }
        showGauntletHud(state);
    }

    private void doRefreshHud(GauntletState state, HyUIHud hud) {
        try {
            int wave = state.currentWave.get();
            int killed = state.totalKilledInWave.get();
            int total = state.totalEnemiesInWave;
            float progress = total > 0 ? Math.max(0f, Math.min(1f, (float) killed / total)) : 0f;
            int barFillWidth = Math.round(300 * progress);

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
                    statusText = "FIGHT!";
                }
                case WAVE_CLEAR -> {
                    killText = total + " / " + total + " defeated";
                    statusText = "Wave cleared!";
                }
                case BUFF_SELECT -> {
                    killText = "Choose a buff!";
                    long remaining = Math.max(0, BUFF_SELECT_TIMEOUT_MS - elapsed);
                    int secs = (int) Math.ceil(remaining / 1000.0);
                    statusText = "Selecting buff... (" + secs + "s)";
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

            // G1: Wave modifier display
            String modifierText = "";
            if (state.currentModifier != WaveModifier.NONE && state.phase == GauntletPhase.ACTIVE) {
                modifierText = state.currentModifier.getName();
            }
            final String fModifierText = modifierText;

            final String fWaveText = "Wave " + wave;
            final String fKillText = killText;
            final String fStatusText = statusText;
            final int fBarWidth = barFillWidth;

            try { hud.getById("wave-info", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fWaveText)); } catch (Exception ignored) {}
            try { hud.getById("kill-text", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fKillText)); } catch (Exception ignored) {}
            try { hud.getById("status-text", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fStatusText)); } catch (Exception ignored) {}
            try { hud.getById("progress-bar-fill", GroupBuilder.class)
                    .ifPresent(b -> b.withAnchor(new HyUIAnchor()
                            .setWidth(fBarWidth).setHeight(12).setLeft(0).setTop(0))); } catch (Exception ignored) {}
            try { hud.getById("modifier-text", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fModifierText)); } catch (Exception ignored) {}
        } catch (Exception e) {
            // Non-fatal
        }
    }

    private void hideGauntletHud(GauntletState state) {
        state.hudRemoved = true;
        state.pendingHudRemoval = false;
        if (state.hud != null) {
            try { state.hud.remove(); } catch (Exception | NoClassDefFoundError ignored) {}
            state.hud = null;
        }
    }

    private String buildGauntletHudHtml(GauntletState state) {
        int wave = state.currentWave.get();
        // Color intensifies with waves
        String waveColor;
        if (wave <= 5) waveColor = "#55ff55";
        else if (wave <= 10) waveColor = "#ffff55";
        else if (wave <= 15) waveColor = "#ffaa33";
        else if (wave <= 20) waveColor = "#ff5555";
        else waveColor = "#cc55ff";

        int buffCount = state.activeBuffs.size();

        return String.format("""
            <style>
                #gauntlet-container {
                    vertical-align: center;
                    anchor-right: 16;
                    anchor-width: 380;
                    anchor-height: 155;
                    padding: 8;
                    layout-mode: Top;
                    background-color: rgba(10, 10, 18, 0.75);
                    border-radius: 6;
                }
                #gauntlet-title {
                    font-size: 16; font-weight: bold; color: #FFD700;
                    text-align: center; anchor-height: 20; anchor-width: 100%%;
                }
                #wave-info {
                    font-size: 14; font-weight: bold; color: %s;
                    text-align: center; anchor-height: 18; anchor-width: 100%%; margin-top: 2;
                }
                #buff-info {
                    font-size: 10; color: #88aaff;
                    text-align: center; anchor-height: 14; anchor-width: 100%%; margin-top: 1;
                }
                #progress-bar-bg {
                    anchor-width: 300; anchor-height: 12;
                    background-color: #1a1a2a; margin-top: 6; horizontal-align: center;
                }
                #progress-bar-fill {
                    anchor-width: 0; anchor-height: 12;
                    background-color: %s; anchor-left: 0; anchor-top: 0;
                }
                #kill-text {
                    font-size: 11; color: #cccccc;
                    text-align: center; anchor-height: 14; anchor-width: 100%%; margin-top: 4;
                }
                #status-text {
                    font-size: 12; font-weight: bold; color: #ffff55;
                    text-align: center; anchor-height: 16; anchor-width: 100%%; margin-top: 2;
                }
                #modifier-text {
                    font-size: 10; font-weight: bold; color: #ffaa33;
                    text-align: center; anchor-height: 14; anchor-width: 100%%;
                }
            </style>
            <div id="gauntlet-container">
                <p id="gauntlet-title">THE GAUNTLET</p>
                <p id="wave-info">Wave %d</p>
                <p id="modifier-text"></p>
                <p id="buff-info">%d buffs active</p>
                <div id="progress-bar-bg">
                    <div id="progress-bar-fill"></div>
                </div>
                <p id="kill-text">Preparing...</p>
                <p id="status-text">Starting in 3...</p>
            </div>
            """,
            waveColor,
            waveColor,
            wave,
            buffCount
        );
    }

    // === Helpers ===

    private void healPlayer(GauntletState state, float percent) {
        try {
            Ref<EntityStore> ref = state.playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            Store<EntityStore> store = ref.getStore();
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) return;
            statMap.addStatValue(DefaultEntityStatTypes.getHealth(), statMap.get(DefaultEntityStatTypes.getHealth()).getMax() * percent);
        } catch (Exception e) {
            LOGGER.atFine().log("[Gauntlet] Heal error: %s", e.getMessage());
        }
    }

    // === Stuck NPC handling ===

    private void cleanupStuckNpcs(GauntletState state) {
        List<Ref<EntityStore>> toRemove = new ArrayList<>();

        for (Ref<EntityStore> npcRef : state.aliveNpcRefs) {
            if (!npcRef.isValid()) {
                toRemove.add(npcRef);
                continue;
            }
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
                toRemove.add(npcRef);
            }
        }

        if (toRemove.isEmpty()) return;

        LOGGER.atWarning().log("[Gauntlet] Cleaning up %d stuck/fallen NPCs for %s",
                toRemove.size(), state.playerUuid);

        synchronized (state) {
            for (Ref<EntityStore> ref : toRemove) {
                state.aliveNpcRefs.remove(ref);
                state.totalKilledInWave.incrementAndGet();
                npcToPlayerMap.remove(ref);
            }
            if (state.aliveNpcRefs.isEmpty() && state.phase == GauntletPhase.ACTIVE) {
                state.phase = GauntletPhase.WAVE_CLEAR;
                state.phaseStartTime = System.currentTimeMillis();
            }
        }

        despawnNpcs(toRemove);
    }

    private void forceWaveClear(GauntletState state) {
        List<Ref<EntityStore>> remaining = new ArrayList<>(state.aliveNpcRefs);
        synchronized (state) {
            for (Ref<EntityStore> ref : remaining) {
                state.aliveNpcRefs.remove(ref);
                state.totalKilledInWave.incrementAndGet();
                npcToPlayerMap.remove(ref);
            }
            state.phase = GauntletPhase.WAVE_CLEAR;
            state.phaseStartTime = System.currentTimeMillis();
        }

        despawnNpcs(remaining);
        sendMessage(state.playerRef, "#FFAA00",
                "[The Gauntlet] Remaining enemies cleared (timeout).");
    }

    private void despawnNpcs(List<Ref<EntityStore>> npcsToRemove) {
        if (npcsToRemove.isEmpty()) return;

        Map<World, List<Ref<EntityStore>>> npcsByWorld = new HashMap<>();
        for (Ref<EntityStore> npcRef : npcsToRemove) {
            try {
                if (!npcRef.isValid()) continue;
                World npcWorld = npcRef.getStore().getExternalData().getWorld();
                if (npcWorld != null) {
                    npcsByWorld.computeIfAbsent(npcWorld, k -> new ArrayList<>()).add(npcRef);
                }
            } catch (Exception ignored) {}
        }

        for (Map.Entry<World, List<Ref<EntityStore>>> entry : npcsByWorld.entrySet()) {
            World world = entry.getKey();
            List<Ref<EntityStore>> refs = entry.getValue();
            if (!world.isAlive()) continue;
            world.execute(() -> {
                for (Ref<EntityStore> npcRef : refs) {
                    try {
                        if (npcRef.isValid()) {
                            npcRef.getStore().removeEntity(npcRef,
                                    EntityStore.REGISTRY.newHolder(), RemoveReason.REMOVE);
                        }
                    } catch (Exception ignored) {}
                }
            });
        }
    }

    private void updatePlayerAlive(GauntletState state) {
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
            // Keep last known state
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

    private String findPlayerName(PlayerRef playerRef) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return "Unknown";
            Store<EntityStore> store = ref.getStore();
            var displayName = store.getComponent(ref,
                    com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent.getComponentType());
            if (displayName != null && displayName.getDisplayName() != null) {
                var formatted = displayName.getDisplayName().getFormattedMessage();
                if (formatted != null && formatted.rawText != null) return formatted.rawText;
            }
        } catch (Exception ignored) {}
        return "Unknown";
    }

    private void sendMessage(PlayerRef playerRef, String color, String text) {
        try {
            playerRef.sendMessage(Message.raw(text).color(color));
        } catch (Exception e) {
            // Non-fatal
        }
    }
}
