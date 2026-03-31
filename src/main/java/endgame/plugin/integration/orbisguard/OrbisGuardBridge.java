package endgame.plugin.integration.orbisguard;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.orbisguard.api.BlockVector3;
import com.orbisguard.api.OrbisGuardAPI;
import com.orbisguard.api.flags.IFlag;
import com.orbisguard.api.flags.IFlagRegistry;
import com.orbisguard.api.region.IRegionManager;
import endgame.plugin.EndgameQoL;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bridge to OrbisGuard mod API.
 * Watches for endgame instance worlds and applies configured protection flags.
 * This is the ONLY class that imports com.orbisguard — complete isolation.
 */
public class OrbisGuardBridge {

    private static final String REGION_PREFIX = "endgameqol_";
    private static final List<String> INSTANCE_PATTERNS = List.of(
            "frozen_dungeon", "swamp_dungeon", "void_realm");

    private static final BlockVector3 MIN = BlockVector3.at(-10000, -500, -10000);
    private static final BlockVector3 MAX = BlockVector3.at(10000, 500, 10000);

    private final EndgameQoL plugin;
    private volatile OrbisGuardAPI api;
    private final ScheduledExecutorService scheduler;
    private final Set<String> protectedWorlds = ConcurrentHashMap.newKeySet();

    public OrbisGuardBridge(EndgameQoL plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Initialize the bridge. Returns true if OrbisGuard API is available.
     */
    public boolean init() {
        api = OrbisGuardAPI.getInstance();
        return api != null;
    }

    /**
     * Start watching for endgame instance worlds.
     */
    public void start() {
        plugin.getLogger().atInfo().log("[OrbisGuard] Starting instance protection watcher...");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (api == null) return;

                Set<String> activeInstanceWorlds = ConcurrentHashMap.newKeySet();

                for (World world : new ArrayList<>(Universe.get().getWorlds().values())) {
                    String worldName = world.getName().toLowerCase();

                    boolean isEndgameInstance = false;
                    for (String pattern : INSTANCE_PATTERNS) {
                        if (worldName.contains(pattern)) {
                            isEndgameInstance = true;
                            break;
                        }
                    }
                    if (!isEndgameInstance) continue;

                    String actualName = world.getName();
                    activeInstanceWorlds.add(actualName);

                    if (protectedWorlds.add(actualName)) {
                        applyProtection(actualName);
                    }
                }

                // Clean up tracked worlds that no longer exist
                protectedWorlds.retainAll(activeInstanceWorlds);

            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[OrbisGuard] Watcher error: %s", e.getMessage());
            }
        }, 6, 5, TimeUnit.SECONDS);
    }

    private void applyProtection(String worldName) {
        try {
            IRegionManager manager = api.getRegionContainer().getRegionManager(worldName);
            if (manager == null) {
                plugin.getLogger().atWarning().log("[OrbisGuard] Could not get region manager for %s", worldName);
                return;
            }

            String regionId = REGION_PREFIX + worldName.replace(" ", "_").toLowerCase();

            if (manager.hasRegion(regionId)) {
                plugin.getLogger().atFine().log("[OrbisGuard] Region %s already exists in %s", regionId, worldName);
                return;
            }

            manager.createRegion(regionId, MIN, MAX);
            manager.setPriority(regionId, 100);

            IFlagRegistry flags = api.getFlagRegistry();
            var config = plugin.getConfig().get();

            // Block building — set BUILD flag to "deny"
            if (config.isOgBlockBuild()) {
                IFlag<?> buildFlag = flags.getFlag("build");
                if (buildFlag != null) {
                    manager.setFlagFromString(regionId, buildFlag, "deny");
                }
            }

            // Block PvP — set PVP flag to "deny"
            if (config.isOgBlockPvp()) {
                IFlag<?> pvpFlag = flags.getFlag("pvp");
                if (pvpFlag != null) {
                    manager.setFlagFromString(regionId, pvpFlag, "deny");
                }
            }

            // Allow chest/door/light access — OrbisGuard denies these by default for non-members
            for (String accessFlag : new String[]{"chest-access", "door-access", "light-access"}) {
                IFlag<?> flag = flags.getFlag(accessFlag);
                if (flag != null) {
                    manager.setFlagFromString(regionId, flag, "allow");
                }
            }

            // Blocked commands — set BLOCKED_CMDS flag
            String blockedCmds = config.getOgBlockedCommands();
            if (blockedCmds != null && !blockedCmds.isBlank()) {
                IFlag<?> blockedCmdsFlag = flags.getFlag("blocked-cmds");
                if (blockedCmdsFlag != null) {
                    manager.setFlagFromString(regionId, blockedCmdsFlag, blockedCmds.trim());
                }
            }

            manager.save();

            plugin.getLogger().atInfo().log("[OrbisGuard] Protected instance %s (region: %s, build:%b, pvp:%b, cmds:%s)",
                    worldName, regionId, !config.isOgBlockBuild(), !config.isOgBlockPvp(),
                    blockedCmds != null ? blockedCmds : "none");

        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[OrbisGuard] Failed to protect %s: %s", worldName, e.getMessage());
        }
    }

    public boolean isActive() {
        return api != null;
    }

    public void shutdown() {
        protectedWorlds.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        api = null;
        plugin.getLogger().atInfo().log("[OrbisGuard] Bridge shut down");
    }
}
