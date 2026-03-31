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
 * RPG Leveling tab for the ConfigUI.
 * Contains: mod status banner, enable toggle, XP rewards per boss, instance levels (read-only).
 */
public class RPGLevelingTab extends ConfigTabBuilder {

    public RPGLevelingTab(EndgameQoL plugin, ConfigSaveManager saveManager) {
        super(plugin, saveManager);
    }

    @Override
    public String buildHtml(String locale) {
        StringBuilder sb = new StringBuilder();

        sb.append("<p class=\"section-header\">").append(endgame.plugin.utils.HtmlUtil.escape(endgame.plugin.utils.I18n.getFor(locale, "ui.rpg.title"))).append("</p>");

        // Status banner
        boolean enabled = config.isRPGLevelingEnabled();
        boolean modPresent = plugin.isRPGLevelingModPresent();

        String statusBg = getStatusBackgroundColor(enabled, modPresent);
        String statusColor = getStatusColor(enabled, modPresent);
        String statusText = getStatusText(enabled, modPresent, locale);

        sb.append(String.format("""
                <div class="card" style="background-color: %s;">
                    <div class="status-banner">
                        <label class="status-text" style="color: %s;">%s</label>
                    </div>
                    <div class="toggle-row">
                        <input type="checkbox" id="RPGLevelingToggle" %s/>
                        <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                    </div>
                    <p class="section-hint">%s</p>
                </div>
                """, statusBg, statusColor, statusText,
                enabled ? "checked" : "",
                I18n.getFor(locale, "ui.rpg.enable_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.enable_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.restart_hint"))));

        sb.append("<div class=\"divider\"></div>");

        // XP Rewards section (editable)
        sb.append("<p class=\"section-header\">").append(endgame.plugin.utils.HtmlUtil.escape(endgame.plugin.utils.I18n.getFor(locale, "ui.rpg.xp_rewards"))).append("</p>");
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.xp_hint"))).append("</p>");

        // Bosses section
        sb.append("<div class=\"card\">");
        sb.append("<p class=\"group-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.bosses"))).append("</p>");
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

        // Elite Mobs section
        sb.append("<div class=\"card\">");
        sb.append("<p class=\"group-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.rpg.elites"))).append("</p>");
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

        // Instance levels (info only, read-only)
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

        // Apply button
        sb.append(ConfigStyles.applyButton("ApplyChangesBtnRPG"));

        return sb.toString();
    }

    @Override
    public void registerListeners(PageBuilder builder, PlayerRef playerRef, Store<EntityStore> store) {
        // Enable toggle — immediate save since requires restart
        builder.addEventListener("RPGLevelingToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setRPGLevelingEnabled(enabled);
            saveManager.saveNow();
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.rpg_restart")).color("#f5c542"));
        });

        // XP reward listeners for all boss types
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
        builder.addEventListener("ApplyChangesBtnRPG", CustomUIEventBindingType.Activating, (data, ctx) -> {
            if (!saveManager.tryApply()) return;
            saveManager.saveIfDirty();
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.xp_updated")).color("#4ade80"));
        });
    }

    // === Status helper methods ===

    private static String getStatusBackgroundColor(boolean enabled, boolean modPresent) {
        if (enabled && modPresent) return "#1a2a1a";   // green tint — active
        if (enabled && !modPresent) return "#2a2a1a";   // yellow tint — missing
        return "#1e1e2e";                                // default card — disabled
    }

    private static String getStatusColor(boolean enabled, boolean modPresent) {
        if (enabled && modPresent) return "#4ade80";    // green — active
        if (enabled && !modPresent) return "#f5c542";   // yellow — mod not found
        return "#888888";                                // grey — disabled
    }

    private static String getStatusText(boolean enabled, boolean modPresent, String locale) {
        if (enabled && modPresent) return I18n.getFor(locale, "ui.rpg.status_active");
        if (enabled && !modPresent) return I18n.getFor(locale, "ui.rpg.status_missing");
        if (!enabled && modPresent) return I18n.getFor(locale, "ui.rpg.status_disabled_present");
        return I18n.getFor(locale, "ui.rpg.status_disabled");
    }
}
