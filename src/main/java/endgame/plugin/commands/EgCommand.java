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
import endgame.plugin.ui.NativeJournalPage;
import endgame.plugin.ui.NativeStatusPage;
import endgame.plugin.ui.nativeconfig.NativeConfigPage;
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
 *   /eg config       — Config UI (alias: /egconfig)
 *   /eg admin ...    — Admin commands (alias: /egadmin)
 *   /eg status       — Diagnostics dashboard
 *   /eg bestiary     — Bestiary & Achievements
 *   /eg achievements — Achievements page
 *   /eg lang         — Language selection
 *   /eg bounty       — Bounty Board
 */
public class EgCommand extends AbstractCommandCollection {

    public EgCommand(EndgameQoL plugin) {
        super("eg", "EndgameQoL commands");
        // /eg config — opens config UI (also available as /egconfig)
        this.addSubCommand(new ConfigSubCommand(plugin));
        // /eg admin — admin commands (also available as /egadmin)
        this.addSubCommand(new EgAdminCommand(plugin));
        this.addSubCommand(new StatusSubCommand(plugin));
        this.addSubCommand(new LangSubCommand());
        this.addSubCommand(new JournalSubCommand(plugin));
        this.addSubCommand(new PetSubCommand(plugin));
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

    // /eg config — opens config UI (default-allow, deny with "-endgameqol.config")
    private static class ConfigSubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        ConfigSubCommand(EndgameQoL plugin) {
            super("config", "Open the EndgameQoL configuration UI");
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasPermissionDefaultAllow(playerRef, "endgameqol.config")) { sendNoPermission(playerRef); return; }
            NativeConfigPage.open(playerRef, store, plugin);
        }
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
            NativeStatusPage.open(plugin, playerRef, store);
        }
    }

    // /eg journal — unified page with 3 tabs (Bounty, Bestiary, Achievements)
    private static class JournalSubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        JournalSubCommand(EndgameQoL plugin) {
            super("journal", "Open the Journal (Bounty, Bestiary, Achievements)");
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            java.util.UUID uuid = EntityUtils.getUuid(playerRef);
            if (uuid == null) return;
            NativeJournalPage.open(plugin, playerRef, store, uuid, "Bounty");
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


    // /eg pet
    private static class PetSubCommand extends AbstractPlayerCommand {
        private final EndgameQoL plugin;

        PetSubCommand(EndgameQoL plugin) {
            super("pet", "Open the Pets page");
            this.plugin = plugin;
        }

        @Override
        protected boolean canGeneratePermission() { return false; }

        @Override
        protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            if (!hasPermissionDefaultAllow(playerRef, "endgameqol.pet")) { sendNoPermission(playerRef); return; }
            if (CommandRateLimit.isRateLimited(playerRef.getUuid())) return;

            if (!plugin.getConfig().get().pets().isEnabled()) {
                playerRef.sendMessage(Message.raw("[EndgameQoL] Pet system is disabled.").color("#ff5555"));
                return;
            }

            UUID uuid = endgame.plugin.utils.EntityUtils.getUuid(playerRef);
            if (uuid == null) return;

            endgame.plugin.ui.NativePetPage.open(plugin, playerRef, store, uuid);
        }
    }
}
