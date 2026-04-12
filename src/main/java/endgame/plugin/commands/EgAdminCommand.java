package endgame.plugin.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.managers.boss.GolemVoidBossManager;
import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /egadmin — Admin-only command collection for server diagnostics and management.
 *
 * Subcommands:
 *   /egadmin debug boss <type>           — Dump active boss state
 *   /egadmin reset bounties <player|all> — Force refresh bounties
 *   /egadmin reload                      — Hot-reload config from disk
 */
public class EgAdminCommand extends AbstractCommandCollection {

    public EgAdminCommand(EndgameQoL plugin) {
        super("admin", "EndgameQoL admin commands");
        requirePermission("endgameqol.admin");

        // /egadmin debug ...
        AbstractCommandCollection debugCollection = new AbstractCommandCollection("debug", "Debug subcommands") {};
        debugCollection.addSubCommand(new DebugBossCommand(plugin));
        this.addSubCommand(debugCollection);

        // /egadmin reset ...
        AbstractCommandCollection resetCollection = new AbstractCommandCollection("reset", "Reset subcommands") {};
        resetCollection.addSubCommand(new ResetBountiesCommand(plugin));
        this.addSubCommand(resetCollection);

        // /egadmin reload
        this.addSubCommand(new ReloadConfigCommand(plugin));

        // /egadmin portal
        this.addSubCommand(new SpawnPortalCommand(plugin));

    }

    // =========================================================================
    // /egadmin debug boss <type>
    // =========================================================================
    private static class DebugBossCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;
        private final RequiredArg<String> bossTypeArg;

        DebugBossCommand(EndgameQoL plugin) {
            super("boss", "Dump active boss state");
            this.plugin = plugin;
            this.bossTypeArg = this.withRequiredArg("type", "Boss type (golem, hedera, dragon_frost, dragon_fire, alpha_rex)", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String bossType = bossTypeArg.get(context).toLowerCase();

            if ("golem".equals(bossType)) {
                GolemVoidBossManager golemMgr = plugin.getGolemVoidBossManager();
                if (golemMgr == null) {
                    sendNoBoss(playerRef, "Golem Void");
                    return;
                }
                Map<Ref<EntityStore>, GolemVoidBossManager.GolemVoidState> activeBosses = golemMgr.getActiveBosses();
                if (activeBosses.isEmpty()) {
                    sendNoBoss(playerRef, "Golem Void");
                    return;
                }

                for (GolemVoidBossManager.GolemVoidState state : activeBosses.values()) {
                    int hpPct = Math.round(state.lastHealthPercent * 100);
                    playerRef.sendMessage(Message.join(
                            Message.raw("[EgAdmin] ").color("#bb44ff"),
                            Message.raw("=== GOLEM VOID ===").color("#FFD700")
                    ));
                    playerRef.sendMessage(Message.join(
                            Message.raw("  Phase: ").color("#cccccc"),
                            Message.raw(String.valueOf(state.currentPhase)).color("#ffffff")
                    ));
                    playerRef.sendMessage(Message.join(
                            Message.raw("  HP: ").color("#cccccc"),
                            Message.raw(hpPct + "%").color(hpPct > 50 ? "#4ade80" : hpPct > 25 ? "#ffaa00" : "#ff4444")
                    ));
                    playerRef.sendMessage(Message.join(
                            Message.raw("  Invulnerable: ").color("#cccccc"),
                            Message.raw(state.isInvulnerable ? "YES" : "No").color(state.isInvulnerable ? "#ff4444" : "#4ade80")
                    ));
                    playerRef.sendMessage(Message.join(
                            Message.raw("  Ref valid: ").color("#cccccc"),
                            Message.raw(state.bossRef.isValid() ? "Yes" : "INVALID").color(state.bossRef.isValid() ? "#4ade80" : "#ff4444")
                    ));
                }
                return;
            }

            // Check GenericBossManager for other types
            GenericBossManager genericMgr = plugin.getGenericBossManager();
            if (genericMgr == null) {
                sendNoBoss(playerRef, bossType);
                return;
            }

            Map<Ref<EntityStore>, GenericBossManager.GenericBossState> activeBosses = genericMgr.getActiveBosses();
            boolean found = false;

            for (GenericBossManager.GenericBossState state : activeBosses.values()) {
                String npcId = state.npcTypeId.toLowerCase();
                boolean matches = switch (bossType) {
                    case "hedera" -> npcId.contains("endgame_hedera");
                    case "dragon_frost" -> npcId.contains("endgame_dragon_frost");
                    case "dragon_fire" -> npcId.contains("dragon_fire") || npcId.contains("fire_dragon");
                    case "alpha_rex" -> npcId.contains("alpha_rex");
                    default -> false;
                };

                if (matches) {
                    found = true;
                    int hpPct = Math.round(state.lastHealthPercent * 100);
                    boolean isElite = state.config.elite;

                    playerRef.sendMessage(Message.join(
                            Message.raw("[EgAdmin] ").color("#bb44ff"),
                            Message.raw("=== " + state.config.displayName + " ===").color("#FFD700")
                    ));
                    playerRef.sendMessage(Message.join(
                            Message.raw("  NPC ID: ").color("#cccccc"),
                            Message.raw(state.npcTypeId).color("#ffffff")
                    ));
                    if (!isElite) {
                        playerRef.sendMessage(Message.join(
                                Message.raw("  Phase: ").color("#cccccc"),
                                Message.raw(state.currentPhase + "/" + state.config.phaseNames.length).color("#ffffff")
                        ));
                    } else {
                        playerRef.sendMessage(Message.join(
                                Message.raw("  Type: ").color("#cccccc"),
                                Message.raw("Elite").color("#ff8800")
                        ));
                    }
                    playerRef.sendMessage(Message.join(
                            Message.raw("  HP: ").color("#cccccc"),
                            Message.raw(hpPct + "%").color(hpPct > 50 ? "#4ade80" : hpPct > 25 ? "#ffaa00" : "#ff4444")
                    ));
                    playerRef.sendMessage(Message.join(
                            Message.raw("  Invulnerable: ").color("#cccccc"),
                            Message.raw(state.isInvulnerable ? "YES" : "No").color(state.isInvulnerable ? "#ff4444" : "#4ade80")
                    ));
                    playerRef.sendMessage(Message.join(
                            Message.raw("  Ref valid: ").color("#cccccc"),
                            Message.raw(state.bossRef.isValid() ? "Yes" : "INVALID").color(state.bossRef.isValid() ? "#4ade80" : "#ff4444")
                    ));
                }
            }

            if (!found) {
                sendNoBoss(playerRef, bossType);
            }
        }

        private void sendNoBoss(PlayerRef playerRef, String bossType) {
            playerRef.sendMessage(Message.join(
                    Message.raw("[EgAdmin] ").color("#bb44ff"),
                    Message.raw("No active boss of type '" + bossType + "'.").color("#ffaa00")
            ));
        }
    }

    // =========================================================================
    // /egadmin portal <dungeonId>
    // =========================================================================
    private static class SpawnPortalCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;
        private final RequiredArg<String> typeArg;

        SpawnPortalCommand(EndgameQoL plugin) {
            super("portal", "Force-spawn a temporal portal near you");
            this.plugin = plugin;
            this.typeArg = this.withRequiredArg("type", "dungeon id", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            var portalMgr = plugin.getTemporalPortalManager();
            if (portalMgr == null) {
                playerRef.sendMessage(Message.join(
                        Message.raw("[EgAdmin] ").color("#bb44ff"),
                        Message.raw("Temporal Portal system is not initialized.").color("#ff4444")
                ));
                return;
            }

            String type = typeArg.get(context).toLowerCase();
            var config = plugin.getConfig().get().getTemporalPortalConfig();
            endgame.plugin.systems.portal.DungeonDefinition dungeonDef = config.getDungeonById(type);

            if (dungeonDef == null) {
                // List available dungeon IDs
                String available = String.join(", ", config.getDungeons().keySet());
                playerRef.sendMessage(Message.join(
                        Message.raw("[EgAdmin] ").color("#bb44ff"),
                        Message.raw("Unknown dungeon. Available: " + available).color("#ff4444")
                ));
                return;
            }

            portalMgr.forceSpawnNear(playerRef, dungeonDef);
            playerRef.sendMessage(Message.join(
                    Message.raw("[EgAdmin] ").color("#bb44ff"),
                    Message.raw("Spawning " + dungeonDef.getDisplayName() + " portal near you...").color("#4ade80")
            ));
        }
    }

    // =========================================================================
    // /egadmin reset bounties <player|all>
    // =========================================================================
    private static class ResetBountiesCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;
        private final RequiredArg<String> targetArg;

        ResetBountiesCommand(EndgameQoL plugin) {
            super("bounties", "Force refresh bounties for a player or all");
            this.plugin = plugin;
            this.targetArg = this.withRequiredArg("target", "Player name or 'all'", ArgTypes.STRING);
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            BountyManager bountyMgr = plugin.getBountyManager();
            if (bountyMgr == null) {
                playerRef.sendMessage(Message.join(
                        Message.raw("[EgAdmin] ").color("#bb44ff"),
                        Message.raw("Bounty system is not initialized.").color("#ff4444")
                ));
                return;
            }

            String target = targetArg.get(context);

            if ("all".equalsIgnoreCase(target)) {
                int count = bountyMgr.forceRefreshAll();
                playerRef.sendMessage(Message.join(
                        Message.raw("[EgAdmin] ").color("#bb44ff"),
                        Message.raw("Bounties refreshed for " + count + " cached player(s).").color("#4ade80")
                ));
                return;
            }

            // Find player by name
            PlayerRef targetPlayer = findPlayerByName(target);
            if (targetPlayer == null) {
                playerRef.sendMessage(Message.join(
                        Message.raw("[EgAdmin] ").color("#bb44ff"),
                        Message.raw("Player '" + target + "' not found online.").color("#ff4444")
                ));
                return;
            }

            UUID targetUuid = targetPlayer.getUuid();
            if (targetUuid == null) {
                playerRef.sendMessage(Message.join(
                        Message.raw("[EgAdmin] ").color("#bb44ff"),
                        Message.raw("Could not resolve UUID for player.").color("#ff4444")
                ));
                return;
            }

            bountyMgr.forceRefreshPlayer(targetUuid);
            playerRef.sendMessage(Message.join(
                    Message.raw("[EgAdmin] ").color("#bb44ff"),
                    Message.raw("Bounties refreshed for " + targetPlayer.getUsername() + ".").color("#4ade80")
            ));
        }

        private PlayerRef findPlayerByName(String name) {
            try {
                for (PlayerRef pRef : Universe.get().getPlayers()) {
                    if (pRef != null && name.equalsIgnoreCase(pRef.getUsername())) {
                        return pRef;
                    }
                }
            } catch (Exception e) {
                HytaleLogger.get("EndgameQoL").atWarning().log("[EgAdmin] Error finding player '%s': %s", name, e.getMessage());
            }
            return null;
        }
    }

    // =========================================================================
    // /egadmin reload
    // =========================================================================
    private static class ReloadConfigCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        ReloadConfigCommand(EndgameQoL plugin) {
            super("reload", "Reload EndgameQoL config from disk");
            this.plugin = plugin;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            plugin.getConfig().load().thenAccept(v -> {
                playerRef.sendMessage(Message.join(
                        Message.raw("[EgAdmin] ").color("#bb44ff"),
                        Message.raw("Config reloaded from disk.").color("#4ade80")
                ));
            }).exceptionally(e -> {
                playerRef.sendMessage(Message.join(
                        Message.raw("[EgAdmin] ").color("#bb44ff"),
                        Message.raw("Config reload failed: " + e.getMessage()).color("#ff4444")
                ));
                return null;
            });
        }
    }
}
