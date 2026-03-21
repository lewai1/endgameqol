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
import endgame.plugin.ui.GauntletUI;
import endgame.plugin.utils.CommandRateLimit;
import endgame.plugin.utils.I18n;

import javax.annotation.Nonnull;

/**
 * /gauntlet — opens The Gauntlet leaderboard HyUI page.
 */
public class GauntletCommand extends AbstractPlayerCommand {

    private final EndgameQoL plugin;

    public GauntletCommand(EndgameQoL plugin) {
        super("gauntlet", "The Gauntlet leaderboard");
        this.plugin = plugin;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        if (!EgCommand.hasPermissionDefaultAllow(playerRef, "endgameqol.gauntlet")) {
            playerRef.sendMessage(Message.raw("[EndgameQoL] You don't have permission to use this command.").color("#ff5555"));
            return;
        }
        if (CommandRateLimit.isRateLimited(playerRef.getUuid())) return;

        if (plugin.getGauntletManager() == null) {
            playerRef.sendMessage(Message.raw("[The Gauntlet] " + I18n.getForPlayer(playerRef, "commands.gauntlet.unavailable")).color("#ff5555"));
            return;
        }

        GauntletUI.open(plugin, playerRef, store);
    }
}
