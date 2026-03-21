package endgame.plugin.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple per-player command rate limiter.
 * Prevents command spam on large servers (1s cooldown per player).
 * Not applied to admin commands (/egadmin, /egconfig).
 */
public final class CommandRateLimit {

    private static final ConcurrentHashMap<UUID, Long> lastUseTime = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1000;

    private CommandRateLimit() {}

    /**
     * Check if a player is rate-limited. If not, records the current time.
     * @return true if the command should be blocked (player is on cooldown)
     */
    public static boolean isRateLimited(UUID uuid) {
        if (uuid == null) return false;
        long now = System.currentTimeMillis();
        Long last = lastUseTime.get(uuid);
        if (last != null && now - last < COOLDOWN_MS) return true;
        lastUseTime.put(uuid, now);
        return false;
    }

    /**
     * Remove stale entries older than 60s. Called from DangerZoneTickSystem.cleanup().
     */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        lastUseTime.entrySet().removeIf(e -> now - e.getValue() > 60_000);
    }
}
