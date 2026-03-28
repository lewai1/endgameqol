package endgame.plugin.managers.boss;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.LabelBuilder;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import endgame.plugin.EndgameQoL;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GolemVoidBossManager - Handles the Golem Void boss mechanics.
 *
 * Features:
 * - Boss bar UI shown to players via HyUI
 * - Phase system (3 phases based on HP thresholds: 66%, 33%)
 * - Temporary invulnerability during phase transitions
 * - Health tracking and UI updates
 */
public class GolemVoidBossManager {

    private static final double MINION_Y_OFFSET = 2.0;
    private static final String EYE_VOID_HEALTH_MODIFIER_KEY = "EndgameQoL.EyeVoidHealth";
    private static final int HUD_REFRESH_RATE_MS = 200;

    private final Map<Ref<EntityStore>, GolemVoidState> activeBosses = new ConcurrentHashMap<>();
    private final Map<UUID, HyUIHud> playerHuds = new ConcurrentHashMap<>();
    // Track HUDs that have been removed — onRefresh must bail immediately if key is in this set
    private final Set<UUID> removedPlayerHuds = ConcurrentHashMap.newKeySet();
    // Deferred HUD removals — populated from onRefresh callbacks, processed in tick()
    // CRITICAL: Never call h.remove() inside onRefresh — it corrupts HyUI's command buffer mid-cycle
    private final Set<UUID> pendingHudRemovals = ConcurrentHashMap.newKeySet();
    // Track boss refs that need to be removed (populated from callbacks, processed in tick)
    private final Set<Ref<EntityStore>> pendingBossRemovals = ConcurrentHashMap.newKeySet();
    private final EndgameQoL plugin;
    private volatile long lastRemovedKeysCleanup = 0;

    public GolemVoidBossManager(EndgameQoL plugin) {
        this.plugin = plugin;
        plugin.getLogger().atFine().log("[GolemVoidBoss] System initialized");
    }

    public static class GolemVoidState {
        public final Ref<EntityStore> bossRef;
        public final String npcTypeId;
        public volatile int currentPhase = 1;
        public volatile boolean isInvulnerable = false;
        public volatile long invulnerabilityEndTime = 0;
        public volatile float lastHealthPercent = 1.0f;
        public final long spawnTimestamp = System.currentTimeMillis();

        public GolemVoidState(Ref<EntityStore> bossRef, String npcTypeId) {
            this.bossRef = bossRef;
            this.npcTypeId = npcTypeId;
        }
    }

    public void registerBoss(Ref<EntityStore> bossRef, String npcTypeId, Store<EntityStore> store) {
        if (bossRef == null || !bossRef.isValid()) return;

        if (activeBosses.containsKey(bossRef)) {
            plugin.getLogger().atFine().log("[GolemVoidBoss] Boss already registered: %s", npcTypeId);
            return;
        }

        GolemVoidState state = new GolemVoidState(bossRef, npcTypeId);

        ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
        if (statType != null) {
            EntityStatMap statMap = store.getComponent(bossRef, statType);
            if (statMap != null) {
                EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
                if (healthValue != null) {
                    float currentHealth = healthValue.get();
                    float maxHealth = healthValue.getMax();
                    float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 1.0f;

                    // Calculate correct phase based on actual health percentage
                    int actualPhase = calculatePhase(healthPercent);
                    state.currentPhase = actualPhase;
                    state.lastHealthPercent = healthPercent;

                    plugin.getLogger().atFine().log(
                        "[GolemVoidBoss] Boss registered: %s (HP: %.0f/%.0f = %.0f%%, Phase %d)",
                        npcTypeId, currentHealth, maxHealth, healthPercent * 100, actualPhase);
                } else {
                    plugin.getLogger().atFine().log("[GolemVoidBoss] Boss registered: %s (Phase 1, no health data)", npcTypeId);
                }
            } else {
                plugin.getLogger().atFine().log("[GolemVoidBoss] Boss registered: %s (Phase 1, no stat map)", npcTypeId);
            }
        } else {
            plugin.getLogger().atFine().log("[GolemVoidBoss] Boss registered: %s (Phase 1)", npcTypeId);
        }

        activeBosses.put(bossRef, state);

        plugin.getLogger().atFine().log("[GolemVoidBoss] Boss ready - use showBossBarToPlayer() to display bar");
    }

    /**
     * Unregister a boss and hide bars.
     */
    public void unregisterBoss(Ref<EntityStore> bossRef) {
        GolemVoidState state = activeBosses.remove(bossRef);
        if (state != null) {
            plugin.getLogger().atFine().log("[GolemVoidBoss] Boss unregistered: %s", state.npcTypeId);
            // Clear all HUDs
            hideAllBossBars();
        }
    }

    /**
     * Update boss state when it takes damage.
     * Called from GolemVoidDamageSystem.
     */
    public void onBossDamaged(Ref<EntityStore> bossRef, EntityStatMap statMap, Store<EntityStore> store) {
        GolemVoidState state = activeBosses.get(bossRef);
        if (state == null || statMap == null) return;

        EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthValue == null) return;

        float currentHealth = healthValue.get();
        float maxHealth = healthValue.getMax();
        float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0f;

        state.lastHealthPercent = healthPercent;

        // Check for phase transition
        int newPhase = calculatePhase(healthPercent);
        if (newPhase > state.currentPhase) {
            triggerPhaseChange(state, newPhase, store);
        }
    }

    /**
     * Periodic tick to check invulnerability timeout and update UI.
     * IMPORTANT: This method handles invulnerability timeout even if no damage is being dealt.
     * This prevents the bug where bosses stay invulnerable forever if players flee.
     */
    public void tick(Store<EntityStore> store) {
        long now = System.currentTimeMillis();

        // CRITICAL: h.remove() must happen outside onRefresh to avoid corrupting HyUI's command buffer
        if (!pendingHudRemovals.isEmpty()) {
            for (UUID key : Set.copyOf(pendingHudRemovals)) {
                removedPlayerHuds.add(key);
                HyUIHud hud = playerHuds.remove(key);
                if (hud != null) {
                    try { hud.remove(); } catch (Exception e) { plugin.getLogger().atFine().log("[GolemVoidBoss] HUD element update error: %s", e.getMessage()); }
                }
            }
            pendingHudRemovals.clear();
        }

        if (!pendingBossRemovals.isEmpty()) {
            for (Ref<EntityStore> bossRef : pendingBossRemovals) {
                activeBosses.remove(bossRef);
            }
            pendingBossRemovals.clear();
            if (activeBosses.isEmpty()) {
                hideAllBossBars();
            }
        }

        // Every 60s, clear stale removal flags to prevent permanent HUD freeze.
        // The identity check (playerHuds.get(uuid) != h) in onRefresh is the real correctness
        // guard — clearing the set just prevents a stuck state where UUID is in both
        // removedPlayerHuds AND playerHuds (new boss spawned after removal flag was set).
        if (now - lastRemovedKeysCleanup > 60_000) {
            lastRemovedKeysCleanup = now;
            removedPlayerHuds.clear();
        }

        // Check each active boss
        for (Map.Entry<Ref<EntityStore>, GolemVoidState> entry : activeBosses.entrySet()) {
            GolemVoidState state = entry.getValue();
            Ref<EntityStore> bossRef = entry.getKey();

            if (!bossRef.isValid()) {
                pendingBossRemovals.add(bossRef);
                continue;
            }

            if (state.isInvulnerable && now >= state.invulnerabilityEndTime) {
                removeInvulnerability(state);
                plugin.getLogger().atFine().log("[GolemVoidBoss] Invulnerability timeout triggered in tick() for %s", state.npcTypeId);
            }

            // Strip trap/debuff effects (Root, Stun, etc.) from the boss every tick
            stripBossEffects(bossRef);
        }
    }

    private int calculatePhase(float healthPercent) {
        endgame.plugin.config.BossConfig bc = plugin.getConfig().get().getBossConfig(endgame.plugin.utils.BossType.GOLEM_VOID);
        if (healthPercent <= bc.getPhase3Threshold()) return 3;
        if (healthPercent <= bc.getPhase2Threshold()) return 2;
        return 1;
    }

    private void triggerPhaseChange(GolemVoidState state, int newPhase, Store<EntityStore> store) {
        state.currentPhase = newPhase;

        plugin.getLogger().atFine().log("[GolemVoidBoss] Phase change to %d for %s", newPhase, state.npcTypeId);

        // Apply 3 second invulnerability
        applyInvulnerability(state);

        // Spawn Eye_Void minions based on phase (configurable)
        endgame.plugin.config.BossConfig golemCfg = plugin.getConfig().get().getBossConfig(endgame.plugin.utils.BossType.GOLEM_VOID);
        int minionCount = switch (newPhase) {
            case 2 -> golemCfg.getPhase2MinionCount();
            case 3 -> golemCfg.getPhase3MinionCount();
            default -> 0;
        };

        if (minionCount > 0) {
            spawnEyeVoidMinions(state, store, minionCount);
        }
    }

    /**
     * Spawn Eye_Void minions around the boss using world.execute() to avoid threading issues.
     * Fixed: Only spawns in the boss's world, not all worlds.
     */
    private void spawnEyeVoidMinions(GolemVoidState state, Store<EntityStore> store, int count) {
        try {
            ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();
            if (transformType == null || !state.bossRef.isValid()) return;

            TransformComponent transform = store.getComponent(state.bossRef, transformType);
            if (transform == null) return;

            final double bossX = transform.getPosition().getX();
            final double bossY = transform.getPosition().getY();
            final double bossZ = transform.getPosition().getZ();

            // Get the world from the store - only spawn in THIS world, not all worlds
            final com.hypixel.hytale.server.core.universe.world.World bossWorld = store.getExternalData().getWorld();

            final double spawnRadius = plugin.getConfig().get().getMinionSpawnRadius();
            final float healthMultiplier = plugin.getConfig().get().getEyeVoidHealthMultiplier();

            // Use world.execute() to run spawn on world thread (outside system context)
            bossWorld.execute(() -> {
                try {
                    Store<EntityStore> worldStore = bossWorld.getEntityStore().getStore();
                    NPCPlugin npcPlugin = NPCPlugin.get();

                    for (int i = 0; i < count; i++) {
                        double angle = (2.0 * Math.PI * i) / count;
                        double offsetX = Math.cos(angle) * spawnRadius;
                        double offsetZ = Math.sin(angle) * spawnRadius;

                        Vector3d spawnPos = new Vector3d(bossX + offsetX, bossY + MINION_Y_OFFSET, bossZ + offsetZ);
                        Vector3f rotation = new Vector3f(0, (float) Math.toDegrees(angle), 0);

                        // Spawn Eye_Void - uses vanilla role from Server/NPC/Roles/Void/Eye_Void.json
                        var result = npcPlugin.spawnNPC(worldStore, "Eye_Void", null, spawnPos, rotation);

                        if (result != null) {
                            // Feature 4D: Scale Eye_Void health based on config multiplier
                            applyEyeVoidHealthScaling(result.left(), worldStore, healthMultiplier);

                            plugin.getLogger().atFine().log(
                                "[GolemVoidBoss] Spawned Eye_Void #%d at %.1f, %.1f, %.1f (radius=%.1f)",
                                i + 1, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), spawnRadius);
                        }
                    }

                    plugin.getLogger().atFine().log("[GolemVoidBoss] Spawned %d Eye_Void minions for phase change", count);
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[GolemVoidBoss] Failed to spawn Eye_Void: %s", e.getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GolemVoidBoss] Failed to queue minion spawn: %s", e.getMessage());
        }
    }

    /**
     * Apply health scaling to a spawned Eye_Void minion.
     * Uses StaticModifier pattern from BossHealthManager.
     */
    private void applyEyeVoidHealthScaling(Ref<EntityStore> minionRef, Store<EntityStore> store, float healthMultiplier) {
        if (minionRef == null || !minionRef.isValid() || healthMultiplier <= 1.0f) return;

        try {
            ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
            if (statType == null) return;

            EntityStatMap statMap = store.getComponent(minionRef, statType);
            if (statMap == null) return;

            int healthStat = DefaultEntityStatTypes.getHealth();
            EntityStatValue healthValue = statMap.get(healthStat);
            if (healthValue == null) return;

            float baseMax = healthValue.getMax();
            float additionalHealth = baseMax * (healthMultiplier - 1.0f);

            // Apply MAX modifier (ADDITIVE) to increase max HP
            StaticModifier mod = new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, additionalHealth);
            statMap.putModifier(healthStat, EYE_VOID_HEALTH_MODIFIER_KEY, mod);

            // Heal to new max
            statMap.addStatValue(healthStat, additionalHealth);

            plugin.getLogger().atFine().log("[GolemVoidBoss] Eye_Void health scaled: %.0f -> %.0f (%.1fx)",
                    baseMax, baseMax + additionalHealth, healthMultiplier);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GolemVoidBoss] Failed to scale Eye_Void health: %s", e.getMessage());
        }
    }

    private void applyInvulnerability(GolemVoidState state) {
        if (state.isInvulnerable) return;

        state.isInvulnerable = true;
        endgame.plugin.config.BossConfig bc = plugin.getConfig().get().getBossConfig(endgame.plugin.utils.BossType.GOLEM_VOID);
        state.invulnerabilityEndTime = System.currentTimeMillis() + bc.getPhaseInvulnerabilityMs();

        plugin.getLogger().atFine().log("[GolemVoidBoss] Invulnerability ON (3s)");
    }

    /**
     * Strip active effects from the boss every tick.
     * Combined with the data-driven Snapjaw trap override (Cooldown: 0 -> 0.3s),
     * this reliably prevents bear trap immobilization:
     * - Trap reapplies effect every 300ms, we clear every 200ms
     * - Boss is never permanently stuck (max 200ms pause per cycle)
     */
    private void stripBossEffects(Ref<EntityStore> bossRef) {
        try {
            if (!bossRef.isValid()) return;
            Store<EntityStore> bossStore = bossRef.getStore();
            World bossWorld = bossStore.getExternalData().getWorld();
            if (bossWorld == null) return;

            // ALL ECS access must run on the boss's world thread (not the ticking player's thread)
            bossWorld.execute(() -> {
                try {
                    if (!bossRef.isValid()) return;
                    ComponentType<EntityStore, EffectControllerComponent> effectType = EffectControllerComponent.getComponentType();
                    if (effectType == null) return;
                    EffectControllerComponent ec = bossStore.getComponent(bossRef, effectType);
                    if (ec != null && !ec.getActiveEffects().isEmpty()) {
                        int effectCount = ec.getActiveEffects().size();
                        ec.clearEffects(bossRef, bossStore);
                        plugin.getLogger().atFine().log(
                                "[GolemVoidBoss] Stripped %d effect(s) from boss", effectCount);
                    }
                } catch (Exception e) {
                    plugin.getLogger().atFine().log("[GolemVoidBoss] Effect strip deferred error: %s", e.getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GolemVoidBoss] Failed to strip effects: %s", e.getMessage());
        }
    }

    private void removeInvulnerability(GolemVoidState state) {
        if (!state.isInvulnerable) return;

        state.isInvulnerable = false;
        plugin.getLogger().atFine().log("[GolemVoidBoss] Invulnerability OFF");
    }

    /**
     * Check if boss is currently invulnerable.
     */
    public boolean isBossInvulnerable(Ref<EntityStore> bossRef) {
        GolemVoidState state = activeBosses.get(bossRef);
        return state != null && state.isInvulnerable;
    }

    /**
     * Check and remove invulnerability if timeout has passed.
     */
    public void checkInvulnerabilityTimeout(Ref<EntityStore> bossRef) {
        GolemVoidState state = activeBosses.get(bossRef);
        if (state == null) return;

        long now = System.currentTimeMillis();
        if (state.isInvulnerable && now >= state.invulnerabilityEndTime) {
            removeInvulnerability(state);
        }
    }

    /**
     * Update health tracking for UI purposes (called even when invulnerable).
     */
    public void updateHealthTracking(Ref<EntityStore> bossRef, EntityStatMap statMap) {
        GolemVoidState state = activeBosses.get(bossRef);
        if (state == null || statMap == null) return;

        EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthValue == null) return;

        float currentHealth = healthValue.get();
        float maxHealth = healthValue.getMax();
        state.lastHealthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0f;
    }

    /**
     * Show boss bar to a specific player.
     * Called when player engages with or is near the boss.
     */
    public void showBossBarToPlayer(PlayerRef playerRef, Store<EntityStore> store) {
        if (activeBosses.isEmpty()) return;
        if (playerRef == null) return;

        UUID playerUuid = endgame.plugin.utils.EntityUtils.getUuid(playerRef);
        if (playerUuid == null) return;

        // Skip if player already has HUD (avoid duplicates)
        if (playerHuds.containsKey(playerUuid)) return;

        // Clear removed flag so the new HUD's refresh will work
        removedPlayerHuds.remove(playerUuid);

        // Get the first active boss state (thread-safe)
        GolemVoidState state = activeBosses.values().stream().findFirst().orElse(null);
        if (state == null) return;

        String html = buildBossBarHtml(state);

        try {
            HyUIHud hud = HudBuilder.hudForPlayer(playerRef)
                .fromHtml(html)
                .withRefreshRate(HUD_REFRESH_RATE_MS)
                .onRefresh(h -> {
                    try {
                    // CRITICAL: Bail immediately if this HUD was marked as removed.
                    // This prevents sending commands to a client-side removed element.
                    if (removedPlayerHuds.contains(playerUuid)) return;
                    if (playerHuds.get(playerUuid) != h) return;

                    GolemVoidState currentState = activeBosses.values().stream().findFirst().orElse(null);
                    if (currentState == null || !currentState.bossRef.isValid()
                            || currentState.lastHealthPercent <= 0.001f) {
                        // Boss dead/invalid — defer removal to tick(), do NOT call h.remove() here
                        removedPlayerHuds.add(playerUuid);
                        pendingHudRemovals.add(playerUuid);
                        return;
                    }

                    // Snapshot volatile fields to prevent mid-update tearing
                    final float snapshotHealth = currentState.lastHealthPercent;
                    final int snapshotPhase = currentState.currentPhase;
                    final boolean snapshotInvuln = currentState.isInvulnerable;

                    int healthPct = Math.round(snapshotHealth * 100);
                    String phText = getPhaseText(snapshotPhase);
                    if (snapshotInvuln) phText += " [INVULNERABLE]";

                    final String finalPhText = phText;
                    int fillWidth = Math.max(0, Math.min(HEALTH_BAR_WIDTH,
                            Math.round(HEALTH_BAR_WIDTH * snapshotHealth)));

                    try { h.getById("health-text", LabelBuilder.class)
                            .ifPresent(l -> l.withText(healthPct + "% HP")); } catch (Exception e) { plugin.getLogger().atFine().log("[GolemVoidBoss] HUD element update error: %s", e.getMessage()); }
                    try { h.getById("phase-text", LabelBuilder.class)
                            .ifPresent(l -> l.withText(finalPhText)); } catch (Exception e) { plugin.getLogger().atFine().log("[GolemVoidBoss] HUD element update error: %s", e.getMessage()); }
                    try { h.getById("health-bar-fill", GroupBuilder.class)
                            .ifPresent(b -> b.withAnchor(new HyUIAnchor()
                                    .setWidth(fillWidth).setHeight(14).setLeft(0).setTop(0))); } catch (Exception e) { plugin.getLogger().atFine().log("[GolemVoidBoss] HUD element update error: %s", e.getMessage()); }
                    } catch (Exception e) {
                        // Outer catch — flag for deferred removal, NEVER call h.remove() here
                        removedPlayerHuds.add(playerUuid);
                        pendingHudRemovals.add(playerUuid);
                    }
                })
                .show();

            // CRITICAL: Store the HUD reference for later removal
            playerHuds.put(playerUuid, hud);
            plugin.getLogger().atFine().log("[GolemVoidBoss] Boss bar shown to player");
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GolemVoidBoss] Failed to show boss bar: %s", e.getMessage());
        }
    }

    /**
     * Hide boss bar for a specific player (e.g., when they die or leave the zone).
     */
    public void hideBossBarForPlayer(PlayerRef playerRef) {
        if (playerRef == null) return;
        UUID playerUuid = endgame.plugin.utils.EntityUtils.getUuid(playerRef);
        if (playerUuid == null) return;
        if (playerHuds.containsKey(playerUuid)) {
            removedPlayerHuds.add(playerUuid);
            pendingHudRemovals.add(playerUuid);
        }
        plugin.getLogger().atFine().log("[GolemVoidBoss] Boss bar hidden for player (death/leave)");
    }

    /**
     * Hide boss bar for a specific player holder (e.g., when they leave/enter a world).
     */
    public void hideBossBarForHolder(Holder<EntityStore> holder) {
        if (holder == null) return;
        try {
            for (PlayerRef pRef : Universe.get().getPlayers()) {
                if (pRef == null) continue;
                try {
                    if (Objects.equals(pRef.getHolder(), holder)) {
                        hideBossBarForPlayer(pRef);
                        break;
                    }
                } catch (Exception e) {
                    // pRef.getHolder() may throw during instance teardown — skip this player
                }
            }
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[GolemVoidBoss] hideBossBarForHolder skipped: %s", e.getMessage());
        }
    }

    /**
     * Hide boss bar for a player by UUID (e.g., when they disconnect).
     */
    public void hideBossBarForPlayerUuid(UUID playerUuid) {
        if (playerUuid == null) return;
        if (playerHuds.containsKey(playerUuid)) {
            removedPlayerHuds.add(playerUuid);
            pendingHudRemovals.add(playerUuid);
        }
        plugin.getLogger().atFine().log("[GolemVoidBoss] Boss bar hidden for disconnected player");
    }

    /**
     * Hide all boss bars (e.g., when boss dies).
     */
    public void hideAllBossBars() {
        for (UUID key : playerHuds.keySet()) {
            removedPlayerHuds.add(key);
            pendingHudRemovals.add(key);
        }
        plugin.getLogger().atFine().log("[GolemVoidBoss] All boss bars marked for removal");
    }

    // Health bar background width in pixels
    private static final int HEALTH_BAR_WIDTH = 380;

    private String buildBossBarHtml(GolemVoidState state) {
        String phaseText = getPhaseText(state.currentPhase);
        String phaseColor = getPhaseColor(state.currentPhase);
        String healthBarColor = getHealthBarColor(state.lastHealthPercent);

        return endgame.plugin.utils.BossBarHtmlBuilder.buildBossBar(
                "GOLEM VOID", "#bb44ff",
                phaseText, phaseColor,
                state.isInvulnerable,
                state.lastHealthPercent, HEALTH_BAR_WIDTH, healthBarColor);
    }

    private String getHealthBarColor(float healthPercent) {
        if (healthPercent > 0.66f) return "#00cc00";
        if (healthPercent > 0.33f) return "#ffaa00";
        return "#cc0000";
    }

    private String getPhaseText(int phase) {
        return switch (phase) {
            case 1 -> "Phase 1 - Awakened";
            case 2 -> "Phase 2 - Enraged";
            case 3 -> "Phase 3 - FURY";
            default -> "Phase " + phase;
        };
    }

    private String getPhaseColor(int phase) {
        return switch (phase) {
            case 1 -> "#88ff88";
            case 2 -> "#ffaa00";
            case 3 -> "#ff0000";
            default -> "#ffffff";
        };
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public GolemVoidState getBossState(Ref<EntityStore> bossRef) {
        return activeBosses.get(bossRef);
    }

    public Map<Ref<EntityStore>, GolemVoidState> getActiveBosses() {
        return java.util.Collections.unmodifiableMap(activeBosses);
    }

    public EndgameQoL getPlugin() {
        return plugin;
    }

    /**
     * Force clear all boss state and HUDs. Called on world change or boss death.
     */
    public void forceClearAllBossBars() {
        activeBosses.clear();
        pendingBossRemovals.clear();
        pendingHudRemovals.clear();
        // Force clear does IMMEDIATE removal (for shutdown) - tick() won't run again
        for (Map.Entry<UUID, HyUIHud> entry : playerHuds.entrySet()) {
            removedPlayerHuds.add(entry.getKey());
            try {
                entry.getValue().remove();
            } catch (Exception e) { plugin.getLogger().atFine().log("[GolemVoidBoss] HUD element update error: %s", e.getMessage()); }
        }
        playerHuds.clear();
        removedPlayerHuds.clear();
        plugin.getLogger().atFine().log("[GolemVoidBoss] Force cleared all boss bars and state");
    }
}
