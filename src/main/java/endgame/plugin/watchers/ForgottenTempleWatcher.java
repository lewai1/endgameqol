package endgame.plugin.watchers;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.spawns.VorthakSpawner;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Watches Forgotten Temple instances and spawns Vorthak when players enter.
 * Tracks spawn state per-world to handle multiple simultaneous instances.
 */
public class ForgottenTempleWatcher {

    private final EndgameQoL plugin;
    private final VorthakSpawner vorthakSpawner;
    private final ScheduledExecutorService scheduler;

    // Track which world names have had Vorthak spawned (per-instance tracking)
    private final Set<String> spawnedWorlds = ConcurrentHashMap.newKeySet();

    public ForgottenTempleWatcher(EndgameQoL plugin) {
        this.plugin = plugin;
        this.vorthakSpawner = new VorthakSpawner(plugin);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Start watching for Forgotten Temple instances.
     */
    public void start() {
        plugin.getLogger().atInfo().log("[ForgottenTempleWatcher] Starting watcher...");

        // Check every 5 seconds for players in Forgotten Temple
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Collect all currently active forgotten temple world names
                Set<String> activeTempleWorlds = ConcurrentHashMap.newKeySet();

                // Iterate ALL worlds (snapshot to avoid ConcurrentModificationException)
                for (World world : new ArrayList<>(Universe.get().getWorlds().values())) {
                    if (!world.getName().toLowerCase().contains("instance-forgotten_temple")) {
                        continue;
                    }

                    String worldName = world.getName();
                    activeTempleWorlds.add(worldName);

                    if (world.getPlayerCount() == 0) {
                        // No players in this instance, allow re-spawn next time
                        spawnedWorlds.remove(worldName);
                    } else if (world.isAlive() && plugin.getConfig().get().isVorthakEnabled() && spawnedWorlds.add(worldName)) {
                        // Players present AND not yet spawned in this world
                        // spawnedWorlds.add() returns true if the element was new
                        world.execute(() -> {
                            Store<EntityStore> store = world.getEntityStore().getStore();
                            vorthakSpawner.spawn(store);
                            // Reset Vorthak shop stock so trades are always fresh on entry
                            // (game-time based RefreshInterval may not advance in stopped-time instances)
                            // MUST run on game thread — BarterShopState is not thread-safe
                            plugin.resetVorthakShopStock();
                        });

                        plugin.getLogger().atFine().log(
                                "[ForgottenTempleWatcher] Detected player(s) in %s, spawned Vorthak", worldName);
                    }
                }

                // Clean up tracked worlds that no longer exist (instance was destroyed)
                spawnedWorlds.retainAll(activeTempleWorlds);

            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[ForgottenTempleWatcher] Error: %s", e.getMessage());
            }
        }, 4, 5, TimeUnit.SECONDS);
    }

    /**
     * Stop the watcher.
     */
    public void stop() {
        spawnedWorlds.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        plugin.getLogger().atInfo().log("[ForgottenTempleWatcher] Watcher stopped");
    }

    /**
     * Force reset the spawn state (useful for plugin reload).
     */
    public void resetSpawnState() {
        spawnedWorlds.clear();
    }
}
