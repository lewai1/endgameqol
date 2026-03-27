package endgame.plugin.ui.config;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.ui.ConfigUI;
import endgame.plugin.utils.HtmlUtil;
import endgame.plugin.utils.I18n;

/**
 * Armor tab for the ConfigUI.
 *
 * Contains:
 * - Mana Regen toggle + per-piece rates (Mithril, Onyxium, Prisma)
 * - HP Regen toggle + delay + per-piece rates (Onyxium, Prisma)
 * - Full set summary info grid
 * - Apply button
 */
public class ArmorTab extends ConfigTabBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ConfigUI");

    public ArmorTab(EndgameQoL plugin, ConfigSaveManager saveManager) {
        super(plugin, saveManager);
    }

    @Override
    public String buildHtml(String locale) {
        StringBuilder sb = new StringBuilder();

        sb.append("<p class=\"section-header\">").append(endgame.plugin.utils.HtmlUtil.escape(I18n.getFor(locale, "ui.armor.title"))).append("</p>");
        sb.append("<p class=\"section-hint\">").append(endgame.plugin.utils.HtmlUtil.escape(I18n.getFor(locale, "ui.armor.hint"))).append("</p>");

        // ── Mana Regen Section ──
        sb.append("<div class=\"scope-card\">");
        sb.append(String.format("""
                <div class="toggle-row">
                    <input type="checkbox" id="ManaRegenToggle" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                </div>
                """, config.isManaRegenArmorEnabled() ? "checked" : "",
                I18n.getFor(locale, "ui.armor.mana_toggle_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.armor.mana_toggle"))));
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.armor.mana_hint"))).append("</p>");
        sb.append("</div>");

        sb.append("<div class=\"card\">");
        sb.append("<p class=\"group-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.armor.mana_regen"))).append("</p>");
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.armor.mana_regen_hint"))).append("</p>");

        sb.append(String.format("""
                <div class="combat-row">
                    <label class="combat-label" data-hyui-tooltiptext="Mithril armor mana regen per piece. Default: 0.5/sec. Full set: 2.0/sec.">Mithril:</label>
                    <input type="number" id="ManaRegenMithril" class="combat-input" step="0.25" value="%.2f"/>
                    <p class="combat-hint">/sec per piece</p>
                    <p class="combat-value">= %.1f/sec full set</p>
                </div>
                """, config.getManaRegenMithrilPerPiece(), config.getManaRegenMithrilPerPiece() * 4));

        sb.append(String.format("""
                <div class="combat-row">
                    <label class="combat-label" data-hyui-tooltiptext="Onyxium armor mana regen per piece. Default: 0.75/sec. Full set: 3.0/sec.">Onyxium:</label>
                    <input type="number" id="ManaRegenOnyxium" class="combat-input" step="0.25" value="%.2f"/>
                    <p class="combat-hint">/sec per piece</p>
                    <p class="combat-value">= %.1f/sec full set</p>
                </div>
                """, config.getManaRegenOnyxiumPerPiece(), config.getManaRegenOnyxiumPerPiece() * 4));

        sb.append(String.format("""
                <div class="combat-row">
                    <label class="combat-label" data-hyui-tooltiptext="Prisma armor mana regen per piece. Default: 1.0/sec. Full set: 4.0/sec.">Prisma:</label>
                    <input type="number" id="ManaRegenPrisma" class="combat-input" step="0.25" value="%.2f"/>
                    <p class="combat-hint">/sec per piece</p>
                    <p class="combat-value">= %.1f/sec full set</p>
                </div>
                """, config.getManaRegenPrismaPerPiece(), config.getManaRegenPrismaPerPiece() * 4));
        sb.append("</div>");

        sb.append("<div class=\"divider\"></div>");

        // ── HP Regen Section ──
        sb.append("<div class=\"scope-card\">");
        sb.append(String.format("""
                <div class="toggle-row">
                    <input type="checkbox" id="HPRegenToggle" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                </div>
                """, config.isArmorHPRegenEnabled() ? "checked" : "",
                I18n.getFor(locale, "ui.armor.hp_toggle_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.armor.hp_toggle"))));
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.armor.hp_hint"))).append("</p>");
        sb.append("</div>");

        sb.append("<div class=\"card\">");
        sb.append("<p class=\"group-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.armor.hp_regen"))).append("</p>");

        sb.append(String.format("""
                <div class="combat-row">
                    <label class="combat-label" data-hyui-tooltiptext="Seconds after last damage before HP regen activates. Default: 15s. Range: 1-60.">%s</label>
                    <input type="number" id="HPRegenDelay" class="combat-input" step="1" value="%.0f"/>
                    <p class="combat-hint">(1 - 60) seconds after damage</p>
                </div>
                """, HtmlUtil.escape(I18n.getFor(locale, "ui.armor.hp_delay")),
                config.getArmorHPRegenDelaySec()));

        sb.append("<p class=\"subgroup-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.armor.hp_regen_per_piece"))).append("</p>");

        sb.append(String.format("""
                <div class="combat-row">
                    <label class="combat-label" data-hyui-tooltiptext="Onyxium armor HP regen per piece. Default: 0.5/sec. Full set: 2.0/sec.">Onyxium:</label>
                    <input type="number" id="HPRegenOnyxium" class="combat-input" step="0.25" value="%.2f"/>
                    <p class="combat-hint">/sec per piece</p>
                    <p class="combat-value">= %.1f/sec full set</p>
                </div>
                """, config.getArmorHPRegenOnyxiumPerPiece(), config.getArmorHPRegenOnyxiumPerPiece() * 4));

        sb.append(String.format("""
                <div class="combat-row">
                    <label class="combat-label" data-hyui-tooltiptext="Prisma armor HP regen per piece. Default: 0.75/sec. Full set: 3.0/sec.">Prisma:</label>
                    <input type="number" id="HPRegenPrisma" class="combat-input" step="0.25" value="%.2f"/>
                    <p class="combat-hint">/sec per piece</p>
                    <p class="combat-value">= %.1f/sec full set</p>
                </div>
                """, config.getArmorHPRegenPrismaPerPiece(), config.getArmorHPRegenPrismaPerPiece() * 4));
        sb.append("</div>");

        sb.append("<div class=\"divider\"></div>");

        // ── Full Set Summary ──
        sb.append("<div class=\"card\">");
        sb.append("<p class=\"group-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.armor.full_set"))).append("</p>");
        sb.append(String.format("""
                <div class="info-grid">
                    <div class="info-row"><p class="info-name">Mithril (4 pieces)</p><p class="info-stats">+%.1f mana/sec | ~50%% Physical resist</p></div>
                    <div class="info-row"><p class="info-name">Onyxium (4 pieces)</p><p class="info-stats">+%.1f mana/sec +%.1f HP/sec | ~61%% Physical resist + Fire</p></div>
                    <div class="info-row"><p class="info-name">Prisma (4 pieces)</p><p class="info-stats">+%.1f mana/sec +%.1f HP/sec | ~78%% Physical resist + Fire/Ice</p></div>
                </div>
                """,
                config.getManaRegenMithrilPerPiece() * 4,
                config.getManaRegenOnyxiumPerPiece() * 4, config.getArmorHPRegenOnyxiumPerPiece() * 4,
                config.getManaRegenPrismaPerPiece() * 4, config.getArmorHPRegenPrismaPerPiece() * 4));
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.armor.full_set_hint"))).append("</p>");
        sb.append("</div>");

        // Apply button
        sb.append(ConfigStyles.applyButton("ApplyChangesBtnArmor"));

        return sb.toString();
    }

    @Override
    public void registerListeners(PageBuilder builder, PlayerRef playerRef,
                                  Store<EntityStore> store) {

        // ── Mana Regen ──

        builder.addEventListener("ManaRegenToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setManaRegenArmorEnabled(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Mana Regen Armor: %b", enabled);
        });

        builder.addEventListener("ManaRegenMithril", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float rate = parseFloat(data, 0.5f);
            config.setManaRegenMithrilPerPiece(rate);
            notifyIfClamped(playerRef, "Mithril Mana Regen", rate, config.getManaRegenMithrilPerPiece());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Mana Regen Mithril: %.2f/sec", rate);
        });

        builder.addEventListener("ManaRegenOnyxium", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float rate = parseFloat(data, 0.75f);
            config.setManaRegenOnyxiumPerPiece(rate);
            notifyIfClamped(playerRef, "Onyxium Mana Regen", rate, config.getManaRegenOnyxiumPerPiece());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Mana Regen Onyxium: %.2f/sec", rate);
        });

        builder.addEventListener("ManaRegenPrisma", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float rate = parseFloat(data, 1.0f);
            config.setManaRegenPrismaPerPiece(rate);
            notifyIfClamped(playerRef, "Prisma Mana Regen", rate, config.getManaRegenPrismaPerPiece());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Mana Regen Prisma: %.2f/sec", rate);
        });

        // ── HP Regen ──

        builder.addEventListener("HPRegenToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setArmorHPRegenEnabled(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] HP Regen Armor: %b", enabled);
        });

        builder.addEventListener("HPRegenDelay", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float delay = parseFloat(data, 15.0f);
            config.setArmorHPRegenDelaySec(delay);
            notifyIfClamped(playerRef, "HP Regen Delay", delay, config.getArmorHPRegenDelaySec());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] HP Regen Delay: %.0f sec", delay);
        });

        builder.addEventListener("HPRegenOnyxium", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float rate = parseFloat(data, 0.5f);
            config.setArmorHPRegenOnyxiumPerPiece(rate);
            notifyIfClamped(playerRef, "Onyxium HP Regen", rate, config.getArmorHPRegenOnyxiumPerPiece());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] HP Regen Onyxium: %.2f/sec", rate);
        });

        builder.addEventListener("HPRegenPrisma", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float rate = parseFloat(data, 0.75f);
            config.setArmorHPRegenPrismaPerPiece(rate);
            notifyIfClamped(playerRef, "Prisma HP Regen", rate, config.getArmorHPRegenPrismaPerPiece());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] HP Regen Prisma: %.2f/sec", rate);
        });

        // ── Apply Button ──

        builder.addEventListener("ApplyChangesBtnArmor", CustomUIEventBindingType.Activating, (data, ctx) -> {
            if (!saveManager.tryApply()) return;
            LOGGER.atInfo().log("[ConfigUI] Apply Changes button clicked (Armor tab)");
            saveManager.saveIfDirty();
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.armor_saved")).color("#4ade80"));
            ConfigUI.open(plugin, playerRef, store, "bosses", "armor");
        });
    }
}
