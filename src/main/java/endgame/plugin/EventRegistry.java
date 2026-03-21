package endgame.plugin;

import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;

import endgame.plugin.utils.I18n;

import java.util.UUID;

/**
 * Manages event handler registration and event handler methods.
 * Extracted from EndgameQoL to reduce main class size.
 */
public class EventRegistry {

    private final EndgameQoL plugin;
    private final SystemRegistry systemRegistry;
    private final DatabaseInitializer databaseInitializer;

    public EventRegistry(EndgameQoL plugin, SystemRegistry systemRegistry, DatabaseInitializer databaseInitializer) {
        this.plugin = plugin;
        this.systemRegistry = systemRegistry;
        this.databaseInitializer = databaseInitializer;
    }

    /**
     * Register all global event handlers. Called from setup().
     */
    public void register() {
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class,
                (event) -> endgame.plugin.events.PlayerEventHandler.onPlayerReady(event, plugin));
        endgame.plugin.events.RecipeOverrideSystem recipeSystem = new endgame.plugin.events.RecipeOverrideSystem(plugin);
        plugin.setRecipeOverrideSystem(recipeSystem);
        plugin.getEntityStoreRegistry().registerSystem(recipeSystem);
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.BlockProtectionSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.BlockProtectionSystem.DamageProtection(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.BlockProtectionSystem.PlaceProtection(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.events.CraftTracker(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.block.PrismaHatchetAreaBreakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.block.PrismaPickaxeAreaBreakSystem());
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        plugin.getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, this::onPlayerLeaveWorld);
        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerEnterWorld);
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.ui.snake.ArcadeMachineUseSystem());
    }

    /** Shutdown subsystems. Called from plugin shutdown. */
    public void shutdown() {
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        try {
            if (event.getPlayerRef() == null) return;
            // Thread-safe: PlayerRef.getUuid() reads a final field, no ECS store access needed
            UUID playerUuid = event.getPlayerRef().getUuid();
            if (playerUuid == null) return;
            // All downstream methods only touch ConcurrentHashMaps — thread-safe from event thread
            if (systemRegistry.getGolemVoidBossManager() != null) {
                systemRegistry.getGolemVoidBossManager().hideBossBarForPlayerUuid(playerUuid);
            }
            if (systemRegistry.getGenericBossManager() != null) {
                systemRegistry.getGenericBossManager().hideBossBarForPlayerUuid(playerUuid);
            }
            if (systemRegistry.getDangerZoneTickSystem() != null) {
                systemRegistry.getDangerZoneTickSystem().clearPlayerState(playerUuid);
            }
            if (systemRegistry.getDaggerVanishSystem() != null) {
                systemRegistry.getDaggerVanishSystem().onPlayerDisconnect(playerUuid);
            }
            if (systemRegistry.getPrismaManaCostSystem() != null) {
                systemRegistry.getPrismaManaCostSystem().onPlayerDisconnect(playerUuid);
            }
            if (systemRegistry.getPrismaMirageSystem() != null) {
                systemRegistry.getPrismaMirageSystem().onPlayerDisconnect(playerUuid);
            }
            if (systemRegistry.getWardenTrialManager() != null) {
                systemRegistry.getWardenTrialManager().failTrial(playerUuid);
            }
            if (systemRegistry.getComboMeterManager() != null) {
                systemRegistry.getComboMeterManager().onPlayerDisconnect(playerUuid);
            }
            if (systemRegistry.getGauntletManager() != null) {
                systemRegistry.getGauntletManager().failGauntlet(playerUuid);
            }
            if (systemRegistry.getBountyManager() != null) {
                systemRegistry.getBountyManager().onPlayerDisconnect(playerUuid);
            }
            if (systemRegistry.getBlinkTrailDamageSystem() != null) {
                systemRegistry.getBlinkTrailDamageSystem().onPlayerDisconnect(playerUuid);
            }
            if (systemRegistry.getAccessoryPassiveSystem() != null) {
                systemRegistry.getAccessoryPassiveSystem().onPlayerDisconnect(playerUuid);
            }

            // Achievement + Bestiary: evict from manager cache on disconnect
            if (plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().onPlayerDisconnect(playerUuid);
            }

            // Clean area break mode tracking
            endgame.plugin.systems.block.PrismaPickaxeAreaBreakSystem.clearPlayer(playerUuid);

            // Clean i18n locale cache
            I18n.onPlayerDisconnect(playerUuid);

            // Database sync: save player data on disconnect (before uncaching component)
            if (databaseInitializer.getDatabaseSyncService() != null
                    && databaseInitializer.getDatabaseSyncService().isEnabled()) {
                databaseInitializer.syncPlayerToDatabase(playerUuid.toString(), event.getPlayerRef().getUsername());
            }

            // Uncache the ECS component reference (Hytale auto-persists the data)
            plugin.onPlayerComponentRemoved(playerUuid);
        } catch (Exception e) {
            plugin.getLogger().atWarning().withCause(e).log("[EndgameQoL] Error in onPlayerDisconnect");
        }
    }

    private void onPlayerLeaveWorld(DrainPlayerFromWorldEvent event) {
        try {
            if (event.getHolder() == null) return;

            // Thread-safe: find PlayerRef by matching holder (field access only, no ECS)
            // then use UUID-based cleanup (ConcurrentHashMap operations only)
            UUID playerUuid = null;
            for (PlayerRef pRef : Universe.get().getPlayers()) {
                if (pRef != null && java.util.Objects.equals(pRef.getHolder(), event.getHolder())) {
                    playerUuid = pRef.getUuid(); // final field, thread-safe
                    break;
                }
            }

            if (playerUuid != null) {
                // All UUID-based methods only touch ConcurrentHashMaps — thread-safe
                if (systemRegistry.getGolemVoidBossManager() != null) {
                    systemRegistry.getGolemVoidBossManager().hideBossBarForPlayerUuid(playerUuid);
                }
                if (systemRegistry.getGenericBossManager() != null) {
                    systemRegistry.getGenericBossManager().hideBossBarForPlayerUuid(playerUuid);
                }
                if (systemRegistry.getDangerZoneTickSystem() != null) {
                    systemRegistry.getDangerZoneTickSystem().clearPlayerState(playerUuid);
                }
                if (systemRegistry.getWardenTrialManager() != null) {
                    systemRegistry.getWardenTrialManager().failTrial(playerUuid);
                }
                if (systemRegistry.getGauntletManager() != null) {
                    systemRegistry.getGauntletManager().failGauntlet(playerUuid);
                }
                if (systemRegistry.getComboMeterManager() != null) {
                    // Only hide HUD — don't destroy combo state (survives world transfers)
                    systemRegistry.getComboMeterManager().onPlayerLeaveWorld(playerUuid);
                }
            }

            String worldName = event.getWorld() != null ? event.getWorld().getName() : "";
            plugin.getLogger().atFine().log("[EndgameQoL] Player left world: %s — boss bar + state cleanup done", worldName);
        } catch (Exception e) {
            plugin.getLogger().atWarning().withCause(e).log("[EndgameQoL] Error in onPlayerLeaveWorld (non-fatal)");
        }
    }

    private void onPlayerEnterWorld(AddPlayerToWorldEvent event) {
        try {
            if (event.getHolder() == null) return;

            // Thread-safe: find PlayerRef by matching holder (field access only, no ECS)
            UUID playerUuid = null;
            for (PlayerRef pRef : Universe.get().getPlayers()) {
                if (pRef != null && java.util.Objects.equals(pRef.getHolder(), event.getHolder())) {
                    playerUuid = pRef.getUuid(); // final field, thread-safe
                    break;
                }
            }

            // ALWAYS clear boss bars on world entry — handles cross-instance transitions
            // (e.g., Golem Void → Frost Dragon) and non-boss worlds alike.
            // UUID-based methods only touch ConcurrentHashMaps — thread-safe from event thread.
            if (playerUuid != null) {
                if (systemRegistry.getGolemVoidBossManager() != null) {
                    systemRegistry.getGolemVoidBossManager().hideBossBarForPlayerUuid(playerUuid);
                }
                if (systemRegistry.getGenericBossManager() != null) {
                    systemRegistry.getGenericBossManager().hideBossBarForPlayerUuid(playerUuid);
                }
            }

            // WorldConfig mutation and shop reset MUST run on the world thread
            World world = event.getWorld();
            if (world != null) {
                boolean isEndgame = EndgameQoL.isEndgameInstance(world);
                boolean isFrozen = EndgameQoL.isFrozenDungeonInstance(world);
                boolean isSwamp = EndgameQoL.isSwampDungeonInstance(world);
                if ((isEndgame || isFrozen || isSwamp) && world.isAlive()) {
                    world.execute(() -> {
                        if (isEndgame) {
                            boolean pvpEnabled = plugin.getConfig().get().isPvpEnabled();
                            WorldConfig wc = world.getWorldConfig();
                            if (wc.isPvpEnabled() != pvpEnabled) {
                                wc.setPvpEnabled(pvpEnabled);
                                wc.markChanged();
                            }
                        }
                        if (isFrozen) {
                            plugin.resetKorvynShopStock();
                        }
                        if (isSwamp) {
                            plugin.resetMorghulShopStock();
                        }
                    });
                }
            }

            String worldName = event.getWorld() != null ? event.getWorld().getName() : "";
            plugin.getLogger().atFine().log("[EndgameQoL] Player entering world: %s — boss bar cleanup done", worldName);
        } catch (Exception e) {
            plugin.getLogger().atWarning().withCause(e).log("[EndgameQoL] Error in onPlayerEnterWorld (non-fatal)");
        }
    }
}
