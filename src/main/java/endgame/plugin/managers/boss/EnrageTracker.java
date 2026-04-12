package endgame.plugin.managers.boss;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BossConfig;
import endgame.plugin.utils.BossType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks sustained damage to bosses and triggers enrage when a threshold is exceeded
 * within a sliding time window. While enraged, the boss deals increased damage.
 *
 * Thread-safe: ConcurrentHashMap for state lookup, per-boss synchronized blocks
 * to avoid global lock contention during multi-boss fights.
 */
public class EnrageTracker {

    private final EndgameQoL plugin;
    private final Map<Ref<EntityStore>, BossEnrageState> states = new ConcurrentHashMap<>();

    public EnrageTracker(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Record damage dealt to a boss. If total damage in the sliding window exceeds
     * the threshold, the boss enters enrage.
     */
    public void recordDamage(Ref<EntityStore> ref, String bossType, float amount, long now) {
        BossConfig config = getBossConfig(bossType);
        if (config == null || !config.isEnrageEnabled()) return;

        BossEnrageState state = states.computeIfAbsent(ref, k -> new BossEnrageState(bossType));

        synchronized (state) {
            // Don't record during active enrage or cooldown
            if (state.enraged || now < state.cooldownEndTime) return;

            // Add damage entry
            state.damageWindow.addLast(new DamageEntry(amount, now));
            state.runningTotal += amount;

            // Expire old entries outside the window
            long windowMs = config.getEnrageWindowMs();
            while (!state.damageWindow.isEmpty() && (now - state.damageWindow.peekFirst().timestamp) > windowMs) {
                state.runningTotal -= state.damageWindow.pollFirst().amount;
            }

            if (state.runningTotal >= config.getEnrageDamageThreshold()) {
                state.enraged = true;
                state.enrageStartTime = now;
                plugin.getLogger().atFine().log("[EnrageTracker] %s ENRAGED (%.0f damage in %dms window, threshold: %.0f)",
                        bossType, state.runningTotal, windowMs, config.getEnrageDamageThreshold());
                state.damageWindow.clear();
                state.runningTotal = 0f;
            }
        }
    }

    /**
     * Check if a boss is currently enraged.
     */
    public boolean isEnraged(Ref<EntityStore> ref) {
        BossEnrageState state = states.get(ref);
        if (state == null) return false;
        synchronized (state) {
            return state.enraged;
        }
    }

    /**
     * Get the enrage damage multiplier for a boss. Returns 1.0 if not enraged.
     */
    public float getEnrageMultiplier(Ref<EntityStore> ref) {
        BossEnrageState state = states.get(ref);
        if (state == null) return 1.0f;

        synchronized (state) {
            if (!state.enraged) return 1.0f;

            BossConfig config = getBossConfig(state.bossType);
            if (config == null) return 1.0f;

            return config.getEnrageDamageMultiplier();
        }
    }

    /**
     * Tick all tracked bosses — expire enrage durations and start cooldowns.
     * Called from DangerZoneTickSystem at the boss tick rate (200ms).
     */
    public void tick(long now) {
        // ConcurrentHashMap iteration is weakly-consistent safe — concurrent
        // recordDamage()/removeBoss() mutations are tolerated without copy.
        for (Map.Entry<Ref<EntityStore>, BossEnrageState> entry : states.entrySet()) {
            BossEnrageState state = entry.getValue();
            BossConfig config = getBossConfig(state.bossType);
            if (config == null) continue;

            synchronized (state) {
                if (state.enraged) {
                    long elapsed = now - state.enrageStartTime;
                    if (elapsed >= config.getEnrageDurationMs()) {
                        state.enraged = false;
                        state.cooldownEndTime = now + config.getEnrageCooldownMs();
                        state.damageWindow.clear();
                        state.runningTotal = 0f;
                        plugin.getLogger().atFine().log("[EnrageTracker] %s enrage ended (lasted %dms, cooldown %dms)",
                                state.bossType, elapsed, config.getEnrageCooldownMs());
                    }
                }

                // Expire old damage entries even when not enraged
                if (!state.enraged && !state.damageWindow.isEmpty()) {
                    long windowMs = config.getEnrageWindowMs();
                    while (!state.damageWindow.isEmpty() && (now - state.damageWindow.peekFirst().timestamp) > windowMs) {
                        state.runningTotal -= state.damageWindow.pollFirst().amount;
                    }
                }
            }
        }
    }

    /**
     * Remove tracking for a specific boss (on death).
     */
    public void removeBoss(Ref<EntityStore> ref) {
        BossEnrageState removed = states.remove(ref);
        if (removed != null) {
            plugin.getLogger().atFine().log("[EnrageTracker] Removed tracking for %s", removed.bossType);
        }
    }

    /**
     * Clear all enrage state (shutdown).
     */
    public void clear() {
        states.clear();
    }

    private BossConfig getBossConfig(String bossTypeId) {
        BossType type = BossType.fromTypeId(bossTypeId);
        if (type == null) return null;
        return plugin.getConfig().get().getBossConfig(type);
    }

    // --- Inner classes ---

    private static class BossEnrageState {
        final String bossType;
        final Deque<DamageEntry> damageWindow = new ArrayDeque<>();
        float runningTotal; // Running sum of damage in the window
        boolean enraged;
        long enrageStartTime;
        long cooldownEndTime;

        BossEnrageState(String bossType) {
            this.bossType = bossType;
        }
    }

    private record DamageEntry(float amount, long timestamp) {}
}
