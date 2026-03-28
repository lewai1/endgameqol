package endgame.plugin;

import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerBuilder;

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
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.events.MiningTracker(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.block.PrismaHatchetAreaBreakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.block.PrismaPickaxeAreaBreakSystem());
        plugin.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        plugin.getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, this::onPlayerLeaveWorld);
        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, this::onPlayerEnterWorld);
        plugin.getEventRegistry().registerGlobal(AddWorldEvent.class, this::onWorldAdded);
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.ui.snake.ArcadeMachineUseSystem());
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        try {
            if (event.getPlayerRef() == null) return;
            UUID playerUuid = event.getPlayerRef().getUuid();
            if (playerUuid == null) return;

            // Boss bars
            var golemMgr = systemRegistry.getGolemVoidBossManager();
            if (golemMgr != null) golemMgr.hideBossBarForPlayerUuid(playerUuid);
            var genericMgr = systemRegistry.getGenericBossManager();
            if (genericMgr != null) genericMgr.hideBossBarForPlayerUuid(playerUuid);

            // Systems cleanup
            var dangerZone = systemRegistry.getDangerZoneTickSystem();
            if (dangerZone != null) dangerZone.clearPlayerState(playerUuid);
            var daggerVanish = systemRegistry.getDaggerVanishSystem();
            if (daggerVanish != null) daggerVanish.onPlayerDisconnect(playerUuid);
            var manaCost = systemRegistry.getPrismaManaCostSystem();
            if (manaCost != null) manaCost.onPlayerDisconnect(playerUuid);
            var mirage = systemRegistry.getPrismaMirageSystem();
            if (mirage != null) mirage.onPlayerDisconnect(playerUuid);
            var blink = systemRegistry.getBlinkTrailDamageSystem();
            if (blink != null) blink.onPlayerDisconnect(playerUuid);
            var accessory = systemRegistry.getAccessoryPassiveSystem();
            if (accessory != null) accessory.onPlayerDisconnect(playerUuid);

            // Game modes
            var warden = systemRegistry.getWardenTrialManager();
            if (warden != null) warden.failTrial(playerUuid);
            var combo = systemRegistry.getComboMeterManager();
            if (combo != null) combo.onPlayerDisconnect(playerUuid);
            var gauntlet = systemRegistry.getGauntletManager();
            if (gauntlet != null) gauntlet.failGauntlet(playerUuid);
            var bounty = systemRegistry.getBountyManager();
            if (bounty != null) bounty.onPlayerDisconnect(playerUuid);

            // Achievement + Bestiary
            var achievementMgr = plugin.getAchievementManager();
            if (achievementMgr != null) achievementMgr.onPlayerDisconnect(playerUuid);

            // Static state cleanup
            endgame.plugin.systems.block.PrismaPickaxeAreaBreakSystem.clearPlayer(playerUuid);
            endgame.plugin.utils.CommandRateLimit.clearPlayer(playerUuid);
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

            UUID playerUuid = findUuidByHolder(event.getHolder());

            if (playerUuid != null) {
                // All UUID-based methods only touch ConcurrentHashMaps — thread-safe
                var golemBoss = systemRegistry.getGolemVoidBossManager();
                if (golemBoss != null) golemBoss.hideBossBarForPlayerUuid(playerUuid);
                var genericBoss = systemRegistry.getGenericBossManager();
                if (genericBoss != null) genericBoss.hideBossBarForPlayerUuid(playerUuid);
                var dangerZone = systemRegistry.getDangerZoneTickSystem();
                if (dangerZone != null) dangerZone.clearPlayerState(playerUuid);
                var warden = systemRegistry.getWardenTrialManager();
                if (warden != null) warden.failTrial(playerUuid);
                var gauntlet = systemRegistry.getGauntletManager();
                if (gauntlet != null) gauntlet.failGauntlet(playerUuid);
                var combo = systemRegistry.getComboMeterManager();
                // Only hide HUD — don't destroy combo state (survives world transfers)
                if (combo != null) combo.onPlayerLeaveWorld(playerUuid);
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

            UUID playerUuid = findUuidByHolder(event.getHolder());

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

                // Publish dungeon enter event
                if ((isFrozen || isSwamp) && playerUuid != null) {
                    String dungeonType = isFrozen ? "frozen_dungeon" : "swamp_dungeon";
                    plugin.getGameEventBus().publish(
                            new endgame.plugin.events.domain.GameEvent.DungeonEnterEvent(playerUuid, dungeonType));
                }

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

    /**
     * Thread-safe UUID lookup by holder reference (field access only, no ECS).
     */
    private static UUID findUuidByHolder(Object holder) {
        if (holder == null) return null;
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef != null && java.util.Objects.equals(pRef.getHolder(), holder)) {
                return pRef.getUuid();
            }
        }
        return null;
    }

    private void onWorldAdded(AddWorldEvent event) {
        try {
            World world = event.getWorld();
            if (world == null) return;

            // Register map markers for Swamp Dungeon POIs
            if (EndgameQoL.isSwampDungeonInstance(world)) {
                world.getWorldMapManager().addMarkerProvider("endgame_swamp_poi", (w, player, collector) -> {
                    collector.add(new MapMarkerBuilder("swamp_rope", "Rope_Infused.png",
                            new Transform(new Vector3i(-68, 72, 130)))
                            .withName(Message.raw("Infused Rope (4/5)")).build());
                    collector.add(new MapMarkerBuilder("swamp_gem", "Swamp_Gem_Icon.png",
                            new Transform(new Vector3i(72, 79, 104)))
                            .withName(Message.raw("Swamp Gem (Breakable) (3/5)")).build());
                    collector.add(new MapMarkerBuilder("swamp_bramble", "Hedera_Bramble_Icon.png",
                            new Transform(new Vector3i(-59, 86, 2)))
                            .withName(Message.raw("Hedera's Bramble (5/5)")).build());
                    collector.add(new MapMarkerBuilder("swamp_scale", "Swamp_Crocodile_Scale.png",
                            new Transform(new Vector3i(62, 61, 20)))
                            .withName(Message.raw("Crocodile Scale (2/5)")).build());
                    collector.add(new MapMarkerBuilder("swamp_ingot", "Swamp_Ingot_Icon.png",
                            new Transform(new Vector3i(51, 102, 59)))
                            .withName(Message.raw("Trader & Swamp Ingot (1/5)")).build());
                    collector.add(new MapMarkerBuilder("swamp_autel", "Hedera_Autel.png",
                            new Transform(new Vector3i(21, 60, -88)))
                            .withName(Message.raw("Hedera Autel (Craft Hedera Key)")).build());
                });
                plugin.getLogger().atFine().log("[EndgameQoL] Registered Swamp Dungeon map markers");
            }
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[EndgameQoL] Failed to register map markers: %s", e.getMessage());
        }
    }
}
