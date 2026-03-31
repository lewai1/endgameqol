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
 * Weapons tab for the ConfigUI.
 *
 * Exposes ALL 19 weapon-related config fields across 6 weapon effect groups:
 * - Hedera Daggers: poison toggle/damage/ticks, lifesteal toggle/percent
 * - Prisma Mirage: toggle/cooldown/lifetime
 * - Void Mark: toggle/duration/execution toggle/threshold/multiplier
 * - Dagger Blink: toggle/distance
 * - Dagger Trail: toggle/damage
 * - Dagger Vanish: cooldown/invulnerability
 */
public class WeaponsTab extends ConfigTabBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ConfigUI");

    public WeaponsTab(EndgameQoL plugin, ConfigSaveManager saveManager) {
        super(plugin, saveManager);
    }

    @Override
    public String buildHtml(String locale) {
        StringBuilder sb = new StringBuilder();

        sb.append("<p class=\"section-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.title"))).append("</p>");
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.hint"))).append("</p>");

        // ── HEDERA DAGGERS ──
        sb.append(String.format("""
                <div class="weapon-card">
                    <div style="layout-mode: left; vertical-align: center; anchor-height: 32;">
                        <span class="item-icon" data-hyui-item-id="Weapon_Daggers_Hedera" style="anchor-width: 28; anchor-height: 28;"></span>
                        <p class="weapon-header" style="padding-left: 8;">%s</p>
                        <p style="font-size: 10; color: #ff8800; padding-left: 8;">%s</p>
                    </div>
                    <p class="weapon-desc">%s</p>

                    <p class="group-header">%s</p>
                    <p class="section-hint">%s</p>
                    <div class="craft-row">
                        <input type="checkbox" id="HederaPoisonToggle" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Poison damage applied each second. Higher = faster kills.">%s</label>
                        <input type="number" id="HederaPoisonDmg" class="combat-input" step="1" value="%.0f"/>
                        <p class="combat-hint">(0.1 - 50.0) HP per second</p>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="How many seconds the poison lasts. Total damage = per tick x duration.">%s</label>
                        <input type="number" id="HederaPoisonTicks" class="combat-input" step="1" value="%d"/>
                        <p class="combat-hint">(1 - 20) 1 tick = 1 second</p>
                    </div>

                    <div class="divider"></div>

                    <p class="group-header">%s</p>
                    <p class="section-hint">%s</p>
                    <div class="craft-row">
                        <input type="checkbox" id="HederaLifestealToggle" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Percentage of your damage that heals you on each hit.">%s</label>
                        <input type="number" id="HederaLifestealPct" class="combat-input" step="1" value="%d"/>
                        <p class="combat-hint">(1 - 50%%) of damage healed</p>
                    </div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.hedera_daggers")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.legendary")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.hedera_desc")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.poison")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.poison_hint")),
                config.isEnableHederaDaggerPoison() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.poison_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.poison_dmg")),
                config.getHederaDaggerPoisonDamage(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.poison_ticks")),
                config.getHederaDaggerPoisonTicks(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.lifesteal")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.lifesteal_hint")),
                config.isEnableHederaDaggerLifesteal() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.lifesteal_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.lifesteal_pct")),
                Math.round(config.getHederaDaggerLifestealPercent() * 100)));

        // ── HEDERA DAGGERS SUMMARY ──
        sb.append(String.format("""
                <div class="info-grid">
                    <p class="subgroup-header">%s</p>
                    <div class="info-row"><p class="info-name">%s</p><p class="info-stats">%.0f dmg x %d ticks = %.0f HP total</p></div>
                    <div class="info-row"><p class="info-name">%s</p><p class="info-stats">%d%% of damage heals you</p></div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.effect_summary")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.total_poison")),
                config.getHederaDaggerPoisonDamage(),
                config.getHederaDaggerPoisonTicks(),
                config.getHederaDaggerPoisonDamage() * config.getHederaDaggerPoisonTicks(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.lifesteal_rate")),
                Math.round(config.getHederaDaggerLifestealPercent() * 100)));

        sb.append("<div class=\"divider\"></div>");

        // ── BLAZEFIST BURN ──
        sb.append(String.format("""
                <div class="weapon-card">
                    <div style="layout-mode: left; vertical-align: center; anchor-height: 32;">
                        <span class="item-icon" data-hyui-item-id="Endgame_Accessory_Blazefist" style="anchor-width: 28; anchor-height: 28;"></span>
                        <p class="weapon-header" style="padding-left: 8;">%s</p>
                        <p style="font-size: 10; color: #ff6600; padding-left: 8;">%s</p>
                    </div>
                    <p class="weapon-desc">%s</p>

                    <div class="craft-row">
                        <input type="checkbox" id="BlazefistBurnToggle" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Fire damage per tick (1 tick = 1 second). Total = per tick x ticks.">%s</label>
                        <input type="number" id="BlazefistBurnDmg" class="combat-input" step="5" value="%.0f"/>
                        <p class="combat-hint">(1 - 200) HP per second</p>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="How many seconds the burn lasts. Total damage = per tick x ticks.">%s</label>
                        <input type="number" id="BlazefistBurnTicks" class="combat-input" step="1" value="%d"/>
                        <p class="combat-hint">(1 - 10) 1 tick = 1 second</p>
                    </div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blazefist")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.legendary")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blazefist_desc")),
                config.isBlazefistBurnEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blazefist_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blazefist_dmg")),
                config.getBlazefistBurnDamage(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blazefist_ticks")),
                config.getBlazefistBurnTicks()));

        // Blazefist summary
        sb.append(String.format("""
                <div class="info-grid">
                    <p class="subgroup-header">%s</p>
                    <div class="info-row"><p class="info-name">%s</p><p class="info-stats">%.0f dmg x %d ticks = %.0f HP total</p></div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.effect_summary")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blazefist_total")),
                config.getBlazefistBurnDamage(),
                config.getBlazefistBurnTicks(),
                config.getBlazefistBurnDamage() * config.getBlazefistBurnTicks()));

        sb.append("<div class=\"divider\"></div>");

        // ── PRISMA MIRAGE ──
        sb.append(String.format("""
                <div class="weapon-card">
                    <p class="weapon-header">%s</p>
                    <p class="weapon-desc">%s</p>

                    <div class="craft-row">
                        <input type="checkbox" id="PrismaMirageToggle" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Cooldown between mirage clone spawns (milliseconds).">%s</label>
                        <input type="number" id="PrismaMirageCooldown" class="combat-input" step="1000" value="%d"/>
                        <p class="combat-hint">(1000 - 60000) ms</p>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="How long each clone lasts before despawning (milliseconds).">%s</label>
                        <input type="number" id="PrismaMirageLifetime" class="combat-input" step="1000" value="%d"/>
                        <p class="combat-hint">(1000 - 30000) ms</p>
                    </div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.mirage")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.mirage_desc")),
                config.isPrismaMirageEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.mirage_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.mirage_cooldown")),
                config.getPrismaMirageCooldownMs(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.mirage_lifetime")),
                config.getPrismaMirageLifetimeMs()));

        sb.append("<div class=\"divider\"></div>");

        // ── VOID MARK ──
        sb.append(String.format("""
                <div class="weapon-card">
                    <p class="weapon-header">%s</p>
                    <p class="weapon-desc">%s</p>

                    <div class="craft-row">
                        <input type="checkbox" id="VoidMarkToggle" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="How long the void mark lasts on the target (milliseconds).">%s</label>
                        <input type="number" id="VoidMarkDuration" class="combat-input" step="1000" value="%d"/>
                        <p class="combat-hint">(1000 - 60000) ms</p>
                    </div>
                    <div class="craft-row">
                        <input type="checkbox" id="VoidMarkExecToggle" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="HP threshold below which execution triggers. 25 = 25%% HP.">%s</label>
                        <input type="number" id="VoidMarkThreshold" class="combat-input" step="1" value="%d"/>
                        <p class="combat-hint">(5 - 75%%) of max HP</p>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Damage multiplier for execution strike. 3.0 = triple damage.">%s</label>
                        <input type="number" id="VoidMarkMultiplier" class="combat-input" step="0.5" value="%.1f"/>
                        <p class="combat-hint">(1.0 - 10.0) x multiplier</p>
                    </div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.voidmark")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.voidmark_desc")),
                config.isVoidMarkEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.voidmark_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.voidmark_duration")),
                config.getVoidMarkDurationMs(),
                config.isVoidMarkExecutionEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.voidmark_exec_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.voidmark_threshold")),
                Math.round(config.getVoidMarkExecutionThreshold() * 100),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.voidmark_multiplier")),
                config.getVoidMarkExecutionMultiplier()));

        sb.append("<div class=\"divider\"></div>");

        // ── DAGGER BLINK ──
        sb.append(String.format("""
                <div class="weapon-card">
                    <p class="weapon-header">%s</p>
                    <p class="weapon-desc">%s</p>

                    <div class="craft-row">
                        <input type="checkbox" id="DaggerBlinkToggle" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Maximum blink teleport distance (blocks).">%s</label>
                        <input type="number" id="DaggerBlinkDist" class="combat-input" step="1" value="%.1f"/>
                        <p class="combat-hint">(3.0 - 30.0) blocks</p>
                    </div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blink")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blink_desc")),
                config.isDaggerBlinkEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blink_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.blink_distance")),
                config.getDaggerBlinkDistance()));

        // ── DAGGER TRAIL ──
        sb.append(String.format("""
                <div class="weapon-card">
                    <p class="weapon-header">%s</p>
                    <p class="weapon-desc">%s</p>

                    <div class="craft-row">
                        <input type="checkbox" id="DaggerTrailToggle" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Damage dealt to enemies caught in the trail.">%s</label>
                        <input type="number" id="DaggerTrailDmg" class="combat-input" step="1" value="%.0f"/>
                        <p class="combat-hint">(1.0 - 100.0) HP</p>
                    </div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.trail")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.trail_desc")),
                config.isDaggerTrailEnabled() ? "checked" : "",
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.trail_toggle")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.trail_damage")),
                config.getDaggerTrailDamage()));

        // ── DAGGER VANISH ──
        sb.append(String.format("""
                <div class="weapon-card">
                    <p class="weapon-header">%s</p>
                    <p class="weapon-desc">%s</p>

                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Cooldown between vanish uses (milliseconds).">%s</label>
                        <input type="number" id="DaggerVanishCooldown" class="combat-input" step="1000" value="%d"/>
                        <p class="combat-hint">(1000 - 60000) ms</p>
                    </div>
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="How long you are invulnerable after vanishing (milliseconds).">%s</label>
                        <input type="number" id="DaggerVanishInvuln" class="combat-input" step="500" value="%d"/>
                        <p class="combat-hint">(500 - 10000) ms</p>
                    </div>
                </div>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.vanish")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.vanish_desc")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.vanish_cooldown")),
                config.getDaggerVanishCooldownMs(),
                HtmlUtil.escape(I18n.getFor(locale, "ui.weapons.vanish_invuln")),
                config.getDaggerVanishInvulnerabilityMs()));

        // ── APPLY BUTTON ──
        sb.append(ConfigStyles.applyButton("ApplyChangesBtnWeapons"));

        return sb.toString();
    }

    @Override
    public void registerListeners(PageBuilder builder, PlayerRef playerRef,
                                  Store<EntityStore> store) {

        // === HEDERA DAGGERS ===

        builder.addEventListener("HederaPoisonToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setEnableHederaDaggerPoison(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Hedera Dagger Poison: %b", enabled);
        });

        builder.addEventListener("HederaPoisonDmg", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float dmg = parseFloat(data, 5.0f);
            config.setHederaDaggerPoisonDamage(dmg);
            notifyIfClamped(playerRef, "Poison Damage", dmg, config.getHederaDaggerPoisonDamage());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Hedera Dagger Poison Damage: %.1f", dmg);
        });

        builder.addEventListener("HederaPoisonTicks", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int ticks = parseInt(data, 4);
            config.setHederaDaggerPoisonTicks(ticks);
            notifyIfClamped(playerRef, "Poison Ticks", ticks, config.getHederaDaggerPoisonTicks());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Hedera Dagger Poison Ticks: %d", ticks);
        });

        builder.addEventListener("HederaLifestealToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setEnableHederaDaggerLifesteal(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Hedera Dagger Lifesteal: %b", enabled);
        });

        builder.addEventListener("HederaLifestealPct", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int pct = parseInt(data, 8);
            float percent = pct / 100.0f;
            config.setHederaDaggerLifestealPercent(percent);
            notifyIfClamped(playerRef, "Lifesteal %", pct,
                    Math.round(config.getHederaDaggerLifestealPercent() * 100));
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Hedera Dagger Lifesteal: %d%% (%.2f)", pct, percent);
        });

        // === BLAZEFIST BURN ===

        builder.addEventListener("BlazefistBurnToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setBlazefistBurnEnabled(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Blazefist Burn: %b", enabled);
        });

        builder.addEventListener("BlazefistBurnDmg", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float dmg = parseFloat(data, 50.0f);
            config.setBlazefistBurnDamage(dmg);
            notifyIfClamped(playerRef, "Blazefist Burn Damage", dmg, config.getBlazefistBurnDamage());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Blazefist Burn Damage: %.1f", dmg);
        });

        builder.addEventListener("BlazefistBurnTicks", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int ticks = parseInt(data, 3);
            config.setBlazefistBurnTicks(ticks);
            notifyIfClamped(playerRef, "Blazefist Burn Ticks", ticks, config.getBlazefistBurnTicks());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Blazefist Burn Ticks: %d", ticks);
        });

        // === PRISMA MIRAGE ===

        builder.addEventListener("PrismaMirageToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setPrismaMirageEnabled(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Prisma Mirage: %b", enabled);
        });

        builder.addEventListener("PrismaMirageCooldown", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int ms = parseInt(data, 15000);
            config.setPrismaMirageCooldownMs(ms);
            notifyIfClamped(playerRef, "Mirage Cooldown", ms, config.getPrismaMirageCooldownMs());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Prisma Mirage Cooldown: %dms", ms);
        });

        builder.addEventListener("PrismaMirageLifetime", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int ms = parseInt(data, 5000);
            config.setPrismaMirageLifetimeMs(ms);
            notifyIfClamped(playerRef, "Mirage Lifetime", ms, config.getPrismaMirageLifetimeMs());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Prisma Mirage Lifetime: %dms", ms);
        });

        // === VOID MARK ===

        builder.addEventListener("VoidMarkToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setVoidMarkEnabled(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Void Mark: %b", enabled);
        });

        builder.addEventListener("VoidMarkDuration", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int ms = parseInt(data, 10000);
            config.setVoidMarkDurationMs(ms);
            notifyIfClamped(playerRef, "Void Mark Duration", ms, config.getVoidMarkDurationMs());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Void Mark Duration: %dms", ms);
        });

        builder.addEventListener("VoidMarkExecToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setVoidMarkExecutionEnabled(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Void Mark Execution: %b", enabled);
        });

        builder.addEventListener("VoidMarkThreshold", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int pct = parseInt(data, 25);
            float threshold = pct / 100.0f;
            config.setVoidMarkExecutionThreshold(threshold);
            notifyIfClamped(playerRef, "Void Mark Threshold %", pct,
                    Math.round(config.getVoidMarkExecutionThreshold() * 100));
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Void Mark Threshold: %d%% (%.2f)", pct, threshold);
        });

        builder.addEventListener("VoidMarkMultiplier", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float mult = parseFloat(data, 3.0f);
            config.setVoidMarkExecutionMultiplier(mult);
            notifyIfClamped(playerRef, "Void Mark Multiplier", mult, config.getVoidMarkExecutionMultiplier());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Void Mark Multiplier: %.1f", mult);
        });

        // === DAGGER BLINK ===

        builder.addEventListener("DaggerBlinkToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setDaggerBlinkEnabled(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Dagger Blink: %b", enabled);
        });

        builder.addEventListener("DaggerBlinkDist", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float dist = parseFloat(data, 12.0f);
            config.setDaggerBlinkDistance(dist);
            notifyIfClamped(playerRef, "Blink Distance", dist, config.getDaggerBlinkDistance());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Dagger Blink Distance: %.1f", dist);
        });

        // === DAGGER TRAIL ===

        builder.addEventListener("DaggerTrailToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            boolean enabled = Boolean.parseBoolean(data.toString());
            config.setDaggerTrailEnabled(enabled);
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Dagger Trail: %b", enabled);
        });

        builder.addEventListener("DaggerTrailDmg", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            float dmg = parseFloat(data, 15.0f);
            config.setDaggerTrailDamage(dmg);
            notifyIfClamped(playerRef, "Trail Damage", dmg, config.getDaggerTrailDamage());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Dagger Trail Damage: %.1f", dmg);
        });

        // === DAGGER VANISH ===

        builder.addEventListener("DaggerVanishCooldown", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int ms = parseInt(data, 10000);
            config.setDaggerVanishCooldownMs(ms);
            notifyIfClamped(playerRef, "Vanish Cooldown", ms, config.getDaggerVanishCooldownMs());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Dagger Vanish Cooldown: %dms", ms);
        });

        builder.addEventListener("DaggerVanishInvuln", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            if (data == null) return;
            int ms = parseInt(data, 2000);
            config.setDaggerVanishInvulnerabilityMs(ms);
            notifyIfClamped(playerRef, "Vanish Invulnerability", ms, config.getDaggerVanishInvulnerabilityMs());
            saveManager.markDirty();
            LOGGER.atFine().log("[ConfigUI] Dagger Vanish Invulnerability: %dms", ms);
        });

        // === APPLY BUTTON ===

        builder.addEventListener("ApplyChangesBtnWeapons", CustomUIEventBindingType.Activating, (data, ctx) -> {
            if (!saveManager.tryApply()) return;
            LOGGER.atInfo().log("[ConfigUI] Apply Changes button clicked (Weapons tab)");
            saveManager.saveIfDirty();
            if (plugin.getBossHealthManager() != null) {
                plugin.getBossHealthManager().refreshAllBossStats();
            }
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.weapons_saved")).color("#4ade80"));
        });
    }
}
