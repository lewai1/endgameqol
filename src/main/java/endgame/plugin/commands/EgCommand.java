package endgame.plugin.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import endgame.plugin.EndgameQoL;
import endgame.plugin.ui.BestiaryUI;
import endgame.plugin.ui.BountyUI;
import endgame.plugin.ui.GauntletUI;
import endgame.plugin.ui.StatusUI;
import endgame.plugin.utils.CommandRateLimit;
import endgame.plugin.utils.EntityUtils;
import endgame.plugin.utils.I18n;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * /eg — Parent command collection for EndgameQoL.
 *
 * Subcommands:
 *   /eg status      — Diagnostics dashboard (requires endgameqol.admin)
 *   /eg bestiary    — Bestiary & Achievements
 *   /eg achievements — Achievements page
 *   /eg lang        — Language selection
 *   /eg bounty      — Bounty Board
 *   /eg gauntlet    — The Gauntlet leaderboard
 *
 * Running /eg with no subcommand shows help text.
 *
 * Permissions use default-allow: all players can use commands unless the server
 * owner explicitly denies them (e.g. "-endgameqol.bounty" or "-endgameqol.*").
 */
public class EgCommand extends AbstractCommandCollection {

    public EgCommand(EndgameQoL plugin) {
        super("eg", "EndgameQoL commands");
        this.addSubCommand(new StatusSubCommand(plugin));
        this.addSubCommand(new LangSubCommand());
        this.addSubCommand(new BestiarySubCommand(plugin));
        this.addSubCommand(new AchievementsSubCommand(plugin));
        this.addSubCommand(new AchShortcutSubCommand(plugin));
        this.addSubCommand(new BountySubCommand(plugin));
        this.addSubCommand(new GauntletSubCommand(plugin));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    /**
     * Default-allow permission check. Returns true unless the server owner
     * explicitly denies the permission (e.g. "-endgameqol.bounty").
     */
    static boolean hasPermissionDefaultAllow(@Nonnull PlayerRef playerRef, @Nonnull String permission) {
        UUID uuid = playerRef.getUuid();
        if (uuid == null) return true;
        return PermissionsModule.get().hasPermission(uuid, permission, true);
    }

    private static void sendNoPermission(@Nonnull PlayerRef playerRef) {
        playerRef.sendMessage(Message.raw("[EndgameQoL] You don't have permission to use this command.").color("#ff5555"));
    }

    // /eg status — admin only (default-deny via requirePermission)
    private static class StatusSubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        StatusSubCommand(EndgameQoL plugin) {
            super("status", "EndgameQoL diagnostics dashboard");
            this.plugin = plugin;
            requirePermission("endgameqol.admin");
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            StatusUI.open(plugin, playerRef, store);
        }
    }

    // /eg bestiary
    private static class BestiarySubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        BestiarySubCommand(EndgameQoL plugin) {
            super("bestiary", "Open the Bestiary & Achievements");
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasPermissionDefaultAllow(playerRef, "endgameqol.bestiary")) { sendNoPermission(playerRef); return; }
            java.util.UUID uuid = EntityUtils.getUuid(playerRef);
            if (uuid == null) return;
            BestiaryUI.open(plugin, playerRef, store, uuid, "bestiary");
        }
    }

    // /eg achievements
    private static class AchievementsSubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        AchievementsSubCommand(EndgameQoL plugin) {
            super("achievements", "Open the Achievements page");
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasPermissionDefaultAllow(playerRef, "endgameqol.achievements")) { sendNoPermission(playerRef); return; }
            java.util.UUID uuid = EntityUtils.getUuid(playerRef);
            if (uuid == null) return;
            BestiaryUI.open(plugin, playerRef, store, uuid, "achievements");
        }
    }

    // /eg ach (shortcut for /eg achievements)
    private static class AchShortcutSubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        AchShortcutSubCommand(EndgameQoL plugin) {
            super("ach", "Shortcut for /eg achievements");
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasPermissionDefaultAllow(playerRef, "endgameqol.achievements")) { sendNoPermission(playerRef); return; }
            java.util.UUID uuid = EntityUtils.getUuid(playerRef);
            if (uuid == null) return;
            BestiaryUI.open(plugin, playerRef, store, uuid, "achievements");
        }
    }

    // /eg lang <locale|auto>
    private static class LangSubCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> localeArg;

        LangSubCommand() {
            super("lang", "Set your language (e.g. fr-FR, es-ES, auto)");
            this.localeArg = this.withRequiredArg("locale", "Locale code (e.g. fr-FR, es-ES) or 'auto'", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            String input = localeArg.get(context);
            if (input == null || input.isEmpty()) {
                List<String> supported = I18n.getSupportedLocales();
                playerRef.sendMessage(Message.raw("[EndgameQoL] Usage: /eg lang <locale|auto>  Supported: "
                        + String.join(", ", supported)).color("#ffaa00"));
                return;
            }

            if ("auto".equalsIgnoreCase(input)) {
                I18n.setPlayerOverride(playerRef, null);
                I18n.sendUpdateTranslationsPacket(playerRef);
                String resolved = I18n.resolveLocale(playerRef);
                playerRef.sendMessage(Message.raw("[EndgameQoL] Language set to auto-detect (" + resolved + ")").color("#4ade80"));
                return;
            }

            if (!I18n.isSupported(input)) {
                List<String> supported = I18n.getSupportedLocales();
                playerRef.sendMessage(Message.raw("[EndgameQoL] Unknown locale '" + input
                        + "'. Supported: " + String.join(", ", supported) + ", auto").color("#ff5555"));
                return;
            }

            I18n.setPlayerOverride(playerRef, input);
            I18n.sendUpdateTranslationsPacket(playerRef);
            playerRef.sendMessage(Message.raw("[EndgameQoL] Language set to " + input).color("#4ade80"));
        }
    }

    // /eg bounty
    private static class BountySubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        BountySubCommand(EndgameQoL plugin) {
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
            if (!hasPermissionDefaultAllow(playerRef, "endgameqol.bounty")) { sendNoPermission(playerRef); return; }
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

    // /eg gauntlet
    private static class GauntletSubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        GauntletSubCommand(EndgameQoL plugin) {
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
            if (!hasPermissionDefaultAllow(playerRef, "endgameqol.gauntlet")) { sendNoPermission(playerRef); return; }
            if (CommandRateLimit.isRateLimited(playerRef.getUuid())) return;

            if (plugin.getGauntletManager() == null) {
                playerRef.sendMessage(Message.raw("[The Gauntlet] " + I18n.getForPlayer(playerRef, "commands.gauntlet.unavailable")).color("#ff5555"));
                return;
            }

            GauntletUI.open(plugin, playerRef, store);
        }
    }
}
