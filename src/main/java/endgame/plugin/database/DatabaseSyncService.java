package endgame.plugin.database;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nullable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async wrapper around EndgameRepository.
 * When constructed with null repository (database disabled), all methods are no-ops.
 * All DB writes run on a dedicated thread pool, never on the game thread.
 */
public class DatabaseSyncService {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Database");

    private static final int MAX_FAILURES_BEFORE_DEGRADED = 3;

    @Nullable
    private final EndgameRepository repository;
    @Nullable
    private final ScheduledExecutorService executor;

    // Health tracking for graceful degradation
    private volatile boolean healthy = true;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /**
     * @param repository null = database disabled, all methods become no-ops
     */
    public DatabaseSyncService(@Nullable EndgameRepository repository) {
        this.repository = repository;
        if (repository != null) {
            this.executor = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "EndgameQoL-DB-Sync");
                t.setDaemon(true);
                return t;
            });
        } else {
            this.executor = null;
        }
    }

    /**
     * Initialize database (create tables). Call once during plugin setup.
     */
    public void initialize() {
        if (repository == null) return;
        try {
            repository.createTables();
            LOGGER.atInfo().log("[Database] Sync service initialized");
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[Database] Failed to initialize");
        }
    }

    /**
     * Async save player data snapshot.
     */
    public void syncAsync(PlayerDataSnapshot snapshot) {
        if (repository == null || executor == null) return;
        executor.execute(() -> {
            try {
                repository.savePlayerData(snapshot);
                onSuccess();
            } catch (Exception e) {
                onFailure(e, "sync player " + snapshot.uuid());
            }
        });
    }

    /**
     * Async load player data by UUID.
     */
    public void loadAsync(String uuid, java.util.function.Consumer<PlayerDataSnapshot> callback) {
        if (repository == null || executor == null) {
            callback.accept(null);
            return;
        }
        executor.execute(() -> {
            try {
                PlayerDataSnapshot data = repository.loadPlayerData(uuid);
                onSuccess();
                callback.accept(data);
            } catch (Exception e) {
                onFailure(e, "load player " + uuid);
                callback.accept(null);
            }
        });
    }

    /**
     * Async update leaderboard entry.
     */
    public void updateLeaderboardAsync(String uuid, String name, int bestWave) {
        if (repository == null || executor == null) return;
        executor.execute(() -> {
            try {
                repository.upsertLeaderboard(uuid, name, bestWave);
                onSuccess();
            } catch (Exception e) {
                onFailure(e, "update leaderboard for " + uuid);
            }
        });
    }

    /**
     * Check if database sync is enabled and active.
     */
    public boolean isEnabled() {
        return repository != null;
    }

    /**
     * Whether the database connection is healthy (no consecutive failures exceeding threshold).
     */
    public boolean isHealthy() {
        return healthy;
    }

    private void onSuccess() {
        if (!healthy) {
            LOGGER.atInfo().log("[Database] Connection restored — resuming normal operation");
        }
        consecutiveFailures.set(0);
        healthy = true;
    }

    private void onFailure(Exception e, String operation) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures == MAX_FAILURES_BEFORE_DEGRADED) {
            healthy = false;
            LOGGER.atWarning().withCause(e).log(
                    "[Database] DEGRADED — %d consecutive failures (latest: %s). Writes will continue to retry",
                    failures, operation);
        } else {
            LOGGER.atWarning().withCause(e).log("[Database] Failed to %s (failure #%d)",
                    operation, failures);
        }
    }

    /**
     * Gracefully shut down the executor. Call from plugin shutdown().
     */
    public void shutdown() {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.atInfo().log("[Database] Sync service shut down");
    }
}
