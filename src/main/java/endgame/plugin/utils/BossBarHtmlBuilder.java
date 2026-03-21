package endgame.plugin.utils;

/**
 * Shared HTML builder for boss/elite health bar HUDs.
 * Used by GenericBossManager and GolemVoidBossManager to avoid ~150 lines of duplication.
 */
public final class BossBarHtmlBuilder {

    private BossBarHtmlBuilder() {}

    /**
     * Build a standard boss health bar HUD.
     *
     * @param bossName      display name (e.g. "GOLEM VOID", "FROST DRAGON")
     * @param nameColor     hex color for the boss name
     * @param phaseText     full phase string (e.g. "Phase 2 - Enraged")
     * @param phaseColor    hex color for the phase text
     * @param invulnerable  whether to append "[INVULNERABLE]"
     * @param healthPercent 0.0–1.0
     * @param barWidth      total bar width in pixels
     * @param barColor      hex color for the health bar fill
     */
    public static String buildBossBar(String bossName, String nameColor,
                                       String phaseText, String phaseColor,
                                       boolean invulnerable,
                                       float healthPercent, int barWidth, String barColor) {
        int healthPct = Math.round(healthPercent * 100);
        int fillWidth = Math.round(barWidth * healthPercent);
        String invulnText = invulnerable ? " [INVULNERABLE]" : "";

        return String.format("""
                <style>
                    #boss-bar-container {
                        anchor-top: 40;
                        horizontal-align: center;
                        anchor-width: 400;
                        anchor-height: 90;
                        padding: 10;
                        layout-mode: Top;
                    }
                    #boss-name {
                        font-size: 16;
                        font-weight: bold;
                        color: %s;
                        text-align: center;
                        anchor-height: 20;
                        anchor-width: 100%%;
                    }
                    #phase-text {
                        font-size: 11;
                        color: %s;
                        text-align: center;
                        anchor-height: 14;
                        anchor-width: 100%%;
                        margin-top: 2;
                    }
                    #health-bar-bg {
                        anchor-width: %d;
                        anchor-height: 14;
                        background-color: #1a1a1a;
                        margin-top: 6;
                        horizontal-align: center;
                    }
                    #health-bar-fill {
                        anchor-width: %d;
                        anchor-height: 14;
                        background-color: %s;
                        anchor-left: 0;
                        anchor-top: 0;
                    }
                    #health-text {
                        font-size: 12;
                        color: #ffffff;
                        text-align: center;
                        anchor-height: 14;
                        anchor-width: 100%%;
                        margin-top: 4;
                    }
                </style>
                <div id="boss-bar-container">
                    <p id="boss-name">%s</p>
                    <p id="phase-text">%s%s</p>
                    <div id="health-bar-bg">
                        <div id="health-bar-fill"></div>
                    </div>
                    <p id="health-text">%d%% HP</p>
                </div>
                """,
                nameColor,
                phaseColor,
                barWidth,
                fillWidth,
                barColor,
                bossName,
                phaseText,
                invulnText,
                healthPct);
    }

    /**
     * Build a smaller elite health bar HUD (no phase text, compact layout).
     *
     * @param eliteName  display name
     * @param nameColor  hex color for the name
     * @param healthPercent 0.0–1.0
     * @param barWidth   total bar width in pixels
     * @param barColor   hex color for the health bar fill
     */
    public static String buildEliteBar(String eliteName, String nameColor,
                                        float healthPercent, int barWidth, String barColor) {
        int healthPct = Math.round(healthPercent * 100);
        int fillWidth = Math.round(barWidth * healthPercent);

        return String.format("""
                <style>
                    #boss-bar-container {
                        anchor-top: 50;
                        horizontal-align: center;
                        anchor-width: 300;
                        anchor-height: 60;
                        padding: 6;
                        layout-mode: Top;
                    }
                    #boss-name {
                        font-size: 13;
                        font-weight: bold;
                        color: %s;
                        text-align: center;
                        anchor-height: 16;
                        anchor-width: 100%%;
                    }
                    #elite-label {
                        font-size: 9;
                        color: #cccccc;
                        text-align: center;
                        anchor-height: 12;
                        anchor-width: 100%%;
                        margin-top: 1;
                    }
                    #health-bar-bg {
                        anchor-width: %d;
                        anchor-height: 10;
                        background-color: #1a1a1a;
                        margin-top: 4;
                        horizontal-align: center;
                    }
                    #health-bar-fill {
                        anchor-width: %d;
                        anchor-height: 10;
                        background-color: %s;
                        anchor-left: 0;
                        anchor-top: 0;
                    }
                    #health-text {
                        font-size: 10;
                        color: #ffffff;
                        text-align: center;
                        anchor-height: 12;
                        anchor-width: 100%%;
                        margin-top: 2;
                    }
                </style>
                <div id="boss-bar-container">
                    <p id="boss-name">%s</p>
                    <p id="elite-label">ELITE</p>
                    <div id="health-bar-bg">
                        <div id="health-bar-fill"></div>
                    </div>
                    <p id="health-text">%d%% HP</p>
                </div>
                """,
                nameColor,
                barWidth,
                fillWidth,
                barColor,
                eliteName,
                healthPct);
    }
}
