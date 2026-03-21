package endgame.plugin.ui.config;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BossConfig;
import endgame.plugin.ui.ConfigUI;
import endgame.plugin.utils.BossType;
import endgame.plugin.utils.HtmlUtil;
import endgame.plugin.utils.I18n;

/**
 * Integration tab for the ConfigUI.
 * Combines RPG Leveling and Endless Leveling into a single tab with shared XP rewards.
 */
public class IntegrationTab extends ConfigTabBuilder {

    public IntegrationTab(EndgameQoL plugin, ConfigSaveManager saveManager) {
        super(plugin, saveManager);
    }

    @Override
    public String buildHtml(String locale) {
        StringBuilder sb = new StringBuilder();

        // ── RPG Leveling section ──
        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.title"))).append("</p>");

        boolean rpgEnabled = config.isRPGLevelingEnabled();
        boolean rpgPresent = plugin.isRPGLevelingModPresent();

        sb.append(String.format("""
                <div class="card" style="background-color: %s;">
                    <div class="status-banner">
                        <label class="status-text" style="color: %s;">%s</label>
                    </div>
                    <div class="toggle-row">
                        <input type="checkbox" id="RPGLevelingToggle" %s/>
                        <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                    </div>
                    <p class="section-hint" style="color: #f5c542;">%s</p>
                </div>
                """,
                getStatusBackgroundColor(rpgEnabled, rpgPresent),
                getStatusColor(rpgEnabled, rpgPresent),
                getStatusText(rpgEnabled, rpgPresent, locale, "rpg"),
                rpgEnabled ? "checked" : "",
                I18n.getFor(locale, "ui.rpg.enable_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.enable_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.restart_hint"))));

        // ── Endless Leveling section ──
        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.el.title"))).append("</p>");

        boolean elEnabled = config.isEndlessLevelingEnabled();
        boolean elPresent = plugin.isEndlessLevelingModPresent();

        sb.append(String.format("""
                <div class="card" style="background-color: %s;">
                    <div class="status-banner">
                        <label class="status-text" style="color: %s;">%s</label>
                    </div>
                    <div class="toggle-row">
                        <input type="checkbox" id="EndlessLevelingToggle" %s/>
                        <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                    </div>
                    <p class="section-hint" style="color: #f5c542;">%s</p>
                </div>
                """,
                getStatusBackgroundColor(elEnabled, elPresent),
                getStatusColor(elEnabled, elPresent),
                getStatusText(elEnabled, elPresent, locale, "el"),
                elEnabled ? "checked" : "",
                I18n.getFor(locale, "ui.el.enable_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.el.enable_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.el.restart_hint"))));

        // ── EL-specific settings (only shown when EL is enabled) ──
        if (elEnabled) {
            sb.append("<div class=\"card\">");
            sb.append(String.format("<p class=\"group-header\" style=\"color: #a78bfa;\">%s</p>",
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.settings"))));

            // Party XP share range
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="%s">%s</label>
                        <input type="number" id="ElXpShareRange" class="combat-input" value="%d"/>
                        <p class="combat-hint">%s</p>
                    </div>
                    """,
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.share_range_tooltip")),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.share_range")),
                    (int) config.getElXpShareRange(),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.share_range_hint"))));

            // Gauntlet XP per milestone
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="%s">%s</label>
                        <input type="number" id="ElGauntletXpBase" class="combat-input" value="%d"/>
                        <p class="combat-hint">%s</p>
                    </div>
                    """,
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.gauntlet_xp_tooltip")),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.gauntlet_xp")),
                    config.getElGauntletXpBase(),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.gauntlet_xp_hint"))));

            // Warden Trial XP per tier
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="%s">%s</label>
                        <input type="number" id="ElWardenXpBase" class="combat-input" value="%d"/>
                        <p class="combat-hint">%s</p>
                    </div>
                    """,
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.warden_xp_tooltip")),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.warden_xp")),
                    config.getElWardenXpBase(),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.warden_xp_hint"))));

            // Achievement XP
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="%s">%s</label>
                        <input type="number" id="ElAchievementXp" class="combat-input" value="%d"/>
                        <p class="combat-hint">%s</p>
                    </div>
                    """,
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.achievement_xp_tooltip")),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.achievement_xp")),
                    config.getElAchievementXp(),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.el.achievement_xp_hint"))));

            sb.append("</div>");
        }

        // ── OrbisGuard section ──
        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.og.title"))).append("</p>");

        boolean ogEnabled = config.isOrbisGuardEnabled();
        boolean ogPresent = plugin.isOrbisGuardModPresent();

        sb.append(String.format("""
                <div class="card" style="background-color: %s;">
                    <div class="status-banner">
                        <label class="status-text" style="color: %s;">%s</label>
                    </div>
                    <div class="toggle-row">
                        <input type="checkbox" id="OrbisGuardToggle" %s/>
                        <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                    </div>
                    <p class="section-hint" style="color: #f5c542;">%s</p>
                </div>
                """,
                getStatusBackgroundColor(ogEnabled, ogPresent),
                getStatusColor(ogEnabled, ogPresent),
                getStatusText(ogEnabled, ogPresent, locale, "og"),
                ogEnabled ? "checked" : "",
                I18n.getFor(locale, "ui.og.enable_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.og.enable_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.og.restart_hint"))));

        // ── OG-specific settings (only shown when OG is enabled) ──
        if (ogEnabled) {
            sb.append("<div class=\"card\">");
            sb.append(String.format("<p class=\"group-header\" style=\"color: #60a5fa;\">%s</p>",
                    HtmlUtil.escape(I18n.getFor(locale, "ui.og.settings"))));

            // Block building
            sb.append(String.format("""
                    <div class="toggle-row">
                        <input type="checkbox" id="OgBlockBuild" %s/>
                        <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                    </div>
                    """,
                    config.isOgBlockBuild() ? "checked" : "",
                    HtmlUtil.escape(I18n.getFor(locale, "ui.og.block_build_tooltip")),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.og.block_build"))));

            // Block PvP
            sb.append(String.format("""
                    <div class="toggle-row">
                        <input type="checkbox" id="OgBlockPvp" %s/>
                        <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                    </div>
                    """,
                    config.isOgBlockPvp() ? "checked" : "",
                    HtmlUtil.escape(I18n.getFor(locale, "ui.og.block_pvp_tooltip")),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.og.block_pvp"))));

            // Blocked commands
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="%s">%s</label>
                        <input type="text" id="OgBlockedCommands" class="combat-input" style="width: 300px;" value="%s"/>
                        <p class="combat-hint">%s</p>
                    </div>
                    """,
                    HtmlUtil.escape(I18n.getFor(locale, "ui.og.blocked_cmds_tooltip")),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.og.blocked_cmds")),
                    HtmlUtil.escape(config.getOgBlockedCommands()),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.og.blocked_cmds_hint"))));

            sb.append("</div>");
        }

        sb.append("<div class=\"divider\"></div>");

        // ── Shared XP Rewards section ──
        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.xp_rewards"))).append("</p>");
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.integration.xp_shared_hint"))).append("</p>");

        // Bosses
        sb.append("<div class=\"card\">");
        sb.append("<p class=\"group-header\" style=\"color: #c084fc;\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.bosses"))).append("</p>");
        for (BossType boss : new BossType[] { BossType.DRAGON_FROST, BossType.HEDERA, BossType.GOLEM_VOID }) {
            BossConfig bc = config.getBossConfig(boss);
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="XP awarded when %s is killed.">%s:</label>
                        <input type="number" id="%s_XpReward" class="combat-input" value="%d"/>
                        <p class="combat-hint">XP (default: %d)</p>
                    </div>
                    """, boss.getDisplayName(), boss.getDisplayName(), boss.name(),
                    bc.getXpReward(), boss.getDefaultXpReward()));
        }
        sb.append("</div>");

        // Elites
        sb.append("<div class=\"card\">");
        sb.append("<p class=\"group-header\" style=\"color: #88aaff;\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.elites"))).append("</p>");
        for (BossType boss : new BossType[] { BossType.ALPHA_REX, BossType.SPECTRE_VOID, BossType.DRAGON_FIRE, BossType.ZOMBIE_ABERRANT, BossType.SWAMP_CROCODILE, BossType.BRAMBLE_ELITE }) {
            BossConfig bc = config.getBossConfig(boss);
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="XP awarded when %s is killed.">%s:</label>
                        <input type="number" id="%s_XpReward" class="combat-input" value="%d"/>
                        <p class="combat-hint">XP (default: %d)</p>
                    </div>
                    """, boss.getDisplayName(), boss.getDisplayName(), boss.name(),
                    bc.getXpReward(), boss.getDefaultXpReward()));
        }
        sb.append("</div>");

        sb.append("<div class=\"divider\"></div>");

        // ── Instance Levels — RPG Leveling ──
        sb.append(String.format("""
                <div class="card">
                    <p class="group-header">%s</p>
                    <p class="section-hint">%s</p>
                    <div class="info-grid">
                        <div class="info-row"><p class="info-name">Frozen Dungeon</p><p class="info-stats">Levels 80-110 | Yeti: Lv100, Rat: Lv85</p></div>
                        <div class="info-row"><p class="info-name">Frost Dragon</p><p class="info-stats">Levels 90-115 | Boss: Lv110</p></div>
                        <div class="info-row"><p class="info-name">Hedera</p><p class="info-stats">Levels 100-135 | Boss: Lv130</p></div>
                        <div class="info-row"><p class="info-name">Golem Void</p><p class="info-stats">Levels 110-155 | Boss: Lv150, Spectre: Lv120</p></div>
                    </div>
                    <p class="section-hint">%s</p>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.instances")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.instances_hint")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.instances_scaling"))));

        // ── Instance Levels — Endless Leveling ──
        sb.append(String.format("""
                <div class="card">
                    <p class="group-header">%s</p>
                    <p class="section-hint">%s</p>
                    <div class="info-grid">
                        <div class="info-row"><p class="info-name">Frozen Dungeon</p><p class="info-stats">Levels 10-25 | Boss: Lv30</p></div>
                        <div class="info-row"><p class="info-name">Swamp Dungeon</p><p class="info-stats">Levels 30-45 | Boss: Lv50</p></div>
                    </div>
                    <p class="section-hint">%s</p>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.el.instances")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.el.instances_hint")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.el.instances_scaling"))));

        // Apply button
        sb.append(ConfigStyles.applyButton("ApplyChangesBtnIntegration"));

        return sb.toString();
    }

    @Override
    public void registerListeners(PageBuilder builder, PlayerRef playerRef, Store<EntityStore> store) {
        // RPG Leveling toggle
        builder.addEventListener("RPGLevelingToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setRPGLevelingEnabled(enabled);
            saveManager.saveNow();
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.rpg_restart")).color("#f5c542"));
        });

        // Endless Leveling toggle
        builder.addEventListener("EndlessLevelingToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setEndlessLevelingEnabled(enabled);
            saveManager.saveNow();
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.el_restart")).color("#f5c542"));
        });

        // OrbisGuard toggle
        builder.addEventListener("OrbisGuardToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setOrbisGuardEnabled(enabled);
            saveManager.saveNow();
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.og_restart")).color("#f5c542"));
            ConfigUI.open(plugin, playerRef, store, "bosses", "integration");
        });

        // OG-specific config listeners (only when OG is enabled — elements don't exist otherwise)
        if (config.isOrbisGuardEnabled()) {
            builder.addEventListener("OgBlockBuild", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                config.setOgBlockBuild(Boolean.parseBoolean(data.toString()));
                saveManager.markDirty();
            });
            builder.addEventListener("OgBlockPvp", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                config.setOgBlockPvp(Boolean.parseBoolean(data.toString()));
                saveManager.markDirty();
            });
            builder.addEventListener("OgBlockedCommands", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                config.setOgBlockedCommands(data.toString());
                saveManager.markDirty();
            });
        }

        // EL-specific config listeners (only when EL is enabled — elements don't exist otherwise)
        if (config.isEndlessLevelingEnabled()) {
            builder.addEventListener("ElXpShareRange", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                config.setElXpShareRange(parseFloat(data, 30.0f));
                saveManager.markDirty();
            });
            builder.addEventListener("ElGauntletXpBase", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                config.setElGauntletXpBase(parseInt(data, 50));
                saveManager.markDirty();
            });
            builder.addEventListener("ElWardenXpBase", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                config.setElWardenXpBase(parseInt(data, 150));
                saveManager.markDirty();
            });
            builder.addEventListener("ElAchievementXp", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                config.setElAchievementXp(parseInt(data, 50));
                saveManager.markDirty();
            });
        }

        // XP reward listeners for all boss types (shared)
        for (BossType boss : BossType.values()) {
            String key = boss.name();
            builder.addEventListener(key + "_XpReward", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                int xp = parseInt(data, 0);
                config.getBossConfig(boss).setXpReward(xp);
                saveManager.markDirty();
            });
        }

        // Apply button
        builder.addEventListener("ApplyChangesBtnIntegration", CustomUIEventBindingType.Activating, (data, ctx) -> {
            if (!saveManager.tryApply()) return;
            saveManager.saveIfDirty();
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.xp_updated")).color("#4ade80"));
            ConfigUI.open(plugin, playerRef, store, "bosses", "integration");
        });
    }

    // === Status helper methods ===

    private static String getStatusBackgroundColor(boolean enabled, boolean modPresent) {
        if (enabled && modPresent) return "#1a2a1a";
        if (enabled && !modPresent) return "#2a2a1a";
        return "#1e1e2e";
    }

    private static String getStatusColor(boolean enabled, boolean modPresent) {
        if (enabled && modPresent) return "#4ade80";
        if (enabled && !modPresent) return "#f5c542";
        return "#888888";
    }

    private static String getStatusText(boolean enabled, boolean modPresent, String locale, String prefix) {
        if (enabled && modPresent) return I18n.getFor(locale, "ui." + prefix + ".status_active");
        if (enabled && !modPresent) return I18n.getFor(locale, "ui." + prefix + ".status_missing");
        if (!enabled && modPresent) return I18n.getFor(locale, "ui." + prefix + ".status_disabled_present");
        return I18n.getFor(locale, "ui." + prefix + ".status_disabled");
    }
}
