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
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.npc.NPCPlugin;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.BossType;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GenericBossManager — handles boss bar HUD, phase transitions, and
 * invulnerability
 * for Frost Dragon, Hedera, and future bosses. Golem Void uses its own
 * dedicated manager.
 */
public class GenericBossManager {

    private static final int HUD_REFRESH_RATE_MS = 200;
    private static final int HEALTH_BAR_WIDTH = 380;
    private static final int ELITE_HEALTH_BAR_WIDTH = 280;

    // Composite key for per-player per-boss HUD tracking — uses Ref directly (stable equals/hashCode)
    private record HudKey(UUID playerUuid, Ref<EntityStore> bossRef) {}

    private final Map<Ref<EntityStore>, GenericBossState> activeBosses = new ConcurrentHashMap<>();
    private final Map<HudKey, HyUIHud> playerHuds = new ConcurrentHashMap<>();
    // Track HUDs that have been removed — onRefresh must bail immediately if key is
    // in this set
    private final Set<HudKey> removedHudKeys = ConcurrentHashMap.newKeySet();
    // Deferred HUD removals — populated from onRefresh callbacks, processed in
    // tick()
    // CRITICAL: Never call h.remove() inside onRefresh — it corrupts HyUI's command
    // buffer mid-cycle
    private final Set<HudKey> pendingHudRemovals = ConcurrentHashMap.newKeySet();
    private final Set<Ref<EntityStore>> pendingBossRemovals = ConcurrentHashMap.newKeySet();
    private final EndgameQoL plugin;
    private volatile long lastRemovedKeysCleanup = 0;

    // =========================================================================
    // Configuration types
    // =========================================================================

    @FunctionalInterface
    public interface PhaseCallback {
        void onPhaseChange(GenericBossState state, int newPhase, Store<EntityStore> store);
    }

    public static class BossEncounterConfig {
        public final BossType bossType;
        public final String displayName;
        public final String nameColor;
        public final float[] phaseThresholds;
        public final String[] phaseNames;
        public final String[] phaseColors;
        public final long invulnerabilityDurationMs;
        public final String[] healthBarColors; // per-phase health bar gradient
        public final PhaseCallback phaseCallback;
        public final boolean elite; // true = elite mob (no phases, different bar style)

        public BossEncounterConfig(BossType bossType, String displayName, String nameColor,
                float[] phaseThresholds, String[] phaseNames, String[] phaseColors,
                long invulnerabilityDurationMs, String[] healthBarColors,
                PhaseCallback phaseCallback) {
            this.bossType = bossType;
            this.displayName = displayName;
            this.nameColor = nameColor;
            this.phaseThresholds = phaseThresholds;
            this.phaseNames = phaseNames;
            this.phaseColors = phaseColors;
            this.invulnerabilityDurationMs = invulnerabilityDurationMs;
            this.healthBarColors = healthBarColors;
            this.phaseCallback = phaseCallback;
            this.elite = false;
        }

        /** Elite mob config — no phases, no callbacks, compact bar style. */
        public BossEncounterConfig(BossType bossType, String displayName, String nameColor,
                String healthBarColor) {
            this.bossType = bossType;
            this.displayName = displayName;
            this.nameColor = nameColor;
            this.phaseThresholds = new float[] {};
            this.phaseNames = new String[] { "Elite" };
            this.phaseColors = new String[] { nameColor };
            this.invulnerabilityDurationMs = 0;
            this.healthBarColors = new String[] { healthBarColor };
            this.phaseCallback = null;
            this.elite = true;
        }
    }

    public static class GenericBossState {
        public final Ref<EntityStore> bossRef;
        public final BossEncounterConfig config;
        public final String npcTypeId;
        public volatile int currentPhase = 1;
        public volatile boolean isInvulnerable = false;
        public volatile long invulnerabilityEndTime = 0;
        public volatile float lastHealthPercent = 1.0f;
        public final long spawnTimestamp = System.currentTimeMillis();

        public GenericBossState(Ref<EntityStore> bossRef, BossEncounterConfig config, String npcTypeId) {
            this.bossRef = bossRef;
            this.config = config;
            this.npcTypeId = npcTypeId;
        }
    }

    // =========================================================================
    // Boss configurations
    // =========================================================================

    private final BossEncounterConfig frostDragonConfig;
    private final BossEncounterConfig hederaConfig;
    private final BossEncounterConfig alphaRexConfig;
    private final BossEncounterConfig swampCrocodileConfig;
    private final BossEncounterConfig brambleEliteConfig;

    public GenericBossManager(EndgameQoL plugin) {
        this.plugin = plugin;

        this.frostDragonConfig = new BossEncounterConfig(
                BossType.DRAGON_FROST,
                "FROST DRAGON",
                "#66ccff",
                new float[] { 0.70f, 0.40f },
                new String[] { "Frozen Calm", "Ice Storm", "Blizzard Fury" },
                new String[] { "#88ccff", "#ffaa00", "#ff0000" },
                3000,
                new String[] { "#66ccff", "#4499ff", "#0044cc" },
                this::onFrostDragonPhaseChange);

        this.hederaConfig = new BossEncounterConfig(
                BossType.HEDERA,
                "HEDERA",
                "#33cc33",
                new float[] { 0.66f, 0.33f },
                new String[] { "Nature's Wrath", "Toxic Bloom", "Death Blossom" },
                new String[] { "#88ff88", "#ccaa00", "#ff0000" },
                3000,
                new String[] { "#33cc33", "#cc8800", "#cc0000" },
                this::onHederaPhaseChange);

        this.alphaRexConfig = new BossEncounterConfig(
                BossType.ALPHA_REX, "ALPHA REX", "#ff8800", "#ff8800");

        this.swampCrocodileConfig = new BossEncounterConfig(
                BossType.SWAMP_CROCODILE, "SWAMP CROCODILE", "#4a7c3f", "#4a7c3f");

        this.brambleEliteConfig = new BossEncounterConfig(
                BossType.BRAMBLE_ELITE, "BRAMBLE ELITE", "#66aa33", "#66aa33");

        plugin.getLogger().atFine().log("[GenericBoss] Manager initialized (Frost Dragon + Hedera + 3 elites)");
    }

    // =========================================================================
    // Registration
    // =========================================================================

    public void registerBoss(Ref<EntityStore> bossRef, String npcTypeId, Store<EntityStore> store) {
        if (bossRef == null || !bossRef.isValid())
            return;
        if (activeBosses.containsKey(bossRef))
            return;

        BossType bossType = BossType.fromTypeId(npcTypeId);
        if (bossType == null)
            return;

        BossEncounterConfig config = getConfigForBossType(bossType);
        if (config == null)
            return;

        GenericBossState state = new GenericBossState(bossRef, config, npcTypeId);

        // Calculate correct initial phase from current HP
        ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
        if (statType != null) {
            EntityStatMap statMap = store.getComponent(bossRef, statType);
            if (statMap != null) {
                EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
                if (healthValue != null) {
                    float currentHealth = healthValue.get();
                    float maxHealth = healthValue.getMax();
                    float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 1.0f;
                    state.currentPhase = calculatePhase(config, healthPercent);
                    state.lastHealthPercent = healthPercent;

                    plugin.getLogger().atFine().log(
                            "[GenericBoss] Registered %s: %s (HP: %.0f/%.0f = %.0f%%, Phase %d)",
                            config.displayName, npcTypeId, currentHealth, maxHealth,
                            healthPercent * 100, state.currentPhase);
                }
            }
        }

        activeBosses.put(bossRef, state);
    }

    public void unregisterBoss(Ref<EntityStore> bossRef) {
        GenericBossState state = activeBosses.remove(bossRef);
        if (state != null) {
            plugin.getLogger().atFine().log("[GenericBoss] Unregistered %s: %s",
                    state.config.displayName, state.npcTypeId);
            hideAllBossBarsForBoss(bossRef);
        }
    }

    // =========================================================================
    // Damage / Phase transitions
    // =========================================================================

    public void onBossDamaged(Ref<EntityStore> bossRef, EntityStatMap statMap, Store<EntityStore> store) {
        GenericBossState state = activeBosses.get(bossRef);
        if (state == null || statMap == null)
            return;

        EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthValue == null)
            return;

        float currentHealth = healthValue.get();
        float maxHealth = healthValue.getMax();
        float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0f;
        state.lastHealthPercent = healthPercent;

        // No phase transitions for elites
        if (state.config.elite)
            return;

        int newPhase = calculatePhase(state.config, healthPercent);
        if (newPhase > state.currentPhase) {
            triggerPhaseChange(state, newPhase, store);
        }
    }

    private void triggerPhaseChange(GenericBossState state, int newPhase, Store<EntityStore> store) {
        state.currentPhase = newPhase;
        plugin.getLogger().atFine().log("[GenericBoss] %s phase change to %d (%s)",
                state.config.displayName, newPhase, state.config.phaseNames[newPhase - 1]);

        applyInvulnerability(state);

        if (state.config.phaseCallback != null) {
            try {
                state.config.phaseCallback.onPhaseChange(state, newPhase, store);
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[GenericBoss] Phase callback error: %s", e.getMessage());
            }
        }
    }

    // =========================================================================
    // Phase callbacks
    // =========================================================================

    private void onFrostDragonPhaseChange(GenericBossState state, int newPhase, Store<EntityStore> store) {
        int spiritCount = newPhase == 2 ? 2 : 3;
        spawnMinionsAroundBoss(state, store, "Spirit_Frost", spiritCount, 10.0);
        plugin.getLogger().atFine().log("[GenericBoss] Frost Dragon phase %d — spawned %d Spirit_Frost",
                newPhase, spiritCount);
    }

    private void onHederaPhaseChange(GenericBossState state, int newPhase, Store<EntityStore> store) {
        // Hedera phase transition: strip all effects (cleanse) + effect stripping on
        // tick
        stripBossEffects(state.bossRef);
        plugin.getLogger().atFine().log("[GenericBoss] Hedera phase %d — effect cleanse triggered", newPhase);
    }

    private void spawnMinionsAroundBoss(GenericBossState state, Store<EntityStore> store,
            String minionId, int count, double radius) {
        try {
            TransformComponent transform = store.getComponent(state.bossRef, TransformComponent.getComponentType());
            if (transform == null)
                return;

            Vector3d bossPos = transform.getPosition();
            if (bossPos == null)
                return;

            final var bossWorld = store.getExternalData().getWorld();

            bossWorld.execute(() -> {
                try {
                    Store<EntityStore> worldStore = bossWorld.getEntityStore().getStore();
                    for (int i = 0; i < count; i++) {
                        double angle = (2.0 * Math.PI * i) / count;
                        double offsetX = Math.cos(angle) * radius;
                        double offsetZ = Math.sin(angle) * radius;
                        Vector3d spawnPos = new Vector3d(bossPos.x + offsetX, bossPos.y + 2.0, bossPos.z + offsetZ);
                        Vector3f rotation = new Vector3f(0, (float) Math.toDegrees(angle), 0);
                        NPCPlugin.get().spawnNPC(worldStore, minionId, null, spawnPos, rotation);
                    }
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[GenericBoss] Failed to spawn minions: %s", e.getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GenericBoss] Failed to queue minion spawn: %s", e.getMessage());
        }
    }

    // =========================================================================
    // Invulnerability
    // =========================================================================

    private void applyInvulnerability(GenericBossState state) {
        if (state.isInvulnerable)
            return;
        state.isInvulnerable = true;
        state.invulnerabilityEndTime = System.currentTimeMillis() + state.config.invulnerabilityDurationMs;
        plugin.getLogger().atFine().log("[GenericBoss] %s invulnerability ON (%dms)",
                state.config.displayName, state.config.invulnerabilityDurationMs);
    }

    private void removeInvulnerability(GenericBossState state) {
        if (!state.isInvulnerable)
            return;
        state.isInvulnerable = false;
        plugin.getLogger().atFine().log("[GenericBoss] %s invulnerability OFF", state.config.displayName);
    }

    public boolean isBossInvulnerable(Ref<EntityStore> bossRef) {
        GenericBossState state = activeBosses.get(bossRef);
        return state != null && state.isInvulnerable;
    }

    public void checkInvulnerabilityTimeout(Ref<EntityStore> bossRef) {
        GenericBossState state = activeBosses.get(bossRef);
        if (state == null)
            return;
        long now = System.currentTimeMillis();
        if (state.isInvulnerable && now >= state.invulnerabilityEndTime) {
            removeInvulnerability(state);
        }
    }

    public void updateHealthTracking(Ref<EntityStore> bossRef, EntityStatMap statMap) {
        GenericBossState state = activeBosses.get(bossRef);
        if (state == null || statMap == null)
            return;
        EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
        if (healthValue == null)
            return;
        float currentHealth = healthValue.get();
        float maxHealth = healthValue.getMax();
        state.lastHealthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0f;
    }

    // =========================================================================
    // Tick
    // =========================================================================

    public void tick(Store<EntityStore> store) {
        long now = System.currentTimeMillis();

        // Process deferred HUD removals (from onRefresh callbacks)
        // CRITICAL: h.remove() must happen outside onRefresh to avoid corrupting HyUI's
        // command buffer
        if (!pendingHudRemovals.isEmpty()) {
            for (HudKey key : Set.copyOf(pendingHudRemovals)) {
                removedHudKeys.add(key);
                HyUIHud hud = playerHuds.remove(key);
                if (hud != null) {
                    try {
                        hud.remove();
                    } catch (Exception ignored) {
                    }
                }
            }
            pendingHudRemovals.clear();
        }

        // Process pending boss removals
        if (!pendingBossRemovals.isEmpty()) {
            for (Ref<EntityStore> bossRef : pendingBossRemovals) {
                activeBosses.remove(bossRef);
            }
            pendingBossRemovals.clear();
        }

        // Every 60s, clear stale removal flags to prevent permanent HUD freeze.
        // The identity check (playerHuds.get(key) != h) in onRefresh is the real correctness
        // guard — clearing the set just prevents a stuck state where HudKey is in both
        // removedHudKeys AND playerHuds (new boss spawned after removal flag was set).
        if (now - lastRemovedKeysCleanup > 60_000) {
            lastRemovedKeysCleanup = now;
            removedHudKeys.clear();
        }

        for (Map.Entry<Ref<EntityStore>, GenericBossState> entry : activeBosses.entrySet()) {
            GenericBossState state = entry.getValue();
            Ref<EntityStore> bossRef = entry.getKey();

            if (!bossRef.isValid()) {
                pendingBossRemovals.add(bossRef);
                continue;
            }

            // Check invulnerability timeout every tick
            if (state.isInvulnerable && now >= state.invulnerabilityEndTime) {
                removeInvulnerability(state);
            }

            // Strip effects from bosses (trap immunity)
            stripBossEffects(bossRef);
        }
    }

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
                        int count = ec.getActiveEffects().size();
                        ec.clearEffects(bossRef, bossStore);
                        plugin.getLogger().atFine().log("[GenericBoss] Stripped %d effect(s)", count);
                    }
                } catch (Exception ignored) {
                    // Effect controller may not exist for all NPCs
                }
            });
        } catch (Exception e) {
            // Silently ignore — effect controller may not exist for all NPCs
        }
    }

    // =========================================================================
    // Phase calculation
    // =========================================================================

    private int calculatePhase(BossEncounterConfig config, float healthPercent) {
        // Thresholds are in descending order: {0.70, 0.40} means phase 2 at 70%, phase
        // 3 at 40%
        for (int i = config.phaseThresholds.length - 1; i >= 0; i--) {
            if (healthPercent <= config.phaseThresholds[i]) {
                return i + 2; // phase 2 for first threshold, phase 3 for second, etc.
            }
        }
        return 1;
    }

    public int getCurrentPhase(Ref<EntityStore> bossRef) {
        GenericBossState state = activeBosses.get(bossRef);
        return state != null ? state.currentPhase : 0;
    }

    // =========================================================================
    // Boss Bar HUD
    // =========================================================================

    public void showBossBarToPlayer(PlayerRef playerRef, Ref<EntityStore> bossRef, Store<EntityStore> store) {
        if (playerRef == null || bossRef == null)
            return;
        UUID playerUuid = endgame.plugin.utils.EntityUtils.getUuid(playerRef);
        if (playerUuid == null)
            return;

        GenericBossState state = activeBosses.get(bossRef);
        if (state == null)
            return;

        HudKey hudKey = new HudKey(playerUuid, bossRef);
        if (playerHuds.containsKey(hudKey))
            return;

        // Clear removed flag so the new HUD's refresh will work
        removedHudKeys.remove(hudKey);

        String html = state.config.elite ? buildEliteBarHtml(state) : buildBossBarHtml(state);

        try {
            HyUIHud hud = HudBuilder.hudForPlayer(playerRef)
                    .fromHtml(html)
                    .withRefreshRate(HUD_REFRESH_RATE_MS)
                    .onRefresh(h -> {
                        try {
                            // CRITICAL: Bail immediately if this HUD was marked as removed.
                            // This prevents sending commands to a client-side removed element.
                            if (removedHudKeys.contains(hudKey))
                                return;
                            if (playerHuds.get(hudKey) != h)
                                return;

                            GenericBossState currentState = activeBosses.get(bossRef);
                            if (currentState == null || !currentState.bossRef.isValid()
                                    || currentState.lastHealthPercent <= 0.001f) {
                                // Boss dead/invalid — defer removal to tick(), do NOT call h.remove() here
                                removedHudKeys.add(hudKey);
                                pendingHudRemovals.add(hudKey);
                                return;
                            }

                            // Snapshot volatile fields to prevent mid-update tearing
                            final float snapshotHealth = currentState.lastHealthPercent;
                            final int snapshotPhase = currentState.currentPhase;
                            final boolean snapshotInvuln = currentState.isInvulnerable;

                            int healthPct = Math.round(snapshotHealth * 100);

                            if (currentState.config.elite) {
                                // Elite bar: update health text and fill only (no phase text)
                                int eliteFillWidth = Math.max(0, Math.min(ELITE_HEALTH_BAR_WIDTH,
                                        Math.round(ELITE_HEALTH_BAR_WIDTH * snapshotHealth)));
                                try {
                                    h.getById("health-text", LabelBuilder.class)
                                            .ifPresent(l -> l.withText(healthPct + "% HP"));
                                } catch (Exception e) {
                                    plugin.getLogger().atFine().log("[GenericBoss] HUD element update error: %s", e.getMessage());
                                }
                                try {
                                    h.getById("health-bar-fill", GroupBuilder.class)
                                            .ifPresent(b -> b.withAnchor(new HyUIAnchor()
                                                    .setWidth(eliteFillWidth).setHeight(10).setLeft(0).setTop(0)));
                                } catch (Exception e) {
                                    plugin.getLogger().atFine().log("[GenericBoss] HUD element update error: %s", e.getMessage());
                                }
                            } else {
                                // Boss bar: update phase text, health text, and fill (using snapshots)
                                String phText = (snapshotPhase >= 1 && snapshotPhase <= currentState.config.phaseNames.length)
                                        ? currentState.config.phaseNames[snapshotPhase - 1]
                                        : "Unknown";
                                String fullPhaseText = "Phase " + snapshotPhase + " - " + phText;
                                if (snapshotInvuln)
                                    fullPhaseText += " [INVULNERABLE]";

                                final String finalPhaseText = fullPhaseText;
                                int fillWidth = Math.max(0, Math.min(HEALTH_BAR_WIDTH,
                                        Math.round(HEALTH_BAR_WIDTH * snapshotHealth)));

                                try {
                                    h.getById("health-text", LabelBuilder.class)
                                            .ifPresent(l -> l.withText(healthPct + "% HP"));
                                } catch (Exception e) {
                                    plugin.getLogger().atFine().log("[GenericBoss] HUD element update error: %s", e.getMessage());
                                }
                                try {
                                    h.getById("phase-text", LabelBuilder.class)
                                            .ifPresent(l -> l.withText(finalPhaseText));
                                } catch (Exception e) {
                                    plugin.getLogger().atFine().log("[GenericBoss] HUD element update error: %s", e.getMessage());
                                }
                                try {
                                    h.getById("health-bar-fill", GroupBuilder.class)
                                            .ifPresent(b -> b.withAnchor(new HyUIAnchor()
                                                    .setWidth(fillWidth).setHeight(14).setLeft(0).setTop(0)));
                                } catch (Exception e) {
                                    plugin.getLogger().atFine().log("[GenericBoss] HUD element update error: %s", e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            // Outer catch — flag for deferred removal, NEVER call h.remove() here
                            removedHudKeys.add(hudKey);
                            pendingHudRemovals.add(hudKey);
                        }
                    })
                    .show();

            playerHuds.put(hudKey, hud);
            plugin.getLogger().atFine().log("[GenericBoss] Boss bar shown: %s", state.config.displayName);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GenericBoss] Failed to show boss bar: %s", e.getMessage());
        }
    }

    private String buildBossBarHtml(GenericBossState state) {
        BossEncounterConfig config = state.config;
        String phaseName = config.phaseNames[state.currentPhase - 1];
        String phaseText = "Phase " + state.currentPhase + " - " + phaseName;
        String phaseColor = config.phaseColors[state.currentPhase - 1];
        String healthBarColor = config.healthBarColors[state.currentPhase - 1];

        return endgame.plugin.utils.BossBarHtmlBuilder.buildBossBar(
                config.displayName, config.nameColor,
                phaseText, phaseColor,
                state.isInvulnerable,
                state.lastHealthPercent, HEALTH_BAR_WIDTH, healthBarColor);
    }

    private String buildEliteBarHtml(GenericBossState state) {
        BossEncounterConfig config = state.config;
        String healthBarColor = config.healthBarColors[0];

        return endgame.plugin.utils.BossBarHtmlBuilder.buildEliteBar(
                config.displayName, config.nameColor,
                state.lastHealthPercent, ELITE_HEALTH_BAR_WIDTH, healthBarColor);
    }

    // =========================================================================
    // Boss bar hide/clear methods
    // =========================================================================

    public void hideBossBarForPlayer(PlayerRef playerRef) {
        if (playerRef == null)
            return;
        UUID playerUuid = endgame.plugin.utils.EntityUtils.getUuid(playerRef);
        if (playerUuid == null)
            return;
        for (HudKey key : playerHuds.keySet()) {
            if (playerUuid.equals(key.playerUuid)) {
                removedHudKeys.add(key);
                pendingHudRemovals.add(key);
            }
        }
    }

    public void hideBossBarForHolder(Holder<EntityStore> holder) {
        if (holder == null)
            return;
        try {
            for (PlayerRef pRef : Universe.get().getPlayers()) {
                if (pRef == null)
                    continue;
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
            plugin.getLogger().atFine().log("[GenericBoss] hideBossBarForHolder skipped: %s", e.getMessage());
        }
    }

    public void hideBossBarForPlayerUuid(UUID playerUuid) {
        if (playerUuid == null)
            return;
        for (HudKey key : playerHuds.keySet()) {
            if (playerUuid.equals(key.playerUuid)) {
                removedHudKeys.add(key);
                pendingHudRemovals.add(key);
            }
        }
    }

    private void hideAllBossBarsForBoss(Ref<EntityStore> bossRef) {
        for (HudKey key : playerHuds.keySet()) {
            if (bossRef.equals(key.bossRef)) {
                removedHudKeys.add(key);
                pendingHudRemovals.add(key);
            }
        }
    }

    public void hideAllBossBars() {
        for (HudKey key : playerHuds.keySet()) {
            removedHudKeys.add(key);
            pendingHudRemovals.add(key);
        }
    }

    public void forceClearAllBossBars() {
        activeBosses.clear();
        pendingBossRemovals.clear();
        pendingHudRemovals.clear();
        // Force clear does IMMEDIATE removal (for shutdown) - tick() won't run again
        for (Map.Entry<HudKey, HyUIHud> entry : playerHuds.entrySet()) {
            removedHudKeys.add(entry.getKey());
            try {
                entry.getValue().remove();
            } catch (Exception ignored) {
            }
        }
        playerHuds.clear();
        removedHudKeys.clear();
        plugin.getLogger().atFine().log("[GenericBoss] Force cleared all boss bars and state");
    }

    // =========================================================================
    // Config lookup
    // =========================================================================

    public BossEncounterConfig getConfigForBossType(BossType bossType) {
        return switch (bossType) {
            case DRAGON_FROST -> frostDragonConfig;
            case HEDERA -> hederaConfig;
            case ALPHA_REX -> alphaRexConfig;
            case SWAMP_CROCODILE -> swampCrocodileConfig;
            case BRAMBLE_ELITE -> brambleEliteConfig;
            default -> null;
        };
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public GenericBossState getBossState(Ref<EntityStore> bossRef) {
        return activeBosses.get(bossRef);
    }

    public Map<Ref<EntityStore>, GenericBossState> getActiveBosses() {
        return Collections.unmodifiableMap(activeBosses);
    }

    public EndgameQoL getPlugin() {
        return plugin;
    }
}
