package endgame.plugin.ui.config;

/**
 * Shared CSS constants for the ConfigUI tab system.
 * Unified dark navy palette with purple accent (EndgameQoL brand).
 */
public final class ConfigStyles {

    private ConfigStyles() {}

    public static final String CSS = """
            <style>
                .config-container { anchor-width: 1000; }
                .tab-content { layout-mode: top; padding: 12; }

                /* Typography */
                .section-header { font-size: 16; color: #bb99ff; font-weight: bold; anchor-height: 26; padding-bottom: 4; }
                .section-hint { font-size: 10; color: #666680; anchor-height: 16; padding-bottom: 8; }
                .group-header { font-size: 13; color: #8899cc; font-weight: bold; anchor-height: 22; padding-top: 4; }
                .subgroup-header { font-size: 11; color: #667799; anchor-height: 18; padding-top: 2; }

                /* Cards — unified dark navy */
                .card { layout-mode: top; padding: 10; background-color: #181c28; margin-bottom: 6; }
                .card-row { layout-mode: left; vertical-align: center; anchor-height: 36; padding: 6; }
                .card-title { font-size: 13; color: #ffffff; font-weight: bold; anchor-width: 100; }
                .card-dropdown { anchor-width: 280; anchor-height: 32; }

                /* Scope/toggle cards — same base, subtle left accent via padding */
                .scope-card { layout-mode: top; padding: 10; background-color: #181c28; margin-bottom: 6; }
                .scope-header { font-size: 12; color: #8899cc; font-weight: bold; anchor-height: 20; padding-bottom: 4; }
                .toggle-row { layout-mode: left; vertical-align: center; anchor-height: 32; padding-left: 4; }
                .toggle-label { font-size: 12; color: #c0c8d8; padding-left: 10; }

                /* Custom difficulty box */
                .custom-card { layout-mode: top; padding: 12; background-color: #1e2030; margin-bottom: 8; }
                .custom-header { font-size: 13; color: #bb99ff; font-weight: bold; anchor-height: 22; }
                .slider-row { layout-mode: left; vertical-align: center; anchor-height: 40; padding-top: 4; }
                .slider-label { font-size: 12; color: #c0c8d8; anchor-width: 100; }
                .slider { anchor-width: 200; anchor-height: 24; }
                .slider-value { font-size: 14; color: #bb99ff; font-weight: bold; anchor-width: 70; padding-left: 12; }

                /* Info grid */
                .info-grid { layout-mode: top; padding-top: 6; }
                .info-row { layout-mode: left; anchor-height: 26; padding: 4; background-color: #1e2233; margin-bottom: 2; }
                .info-name { font-size: 12; color: #bb99ff; anchor-width: 160; }
                .info-stats { font-size: 11; color: #8899aa; }

                /* Combat inputs */
                .combat-section { layout-mode: top; background-color: #141820; padding: 6; margin-bottom: 4; }
                .combat-row { layout-mode: left; vertical-align: center; anchor-height: 30; padding-left: 8; margin-bottom: 2; }
                .combat-label { font-size: 12; color: #c0c8d8; anchor-width: 130; }
                .combat-input { anchor-width: 70; anchor-height: 26; }
                .combat-hint { font-size: 10; color: #556677; padding-left: 8; }
                .combat-value { font-size: 11; color: #66cc88; padding-left: 4; anchor-width: 80; }

                /* Crafting toggles */
                .craft-row { layout-mode: left; vertical-align: center; anchor-height: 36; padding: 6; background-color: #181c28; margin-bottom: 3; }
                .craft-label { font-size: 12; color: #c0c8d8; padding-left: 12; flex-weight: 1; }

                /* Weapon effects — same as card, no special color */
                .weapon-card { layout-mode: top; padding: 10; background-color: #181c28; margin-bottom: 6; }
                .weapon-header { font-size: 13; color: #bb99ff; font-weight: bold; anchor-height: 24; }
                .weapon-desc { font-size: 10; color: #556677; anchor-height: 16; padding-bottom: 6; }

                /* Sub-tabs within Scaling tab */
                .subtab-nav { layout-mode: left; anchor-height: 34; margin-bottom: 6; padding: 2; }
                .subtab-btn { anchor-height: 28; anchor-width: 120; background-color: #1e2233; margin-right: 6; }
                .subtab-btn-active { anchor-height: 28; anchor-width: 120; background-color: #3a3a60; margin-right: 6; }
                .subtab-btn-label { font-size: 11; color: #667788; }
                .subtab-btn-label-active { font-size: 11; color: #ffffff; font-weight: bold; }
                .subtab-content { layout-mode: top; padding: 6; background-color: #141820; }

                /* Button styles */
                @ApplyBtnBg { background-color: #2a3a5a; }
                @ApplyBtnHoverBg { background-color: #3a4a6a; }
                @ApplyBtnPressedBg { background-color: #1a2a4a; }
                @ApplyBtnLabel { font-size: 13; color: #ffffff; font-weight: bold; horizontal-align: center; vertical-align: center; }
                @ApplyBtnHoverLabel { font-size: 13; color: #bb99ff; font-weight: bold; horizontal-align: center; vertical-align: center; }

                /* Status banner */
                .status-banner { layout-mode: left; vertical-align: center; anchor-height: 32; padding: 8; margin-bottom: 6; }
                .status-text { font-size: 13; font-weight: bold; padding-left: 8; }

                /* Dividers & spacing */
                .divider { anchor-height: 1; background-color: #2a2a40; margin-top: 6; margin-bottom: 6; }
                .spacer { anchor-height: 6; }
            </style>
            """;

    /** Standard Apply button HTML template. ID is parameterized. */
    public static String applyButton(String id) {
        return String.format("""
                <button id="%s" class="custom-textbutton"
                    data-hyui-default-bg="@ApplyBtnBg"
                    data-hyui-hovered-bg="@ApplyBtnHoverBg"
                    data-hyui-pressed-bg="@ApplyBtnPressedBg"
                    data-hyui-default-label-style="@ApplyBtnLabel"
                    data-hyui-hovered-label-style="@ApplyBtnHoverLabel"
                    style="anchor-height: 38; anchor-width: 200; margin-top: 8;">Apply Changes</button>
                """, id);
    }
}
