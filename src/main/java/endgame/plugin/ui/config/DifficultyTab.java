package endgame.plugin.ui.config;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.Difficulty;
import endgame.plugin.ui.ConfigUI;
import endgame.plugin.utils.HtmlUtil;
import endgame.plugin.utils.I18n;

/**
 * Difficulty tab for the ConfigUI.
 *
 * Contains:
 * - Global difficulty preset selector (EASY/MEDIUM/HARD/EXTREME/CUSTOM)
 * - Custom multiplier sliders (shown only when CUSTOM is selected)
 * - Scope checkboxes (AffectsBosses, AffectsMobs)
 * - Preset reference info grid
 */
public class DifficultyTab extends ConfigTabBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ConfigUI");

    public DifficultyTab(EndgameQoL plugin, ConfigSaveManager saveManager) {
        super(plugin, saveManager);
    }

    @Override
    public String buildHtml(String locale) {
        Difficulty currentDifficulty = config.getDifficulty();
        boolean isCustom = currentDifficulty == Difficulty.CUSTOM;

        String customBox = isCustom ? buildCustomMultiplierBox(locale) : "";

        return String.format("""
                <p class="section-header">%s</p>
                <p class="section-hint">%s</p>

                <!-- Difficulty selector card -->
                <div class="card">
                    <div class="card-row">
                        <label class="card-title" data-hyui-tooltiptext="%s">%s</label>
                        <select id="DifficultySelect" class="card-dropdown" value="%s">
                            <option value="EASY" %s>%s</option>
                            <option value="MEDIUM" %s>%s</option>
                            <option value="HARD" %s>%s</option>
                            <option value="EXTREME" %s>%s</option>
                            <option value="CUSTOM" %s>%s</option>
                        </select>
                    </div>
                </div>

                <!-- Custom multipliers (only shown when CUSTOM selected) -->
                %s

                <!-- Scope selection -->
                <div class="scope-card">
                    <p class="scope-header" data-hyui-tooltiptext="%s">%s</p>
                    <div class="toggle-row">
                        <input type="checkbox" id="AffectsBosses" %s/>
                        <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                    </div>
                    <div class="toggle-row">
                        <input type="checkbox" id="AffectsMobs" %s/>
                        <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                    </div>
                </div>

                <!-- Difficulty reference -->
                <p class="subgroup-header">%s</p>
                <div class="info-grid">
                    <div class="info-row"><p class="info-name">%s</p><p class="info-stats">%s</p></div>
                    <div class="info-row"><p class="info-name">%s</p><p class="info-stats">%s</p></div>
                    <div class="info-row"><p class="info-name">%s</p><p class="info-stats">%s</p></div>
                    <div class="info-row"><p class="info-name">%s</p><p class="info-stats">%s</p></div>
                    <div class="info-row"><p class="info-name">%s</p><p class="info-stats">%s</p></div>
                </div>
                """,
                // Section header and hint
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.title")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.hint")),
                // Preset tooltip (attribute - no escape) and label text
                I18n.getFor(locale, "ui.difficulty.preset_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.preset")),
                // Dropdown value and selected attributes + option text
                currentDifficulty.name(),
                currentDifficulty == Difficulty.EASY ? "selected" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.easy")),
                currentDifficulty == Difficulty.MEDIUM ? "selected" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.medium")),
                currentDifficulty == Difficulty.HARD ? "selected" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.hard")),
                currentDifficulty == Difficulty.EXTREME ? "selected" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.extreme")),
                currentDifficulty == Difficulty.CUSTOM ? "selected" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.custom")),
                // Custom multiplier box (empty string if not CUSTOM)
                customBox,
                // Scope: apply-to tooltip (attribute - no escape) and header text
                I18n.getFor(locale, "ui.difficulty.apply_to_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.apply_to")),
                // Bosses checkbox + tooltip (attribute) + label text
                config.isDifficultyAffectsBosses() ? "checked" : "",
                I18n.getFor(locale, "ui.difficulty.bosses_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.bosses")),
                // Mobs checkbox + tooltip (attribute) + label text
                config.isDifficultyAffectsMobs() ? "checked" : "",
                I18n.getFor(locale, "ui.difficulty.mobs_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.mobs")),
                // Preset reference header
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.preset_ref")),
                // Reference grid rows
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_easy")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_easy_stats")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_medium")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_medium_stats")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_hard")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_hard_stats")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_extreme")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_extreme_stats")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_custom")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.ref_custom_stats")));
    }

    @Override
    public void registerListeners(PageBuilder builder, PlayerRef playerRef,
                                  Store<EntityStore> store) {

        // Difficulty preset selector
        builder.addEventListener("DifficultySelect", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            Difficulty difficulty = Difficulty.fromString(data.toString());
            config.setDifficulty(difficulty);
            saveManager.markDirty();
            LOGGER.atInfo().log("[ConfigUI] Difficulty: %s", difficulty.getDisplayName());
            ctx.updatePage(true);
        });

        // Scope: affects bosses
        builder.addEventListener("AffectsBosses", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean affects = Boolean.parseBoolean(data.toString());
            config.setDifficultyAffectsBosses(affects);
            saveManager.markDirty();
            LOGGER.atInfo().log("[ConfigUI] Difficulty affects bosses: %b", affects);
        });

        // Scope: affects mobs
        builder.addEventListener("AffectsMobs", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean affects = Boolean.parseBoolean(data.toString());
            config.setDifficultyAffectsMobs(affects);
            saveManager.markDirty();
            LOGGER.atInfo().log("[ConfigUI] Difficulty affects mobs: %b", affects);
        });

        // Custom multiplier sliders (only active when CUSTOM difficulty is selected)
        if (config.getDifficulty() == Difficulty.CUSTOM) {
            builder.addEventListener("CustomHealthMult", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                float mult = parsePercentageToMultiplier(data);
                config.setCustomHealthMultiplier(mult);
                saveManager.markDirty();
                LOGGER.atInfo().log("[ConfigUI] Custom health multiplier: %.2f (from input: %s)", mult, data);
            });

            builder.addEventListener("CustomDamageMult", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                float mult = parsePercentageToMultiplier(data);
                config.setCustomDamageMultiplier(mult);
                saveManager.markDirty();
                LOGGER.atInfo().log("[ConfigUI] Custom damage multiplier: %.2f (from input: %s)", mult, data);
            });
        }
    }

    /**
     * Build the custom multiplier box shown when CUSTOM difficulty is selected.
     * Contains health and damage sliders (range 10-1000%).
     */
    private String buildCustomMultiplierBox(String locale) {
        int healthPct = Math.round(config.getCustomHealthMultiplier() * 100);
        int damagePct = Math.round(config.getCustomDamageMultiplier() * 100);
        healthPct = Math.max(10, healthPct);
        damagePct = Math.max(10, damagePct);

        return String.format("""
                <div class="custom-card">
                    <p class="custom-header">%s</p>
                    <p style="font-size: 10; color: #9966cc; anchor-height: 16; padding-bottom: 2;">%s</p>
                    <p style="font-size: 10; color: #9966cc; anchor-height: 16; padding-bottom: 6;">%s</p>
                    <div class="slider-row">
                        <label class="slider-label" data-hyui-tooltiptext="%s">%s</label>
                        <input type="range" class="slider-number-field" id="CustomHealthMult" min="10" max="1000" value="%d" step="10" style="anchor-width: 220; anchor-height: 24;"/>
                        <p class="slider-value">= x%.2f</p>
                    </div>
                    <div class="slider-row">
                        <label class="slider-label" data-hyui-tooltiptext="%s">%s</label>
                        <input type="range" class="slider-number-field" id="CustomDamageMult" min="10" max="1000" value="%d" step="10" style="anchor-width: 220; anchor-height: 24;"/>
                        <p class="slider-value">= x%.2f</p>
                    </div>
                </div>
                """,
                // Custom header
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.custom_title")),
                // Slider hints
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.custom_hint_slider")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.custom_hint_range")),
                // Health tooltip (attribute - no escape) and label
                I18n.getFor(locale, "ui.difficulty.health_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.health")),
                healthPct, config.getCustomHealthMultiplier(),
                // Damage tooltip (attribute - no escape) and label
                I18n.getFor(locale, "ui.difficulty.damage_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.difficulty.damage")),
                damagePct, config.getCustomDamageMultiplier());
    }
}
