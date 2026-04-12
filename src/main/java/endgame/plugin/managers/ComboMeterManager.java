package endgame.plugin.managers;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.ui.ComboMeterHud;

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

    // Tier names for chat announcements
    private static final String[] TIER_NAMES = {"x2", "x3", "x4", "FRENZY"};

    public static class ComboState {
        final UUID playerUuid;
        // All mutable fields guarded by synchronized(this)
        int comboCount = 0;
        long lastKillTime = 0;
        int comboTier = 0; // 0=none, 1=x2, 2=x3, 3=x4, 4=FRENZY
        volatile ComboMeterHud hud = null;
        volatile boolean hudActive = false;
        int personalBest = 0;
        boolean newRecord = false;

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
                if (!state.hudActive || newTier != oldTier) {
                    shouldRebuildHud = true;
                }
            }
        }

        if (newTier != oldTier && newTier > 0) {
            ComboTierCallback cb = comboTierCallback;
            if (cb != null) {
                cb.onComboTier(playerUuid, newTier);
            }
            plugin.getGameEventBus().publish(new endgame.plugin.events.domain.GameEvent.ComboTierChangeEvent(
                    playerUuid, oldTier, newTier, state.comboCount));
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
     * Called periodically (200ms interval).
     */
    public void tick() {
        if (playerStates.isEmpty()) return;

        EndgameConfig config = plugin.getConfig().get();
        long now = System.currentTimeMillis();

        var iterator = playerStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ComboState> entry = iterator.next();
            ComboState state = entry.getValue();

            // Decide what needs to happen inside the lock, then execute HUD rebuilds outside it
            boolean needsRebuild = false;
            synchronized (state) {
                long timerMs = getEffectiveTimerMs(state, config);

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
                            needsRebuild = true;
                            LOGGER.atFine().log("[Combo] Player %s decayed to tier %d", entry.getKey(), newTier);
                        } else {
                            endCombo(state, iterator);
                        }
                    } else if (!config.isComboDecayEnabled()) {
                        endCombo(state, iterator);
                    }
                }
            }

            // HUD rebuild outside the state lock (rebuildHud may internally lock again
            // to re-acquire state — calling it outside avoids re-entrant locking fragility)
            if (needsRebuild) {
                rebuildHud(state);
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

    private long getEffectiveTimerMs(ComboState state, EndgameConfig config) {
        return (long) (config.getComboTimerSeconds() * 1000);
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

    // === Native CustomUIHud ===

    private void rebuildHud(ComboState state) {
        hideHud(state);
        showHud(state);
    }

    private void showHud(ComboState state) {
        synchronized (state) {
            if (state.hudActive) return;
        }
        try {
            PlayerRef playerRef = findPlayerRef(state.playerUuid);
            if (playerRef == null) return;

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
            if (player == null) return;

            ComboMeterHud hud = new ComboMeterHud(playerRef);
            player.getHudManager().setCustomHud(playerRef, hud);
            hud.setTier(state.comboCount, state.comboTier, state.personalBest,
                    state.newRecord, plugin.getConfig().get().isComboTierEffectsEnabled());

            synchronized (state) {
                state.hud = hud;
                state.hudActive = true;
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[Combo] Failed to show HUD: %s", e.getMessage());
        }
    }

    /**
     * Called periodically to update the timer bar animation.
     * Should be invoked from a tick system (~100-200ms).
     */
    public void refreshHuds() {
        EndgameConfig config = plugin.getConfig().get();
        long now = System.currentTimeMillis();

        for (ComboState state : playerStates.values()) {
            ComboMeterHud hud;
            int comboCount, comboTier, personalBest;
            boolean newRecord;
            long lastKillTime, timerMs;

            synchronized (state) {
                hud = state.hud;
                if (hud == null || !state.hudActive) continue;
                comboCount = state.comboCount;
                comboTier = state.comboTier;
                personalBest = state.personalBest;
                newRecord = state.newRecord;
                lastKillTime = state.lastKillTime;
                timerMs = getEffectiveTimerMs(state, config);
            }

            float timeRemaining = Math.max(0f, 1f - (float) (now - lastKillTime) / timerMs);
            hud.refresh(comboCount, comboTier, timeRemaining, personalBest,
                    newRecord, config.isComboTierEffectsEnabled());
        }
    }

    private void hideHud(ComboState state) {
        synchronized (state) {
            state.hudActive = false;
            if (state.hud != null) {
                try {
                    PlayerRef pr = findPlayerRef(state.playerUuid);
                    if (pr != null) {
                        Ref<EntityStore> ref = pr.getReference();
                        if (ref != null && ref.isValid()) {
                            Player player = ref.getStore().getComponent(ref, Player.getComponentType());
                            if (player != null) {
                                player.getHudManager().setCustomHud(pr, null);
                            }
                        }
                    }
                } catch (Exception ignored) {}
                state.hud = null;
            }
        }
    }

    private PlayerRef findPlayerRef(UUID playerUuid) {
        return endgame.plugin.utils.PlayerRefCache.getByUuid(playerUuid);
    }
}
