package endgame.plugin.rifts;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandUtil;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RiftCommand extends AbstractCommandCollection {

    public RiftCommand() {
        super("rift", "Dungeon rift portal commands");
        this.addSubCommand(new SpawnSubCommand());
        this.addSubCommand(new ForceSubCommand());
        this.addSubCommand(new StatusSubCommand());
        this.addSubCommand(new CloseSubCommand());
        this.addSubCommand(new ReloadSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    // /rift spawn [dungeon] — spawn portal at player location
    private static class SpawnSubCommand extends AbstractPlayerCommand {
        private static final String PERMISSION = HytalePermissions.fromCommand("rift.spawn");
        private final OptionalArg<String> dungeonArg;

        SpawnSubCommand() {
            super("spawn", "Spawn a rift portal at your location");
            this.requirePermission(PERMISSION);
            this.dungeonArg = this.withOptionalArg("dungeon",
                    "Dungeon type (frozen_dungeon, swamp_dungeon, golem_void)", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() { return false; }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef,
                               @Nonnull World world) {
            CommandUtil.requirePermission(context.sender(), PERMISSION);
            endgame.plugin.EndgameQoL plugin = endgame.plugin.EndgameQoL.getInstance();
            if (plugin == null) {
                playerRef.sendMessage(Message.raw("HyRifts is not loaded.").color("#ff6666"));
                return;
            }
            var transform = playerRef.getTransform();
            if (transform == null) {
                playerRef.sendMessage(Message.raw("Could not get your position.").color("#ff6666"));
                return;
            }
            Vector3d position = transform.getPosition();
            plugin.getRiftManager().forceSpawnAt(world, position, dungeonArg.get(context));
            playerRef.sendMessage(Message.raw(String.format(
                    "Spawning portal at %.0f, %.0f, %.0f... use /rift status to check",
                    position.getX(), position.getY(), position.getZ())).color("#ffaa00"));
        }
    }

    // /rift force [dungeon] — spawn portal at random valid location
    private static class ForceSubCommand extends AbstractCommand {
        private static final String PERMISSION = HytalePermissions.fromCommand("rift.force");
        private final OptionalArg<String> dungeonArg;

        ForceSubCommand() {
            super("force", "Force-spawn a rift portal at a random location");
            this.requirePermission(PERMISSION);
            this.dungeonArg = this.withOptionalArg("dungeon",
                    "Dungeon type (frozen_dungeon, swamp_dungeon, golem_void)", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() { return false; }

        @Nullable
        @Override
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            CommandUtil.requirePermission(context.sender(), PERMISSION);
            endgame.plugin.EndgameQoL plugin = endgame.plugin.EndgameQoL.getInstance();
            if (plugin == null) {
                context.sender().sendMessage(Message.raw("HyRifts is not loaded.").color("#ff6666"));
                return null;
            }
            plugin.getRiftManager().forceSpawn(dungeonArg.get(context));
            context.sender().sendMessage(
                    Message.raw("Force-spawning portal... use /rift status to check").color("#ffaa00"));
            return null;
        }
    }

    // /rift status — show active portals
    private static class StatusSubCommand extends AbstractCommand {
        private static final String PERMISSION = HytalePermissions.fromCommand("rift.status");

        StatusSubCommand() {
            super("status", "Show active rift portals");
            this.requirePermission(PERMISSION);
        }

        @Override
        protected boolean canGeneratePermission() { return false; }

        @Nullable
        @Override
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            CommandUtil.requirePermission(context.sender(), PERMISSION);
            endgame.plugin.EndgameQoL plugin = endgame.plugin.EndgameQoL.getInstance();
            if (plugin == null) {
                context.sender().sendMessage(Message.raw("HyRifts is not loaded.").color("#ff6666"));
                return null;
            }
            Map<String, ActiveRift> portals = plugin.getRiftManager().getActivePortals();
            if (portals.isEmpty()) {
                context.sender().sendMessage(Message.raw("No active portals.").color("#888888"));
                return null;
            }
            context.sender().sendMessage(
                    Message.raw(String.format("Active portals (%d):", portals.size())).color("#4fd7f7"));
            for (ActiveRift portal : portals.values()) {
                long remainingSec = portal.remainingMs() / 1000;
                String timeStr = portal.lifetimeSeconds() <= 0 ? "permanent"
                        : String.format("%dm %ds", remainingSec / 60, remainingSec % 60);
                context.sender().sendMessage(Message.raw(String.format(
                        "  [%s] %s %s at %.0f, %.0f, %.0f - Lv.%d - %s",
                        portal.portalId(), portal.rank().label, portal.dungeonType().displayName,
                        portal.position().getX(), portal.position().getY(), portal.position().getZ(),
                        portal.mobLevel(), timeStr
                )).color(portal.rank().color));
            }
            return null;
        }
    }

    // /rift close <id|all>
    private static class CloseSubCommand extends AbstractCommand {
        private static final String PERMISSION = HytalePermissions.fromCommand("rift.close");
        private final RequiredArg<String> portalIdArg;

        CloseSubCommand() {
            super("close", "Close an active rift portal by ID");
            this.requirePermission(PERMISSION);
            this.portalIdArg = this.withRequiredArg("portalId",
                    "Portal ID (from /rift status) or 'all'", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() { return false; }

        @Nullable
        @Override
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            CommandUtil.requirePermission(context.sender(), PERMISSION);
            endgame.plugin.EndgameQoL plugin = endgame.plugin.EndgameQoL.getInstance();
            if (plugin == null) {
                context.sender().sendMessage(Message.raw("HyRifts is not loaded.").color("#ff6666"));
                return null;
            }
            String portalId = this.portalIdArg.get(context);
            RiftManager manager = plugin.getRiftManager();
            if ("all".equalsIgnoreCase(portalId)) {
                int count = manager.activeCount();
                manager.closeAllPortals();
                context.sender().sendMessage(
                        Message.raw(String.format("Closed %d portal(s).", count)).color("#6cff78"));
            } else {
                if (manager.getActivePortals().containsKey(portalId)) {
                    manager.closePortal(portalId);
                    context.sender().sendMessage(
                            Message.raw("Portal " + portalId + " closed.").color("#6cff78"));
                } else {
                    context.sender().sendMessage(
                            Message.raw("No portal found with ID: " + portalId).color("#ff6666"));
                }
            }
            return null;
        }
    }

    // /rift reload
    private static class ReloadSubCommand extends AbstractCommand {
        private static final String PERMISSION = HytalePermissions.fromCommand("rift.reload");

        ReloadSubCommand() {
            super("reload", "Reload HyRifts configuration");
            this.requirePermission(PERMISSION);
        }

        @Override
        protected boolean canGeneratePermission() { return false; }

        @Nullable
        @Override
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            CommandUtil.requirePermission(context.sender(), PERMISSION);
            endgame.plugin.EndgameQoL plugin = endgame.plugin.EndgameQoL.getInstance();
            if (plugin != null) {
                plugin.reloadRifts();
                context.sender().sendMessage(
                        Message.raw("HyRifts configuration reloaded.").color("#6cff78"));
            } else {
                context.sender().sendMessage(
                        Message.raw("HyRifts is not loaded.").color("#ff6666"));
            }
            return null;
        }
    }
}
