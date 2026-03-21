package endgame.plugin.managers;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.LabelBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;

import com.hypixel.hytale.server.core.Message;
import endgame.plugin.utils.I18n;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player combo meter: tracks consecutive kills within a time window,
 * applies damage boost tiers, and shows a HyUI HUD.
 *
 * Thread safety:
 * - ConcurrentHashMap for per-player state lookup
 * - All mutations on ComboState are synchronized on the state instance
 * - HUD onRefresh never calls remove() — defers via pendingHudRemoval
 */
public class ComboMeterManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Combo");

    // Combo tier thresholds (kill counts)
    private static final int TIER_X2_KILLS = 3;
    private static final int TIER_X3_KILLS = 6;
    private static final int TIER_X4_KILLS = 10;
    private static final int TIER_FRENZY_KILLS = 15;

    // HUD constants
    private static final int HUD_WIDTH = 180;
    private static final int TIMER_BAR_WIDTH = 160;
    private static final long HUD_REFRESH_MS = 100;

    // Tier colors (text, glow bg, bar fill, bar dim)
    private static final String[] TIER_COLORS = {"#55ff55", "#ffff55", "#ff8833", "#ff3333"};
    private static final String[] TIER_NAMES = {"x2", "x3", "x4", "FRENZY"};
    private static final String[] TIER_BAR_COLORS = {"#44cc44", "#cccc44", "#cc7733", "#cc3333"};
    private static final String[] TIER_BG_COLORS = {"#0a1a0a", "#1a1a0a", "#1a100a", "#1a0808"};

    // Tier effect names — only shown for effects that actually work
    private static final String[] TIER_EFFECT_NAMES = {"", "Adrenaline", "Precision", "Bloodlust"};

    public static class ComboState {
        final UUID playerUuid;
        // All mutable fields guarded by synchronized(this)
        int comboCount = 0;
        long lastKillTime = 0;
        int comboTier = 0; // 0=none, 1=x2, 2=x3, 3=x4, 4=FRENZY
        volatile HyUIHud hud = null;
        volatile boolean hudRemoved = false;
        volatile boolean pendingHudRemoval = false;
        int personalBest = 0;
        boolean newRecord = false;
        float comboTimerBonusSeconds = 0;

        ComboState(UUID playerUuid) {
            this.playerUuid = playerUuid;
        }
    }

    private final EndgameQoL plugin;
    private final ConcurrentHashMap<UUID, ComboState> playerStates = new ConcurrentHashMap<>();

    // Callback for bounty integration
    private volatile ComboTierCallback comboTierCallback;

    @FunctionalInterface
    public interface ComboTierCallback {
        void onComboTier(UUID playerUuid, int tier);
    }

    public ComboMeterManager(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player's ECS component is ready. Loads personal best.
     * Always pre-creates the ComboState so that onPlayerKill never starts with PB=0.
     */
    public void onPlayerConnect(UUID playerUuid, endgame.plugin.components.PlayerEndgameComponent comp) {
        ComboState state = playerStates.computeIfAbsent(playerUuid, ComboState::new);
        synchronized (state) {
            // Use Math.max to avoid overwriting a higher in-memory PB
            // (can happen if onComponentSet re-fires during archetype migration)
            state.personalBest = Math.max(state.personalBest, comp.getComboPersonalBest());
        }
    }

    public void setComboTierCallback(ComboTierCallback callback) {
        this.comboTierCallback = callback;
    }

    /**
     * Called when a player kills an NPC. Increments combo and updates tier.
     */
    public void onPlayerKill(UUID playerUuid) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isComboEnabled()) return;

        long now = System.currentTimeMillis();

        ComboState state = playerStates.computeIfAbsent(playerUuid, ComboState::new);

        int oldTier;
        int newTier;
        boolean shouldRebuildHud = false;

        synchronized (state) {
            long timerMs = getEffectiveTimerMs(state, config);

            // Check if combo expired
            if (state.comboCount > 0 && (now - state.lastKillTime) > timerMs) {
                state.comboCount = 0;
                state.comboTier = 0;
            }

            state.comboCount++;
            state.lastKillTime = now;

            // Personal best tracking
            if (state.comboCount > state.personalBest) {
                state.personalBest = state.comboCount;
                state.newRecord = true;
            }

            oldTier = state.comboTier;
            state.comboTier = calculateTier(state.comboCount);
            newTier = state.comboTier;

            // Show/rebuild HUD
            if (state.comboCount >= TIER_X2_KILLS) {
                if (state.hud == null || state.hudRemoved || newTier != oldTier) {
                    shouldRebuildHud = true;
                }
            }
        }

        if (newTier != oldTier && newTier > 0) {
            ComboTierCallback cb = comboTierCallback;
            if (cb != null) {
                cb.onComboTier(playerUuid, newTier);
            }
        }

        if (shouldRebuildHud) {
            // Must run on the player's world thread — kill events can fire on any world thread
            PlayerRef pr = findPlayerRef(state.playerUuid);
            if (pr != null) {
                Ref<EntityStore> ref = pr.getReference();
                if (ref != null && ref.isValid()) {
                    Player p = ref.getStore().getComponent(ref, Player.getComponentType());
                    if (p != null) {
                        World w = p.getWorld();
                        if (w != null && w.isAlive()) {
                            w.execute(() -> rebuildHud(state));
                        }
                    }
                }
            }
        }

        LOGGER.atFine().log("[Combo] Player %s kill #%d, tier=%d",
                playerUuid, state.comboCount, newTier);
    }

    /**
     * Get the damage multiplier for a player based on their combo tier.
     */
    public float getDamageMultiplier(UUID playerUuid) {
        if (playerUuid == null) return 1.0f;

        EndgameConfig config = plugin.getConfig().get();
        if (!config.isComboEnabled()) return 1.0f;

        ComboState state = playerStates.get(playerUuid);
        if (state == null) return 1.0f;

        synchronized (state) {
            if (state.comboTier == 0) return 1.0f;
            if (isExpired(state, config)) return 1.0f;

            return switch (state.comboTier) {
                case 1 -> config.getComboDamageX2();
                case 2 -> config.getComboDamageX3();
                case 3 -> config.getComboDamageX4();
                case 4 -> config.getComboDamageFrenzy();
                default -> 1.0f;
            };
        }
    }

    /**
     * Tick to check for expired combos and manage HUD lifecycle.
     * Called periodically from GauntletTickSystem (200ms interval).
     */
    public void tick() {
        if (playerStates.isEmpty()) return;

        EndgameConfig config = plugin.getConfig().get();
        long now = System.currentTimeMillis();

        var iterator = playerStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ComboState> entry = iterator.next();
            ComboState state = entry.getValue();

            synchronized (state) {
                // Process deferred HUD removal
                if (state.pendingHudRemoval) {
                    state.pendingHudRemoval = false;
                    state.hudRemoved = true;
                    if (state.hud != null) {
                        try { state.hud.remove(); } catch (Exception | NoClassDefFoundError ignored) {}
                        state.hud = null;
                    }
                }

                long timerMs = getEffectiveTimerMs(state, config);

                // Check if combo expired
                if (state.comboCount > 0 && (now - state.lastKillTime) > timerMs) {
                    if (config.isComboDecayEnabled() && state.comboTier > 0) {
                        // Graceful decay — drop one tier
                        int newTier = state.comboTier - 1;
                        int newCount = switch (newTier) {
                            case 3 -> TIER_X4_KILLS;
                            case 2 -> TIER_X3_KILLS;
                            case 1 -> TIER_X2_KILLS;
                            default -> 0;
                        };
                        state.comboTier = newTier;
                        state.comboCount = newCount;
                        state.lastKillTime = now;

                        if (newTier > 0) {
                            rebuildHud(state);
                            LOGGER.atFine().log("[Combo] Player %s decayed to tier %d", entry.getKey(), newTier);
                        } else {
                            endCombo(state, iterator);
                        }
                    } else if (!config.isComboDecayEnabled()) {
                        endCombo(state, iterator);
                    }
                }
            }
        }
    }

    /**
     * End a combo: announce personal best, hide HUD, remove state.
     * Must be called while holding synchronized(state).
     */
    private void endCombo(ComboState state, java.util.Iterator<Map.Entry<UUID, ComboState>> iterator) {
        if (state.newRecord && state.personalBest >= TIER_X2_KILLS) {
            announcePersonalBest(state);
            state.newRecord = false;
        }
        state.comboCount = 0;
        state.comboTier = 0;
        state.comboTimerBonusSeconds = 0;
        hideHud(state);
        iterator.remove();
    }

    /**
     * Called when a player dies. Resets their combo immediately.
     */
    public void onPlayerDeath(UUID playerUuid) {
        ComboState state = playerStates.remove(playerUuid);
        if (state != null) {
            synchronized (state) {
                // Save personal best back to ECS component before clearing
                endgame.plugin.components.PlayerEndgameComponent comp =
                        EndgameQoL.getInstance().getPlayerComponent(playerUuid);
                if (comp != null && state.personalBest > comp.getComboPersonalBest()) {
                    comp.setComboPersonalBest(state.personalBest);
                }
                hideHud(state);
            }
        }
    }

    /**
     * Called when a player leaves a world (e.g., instance transfer).
     * Only hides the HUD — does NOT destroy the combo state, so the combo
     * survives world transfers. Use onPlayerDisconnect() for full cleanup.
     */
    public void onPlayerLeaveWorld(UUID playerUuid) {
        ComboState state = playerStates.get(playerUuid);
        if (state != null) {
            synchronized (state) {
                hideHud(state);
            }
        }
    }

    /**
     * Clean up a specific player (disconnect only).
     * Writes personal best back to the ECS component before eviction.
     */
    public void onPlayerDisconnect(UUID playerUuid) {
        ComboState state = playerStates.remove(playerUuid);
        if (state != null) {
            synchronized (state) {
                // Save personal best back to ECS component
                endgame.plugin.components.PlayerEndgameComponent comp =
                        EndgameQoL.getInstance().getPlayerComponent(playerUuid);
                if (comp != null && state.personalBest > comp.getComboPersonalBest()) {
                    comp.setComboPersonalBest(state.personalBest);
                }
                hideHud(state);
            }
        }
    }

    /**
     * Shutdown cleanup. Writes personal best back to ECS components before clearing.
     */
    public void forceClear() {
        for (var entry : playerStates.entrySet()) {
            ComboState state = entry.getValue();
            synchronized (state) {
                // Save personal best back to ECS component before shutdown
                endgame.plugin.components.PlayerEndgameComponent comp =
                        EndgameQoL.getInstance().getPlayerComponent(entry.getKey());
                if (comp != null && state.personalBest > comp.getComboPersonalBest()) {
                    comp.setComboPersonalBest(state.personalBest);
                }
                hideHud(state);
            }
        }
        playerStates.clear();
    }

    // === Tier Effect Queries ===

    /**
     * Get the crit chance for a player based on combo tier (Precision at x4+).
     * Returns chance as 0.0-1.0.
     */
    public float getCritChance(UUID playerUuid) {
        if (playerUuid == null) return 0f;
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isComboEnabled() || !config.isComboTierEffectsEnabled()) return 0f;

        ComboState state = playerStates.get(playerUuid);
        if (state == null) return 0f;
        synchronized (state) {
            if (state.comboTier < 3) return 0f;
            if (isExpired(state, config)) return 0f;
            return 0.20f;
        }
    }

    /**
     * Get the lifesteal percent for Frenzy tier (Bloodlust).
     */
    public float getLifestealPercent(UUID playerUuid) {
        if (playerUuid == null) return 0f;
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isComboEnabled() || !config.isComboTierEffectsEnabled()) return 0f;

        ComboState state = playerStates.get(playerUuid);
        if (state == null) return 0f;
        synchronized (state) {
            if (state.comboTier < 4) return 0f;
            if (isExpired(state, config)) return 0f;
            return 0.03f;
        }
    }

    /**
     * Check if player should receive heal-on-kill (Adrenaline at x3+).
     * Returns heal amount as fraction of max HP, or 0.
     */
    public float getHealOnKillPercent(UUID playerUuid) {
        if (playerUuid == null) return 0f;
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isComboEnabled() || !config.isComboTierEffectsEnabled()) return 0f;

        ComboState state = playerStates.get(playerUuid);
        if (state == null) return 0f;
        synchronized (state) {
            if (state.comboTier < 2) return 0f;
            if (isExpired(state, config)) return 0f;
            return 0.02f;
        }
    }

    /**
     * Get the current combo tier for a player (0-4).
     */
    public int getComboTier(UUID playerUuid) {
        if (playerUuid == null) return 0;
        ComboState state = playerStates.get(playerUuid);
        if (state == null) return 0;
        synchronized (state) {
            return state.comboTier;
        }
    }

    /**
     * Get the personal best kill streak for a player.
     */
    public int getPersonalBest(UUID playerUuid) {
        if (playerUuid == null) return 0;
        ComboState state = playerStates.get(playerUuid);
        if (state == null) return 0;
        synchronized (state) {
            return state.personalBest;
        }
    }

    /**
     * Add per-player combo timer bonus (e.g., from Gauntlet COMBO_SURGE buff).
     * Stacks additively with each call.
     */
    public void addComboTimerBonus(UUID playerUuid, float bonusSeconds) {
        ComboState state = playerStates.computeIfAbsent(playerUuid, ComboState::new);
        synchronized (state) {
            state.comboTimerBonusSeconds += bonusSeconds;
        }
    }

    /**
     * Reset per-player combo timer bonus (called when gauntlet ends).
     */
    public void resetComboTimerBonus(UUID playerUuid) {
        ComboState state = playerStates.get(playerUuid);
        if (state != null) {
            synchronized (state) {
                state.comboTimerBonusSeconds = 0;
            }
        }
    }

    private long getEffectiveTimerMs(ComboState state, EndgameConfig config) {
        // Must be called while holding synchronized(state) or from a safe context
        float bonus = state.comboTimerBonusSeconds;
        return (long) ((config.getComboTimerSeconds() + bonus) * 1000);
    }

    private boolean isExpired(ComboState state, EndgameConfig config) {
        return System.currentTimeMillis() - state.lastKillTime > getEffectiveTimerMs(state, config);
    }

    // === Personal Best Announcement ===

    private void announcePersonalBest(ComboState state) {
        try {
            PlayerRef playerRef = findPlayerRef(state.playerUuid);
            if (playerRef == null) return;
            playerRef.sendMessage(Message.raw(
                    "[Combo] " + I18n.getForPlayer(playerRef, "combo.new_record", state.personalBest))
                    .color("#FFD700"));
        } catch (Exception ignored) {}
    }

    // === Tier calculation ===

    private int calculateTier(int killCount) {
        if (killCount >= TIER_FRENZY_KILLS) return 4;
        if (killCount >= TIER_X4_KILLS) return 3;
        if (killCount >= TIER_X3_KILLS) return 2;
        if (killCount >= TIER_X2_KILLS) return 1;
        return 0;
    }

    // === HyUI HUD ===

    private void rebuildHud(ComboState state) {
        hideHud(state);
        showHud(state);
    }

    private void showHud(ComboState state) {
        // Guard against double HUD creation (race between onPlayerKill and tick)
        synchronized (state) {
            if (state.hud != null && !state.hudRemoved) {
                return;
            }
        }
        try {
            PlayerRef playerRef = findPlayerRef(state.playerUuid);
            if (playerRef == null) return;

            int tierIdx = Math.max(0, state.comboTier - 1);
            String color = TIER_COLORS[tierIdx];
            String barColor = TIER_BAR_COLORS[tierIdx];

            String html = buildHudHtml(state, color, barColor);
            HyUIHud newHud = HudBuilder.hudForPlayer(playerRef)
                    .fromHtml(html)
                    .withRefreshRate(HUD_REFRESH_MS)
                    .onRefresh(h -> {
                        if (state.hudRemoved) return;
                        ComboState current = playerStates.get(state.playerUuid);
                        if (current == null || current.hud != h) {
                            state.pendingHudRemoval = true;
                            return;
                        }
                        doRefreshHud(current, h);
                    })
                    .show();
            synchronized (state) {
                state.hudRemoved = false;
                state.pendingHudRemoval = false;
                state.hud = newHud;
            }
        } catch (NoClassDefFoundError e) {
            LOGGER.atFine().log("[Combo] HyUI not available");
        } catch (Exception e) {
            LOGGER.atWarning().log("[Combo] Failed to show HUD: %s", e.getMessage());
        }
    }

    private void doRefreshHud(ComboState state, HyUIHud hud) {
        try {
            EndgameConfig config = plugin.getConfig().get();

            int comboCount;
            int comboTier;
            long lastKillTime;
            int personalBest;
            long timerMs;

            synchronized (state) {
                comboCount = state.comboCount;
                comboTier = state.comboTier;
                lastKillTime = state.lastKillTime;
                personalBest = state.personalBest;
                timerMs = getEffectiveTimerMs(state, config);
            }
            long now = System.currentTimeMillis();
            long elapsed = now - lastKillTime;

            float timeRemaining = Math.max(0f, 1f - (float) elapsed / timerMs);
            int barWidth = Math.round(TIMER_BAR_WIDTH * timeRemaining);

            String countText = String.valueOf(comboCount);
            boolean isFrenzy = comboTier == 4;
            String tierText;
            if (comboTier > 0) {
                tierText = isFrenzy ? "!! FRENZY !!" : TIER_NAMES[comboTier - 1];
            } else {
                tierText = "";
            }

            // Tier effect label
            String effectText = "";
            if (comboTier > 0 && config.isComboTierEffectsEnabled()) {
                effectText = TIER_EFFECT_NAMES[comboTier - 1];
            }

            // Personal best label
            String bestText = personalBest > 0 ? "BEST: " + personalBest : "";

            final String fEffectText = effectText;
            final String fBestText = bestText;

            try { hud.getById("combo-count", LabelBuilder.class)
                    .ifPresent(l -> l.withText(countText)); } catch (Exception ignored) {}
            try { hud.getById("combo-tier", LabelBuilder.class)
                    .ifPresent(l -> l.withText(tierText)); } catch (Exception ignored) {}
            try { hud.getById("combo-effect", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fEffectText)); } catch (Exception ignored) {}
            try { hud.getById("combo-best", LabelBuilder.class)
                    .ifPresent(l -> l.withText(fBestText)); } catch (Exception ignored) {}
            try { hud.getById("timer-bar-fill", GroupBuilder.class)
                    .ifPresent(b -> b.withAnchor(new HyUIAnchor()
                            .setWidth(barWidth).setHeight(4).setLeft(0).setTop(0))); } catch (Exception ignored) {}
        } catch (Exception e) {
            // Non-fatal
        }
    }

    private void hideHud(ComboState state) {
        state.hudRemoved = true;
        state.pendingHudRemoval = false;
        if (state.hud != null) {
            try { state.hud.remove(); } catch (Exception | NoClassDefFoundError ignored) {}
            state.hud = null;
        }
    }

    private String buildHudHtml(ComboState state, String color, String barColor) {
        int tierIdx = Math.max(0, state.comboTier - 1);
        String tierName = TIER_NAMES[tierIdx];
        String bgColor = TIER_BG_COLORS[tierIdx];
        boolean isFrenzy = state.comboTier == 4;
        EndgameConfig config = plugin.getConfig().get();
        String effectName = (config.isComboTierEffectsEnabled() && state.comboTier > 0)
                ? TIER_EFFECT_NAMES[state.comboTier - 1] : "";
        String bestText = state.personalBest > 0 ? "BEST: " + state.personalBest : "";

        return String.format("""
            <style>
                #combo-container {
                    anchor-right: 16;
                    anchor-top: 160;
                    anchor-width: %d;
                    anchor-height: 116;
                    padding: 8;
                    layout-mode: top;
                    background-color: %s;
                }
                #combo-top-row {
                    layout-mode: left;
                    anchor-height: 36;
                    anchor-width: 100%%;
                    vertical-align: center;
                    horizontal-align: center;
                }
                #combo-count {
                    font-size: 28;
                    font-weight: bold;
                    color: %s;
                    anchor-height: 36;
                    vertical-align: center;
                    text-align: center;
                    anchor-width: 70;
                }
                #combo-label {
                    font-size: 11;
                    color: #888888;
                    anchor-height: 36;
                    vertical-align: center;
                    padding-left: 6;
                    anchor-width: 50;
                }
                #combo-tier {
                    font-size: %d;
                    font-weight: bold;
                    color: %s;
                    text-align: center;
                    anchor-height: 22;
                    anchor-width: 100%%;
                }
                #combo-effect {
                    font-size: 10;
                    color: #aaaaaa;
                    text-align: center;
                    anchor-height: 14;
                    anchor-width: 100%%;
                }
                #timer-bar-bg {
                    anchor-width: %d;
                    anchor-height: 4;
                    background-color: #111118;
                    margin-top: 4;
                    horizontal-align: center;
                }
                #timer-bar-fill {
                    anchor-width: %d;
                    anchor-height: 4;
                    background-color: %s;
                    anchor-left: 0;
                    anchor-top: 0;
                }
                #combo-best {
                    font-size: 9;
                    color: #666666;
                    text-align: center;
                    anchor-height: 12;
                    anchor-width: 100%%;
                    margin-top: 2;
                }
            </style>
            <div id="combo-container">
                <div id="combo-top-row">
                    <p id="combo-count">%d</p>
                    <p id="combo-label">KILLS</p>
                </div>
                <p id="combo-tier">%s</p>
                <p id="combo-effect">%s</p>
                <div id="timer-bar-bg">
                    <div id="timer-bar-fill"></div>
                </div>
                <p id="combo-best">%s</p>
            </div>
            """,
            HUD_WIDTH,
            bgColor,
            color,
            isFrenzy ? 18 : 14,
            color,
            TIMER_BAR_WIDTH, TIMER_BAR_WIDTH, barColor,
            state.comboCount,
            isFrenzy ? "!! FRENZY !!" : tierName,
            effectName,
            bestText
        );
    }

    private PlayerRef findPlayerRef(UUID playerUuid) {
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef != null && playerUuid.equals(endgame.plugin.utils.EntityUtils.getUuid(pRef))) {
                return pRef;
            }
        }
        return null;
    }
}
