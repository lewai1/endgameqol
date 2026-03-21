package endgame.plugin.ui;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.ui.config.*;
import endgame.plugin.utils.I18n;

/**
 * ConfigUI coordinator.
 *
 * Builds the page shell (styles + decorated container + tab nav), delegates HTML
 * generation and event registration to modular tab builders, and wires the shared
 * ConfigSaveManager for batch saves.
 *
 * Tabs: Difficulty | Scaling | Integration | Weapons | Armor | Crafting | Misc
 */
public class ConfigUI {

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store) {
        open(plugin, playerRef, store, "bosses", "difficulty", "");
    }

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store,
                            String combatSubTab, String mainTab) {
        open(plugin, playerRef, store, combatSubTab, mainTab, "");
    }

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store,
                            String combatSubTab, String mainTab, String craftingSearch) {

        ConfigSaveManager saveManager = new ConfigSaveManager(plugin);

        // Resolve player locale once for all tabs
        String locale = I18n.resolveLocale(playerRef);

        // Create all tab builders
        DifficultyTab difficultyTab = new DifficultyTab(plugin, saveManager);
        ScalingTab scalingTab = new ScalingTab(plugin, saveManager, combatSubTab);
        IntegrationTab integrationTab = new IntegrationTab(plugin, saveManager);
        WeaponsTab weaponsTab = new WeaponsTab(plugin, saveManager);
        ArmorTab armorTab = new ArmorTab(plugin, saveManager);
        CraftingTab craftingTab = new CraftingTab(plugin, saveManager, craftingSearch);
        MiscTab miscTab = new MiscTab(plugin, saveManager);

        // Build full page HTML — tab names are localized
        String tabData = String.join(",",
                "difficulty:" + I18n.getFor(locale, "ui.tab.difficulty") + ":difficulty-content",
                "scaling:" + I18n.getFor(locale, "ui.tab.scaling") + ":scaling-content",
                "integration:" + I18n.getFor(locale, "ui.tab.integration") + ":integration-content",
                "weapons:" + I18n.getFor(locale, "ui.tab.weapons") + ":weapons-content",
                "armor:" + I18n.getFor(locale, "ui.tab.armor") + ":armor-content",
                "crafting:" + I18n.getFor(locale, "ui.tab.crafting") + ":crafting-content",
                "misc:" + I18n.getFor(locale, "ui.tab.misc") + ":misc-content");

        String html = ConfigStyles.CSS + String.format("""
                <div class="page-overlay" style="layout-mode: center; vertical-align: middle; horizontal-align: center;">
                    <div class="decorated-container config-container" data-hyui-title="EndgameQoL Configuration" style="anchor-height: 820;">
                        <div class="container-contents" style="layout-mode: top; padding: 10;">

                            <nav id="config-tabs" class="tabs"
                                 data-tabs="%s"
                                 data-selected="%s"
                                 style="anchor-height: 40; margin-bottom: 8;">
                            </nav>

                            <div id="difficulty-content" class="tab-content" data-hyui-tab-id="difficulty" data-hyui-tab-nav="config-tabs">
                                %s
                            </div>

                            <div id="scaling-content" class="tab-content" data-hyui-tab-id="scaling" data-hyui-tab-nav="config-tabs" style="layout-mode: top;">
                                %s
                            </div>

                            <div id="integration-content" class="tab-content" data-hyui-tab-id="integration" data-hyui-tab-nav="config-tabs" style="layout-mode: TopScrolling; anchor-height: 690;">
                                %s
                            </div>

                            <div id="weapons-content" class="tab-content" data-hyui-tab-id="weapons" data-hyui-tab-nav="config-tabs" style="layout-mode: TopScrolling; anchor-height: 690;">
                                %s
                            </div>

                            <div id="armor-content" class="tab-content" data-hyui-tab-id="armor" data-hyui-tab-nav="config-tabs" style="layout-mode: TopScrolling; anchor-height: 690;">
                                %s
                            </div>

                            <div id="crafting-content" class="tab-content" data-hyui-tab-id="crafting" data-hyui-tab-nav="config-tabs" style="layout-mode: TopScrolling; anchor-height: 690;">
                                %s
                            </div>

                            <div id="misc-content" class="tab-content" data-hyui-tab-id="misc" data-hyui-tab-nav="config-tabs" style="layout-mode: TopScrolling; anchor-height: 690;">
                                %s
                            </div>

                        </div>
                    </div>
                </div>
                """,
                tabData,
                mainTab,
                difficultyTab.buildHtml(locale),
                scalingTab.buildHtml(locale),
                integrationTab.buildHtml(locale),
                weaponsTab.buildHtml(locale),
                armorTab.buildHtml(locale),
                craftingTab.buildHtml(locale),
                miscTab.buildHtml(locale));

        // Build page and register all tab listeners
        var builder = PageBuilder.pageForPlayer(playerRef)
                .fromHtml(html);

        difficultyTab.registerListeners(builder, playerRef, store);
        scalingTab.registerListeners(builder, playerRef, store);
        integrationTab.registerListeners(builder, playerRef, store);
        weaponsTab.registerListeners(builder, playerRef, store);
        armorTab.registerListeners(builder, playerRef, store);
        craftingTab.registerListeners(builder, playerRef, store);
        miscTab.registerListeners(builder, playerRef, store);

        builder.open(store);
    }
}
