package endgame.plugin.ui.config;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BossConfig;
import endgame.plugin.ui.ConfigUI;
import endgame.plugin.utils.BossType;
import endgame.plugin.utils.HtmlUtil;
import endgame.plugin.utils.I18n;

/**
 * Scaling tab for the ConfigUI.
 * Shows per-boss/mob/zone4 health overrides, damage multipliers, player scaling,
 * Golem Void danger zone phase, and the enrage system.
 *
 * Sub-tab navigation (Bosses / Mobs / Zone 4) reopens the full ConfigUI on the
 * "scaling" main tab with the selected combat sub-tab.
 */
public class ScalingTab extends ConfigTabBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ConfigUI");

    private String combatSubTab;

    public ScalingTab(EndgameQoL plugin, ConfigSaveManager saveManager, String combatSubTab) {
        super(plugin, saveManager);
        this.combatSubTab = combatSubTab;
    }

    // ── HTML ──────────────────────────────────────────────────────────────────

    @Override
    public String buildHtml(String locale) {
        StringBuilder sb = new StringBuilder();

        sb.append("<p class=\"section-header\">").append(endgame.plugin.utils.HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.title"))).append("</p>");

        // Sub-tab buttons
        sb.append(String.format("""
                <div class="subtab-nav">
                    <button id="SubTabBosses" class="%s" style="layout-mode: left;"><label class="%s">%s</label></button>
                    <button id="SubTabMobs" class="%s" style="layout-mode: left;"><label class="%s">%s</label></button>
                    <button id="SubTabZone4" class="%s" style="layout-mode: left;"><label class="%s">%s</label></button>
                </div>
                """,
                combatSubTab.equals("bosses") ? "subtab-btn-active" : "subtab-btn",
                combatSubTab.equals("bosses") ? "subtab-btn-label-active" : "subtab-btn-label",
                HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.bosses")),
                combatSubTab.equals("mobs") ? "subtab-btn-active" : "subtab-btn",
                combatSubTab.equals("mobs") ? "subtab-btn-label-active" : "subtab-btn-label",
                HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.mobs")),
                combatSubTab.equals("zone4") ? "subtab-btn-active" : "subtab-btn",
                combatSubTab.equals("zone4") ? "subtab-btn-label-active" : "subtab-btn-label",
                HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.zone4"))));

        // Scrollable content area + apply button
        sb.append("<div style=\"layout-mode: TopScrolling; anchor-height: 580;\">");
        sb.append(buildSubTabContent(locale));
        sb.append(ConfigStyles.applyButton("ApplyChangesBtnScaling"));
        sb.append("</div>");

        return sb.toString();
    }

    /**
     * Build the data-driven content for the currently selected combat sub-tab.
     */
    private String buildSubTabContent(String locale) {
        BossType[] bossTypes = getBossTypesForSubTab(combatSubTab);
        if (bossTypes.length == 0) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"subtab-content\">");
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.health_hint"))).append("</p>");

        // ── Health section ──
        sb.append("<p class=\"subgroup-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.custom_health"))).append("</p>");
        for (BossType boss : bossTypes) {
            BossConfig bc = config.getBossConfig(boss);
            int effectiveHealth = config.getEffectiveBossHealth(boss);
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="%s — Base HP: %d. Set 0 to use difficulty preset.">%s HP:</label>
                        <input type="number" id="%s_Health" class="combat-input" value="%d"/>
                        <p class="combat-hint">=</p>
                        <p class="combat-value">%d HP final</p>
                    </div>
                    """, boss.getDisplayName(), boss.getDefaultHealth(),
                    boss.getDisplayName(), boss.name(), bc.getHealthOverrideRaw(), effectiveHealth));
        }

        // ── Damage section ──
        sb.append("<p class=\"subgroup-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.damage_bonus"))).append("</p>");
        sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.damage_hint"))).append("</p>");
        for (BossType boss : bossTypes) {
            BossConfig bc = config.getBossConfig(boss);
            float effectiveDmg = config.getEffectiveBossDamageMultiplier(boss);
            sb.append(String.format("""
                    <div class="combat-row">
                        <label class="combat-label" data-hyui-tooltiptext="Damage bonus for %s. +50 means 1.5x, -50 means 0.5x. Set 0 for preset.">%s DMG:</label>
                        <input type="number" id="%s_DmgMult" class="combat-input" step="1" value="%d"/>
                        <p class="combat-hint">%%</p>
                        <p class="combat-value">= x%.2f</p>
                    </div>
                    """, boss.getDisplayName(), boss.getDisplayName(), boss.name(),
                    multiplierToPercent(bc.getDamageMultiplierRaw()), effectiveDmg));
        }

        // ── Player scaling section (only for bosses that have scaling) ──
        boolean hasScaling = false;
        for (BossType boss : bossTypes) {
            if (boss.getDefaultPlayerScaling() > 0 || config.getBossConfig(boss).getPlayerScaling() > 0) {
                hasScaling = true;
                break;
            }
        }
        if (hasScaling) {
            sb.append("<div class=\"divider\"></div>");
            sb.append("<p class=\"group-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.player_scaling"))).append("</p>");
            sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.player_scaling_hint"))).append("</p>");
            for (BossType boss : bossTypes) {
                if (boss.getDefaultPlayerScaling() > 0 || config.getBossConfig(boss).getPlayerScaling() > 0) {
                    BossConfig bc = config.getBossConfig(boss);
                    sb.append(String.format("""
                            <div class="combat-row">
                                <label class="combat-label" data-hyui-tooltiptext="HP bonus per extra player. Ex: 50%% with 3 players = x2.0 HP.">%s:</label>
                                <input type="number" id="%s_PlayerScaling" class="combat-input" value="%d"/>
                                <p class="combat-hint">%% per player</p>
                            </div>
                            """, boss.getDisplayName(), boss.name(), bc.getPlayerScaling()));
                }
            }
        }

        // ── Golem-specific danger zone + enrage system (bosses sub-tab only) ──
        if (combatSubTab.equals("bosses")) {
            BossConfig golemBc = config.getBossConfig(BossType.GOLEM_VOID);
            sb.append(String.format("""
                    <p class="subgroup-header">%s</p>
                    <div class="combat-row">
                        <label class="combat-label">%s</label>
                        <input type="number" id="GOLEM_VOID_DangerZonePhase" class="combat-input" value="%d"/>
                        <p class="combat-hint">1-3 (1=all phases)</p>
                    </div>
                    """,
                    HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.danger_zone")),
                    HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.start_phase")),
                    golemBc.getDangerZoneStartPhase()));

            // Prisma balance toggles
            sb.append("<div class=\"divider\"></div>");
            sb.append("<p class=\"group-header\">Prisma Balance</p>");
            sb.append(String.format("""
                    <div class="combat-section">
                        <div class="combat-row">
                            <input type="checkbox" id="PrismaArmorVulnToggle" %s/>
                            <label class="toggle-label" data-hyui-tooltiptext="When enabled, players wearing Prisma armor take 2x damage from Dragon Frost and Hedera.">Prisma Armor Vulnerability (x2 damage from early bosses)</label>
                        </div>
                        <div class="combat-row">
                            <input type="checkbox" id="PrismaWeaponBlockToggle" %s/>
                            <label class="toggle-label" data-hyui-tooltiptext="When enabled, Prisma Sword, Prisma Daggers and Hedera Daggers deal no damage to Dragon Frost and Hedera.">Prisma Weapon Block (no damage on early bosses)</label>
                        </div>
                    </div>
                    """, config.isPrismaArmorVulnerabilityEnabled() ? "checked" : "",
                    config.isPrismaWeaponBossBlockEnabled() ? "checked" : ""));

            // Enrage system
            sb.append("<div class=\"divider\"></div>");
            sb.append("<p class=\"group-header\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.enrage"))).append("</p>");
            sb.append("<p class=\"section-hint\">").append(HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.enrage_hint"))).append("</p>");

            for (BossType boss : bossTypes) {
                BossConfig bc = config.getBossConfig(boss);
                String bKey = boss.name();
                sb.append(String.format("""
                        <div class="combat-section">
                            <div class="combat-row">
                                <input type="checkbox" id="%s_EnrageToggle" %s/>
                                <label class="toggle-label" data-hyui-tooltiptext="Enable enrage for %s.">%s — %s</label>
                            </div>
                            <div class="combat-row">
                                <label class="combat-label" data-hyui-tooltiptext="Damage required to trigger enrage within the time window.">%s</label>
                                <input type="number" id="%s_EnrageThreshold" class="combat-input" step="10" value="%.0f"/>
                                <p class="combat-hint">damage to trigger</p>
                            </div>
                            <div class="combat-row">
                                <label class="combat-label" data-hyui-tooltiptext="Time window for burst damage detection (seconds).">%s</label>
                                <input type="number" id="%s_EnrageWindow" class="combat-input" step="1" value="%.0f"/>
                                <p class="combat-hint">seconds</p>
                            </div>
                            <div class="combat-row">
                                <label class="combat-label" data-hyui-tooltiptext="How long the enrage lasts (seconds).">%s</label>
                                <input type="number" id="%s_EnrageDuration" class="combat-input" step="1" value="%.0f"/>
                                <p class="combat-hint">seconds</p>
                            </div>
                            <div class="combat-row">
                                <label class="combat-label" data-hyui-tooltiptext="Damage multiplier while enraged. 1.5 = 50%% more damage.">%s</label>
                                <input type="number" id="%s_EnrageDmgMult" class="combat-input" step="0.1" value="%.1f"/>
                                <p class="combat-hint">x multiplier</p>
                            </div>
                            <div class="combat-row">
                                <label class="combat-label" data-hyui-tooltiptext="Cooldown before enrage can trigger again (seconds). 0 = no cooldown.">%s</label>
                                <input type="number" id="%s_EnrageCooldown" class="combat-input" step="1" value="%.0f"/>
                                <p class="combat-hint">seconds</p>
                            </div>
                        </div>
                        """,
                        bKey, bc.isEnrageEnabled() ? "checked" : "", boss.getDisplayName(), boss.getDisplayName(),
                        HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.enrage_toggle")),
                        HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.enrage_threshold")),
                        bKey, bc.getEnrageDamageThreshold(),
                        HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.enrage_window")),
                        bKey, bc.getEnrageWindowMs() / 1000.0,
                        HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.enrage_duration")),
                        bKey, bc.getEnrageDurationMs() / 1000.0,
                        HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.enrage_dmg_mult")),
                        bKey, bc.getEnrageDamageMultiplier(),
                        HtmlUtil.escape(I18n.getFor(locale, "ui.scaling.enrage_cooldown")),
                        bKey, bc.getEnrageCooldownMs() / 1000.0));
            }
        }

        sb.append("</div>");
        return sb.toString();
    }

    // ── Event listeners ──────────────────────────────────────────────────────

    @Override
    public void registerListeners(PageBuilder builder, PlayerRef playerRef,
                                  Store<EntityStore> store) {

        // Sub-tab navigation buttons — rebuild page in-place
        builder.addEventListener("SubTabBosses", CustomUIEventBindingType.Activating, (data, ctx) -> {
            this.combatSubTab = "bosses";
            ctx.updatePage(true);
        });
        builder.addEventListener("SubTabMobs", CustomUIEventBindingType.Activating, (data, ctx) -> {
            this.combatSubTab = "mobs";
            ctx.updatePage(true);
        });
        builder.addEventListener("SubTabZone4", CustomUIEventBindingType.Activating, (data, ctx) -> {
            this.combatSubTab = "zone4";
            ctx.updatePage(true);
        });

        // Per-boss listeners for current sub-tab
        BossType[] bossTypes = getBossTypesForSubTab(combatSubTab);

        for (BossType boss : bossTypes) {
            String key = boss.name();

            // Health
            builder.addEventListener(key + "_Health", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                int health = parseInt(data, 0);
                config.getBossConfig(boss).setHealthOverride(health);
                notifyIfClamped(playerRef, boss.getDisplayName() + " HP",
                        health, config.getBossConfig(boss).getHealthOverrideRaw());
                saveManager.markDirty();
                LOGGER.atFine().log("[ConfigUI] %s health: %d", boss.getDisplayName(), health);
            });

            // Damage multiplier
            builder.addEventListener(key + "_DmgMult", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                float mult = parseMultiplier(data);
                config.getBossConfig(boss).setDamageMultiplier(mult);
                notifyIfClamped(playerRef, boss.getDisplayName() + " Damage",
                        mult, config.getBossConfig(boss).getDamageMultiplierRaw());
                saveManager.markDirty();
                LOGGER.atFine().log("[ConfigUI] %s damage multiplier: %.1f", boss.getDisplayName(), mult);
            });

            // Player scaling (only for bosses with scaling)
            if (boss.getDefaultPlayerScaling() > 0 || config.getBossConfig(boss).getPlayerScaling() > 0) {
                builder.addEventListener(key + "_PlayerScaling", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                    if (data == null) return;
                    int scaling = parseInt(data, 0);
                    config.getBossConfig(boss).setPlayerScaling(scaling);
                    notifyIfClamped(playerRef, boss.getDisplayName() + " Player Scaling",
                            scaling, config.getBossConfig(boss).getPlayerScaling());
                    saveManager.markDirty();
                    LOGGER.atFine().log("[ConfigUI] %s player scaling: %d%%", boss.getDisplayName(), scaling);
                });
            }
        }

        // Golem-specific danger zone phase (bosses sub-tab only)
        if (combatSubTab.equals("bosses")) {
            builder.addEventListener("GOLEM_VOID_DangerZonePhase", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                int phase = parseInt(data, 1);
                config.getBossConfig(BossType.GOLEM_VOID).setDangerZoneStartPhase(phase);
                notifyIfClamped(playerRef, "Danger Zone Phase",
                        phase, config.getBossConfig(BossType.GOLEM_VOID).getDangerZoneStartPhase());
                saveManager.markDirty();
                LOGGER.atFine().log("[ConfigUI] Golem Void danger zone start phase: %d", phase);
            });

            // Prisma balance toggles
            builder.addEventListener("PrismaArmorVulnToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                boolean enabled = Boolean.parseBoolean(data.toString());
                config.setPrismaArmorVulnerabilityEnabled(enabled);
                saveManager.markDirty();
                LOGGER.atFine().log("[ConfigUI] Prisma armor vulnerability: %b", enabled);
            });
            builder.addEventListener("PrismaWeaponBlockToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                boolean enabled = Boolean.parseBoolean(data.toString());
                config.setPrismaWeaponBossBlockEnabled(enabled);
                saveManager.markDirty();
                LOGGER.atFine().log("[ConfigUI] Prisma weapon boss block: %b", enabled);
            });

            // Enrage system listeners (bosses only)
            for (BossType boss : bossTypes) {
                String key = boss.name();

                builder.addEventListener(key + "_EnrageToggle", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                    if (data == null) return;
                    boolean enabled = Boolean.parseBoolean(data.toString());
                    config.getBossConfig(boss).setEnrageEnabled(enabled);
                    saveManager.markDirty();
                    LOGGER.atFine().log("[ConfigUI] %s enrage: %b", boss.getDisplayName(), enabled);
                });

                builder.addEventListener(key + "_EnrageThreshold", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                    if (data == null) return;
                    float threshold = parseFloat(data, 200.0f);
                    config.getBossConfig(boss).setEnrageDamageThreshold(threshold);
                    notifyIfClamped(playerRef, boss.getDisplayName() + " Enrage Threshold",
                            threshold, config.getBossConfig(boss).getEnrageDamageThreshold());
                    saveManager.markDirty();
                    LOGGER.atFine().log("[ConfigUI] %s enrage threshold: %.0f", boss.getDisplayName(), threshold);
                });

                builder.addEventListener(key + "_EnrageWindow", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                    if (data == null) return;
                    long windowMs = (long) (parseFloat(data, 5.0f) * 1000);
                    config.getBossConfig(boss).setEnrageWindowMs(windowMs);
                    notifyIfClamped(playerRef, boss.getDisplayName() + " Enrage Window",
                            windowMs, config.getBossConfig(boss).getEnrageWindowMs());
                    saveManager.markDirty();
                    LOGGER.atFine().log("[ConfigUI] %s enrage window: %dms", boss.getDisplayName(), windowMs);
                });

                builder.addEventListener(key + "_EnrageDuration", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                    if (data == null) return;
                    long durationMs = (long) (parseFloat(data, 8.0f) * 1000);
                    config.getBossConfig(boss).setEnrageDurationMs(durationMs);
                    notifyIfClamped(playerRef, boss.getDisplayName() + " Enrage Duration",
                            durationMs, config.getBossConfig(boss).getEnrageDurationMs());
                    saveManager.markDirty();
                    LOGGER.atFine().log("[ConfigUI] %s enrage duration: %dms", boss.getDisplayName(), durationMs);
                });

                builder.addEventListener(key + "_EnrageDmgMult", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                    if (data == null) return;
                    float mult = parseFloat(data, 1.5f);
                    config.getBossConfig(boss).setEnrageDamageMultiplier(mult);
                    notifyIfClamped(playerRef, boss.getDisplayName() + " Enrage Multiplier",
                            mult, config.getBossConfig(boss).getEnrageDamageMultiplier());
                    saveManager.markDirty();
                    LOGGER.atFine().log("[ConfigUI] %s enrage multiplier: %.1f", boss.getDisplayName(), mult);
                });

                builder.addEventListener(key + "_EnrageCooldown", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                    if (data == null) return;
                    long cooldownMs = (long) (parseFloat(data, 15.0f) * 1000);
                    config.getBossConfig(boss).setEnrageCooldownMs(cooldownMs);
                    notifyIfClamped(playerRef, boss.getDisplayName() + " Enrage Cooldown",
                            cooldownMs, config.getBossConfig(boss).getEnrageCooldownMs());
                    saveManager.markDirty();
                    LOGGER.atFine().log("[ConfigUI] %s enrage cooldown: %dms", boss.getDisplayName(), cooldownMs);
                });
            }
        }

        // Apply button
        builder.addEventListener("ApplyChangesBtnScaling", CustomUIEventBindingType.Activating, (data, ctx) -> {
            if (!saveManager.tryApply()) return;
            LOGGER.atInfo().log("[ConfigUI] Apply Changes button clicked (Scaling tab)");
            saveManager.saveIfDirty();
            if (plugin.getBossHealthManager() != null) {
                plugin.getBossHealthManager().refreshAllBossStats();
            }
            playerRef.sendMessage(Message.raw("[EndgameQoL] " + I18n.getForPlayer(playerRef, "ui.config.changes_applied")).color("#4ade80"));
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Get the BossTypes to show for a given combat sub-tab.
     */
    private static BossType[] getBossTypesForSubTab(String subTab) {
        return switch (subTab) {
            case "bosses" -> new BossType[] { BossType.DRAGON_FROST, BossType.HEDERA, BossType.GOLEM_VOID };
            case "mobs" -> new BossType[] { BossType.ALPHA_REX, BossType.SPECTRE_VOID, BossType.SWAMP_CROCODILE };
            case "zone4" -> new BossType[] { BossType.DRAGON_FIRE, BossType.ZOMBIE_ABERRANT };
            default -> new BossType[0];
        };
    }
}
