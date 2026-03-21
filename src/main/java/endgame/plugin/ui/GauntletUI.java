package endgame.plugin.ui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.GauntletLeaderboard;
import endgame.plugin.managers.GauntletManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * HyUI page for The Gauntlet leaderboard (/gauntlet command).
 */
public class GauntletUI {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.GauntletUI");

    private static final String[] MEDAL_COLORS = {"#FFD700", "#C0C0C0", "#CD7F32"};
    private static final String[] MEDAL_LABELS = {"1st", "2nd", "3rd"};
    private static final String[] ROW_BG_COLORS = {"#2a2215", "#22222a", "#22222a"};

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store) {
        GauntletManager manager = plugin.getGauntletManager();

        StringBuilder sb = new StringBuilder();
        sb.append("""
            <style>
                .gauntlet-container { anchor-width: 550; }
                .gauntlet-header {
                    layout-mode: top;
                    anchor-height: 60;
                    anchor-width: 100%;
                    padding: 8;
                }
                .gauntlet-title {
                    font-size: 20;
                    font-weight: bold;
                    color: #c084fc;
                    text-align: center;
                    anchor-height: 28;
                    anchor-width: 100%;
                }
                .gauntlet-subtitle {
                    font-size: 11;
                    color: #8866aa;
                    text-align: center;
                    anchor-height: 18;
                    anchor-width: 100%;
                }
                .lb-row {
                    layout-mode: left;
                    anchor-height: 40;
                    anchor-width: 100%;
                    padding-left: 12;
                    padding-right: 12;
                    vertical-align: center;
                    margin-bottom: 2;
                }
                .lb-rank {
                    font-size: 16;
                    font-weight: bold;
                    anchor-width: 50;
                    anchor-height: 40;
                    vertical-align: center;
                    text-align: center;
                }
                .lb-medal {
                    font-size: 18;
                    font-weight: bold;
                    anchor-width: 50;
                    anchor-height: 40;
                    vertical-align: center;
                    text-align: center;
                }
                .lb-name {
                    font-size: 14;
                    color: #ffffff;
                    anchor-height: 40;
                    vertical-align: center;
                    flex-weight: 1;
                    padding-left: 8;
                }
                .lb-wave {
                    font-size: 16;
                    font-weight: bold;
                    color: #c084fc;
                    anchor-width: 100;
                    anchor-height: 40;
                    vertical-align: center;
                    text-align: center;
                }
                .lb-date {
                    font-size: 9;
                    color: #666666;
                    anchor-width: 80;
                    anchor-height: 40;
                    vertical-align: center;
                    text-align: center;
                    padding-left: 8;
                }
                .lb-divider {
                    anchor-width: 100%;
                    anchor-height: 1;
                    background-color: #2a2a3a;
                    margin-bottom: 2;
                }
                .lb-empty {
                    font-size: 14;
                    color: #555555;
                    text-align: center;
                    anchor-width: 100%;
                    anchor-height: 60;
                    vertical-align: center;
                }
                .lb-count {
                    font-size: 10;
                    color: #555555;
                    text-align: center;
                    anchor-width: 100%;
                    anchor-height: 18;
                    margin-top: 8;
                }
            </style>
            <div class="page-overlay" style="layout-mode: center; vertical-align: middle; horizontal-align: center;">
                <div class="decorated-container gauntlet-container" data-hyui-title="The Gauntlet" style="anchor-height: 560;">
                    <div class="container-contents" style="layout-mode: top; padding: 8;">
                        <div class="gauntlet-header">
                            <p class="gauntlet-title">THE GAUNTLET</p>
                            <p class="gauntlet-subtitle">Leaderboard — Top Survivors</p>
                        </div>
                        <div class="lb-divider" style="background-color: #c084fc; anchor-height: 2; margin-bottom: 6;"></div>
            """);

        List<GauntletLeaderboard.LeaderboardEntry> entries =
                manager != null ? manager.getLeaderboardTop(10) : List.of();

        if (entries.isEmpty()) {
            sb.append("""
                <p class="lb-empty">No challengers yet. Be the first to enter The Gauntlet!</p>
                """);
        } else {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd");
            for (int i = 0; i < entries.size(); i++) {
                GauntletLeaderboard.LeaderboardEntry entry = entries.get(i);
                String dateStr = entry.getTimestamp() > 0 ? dateFormat.format(new Date(entry.getTimestamp())) : "";

                String rowBg = i < 3 ? ROW_BG_COLORS[i] : (i % 2 == 0 ? "#1a1a22" : "#18182000");

                if (i < 3) {
                    // Top 3 with medal styling
                    String medalColor = MEDAL_COLORS[i];
                    String medalLabel = MEDAL_LABELS[i];
                    sb.append(String.format("""
                        <div class="lb-row" style="background-color: %s;">
                            <p class="lb-medal" style="color: %s;">%s</p>
                            <p class="lb-name" style="font-weight: bold; color: %s;">%s</p>
                            <p class="lb-wave" style="color: %s;">Wave %d</p>
                            <p class="lb-date">%s</p>
                        </div>
                        """,
                        rowBg, medalColor, medalLabel,
                        i == 0 ? "#FFD700" : "#ffffff",
                        escapeHtml(entry.getPlayerName()),
                        medalColor, entry.getWave(),
                        dateStr));
                } else {
                    // Normal rows
                    sb.append(String.format("""
                        <div class="lb-row" style="background-color: %s;">
                            <p class="lb-rank" style="color: #666666;">#%d</p>
                            <p class="lb-name">%s</p>
                            <p class="lb-wave">Wave %d</p>
                            <p class="lb-date">%s</p>
                        </div>
                        """,
                        rowBg, i + 1,
                        escapeHtml(entry.getPlayerName()),
                        entry.getWave(),
                        dateStr));
                }

                if (i < entries.size() - 1 && i >= 2) {
                    sb.append("<div class=\"lb-divider\"></div>");
                }
            }

            int totalEntries = manager != null ? manager.getLeaderboardEntryCount() : 0;
            if (totalEntries > 10) {
                sb.append(String.format(
                    "<p class=\"lb-count\">Showing top 10 of %d entries</p>", totalEntries));
            }
        }

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
            LOGGER.atWarning().log("[GauntletUI] Failed to open: %s", e.getMessage());
        }
    }

    private static String escapeHtml(String text) {
        return endgame.plugin.utils.HtmlUtil.escape(text);
    }
}
