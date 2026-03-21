package endgame.plugin.ui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.config.RecipeOverrideConfig;
import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.GauntletManager;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.managers.boss.GolemVoidBossManager;
import endgame.plugin.systems.trial.WardenTrialManager;
import endgame.plugin.utils.I18n;

import java.util.Map;

/**
 * HyUI page for the /eg status diagnostics dashboard.
 * Two-column layout with colored text spans for a polished admin dashboard.
 * Includes: System info, Encounters, Stats, Features, and Metrics.
 */
public class StatusUI {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.StatusUI");
    private static final String VERSION = "4.0.3";

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store) {
        EndgameConfig config = plugin.getConfig().get();
        String locale = I18n.resolveLocale(playerRef);

        // --- Gather all data upfront ---
        boolean dbEnabled = false;
        String dbEngine = "N/A";
        try {
            var dbConfig = plugin.getDatabaseConfig();
            if (dbConfig != null) {
                dbEnabled = dbConfig.get().isEnabled();
                if (dbEnabled) {
                    String jdbcUrl = dbConfig.get().getJdbcUrl();
                    if (jdbcUrl.contains("sqlite")) dbEngine = "SQLite";
                    else if (jdbcUrl.contains("mysql")) dbEngine = "MySQL";
                    else if (jdbcUrl.contains("mariadb")) dbEngine = "MariaDB";
                    else if (jdbcUrl.contains("postgresql")) dbEngine = "PostgreSQL";
                    else dbEngine = "Unknown";
                }
            }
        } catch (Exception ignored) {}

        boolean rpgActive = plugin.isRPGLevelingActive();
        boolean rpgPresent = plugin.isRPGLevelingModPresent();
        boolean elActive = plugin.isEndlessLevelingActive();
        boolean elPresent = plugin.isEndlessLevelingModPresent();

        int golemCount = 0, genericCount = 0;
        GolemVoidBossManager golemMgr = plugin.getGolemVoidBossManager();
        GenericBossManager genericMgr = plugin.getGenericBossManager();
        if (golemMgr != null) golemCount = golemMgr.getActiveBosses().size();
        if (genericMgr != null) genericCount = genericMgr.getActiveBosses().size();
        int totalBosses = golemCount + genericCount;

        GauntletManager gauntletMgr = plugin.getGauntletManager();
        int gauntletCount = gauntletMgr != null ? gauntletMgr.getActiveGauntletCount() : 0;

        WardenTrialManager trialMgr = plugin.getWardenTrialManager();
        int trialCount = trialMgr != null ? trialMgr.getActiveTrialCount() : 0;

        BountyManager bountyMgr = plugin.getBountyManager();
        int bountyPlayers = bountyMgr != null ? bountyMgr.getCachedPlayerCount() : 0;
        int lbEntries = 0;
        if (plugin.getGauntletLeaderboard() != null) {
            lbEntries = plugin.getGauntletLeaderboard().get().getEntryCount();
        }
        int onlinePlayers = 0;
        try { onlinePlayers = Universe.get().getPlayers().size(); } catch (Exception ignored) {}

        // Metrics data
        int recipesLoaded = 0;
        int recipesDisabled = 0;
        try {
            var recipeConfig = plugin.getRecipeOverrideConfig();
            if (recipeConfig != null) {
                Map<String, RecipeOverrideConfig.RecipeEntry> overrides = recipeConfig.get().getRecipeOverrides();
                recipesLoaded = overrides.size();
                for (RecipeOverrideConfig.RecipeEntry entry : overrides.values()) {
                    if (!entry.isEnabled()) recipesDisabled++;
                }
            }
        } catch (Exception ignored) {}
        int localesLoaded = I18n.getLoadedLocaleCount();

        // --- Build page ---
        StringBuilder sb = new StringBuilder();
        sb.append(CSS);

        sb.append("""
            <div class="page-overlay" style="layout-mode: center; vertical-align: middle; horizontal-align: center;">
                <div class="decorated-container sd-page" data-hyui-title="EndgameQoL Status" style="anchor-height: 520;">
                    <div class="container-contents" style="layout-mode: top; padding: 10;">
            """);

        // ── Header ──
        sb.append("""
                <div class="sd-header">
                    <p class="sd-title">
                        <span data-hyui-color="#bb44ff" data-hyui-bold="true">ENDGAME</span><span data-hyui-color="#8833cc" data-hyui-bold="true">QOL</span><span data-hyui-color="#666666"> """)
          .append(esc(I18n.getFor(locale, "ui.status.title")))
          .append("""
                        </span>
                    </p>
                    <p class="sd-ver-badge">
                        <span data-hyui-color="#8855cc">v""").append(VERSION).append("""
                        </span>
                    </p>
                </div>
                <div class="sd-accent-line"></div>
            """);

        // ── Two columns ──
        sb.append("<div class=\"sd-cols\">");

        // ──────── LEFT COLUMN ────────
        sb.append("<div class=\"sd-left\">");

        // Card: System
        sb.append("<div class=\"sd-card\">");
        sectionTitle(sb, I18n.getFor(locale, "ui.status.system"), "#FFD700");
        kvRow(sb, I18n.getFor(locale, "ui.status.difficulty"), config.getDifficultyString(), "#FFD700");
        if (dbEnabled) {
            var dbService = plugin.getDatabaseSyncService();
            boolean dbHealthy = dbService != null && dbService.isHealthy();
            String dbLabel = dbEngine + (dbHealthy ? "" : " " + I18n.getFor(locale, "ui.status.db_degraded"));
            String dbColor = dbHealthy ? "#4ade80" : "#ff4444";
            kvRow(sb, I18n.getFor(locale, "ui.status.database"), dbLabel, dbColor);
        } else {
            kvRow(sb, I18n.getFor(locale, "ui.status.database"), I18n.getFor(locale, "ui.status.db_disabled"), "#555555");
        }
        if (rpgActive) {
            kvRow(sb, "RPG Leveling", I18n.getFor(locale, "ui.status.rpg_active"), "#4ade80");
        } else if (rpgPresent) {
            kvRow(sb, "RPG Leveling", I18n.getFor(locale, "ui.status.rpg_detected"), "#ffaa00");
        } else {
            kvRow(sb, "RPG Leveling", I18n.getFor(locale, "ui.status.rpg_missing"), "#555555");
        }
        if (elActive) {
            kvRow(sb, "Endless Leveling", I18n.getFor(locale, "ui.status.el_active"), "#4ade80");
        } else if (elPresent) {
            kvRow(sb, "Endless Leveling", I18n.getFor(locale, "ui.status.el_detected"), "#ffaa00");
        } else {
            kvRow(sb, "Endless Leveling", I18n.getFor(locale, "ui.status.el_missing"), "#555555");
        }
        String playerSuffix = onlinePlayers != 1 ? "s" : "";
        kvRow(sb, I18n.getFor(locale, "ui.status.online_label"),
                I18n.getFor(locale, "ui.status.online", onlinePlayers, playerSuffix), "#c0c0c0");
        sb.append("</div>");

        // Card: Encounters
        sb.append("<div class=\"sd-card\">");
        sectionTitle(sb, I18n.getFor(locale, "ui.status.encounters"), "#ff8866");
        String bossDetail = totalBosses > 0
                ? golemCount + " " + I18n.getFor(locale, "ui.status.golem") + "  " + genericCount + " " + I18n.getFor(locale, "ui.status.generic")
                : null;
        encounterRow(sb, I18n.getFor(locale, "ui.status.bosses"), totalBosses, bossDetail, locale);
        encounterRow(sb, I18n.getFor(locale, "ui.status.gauntlets"), gauntletCount, null, locale);
        encounterRow(sb, I18n.getFor(locale, "ui.status.trials"), trialCount, null, locale);
        sb.append("</div>");

        // Card: Stats
        sb.append("<div class=\"sd-card\">");
        sectionTitle(sb, I18n.getFor(locale, "ui.status.stats"), "#88aaff");
        kvRow(sb, I18n.getFor(locale, "ui.status.bounty_label"),
                I18n.getFor(locale, "ui.status.bounty_cached", bountyPlayers), "#c0c0c0");
        kvRow(sb, I18n.getFor(locale, "ui.status.leaderboard_label"),
                I18n.getFor(locale, "ui.status.leaderboard", lbEntries), "#c0c0c0");
        sb.append("</div>");

        sb.append("</div>"); // end left

        // ──────── RIGHT COLUMN ────────
        sb.append("<div class=\"sd-right\">");

        // Card: Features
        sb.append("<div class=\"sd-card\">");
        sectionTitle(sb, I18n.getFor(locale, "ui.status.features"), "#4ade80");
        featureRow(sb, I18n.getFor(locale, "ui.status.combo_meter"), config.isComboEnabled(), locale);
        featureRow(sb, I18n.getFor(locale, "ui.status.gauntlet"), config.isGauntletEnabled(), locale);
        featureRow(sb, I18n.getFor(locale, "ui.status.bounty_board"), config.isBountyEnabled(), locale);
        featureRow(sb, I18n.getFor(locale, "ui.status.warden_trial"), config.isWardenTrialEnabled(), locale);
        featureRow(sb, I18n.getFor(locale, "ui.status.pvp"), config.isPvpEnabled(), locale);
        sb.append("</div>");

        // Card: Metrics
        sb.append("<div class=\"sd-card\">");
        sectionTitle(sb, I18n.getFor(locale, "ui.status.metrics"), "#c084fc");
        kvRow(sb, I18n.getFor(locale, "ui.status.recipes_loaded"), String.valueOf(recipesLoaded), "#c0c0c0");
        kvRow(sb, I18n.getFor(locale, "ui.status.recipes_disabled"), String.valueOf(recipesDisabled),
                recipesDisabled > 0 ? "#ffaa00" : "#c0c0c0");
        kvRow(sb, I18n.getFor(locale, "ui.status.locales_loaded"),
                localesLoaded + "/5", "#c0c0c0");
        sb.append("</div>");

        sb.append("</div>"); // end right
        sb.append("</div>"); // end cols

        // Close page
        sb.append("""
                    </div>
                </div>
            </div>
            """);

        try {
            PageBuilder.pageForPlayer(playerRef)
                    .fromHtml(sb.toString())
                    .open(store);
        } catch (Exception e) {
            LOGGER.atWarning().log("[StatusUI] Failed to open: %s", e.getMessage());
        }
    }

    // ── Builders ──

    private static void sectionTitle(StringBuilder sb, String text, String color) {
        sb.append(String.format("""
                <p class="sd-section">
                    <span data-hyui-color="%s" data-hyui-bold="true">%s</span>
                </p>
                """, color, esc(text)));
    }

    private static void kvRow(StringBuilder sb, String label, String value, String valueColor) {
        sb.append(String.format("""
                <div class="sd-kv">
                    <p class="sd-k">%s</p>
                    <p class="sd-v">
                        <span data-hyui-color="%s" data-hyui-bold="true">%s</span>
                    </p>
                </div>
                """, esc(label), valueColor, esc(value)));
    }

    private static void encounterRow(StringBuilder sb, String name, int count, String detail, String locale) {
        String color = count > 0 ? "#ffaa00" : "#4ade80";
        String val = count > 0 ? I18n.getFor(locale, "ui.status.active", count) : I18n.getFor(locale, "ui.status.none");
        sb.append(String.format("""
                <div class="sd-kv">
                    <p class="sd-k">%s</p>
                    <p class="sd-v">
                        <span data-hyui-color="%s" data-hyui-bold="true">%s</span>
                    </p>
                """, esc(name), color, esc(val)));
        if (detail != null && !detail.isEmpty()) {
            sb.append(String.format("""
                    <p class="sd-hint">%s</p>
                    """, esc(detail)));
        }
        sb.append("</div>");
    }

    private static void featureRow(StringBuilder sb, String name, boolean on, String locale) {
        String dotColor = on ? "#4ade80" : "#ff4444";
        String label = on ? I18n.getFor(locale, "ui.status.on") : I18n.getFor(locale, "ui.status.off");
        sb.append(String.format("""
                <div class="sd-feat">
                    <div class="sd-dot" style="background-color: %s;"></div>
                    <p class="sd-feat-name">%s</p>
                    <p class="sd-feat-val">
                        <span data-hyui-color="%s" data-hyui-bold="true">%s</span>
                    </p>
                </div>
                """, dotColor, esc(name), dotColor, esc(label)));
    }

    private static String esc(String t) {
        if (t == null) return "";
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── CSS ──

    private static final String CSS = """
            <style>
                .sd-page { anchor-width: 520; }

                /* Header bar */
                .sd-header {
                    layout-mode: left;
                    anchor-height: 26;
                    anchor-width: 100%;
                    vertical-align: center;
                }
                .sd-title {
                    font-size: 15;
                    flex-weight: 1;
                    anchor-height: 26;
                    vertical-align: center;
                }
                .sd-ver-badge {
                    font-size: 11;
                    anchor-width: 50;
                    anchor-height: 26;
                    vertical-align: center;
                    text-align: center;
                    background-color: #1a1a28;
                }
                .sd-accent-line {
                    anchor-width: 100%;
                    anchor-height: 2;
                    background-color: #bb44ff;
                    margin-bottom: 6;
                }

                /* Columns */
                .sd-cols {
                    layout-mode: left;
                    anchor-width: 100%;
                    anchor-height: 440;
                }
                .sd-left {
                    layout-mode: top;
                    anchor-width: 260;
                    anchor-height: 440;
                    padding-right: 3;
                }
                .sd-right {
                    layout-mode: top;
                    flex-weight: 1;
                    anchor-height: 440;
                    padding-left: 3;
                }

                /* Cards */
                .sd-card {
                    layout-mode: top;
                    background-color: #151520;
                    padding: 8;
                    padding-top: 6;
                    margin-bottom: 4;
                    anchor-width: 100%;
                }

                /* Section title */
                .sd-section {
                    font-size: 12;
                    anchor-height: 18;
                    padding-bottom: 3;
                }

                /* Key-Value rows */
                .sd-kv {
                    layout-mode: left;
                    anchor-height: 22;
                    anchor-width: 100%;
                    vertical-align: center;
                    padding-left: 2;
                }
                .sd-k {
                    font-size: 11;
                    color: #777777;
                    anchor-width: 90;
                    anchor-height: 22;
                    vertical-align: center;
                }
                .sd-v {
                    font-size: 11;
                    anchor-height: 22;
                    vertical-align: center;
                    flex-weight: 1;
                }
                .sd-hint {
                    font-size: 9;
                    color: #555555;
                    anchor-height: 22;
                    vertical-align: center;
                }

                /* Feature toggle rows */
                .sd-feat {
                    layout-mode: left;
                    anchor-height: 24;
                    anchor-width: 100%;
                    vertical-align: center;
                    padding-left: 2;
                }
                .sd-dot {
                    anchor-width: 6;
                    anchor-height: 6;
                    margin-right: 6;
                }
                .sd-feat-name {
                    font-size: 11;
                    color: #bbbbbb;
                    flex-weight: 1;
                    anchor-height: 24;
                    vertical-align: center;
                }
                .sd-feat-val {
                    font-size: 11;
                    anchor-width: 30;
                    anchor-height: 24;
                    vertical-align: center;
                    text-align: center;
                }
            </style>
            """;
}
