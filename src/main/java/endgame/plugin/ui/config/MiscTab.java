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
 * Misc tab for the ConfigUI.
 *
 * Contains:
 * - Instance Rules: PvP toggle, Dungeon Block Protection toggle
 * - Current Config Summary (boss/mob damage multipliers)
 * - Force Refresh button
 * - Combo Meter: enabled, timer, damage tiers (x2/x3/x4/frenzy), tier effects, decay
 * - The Gauntlet: enabled, scaling, buff count
 * - Bounty Board: enabled, streak, refresh hours, weekly
 * - Misc Settings: Void Pocket, Minion Spawn Radius, Eye Void Health Multiplier
 */
public class MiscTab extends ConfigTabBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ConfigUI");

    public MiscTab(EndgameQoL plugin, ConfigSaveManager saveManager) {
        super(plugin, saveManager);
    }

    @Override
    public String buildHtml(String locale) {
        StringBuilder sb = new StringBuilder();

        // ── Section: Instance Rules ──
        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.misc.instance_rules"))).append("</p>");
        sb.append("<div class=\"scope-card\">");
        sb.append(String.format("""
                <div class="toggle-row">
                    <input type="checkbox" id="PvpToggle" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                </div>
                <div class="toggle-row">
                    <input type="checkbox" id="DungeonBlockProtectionToggle" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                </div>
                """,
                config.isPvpEnabled() ? "checked" : "",
                I18n.getFor(locale, "ui.misc.pvp_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.pvp")),
                config.isEnableDungeonBlockProtection() ? "checked" : "",
                I18n.getFor(locale, "ui.misc.block_protection_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.block_protection"))));
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.misc.instance_hint"))).append("</p>");
        sb.append("</div>");

        sb.append("<div class=\"divider\"></div>");

        // ── Current Config Summary ──
        sb.append(String.format("""
                <div class="card">
                    <p class="group-header">%s</p>
                    <div class="info-grid">
                        <div class="info-row"><p class="info-name">%s</p><p class="info-stats">x%.1f (from difficulty)</p></div>
                        <div class="info-row"><p class="info-name">%s</p><p class="info-stats">x%.1f (from difficulty)</p></div>
                    </div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.config_summary")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.boss_dmg_mult")),
                config.getBossDamageMultiplier(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.mob_dmg_mult")),
                config.getMobDamageMultiplier()));

        // ── Force Refresh ──
        sb.append(String.format("""
                <p class="group-header">%s</p>
                <p class="section-hint">%s</p>
                <button id="ApplyChangesBtn" class="custom-textbutton"
                    data-hyui-default-bg="@ApplyBtnBg"
                    data-hyui-hovered-bg="@ApplyBtnHoverBg"
                    data-hyui-pressed-bg="@ApplyBtnPressedBg"
                    data-hyui-default-label-style="@ApplyBtnLabel"
                    data-hyui-hovered-label-style="@ApplyBtnHoverLabel"
                    style="anchor-height: 38; anchor-width: 320; margin-top: 8;">%s</button>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.force_refresh")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.force_refresh_hint")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.force_refresh_btn"))));

        sb.append("<div class=\"divider\"></div>");

        // ── Section: Combo Meter ──
        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo"))).append("</p>");
        sb.append("<div class=\"scope-card\">");
        sb.append(String.format("""
                <div class="toggle-row">
                    <input type="checkbox" id="ComboEnabled" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="%s">%s</label>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="%s">%s</label>
                    <input type="range" class="slider-number-field" id="ComboTimer" min="1" max="30" value="%d" step="1" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">%ds</p>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Damage multiplier at x2 tier (3+ kills). Range: 1.0-5.0.">%s</label>
                    <input type="range" class="slider-number-field" id="ComboDmgX2" min="100" max="500" value="%d" step="5" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">x%.2f</p>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Damage multiplier at x3 tier (6+ kills). Range: 1.0-5.0.">%s</label>
                    <input type="range" class="slider-number-field" id="ComboDmgX3" min="100" max="500" value="%d" step="5" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">x%.2f</p>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Damage multiplier at x4 tier (10+ kills). Range: 1.0-5.0.">%s</label>
                    <input type="range" class="slider-number-field" id="ComboDmgX4" min="100" max="500" value="%d" step="5" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">x%.2f</p>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Damage multiplier at FRENZY tier (15+ kills). Range: 1.0-10.0.">%s</label>
                    <input type="range" class="slider-number-field" id="ComboDmgFrenzy" min="100" max="1000" value="%d" step="5" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">x%.2f</p>
                </div>
                <div class="toggle-row">
                    <input type="checkbox" id="ComboTierEffects" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="Apply visual/particle effects at each combo tier.">%s</label>
                </div>
                <div class="toggle-row">
                    <input type="checkbox" id="ComboDecay" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="Combo gradually decays instead of resetting instantly.">%s</label>
                </div>
                """,
                config.isComboEnabled() ? "checked" : "",
                I18n.getFor(locale, "ui.misc.combo_toggle_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo_toggle")),
                I18n.getFor(locale, "ui.misc.combo_timer_tooltip"),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo_timer")),
                Math.round(config.getComboTimerSeconds()), Math.round(config.getComboTimerSeconds()),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo_x2")),
                Math.round(config.getComboDamageX2() * 100), config.getComboDamageX2(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo_x3")),
                Math.round(config.getComboDamageX3() * 100), config.getComboDamageX3(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo_x4")),
                Math.round(config.getComboDamageX4() * 100), config.getComboDamageX4(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo_frenzy")),
                Math.round(config.getComboDamageFrenzy() * 100), config.getComboDamageFrenzy(),
                config.isComboTierEffectsEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo_tier_effects")),
                config.isComboDecayEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.combo_decay"))));
        sb.append("</div>");

        sb.append("<div class=\"divider\"></div>");

        // ── Section: Warden Trials ──
        sb.append("<p class=\"section-header\">Warden Trials</p>");
        sb.append("<div class=\"scope-card\">");
        sb.append(String.format("""
                <div class="toggle-row">
                    <input type="checkbox" id="WardenTrialEnabled" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="Enable Warden Trial challenges.">Enabled</label>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Time limit for Tier I (seconds). 0 = no limit.">Tier I Timer</label>
                    <input type="range" class="slider-number-field" id="WardenTimer1" min="0" max="600" value="%d" step="10" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">%ds</p>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Time limit for Tier II (seconds). 0 = no limit.">Tier II Timer</label>
                    <input type="range" class="slider-number-field" id="WardenTimer2" min="0" max="600" value="%d" step="10" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">%ds</p>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Time limit for Tier III (seconds). 0 = no limit.">Tier III Timer</label>
                    <input type="range" class="slider-number-field" id="WardenTimer3" min="0" max="600" value="%d" step="10" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">%ds</p>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Time limit for Tier IV (seconds). 0 = no limit.">Tier IV Timer</label>
                    <input type="range" class="slider-number-field" id="WardenTimer4" min="0" max="600" value="%d" step="10" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">%ds</p>
                </div>
                """,
                config.isWardenTrialEnabled() ? "checked" : "",
                config.getWardenTrialTimer(0), config.getWardenTrialTimer(0),
                config.getWardenTrialTimer(1), config.getWardenTrialTimer(1),
                config.getWardenTrialTimer(2), config.getWardenTrialTimer(2),
                config.getWardenTrialTimer(3), config.getWardenTrialTimer(3)));
        sb.append("</div>");

        sb.append("<div class=\"divider\"></div>");

        // ── Section: Bounty Board ──
        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.misc.bounty"))).append("</p>");
        sb.append("<div class=\"scope-card\">");
        sb.append(String.format("""
                <div class="toggle-row">
                    <input type="checkbox" id="BountyEnabled" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="Enable daily bounty quests with rewards.">%s</label>
                </div>
                <div class="toggle-row">
                    <input type="checkbox" id="BountyStreakEnabled" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="Bonus reward for completing all 3 daily bounties.">%s</label>
                </div>
                <div class="slider-row">
                    <label class="slider-label" data-hyui-tooltiptext="Hours between bounty refreshes. Range: 1-168.">%s</label>
                    <input type="range" class="slider-number-field" id="BountyRefreshHours" min="1" max="168" value="%d" step="1" style="anchor-width: 180; anchor-height: 24;"/>
                    <p class="slider-value">%dh</p>
                </div>
                <div class="toggle-row">
                    <input type="checkbox" id="BountyWeeklyEnabled" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="Enable weekly bounty challenges with enhanced rewards.">%s</label>
                </div>
                """,
                config.isBountyEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.bounty_toggle")),
                config.isBountyStreakEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.bounty_streak")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.bounty_refresh")),
                config.getBountyRefreshHours(), config.getBountyRefreshHours(),
                config.isBountyWeeklyEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.bounty_weekly"))));
        sb.append("</div>");

        sb.append("<div class=\"divider\"></div>");

        // ── Section: NPC & Spawning ──
        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.misc.settings"))).append("</p>");

        sb.append("<div class=\"card\">");
        sb.append(String.format("""
                <p class="group-header">Vorthak Merchant</p>
                <div class="toggle-row">
                    <input type="checkbox" id="VorthakEnabled" %s/>
                    <label class="toggle-label" data-hyui-tooltiptext="Enable Vorthak merchant spawning in the Forgotten Temple.">%s</label>
                </div>
                """,
                config.isVorthakEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.vorthak"))));
        sb.append("</div>");

        sb.append("<div class=\"card\">");
        sb.append(String.format("""
                <p class="group-header">Golem Void Minions</p>
                <div class="combat-row">
                    <label class="combat-label" data-hyui-tooltiptext="Radius around the boss where minions spawn. Range: 1-50 blocks.">%s</label>
                    <input type="number" id="MinionSpawnRadius" class="combat-input" step="1" value="%.0f"/>
                    <p class="combat-hint">(1 - 50) blocks</p>
                </div>
                <div class="combat-row">
                    <label class="combat-label" data-hyui-tooltiptext="Health multiplier for Eye of the Void entities. Range: 0.1-10.0.">%s</label>
                    <input type="number" id="EyeVoidHealthMultiplier" class="combat-input" step="0.1" value="%.1f"/>
                    <p class="combat-hint">(0.1 - 10.0) x multiplier</p>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.minion_radius")),
                config.getMinionSpawnRadius(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.misc.eye_void_hp")),
                config.getEyeVoidHealthMultiplier()));
        sb.append("</div>");

        return sb.toString();
    }

    @Override
    public void registerListeners(PageBuilder builder, PlayerRef playerRef,
                                  Store<EntityStore> store) {

        // ── Instance Rules ──

        builder.addEventListener("PvpToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setPvpEnabled(enabled);
            saveManager.markDirty();
            plugin.applyPvpToAllWorlds(enabled);
            plugin.getConfig().save();
            LOGGER.atInfo().log("[ConfigUI] PvP: %b", enabled);
        });

        builder.addEventListener("DungeonBlockProtectionToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setEnableDungeonBlockProtection(enabled);
            saveManager.markDirty();
            LOGGER.atInfo().log("[ConfigUI] Dungeon block protection: %b", enabled);
        });

        // ── Force Refresh ──

        builder.addEventListener("ApplyChangesBtn", CustomUIEventBindingType.Activating, (data, ctx) -> {
            if (!saveManager.tryApply()) return;
            LOGGER.atInfo().log("[ConfigUI] Force Refresh button clicked (Misc tab)");
            saveManager.saveIfDirty();
            if (plugin.getBossHealthManager() != null) {
                plugin.getBossHealthManager().refreshAllBossStats();
            }
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.changes_applied")).color("#4ade80"));
        });

        // ── Combo Meter ──

        builder.addEventListener("ComboEnabled", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setComboEnabled(Boolean.parseBoolean(data.toString()));
            saveManager.markDirty();
            LOGGER.atInfo().log("[ConfigUI] Combo enabled: %s", data);
        });

        builder.addEventListener("ComboTimer", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setComboTimerSeconds(Float.parseFloat(data.toString()));
            saveManager.markDirty();
        });

        builder.addEventListener("ComboDmgX2", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setComboDamageX2(Float.parseFloat(data.toString()) / 100f);
            saveManager.markDirty();
        });

        builder.addEventListener("ComboDmgX3", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setComboDamageX3(Float.parseFloat(data.toString()) / 100f);
            saveManager.markDirty();
        });

        builder.addEventListener("ComboDmgX4", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setComboDamageX4(Float.parseFloat(data.toString()) / 100f);
            saveManager.markDirty();
        });

        builder.addEventListener("ComboDmgFrenzy", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setComboDamageFrenzy(Float.parseFloat(data.toString()) / 100f);
            saveManager.markDirty();
        });

        builder.addEventListener("ComboTierEffects", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setComboTierEffectsEnabled(Boolean.parseBoolean(data.toString()));
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Combo tier effects: %s", data);
        });

        builder.addEventListener("ComboDecay", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setComboDecayEnabled(Boolean.parseBoolean(data.toString()));
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Combo decay: %s", data);
        });

        // ── Warden Trials ──

        builder.addEventListener("WardenTrialEnabled", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setWardenTrialEnabled(Boolean.parseBoolean(data.toString()));
            saveManager.markDirty();
        });

        for (int tier = 0; tier < 4; tier++) {
            final int t = tier;
            builder.addEventListener("WardenTimer" + (tier + 1), CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                config.setWardenTrialTimer(t, parseInt(data, 270));
                saveManager.markDirty();
            });
        }

        // ── Bounty Board ──

        builder.addEventListener("BountyEnabled", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setBountyEnabled(Boolean.parseBoolean(data.toString()));
            saveManager.markDirty();
            LOGGER.atInfo().log("[ConfigUI] Bounty enabled: %s", data);
        });

        builder.addEventListener("BountyStreakEnabled", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setBountyStreakEnabled(Boolean.parseBoolean(data.toString()));
            saveManager.markDirty();
        });

        builder.addEventListener("BountyRefreshHours", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setBountyRefreshHours(parseInt(data, 24));
            saveManager.markDirty();
        });

        builder.addEventListener("BountyWeeklyEnabled", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setBountyWeeklyEnabled(Boolean.parseBoolean(data.toString()));
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Bounty weekly: %s", data);
        });

        // ── Misc Settings ──

        builder.addEventListener("VorthakEnabled", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            config.setVorthakEnabled(Boolean.parseBoolean(data.toString()));
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Vorthak: %s", data);
        });

        builder.addEventListener("MinionSpawnRadius", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float radius = parseFloat(data, 12.0f);
            config.setMinionSpawnRadius(radius);
            notifyIfClamped(playerRef, "Minion Spawn Radius", radius, config.getMinionSpawnRadius());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Minion Spawn Radius: %.0f", radius);
        });

        builder.addEventListener("EyeVoidHealthMultiplier", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float mult = parseFloat(data, 1.5f);
            config.setEyeVoidHealthMultiplier(mult);
            notifyIfClamped(playerRef, "Eye Void HP Multiplier", mult, config.getEyeVoidHealthMultiplier());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Eye Void Health Multiplier: %.1f", mult);
        });
    }
}
