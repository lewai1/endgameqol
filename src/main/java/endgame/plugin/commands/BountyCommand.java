package endgame.plugin.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.ui.BountyUI;
import endgame.plugin.utils.CommandRateLimit;
import endgame.plugin.utils.I18n;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * /bounty — Opens the Bounty Board HyUI page showing daily bounties.
 */
public class BountyCommand extends AbstractPlayerCommand {

    private final EndgameQoL plugin;

    public BountyCommand(EndgameQoL plugin) {
        super("bounty", "View your daily bounties");
        this.plugin = plugin;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!EgCommand.hasPermissionDefaultAllow(playerRef, "endgameqol.bounty")) {
            playerRef.sendMessage(Message.raw("[EndgameQoL] You don't have permission to use this command.").color("#ff5555"));
            return;
        }
        if (CommandRateLimit.isRateLimited(playerRef.getUuid())) return;

        if (!plugin.getConfig().get().isBountyEnabled()) {
            playerRef.sendMessage(Message.raw("[Bounty] " + I18n.getForPlayer(playerRef, "commands.bounty.disabled")).color("#ff5555"));
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) return;

        BountyUI.open(plugin, playerRef, store, playerUuid);
    }
}
