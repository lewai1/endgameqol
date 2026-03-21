package endgame.plugin.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import endgame.plugin.EndgameQoL;
import endgame.plugin.ui.ConfigUI;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;

public class EgConfigCommand extends AbstractPlayerCommand {
    private final EndgameQoL plugin;

    public EgConfigCommand(EndgameQoL plugin) {
        super("egconfig", "Opens the EndgameQoL configuration UI");
        this.plugin = plugin;
        requirePermission("endgameqol.config");
        addAliases("egcfg");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        ConfigUI.open(plugin, playerRef, store);
    }
}
