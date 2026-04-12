package endgame.plugin.ui.nativeconfig;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BossConfig;
import endgame.plugin.config.Difficulty;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.config.RecipeOverrideConfig;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;

/**
 * Native .ui config page replacing HyUI-based /egconfig.
 * All 7 tabs defined statically in EndgameConfigPage.ui with visibility toggling.
 * Uses TextButton Activating events for all controls (no DropdownBox/CheckBox/NumberField).
 */
public class NativeConfigPage extends InteractiveCustomUIPage<NativeConfigPage.ConfigEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.NativeConfig");
    private static final String PAGE_FILE = "Pages/EndgameConfigPage.ui";

    private static final String[] TAB_IDS = {"Difficulty", "Scaling", "Weapons", "Armor", "Crafting", "Misc", "Integration"};
    private static final String[] TAB_BTN_IDS = {"#TabDifficulty", "#TabScaling", "#TabWeapons", "#TabArmor", "#TabCrafting", "#TabMisc", "#TabIntegration"};

    // Preset button IDs
    private static final String[] PRESET_BTN_IDS = {"#PresetEasy", "#PresetMedium", "#PresetHard", "#PresetExtreme", "#PresetCustom"};
    private static final String[] PRESET_NAMES = {"EASY", "MEDIUM", "HARD", "EXTREME", "CUSTOM"};

    // Boss config IDs for scaling tab
    private static final String[] BOSS_IDS = {"DragonFrost", "Hedera", "GolemVoid"};
    private static final BossType[] BOSS_TYPES = {BossType.DRAGON_FROST, BossType.HEDERA, BossType.GOLEM_VOID};

    private static final int RECIPES_PER_PAGE = 10;
    private static final int MAX_INPUTS = 6;
    private static final String[] CAT_IDS = {"All", "Portals", "Weapons", "Armor", "Other"};
    private static final String[] CAT_BTN_IDS = {"#CatAll", "#CatPortals", "#CatWeapons", "#CatArmor", "#CatOther"};

    private final EndgameQoL plugin;
    private String activeTab = "Difficulty";

    // Global search state
    private String globalSearchText = "";
    private boolean globalSearchActive = false;
    private static final int MAX_SEARCH_RESULTS = 15;

    // Recipe override state
    private String craftCategory = "All";
    private int craftPage = 0;
    private String selectedRecipeId = null;
    private String recipeSearchFilter = "";
    private java.util.List<String> filteredRecipeIds = new java.util.ArrayList<>();

    // Searchable settings registry: name, tab, toggle action, value getter, extra keywords
    private record SearchEntry(String name, String tab, String toggleAction,
                               java.util.function.Function<EndgameConfig, String> valueGetter, String keywords) {
        SearchEntry(String name, String tab, String toggleAction, java.util.function.Function<EndgameConfig, String> valueGetter) {
            this(name, tab, toggleAction, valueGetter, "");
        }
    }
    private static final SearchEntry[] SEARCH_REGISTRY = {
        new SearchEntry("Difficulty Bosses", "Difficulty", "toggle:bosses", c -> c.isDifficultyAffectsBosses() ? "ON" : "OFF", "boss scope"),
        new SearchEntry("Difficulty Mobs", "Difficulty", "toggle:mobs", c -> c.isDifficultyAffectsMobs() ? "ON" : "OFF", "mob scope"),
        new SearchEntry("Prisma Armor Vulnerability", "Scaling", "toggle:prismaArmor", c -> c.isPrismaArmorVulnerabilityEnabled() ? "ON" : "OFF", "prisma balance"),
        new SearchEntry("Prisma Weapon Boss Block", "Scaling", "toggle:prismaWeapon", c -> c.isPrismaWeaponBossBlockEnabled() ? "ON" : "OFF", "prisma balance"),
        new SearchEntry("Hedera Poison", "Weapons", "toggle:hederaPoison", c -> c.isEnableHederaDaggerPoison() ? "ON" : "OFF", "dagger hedera"),
        new SearchEntry("Hedera Poison DMG", "Weapons", "toggle:hederaPoison", c -> String.format("%.1f", c.getHederaDaggerPoisonDamage()), "dagger hedera damage"),
        new SearchEntry("Hedera Poison Ticks", "Weapons", "toggle:hederaPoison", c -> c.getHederaDaggerPoisonTicks() + " ticks", "dagger hedera"),
        new SearchEntry("Hedera Lifesteal", "Weapons", "toggle:hederaLifesteal", c -> c.isEnableHederaDaggerLifesteal() ? "ON" : "OFF", "dagger hedera"),
        new SearchEntry("Hedera Lifesteal %", "Weapons", "toggle:hederaLifesteal", c -> Math.round(c.getHederaDaggerLifestealPercent() * 100) + "%", "dagger hedera percent"),
        new SearchEntry("Blazefist Burn", "Weapons", "toggle:blazefist", c -> c.isBlazefistBurnEnabled() ? "ON" : "OFF", "fire accessory"),
        new SearchEntry("Blazefist Burn DMG", "Weapons", "toggle:blazefist", c -> String.format("%.0f", c.getBlazefistBurnDamage()), "fire accessory damage"),
        new SearchEntry("Blazefist Burn Ticks", "Weapons", "toggle:blazefist", c -> c.getBlazefistBurnTicks() + " ticks", "fire accessory"),
        new SearchEntry("Mana Regen Armor", "Armor", "toggle:manaRegen", c -> c.isManaRegenArmorEnabled() ? "ON" : "OFF", "mana regeneration"),
        new SearchEntry("HP Regen Armor", "Armor", "toggle:hpRegen", c -> c.isArmorHPRegenEnabled() ? "ON" : "OFF", "health regeneration"),
        new SearchEntry("PvP Enabled", "Misc", "toggle:pvp", c -> c.isPvpEnabled() ? "ON" : "OFF", "player versus player combat"),
        new SearchEntry("Dungeon Block Protection", "Misc", "toggle:dungeonProt", c -> c.isEnableDungeonBlockProtection() ? "ON" : "OFF", "grief protection"),
        new SearchEntry("Combo Meter", "Misc", "toggle:combo", c -> c.isComboEnabled() ? "ON" : "OFF", "combo kill streak"),
        new SearchEntry("Combo Tier Effects", "Misc", "toggle:comboTierEff", c -> c.isComboTierEffectsEnabled() ? "ON" : "OFF", "combo buff"),
        new SearchEntry("Combo Decay", "Misc", "toggle:comboDecay", c -> c.isComboDecayEnabled() ? "ON" : "OFF", "combo timer"),
        new SearchEntry("Combo Timer", "Misc", "toggle:combo", c -> String.format("%.0fs", c.getComboTimerSeconds()), "combo duration seconds"),
        new SearchEntry("Bounty Board", "Misc", "toggle:bounty", c -> c.isBountyEnabled() ? "ON" : "OFF", "bounty quest mission"),
        new SearchEntry("Bounty Streak", "Misc", "toggle:bountyStreak", c -> c.isBountyStreakEnabled() ? "ON" : "OFF", "bounty bonus"),
        new SearchEntry("Bounty Weekly", "Misc", "toggle:bountyWeekly", c -> c.isBountyWeeklyEnabled() ? "ON" : "OFF", "bounty weekly"),
        new SearchEntry("Bounty Refresh", "Misc", "toggle:bounty", c -> c.getBountyRefreshHours() + "h", "bounty hours cooldown"),
        new SearchEntry("Warden Trials", "Misc", "toggle:warden", c -> c.isWardenTrialEnabled() ? "ON" : "OFF", "warden trial challenge"),
        new SearchEntry("Vorthak Merchant", "Misc", "toggle:vorthak", c -> c.isVorthakEnabled() ? "ON" : "OFF", "shop trade npc"),
        new SearchEntry("Temporal Portals", "Misc", "toggle:temporalPortal", c -> c.getTemporalPortalConfig().isEnabled() ? "ON" : "OFF", "temporal portal random dungeon"),
        new SearchEntry("Respawn Inside Instance", "Misc", "toggle:respawnInside", c -> c.getTemporalPortalConfig().getDungeons().values().stream().anyMatch(endgame.plugin.systems.portal.DungeonDefinition::isAllowRespawnInside) ? "ON" : "OFF", "dungeon respawn death instance"),
        new SearchEntry("Pet System", "Misc", "toggle:pets", c -> c.pets().isEnabled() ? "ON" : "OFF", "pet companion system"),
        new SearchEntry("Portal Spawn Interval", "Misc", "field:portalSpawnMin", c -> c.getTemporalPortalConfig().getSpawnIntervalMinSeconds() + "-" + c.getTemporalPortalConfig().getSpawnIntervalMaxSeconds() + "s", "temporal portal spawn timer"),
        new SearchEntry("Max Portals", "Misc", "field:maxPortals", c -> String.valueOf(c.getTemporalPortalConfig().getMaxConcurrentPortals()), "temporal portal max concurrent"),
        new SearchEntry("RPG Leveling", "Integration", "toggle:rpgLeveling", c -> c.isRPGLevelingEnabled() ? "ON" : "OFF", "xp level mod"),
        new SearchEntry("Endless Leveling", "Integration", "toggle:endlessLeveling", c -> c.isEndlessLevelingEnabled() ? "ON" : "OFF", "xp level mod"),
        new SearchEntry("OrbisGuard", "Integration", "toggle:orbisGuard", c -> c.isOrbisGuardEnabled() ? "ON" : "OFF", "claim protection mod"),
    };

    public NativeConfigPage(@Nonnull PlayerRef playerRef, @Nonnull EndgameQoL plugin) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ConfigEventData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(PAGE_FILE);

        // Bind all tab buttons
        for (int i = 0; i < TAB_IDS.length; i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, TAB_BTN_IDS[i],
                    EventData.of("Action", "tab:" + TAB_IDS[i]), false);
        }

        populateAllTabs(cmd, events);
    }

    /**
     * Refresh the page content without resetting scroll position.
     * Uses sendUpdate() instead of rebuild() to preserve the current scroll state.
     */
    private void refreshPage() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        // Re-bind tab buttons (events are cleared on sendUpdate)
        for (int i = 0; i < TAB_IDS.length; i++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, TAB_BTN_IDS[i],
                    EventData.of("Action", "tab:" + TAB_IDS[i]), false);
        }
        populateAllTabs(cmd, events);
        this.sendUpdate(cmd, events, false);
    }

    private void populateAllTabs(UICommandBuilder cmd, UIEventBuilder events) {
        EndgameConfig config = plugin.getConfig().get();

        // === Global search ===
        cmd.set("#GlobalSearch.Value", globalSearchText);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#GlobalSearch",
                EventData.of("Action", "gsearch:input").append("@SearchInput", "#GlobalSearch.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Validating, "#GlobalSearch",
                EventData.of("Action", "gsearch:apply").append("@SearchInput", "#GlobalSearch.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#GlobalSearchBtn",
                EventData.of("Action", "gsearch:apply"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#GlobalSearchClear",
                EventData.of("Action", "gsearch:clear"), false);

        cmd.set("#SearchResults.Visible", globalSearchActive);
        if (globalSearchActive) {
            populateSearchResults(cmd, events, config);
            // Hide all tabs when search is active
            for (String tabId : TAB_IDS) cmd.set("#Content" + tabId + ".Visible", false);
            return;
        }

        // === Tab visibility ===
        for (String tabId : TAB_IDS) {
            cmd.set("#Content" + tabId + ".Visible", tabId.equals(activeTab));
        }
        // Tab button styles — set individual properties (can't set full Style object via cmd.set)
        for (int i = 0; i < TAB_IDS.length; i++) {
            boolean active = TAB_IDS[i].equals(activeTab);
            cmd.set(TAB_BTN_IDS[i] + ".Style.Default.Background", active ? "#1a2633" : "#0a1119");
            cmd.set(TAB_BTN_IDS[i] + ".Style.Default.LabelStyle.TextColor", active ? "#ffffff" : "#96a9be");
            cmd.set(TAB_BTN_IDS[i] + ".Style.Default.LabelStyle.RenderBold", active);
        }

        // === DIFFICULTY ===
        populateDifficulty(cmd, events, config);
        // === SCALING ===
        populateScaling(cmd, events, config);
        // === WEAPONS ===
        populateWeapons(cmd, events, config);
        // === ARMOR ===
        populateArmor(cmd, events, config);
        // === CRAFTING ===
        populateCrafting(cmd, events, config);
        // === MISC ===
        populateMisc(cmd, events, config);
        // === INTEGRATION ===
        populateIntegration(cmd, events, config);
    }

    // ==================== GLOBAL SEARCH RESULTS ====================

    private void populateSearchResults(UICommandBuilder cmd, UIEventBuilder events, EndgameConfig config) {
        String[] searchWords = globalSearchText.toLowerCase().split("\\s+");
        java.util.List<SearchEntry> matches = new java.util.ArrayList<>();
        for (SearchEntry entry : SEARCH_REGISTRY) {
            String haystack = (entry.name + " " + entry.tab + " " + entry.keywords).toLowerCase();
            boolean found = false;
            for (String word : searchWords) {
                if (!word.isEmpty() && haystack.contains(word)) { found = true; break; }
            }
            if (found) {
                matches.add(entry);
                if (matches.size() >= MAX_SEARCH_RESULTS) break;
            }
        }

        cmd.set("#SearchResultsCount.Text", matches.size() + " result" + (matches.size() != 1 ? "s" : ""));

        for (int i = 0; i < MAX_SEARCH_RESULTS; i++) {
            String prefix = "#SR" + i;
            if (i < matches.size()) {
                SearchEntry entry = matches.get(i);
                String value = entry.valueGetter.apply(config);
                cmd.set(prefix + ".Visible", true);
                cmd.set(prefix + "Tab.Text", entry.tab);
                cmd.set(prefix + "Name.Text", entry.name);
                cmd.set(prefix + "Val.Text", value);
                boolean isToggle = "ON".equals(value) || "OFF".equals(value);
                cmd.set(prefix + "Val.Style.TextColor", isToggle ? ("ON".equals(value) ? "#4aff7f" : "#ff4a4a") : "#6fe3ff");
                cmd.set(prefix + "Btn.Text", isToggle ? "Toggle" : "Go to");
                String btnAction = isToggle ? entry.toggleAction : "tab:" + entry.tab;
                events.addEventBinding(CustomUIEventBindingType.Activating, prefix + "Btn",
                        EventData.of("Action", btnAction), false);
            } else {
                cmd.set(prefix + ".Visible", false);
            }
        }
    }

    // ==================== DIFFICULTY ====================

    private void populateDifficulty(UICommandBuilder cmd, UIEventBuilder events, EndgameConfig config) {
        Difficulty diff = config.getDifficulty();

        // Highlight active preset
        for (int i = 0; i < PRESET_BTN_IDS.length; i++) {
            boolean active = PRESET_NAMES[i].equals(diff.name());
            cmd.set(PRESET_BTN_IDS[i] + ".Style.Default.Background", active ? "#1a3d2e" : "#2b3542");
            cmd.set(PRESET_BTN_IDS[i] + ".Style.Default.LabelStyle.TextColor", active ? "#4aff7f" : "#96a9be");
            events.addEventBinding(CustomUIEventBindingType.Activating, PRESET_BTN_IDS[i],
                    EventData.of("Action", "preset:" + PRESET_NAMES[i]), false);
        }

        // Custom box
        cmd.set("#CustomBox.Visible", diff == Difficulty.CUSTOM);
        cmd.set("#HealthValue.Value", String.format("x%.2f", config.getCustomHealthMultiplier()));
        cmd.set("#DamageValue.Value", String.format("x%.2f", config.getCustomDamageMultiplier()));

        // Scope — toggle buttons show ON/OFF directly
        setToggleValue(cmd, "#ToggleBosses", config.isDifficultyAffectsBosses());
        setToggleValue(cmd, "#ToggleMobs", config.isDifficultyAffectsMobs());

        // Events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleBosses", EventData.of("Action", "toggle:bosses"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleMobs", EventData.of("Action", "toggle:mobs"), false);
        bindNumField(events, "#HealthValue", "healthMult");
        bindAdjust(events, "#HealthDown", "adjust:healthDown");
        bindAdjust(events, "#HealthUp", "adjust:healthUp");
        bindNumField(events, "#DamageValue", "damageMult");
        bindAdjust(events, "#DamageDown", "adjust:damageDown");
        bindAdjust(events, "#DamageUp", "adjust:damageUp");
    }

    // ==================== SCALING ====================

    private void populateScaling(UICommandBuilder cmd, UIEventBuilder events, EndgameConfig config) {
        // Prisma toggles — target toggle buttons directly
        setToggleValue(cmd, "#TogglePrismaArmor", config.isPrismaArmorVulnerabilityEnabled());
        setToggleValue(cmd, "#TogglePrismaWeapon", config.isPrismaWeaponBossBlockEnabled());
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TogglePrismaArmor", EventData.of("Action", "toggle:prismaArmor"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TogglePrismaWeapon", EventData.of("Action", "toggle:prismaWeapon"), false);

        // Per-boss cards
        for (int i = 0; i < BOSS_IDS.length; i++) {
            String id = BOSS_IDS[i];
            BossConfig bc = config.getBossConfig(BOSS_TYPES[i]);
            cmd.set("#" + id + "HP.Value", String.valueOf(bc.getHealthOverrideRaw()));
            cmd.set("#" + id + "DMG.Value", String.format("x%.2f", bc.getDamageMultiplierRaw()));
            cmd.set("#" + id + "Scale.Value", String.valueOf(bc.getPlayerScaling()));

            bindNumField(events, "#" + id + "HP", "boss:" + id + ":hp");
            bindAdjust(events, "#" + id + "HPDown", "boss:" + id + ":hpDown");
            bindAdjust(events, "#" + id + "HPUp", "boss:" + id + ":hpUp");
            bindNumField(events, "#" + id + "DMG", "boss:" + id + ":dmg");
            bindAdjust(events, "#" + id + "DMGDown", "boss:" + id + ":dmgDown");
            bindAdjust(events, "#" + id + "DMGUp", "boss:" + id + ":dmgUp");
            bindNumField(events, "#" + id + "Scale", "boss:" + id + ":scale");
            bindAdjust(events, "#" + id + "ScaleDown", "boss:" + id + ":scaleDown");
            bindAdjust(events, "#" + id + "ScaleUp", "boss:" + id + ":scaleUp");
        }

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyScaling", EventData.of("Action", "apply:scaling"), false);
    }

    // ==================== WEAPONS ====================

    private void populateWeapons(UICommandBuilder cmd, UIEventBuilder events, EndgameConfig config) {
        // Hedera — toggle buttons show ON/OFF directly
        setToggleValue(cmd, "#ToggleHederaPoison", config.isEnableHederaDaggerPoison());
        cmd.set("#HederaPoisonDmg.Value", String.format("%.1f", config.getHederaDaggerPoisonDamage()));
        cmd.set("#HederaPoisonTicks.Value", String.valueOf(config.getHederaDaggerPoisonTicks()));
        setToggleValue(cmd, "#ToggleHederaLifesteal", config.isEnableHederaDaggerLifesteal());
        cmd.set("#HederaLifestealPct.Value", String.valueOf(Math.round(config.getHederaDaggerLifestealPercent() * 100)));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleHederaPoison", EventData.of("Action", "toggle:hederaPoison"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleHederaLifesteal", EventData.of("Action", "toggle:hederaLifesteal"), false);
        bindNumField(events, "#HederaPoisonDmg", "hederaPoisonDmg");
        bindAdjust(events, "#HederaPoisonDmgDown", "adjust:hederaPoisonDmgDown");
        bindAdjust(events, "#HederaPoisonDmgUp", "adjust:hederaPoisonDmgUp");
        bindNumField(events, "#HederaPoisonTicks", "hederaPoisonTicks");
        bindAdjust(events, "#HederaPoisonTicksDown", "adjust:hederaPoisonTicksDown");
        bindAdjust(events, "#HederaPoisonTicksUp", "adjust:hederaPoisonTicksUp");
        bindNumField(events, "#HederaLifestealPct", "hederaLifesteal");
        bindAdjust(events, "#HederaLifestealPctDown", "adjust:hederaLifestealDown");
        bindAdjust(events, "#HederaLifestealPctUp", "adjust:hederaLifestealUp");

        // Blazefist
        setToggleValue(cmd, "#ToggleBlazefist", config.isBlazefistBurnEnabled());
        cmd.set("#BlazefistDmg.Value", String.format("%.0f", config.getBlazefistBurnDamage()));
        cmd.set("#BlazefistTicks.Value", String.valueOf(config.getBlazefistBurnTicks()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleBlazefist", EventData.of("Action", "toggle:blazefist"), false);
        bindNumField(events, "#BlazefistDmg", "blazefistDmg");
        bindAdjust(events, "#BlazefistDmgDown", "adjust:blazefistDmgDown");
        bindAdjust(events, "#BlazefistDmgUp", "adjust:blazefistDmgUp");
        bindNumField(events, "#BlazefistTicks", "blazefistTicks");
        bindAdjust(events, "#BlazefistTicksDown", "adjust:blazefistTicksDown");
        bindAdjust(events, "#BlazefistTicksUp", "adjust:blazefistTicksUp");

    }

    // ==================== ARMOR ====================

    private void populateArmor(UICommandBuilder cmd, UIEventBuilder events, EndgameConfig config) {
        // Mana Regen
        setToggleValue(cmd, "#ToggleManaRegen", config.isManaRegenArmorEnabled());
        cmd.set("#ManaRegenMithril.Value", String.format("%.2f", config.getManaRegenMithrilPerPiece()));
        cmd.set("#ManaRegenOnyxium.Value", String.format("%.2f", config.getManaRegenOnyxiumPerPiece()));
        cmd.set("#ManaRegenPrisma.Value", String.format("%.2f", config.getManaRegenPrismaPerPiece()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleManaRegen", EventData.of("Action", "toggle:manaRegen"), false);
        bindNumField(events, "#ManaRegenMithril", "manaRegenMithril");
        bindAdjust(events, "#ManaRegenMithrilDown", "adjust:manaRegenMithrilDown");
        bindAdjust(events, "#ManaRegenMithrilUp", "adjust:manaRegenMithrilUp");
        bindNumField(events, "#ManaRegenOnyxium", "manaRegenOnyxium");
        bindAdjust(events, "#ManaRegenOnyxiumDown", "adjust:manaRegenOnyxiumDown");
        bindAdjust(events, "#ManaRegenOnyxiumUp", "adjust:manaRegenOnyxiumUp");
        bindNumField(events, "#ManaRegenPrisma", "manaRegenPrisma");
        bindAdjust(events, "#ManaRegenPrismaDown", "adjust:manaRegenPrismaDown");
        bindAdjust(events, "#ManaRegenPrismaUp", "adjust:manaRegenPrismaUp");

        // HP Regen
        setToggleValue(cmd, "#ToggleHPRegen", config.isArmorHPRegenEnabled());
        cmd.set("#HPRegenDelay.Value", String.format("%.0f", config.getArmorHPRegenDelaySec()));
        cmd.set("#HPRegenOnyxium.Value", String.format("%.2f", config.getArmorHPRegenOnyxiumPerPiece()));
        cmd.set("#HPRegenPrisma.Value", String.format("%.2f", config.getArmorHPRegenPrismaPerPiece()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleHPRegen", EventData.of("Action", "toggle:hpRegen"), false);
        bindNumField(events, "#HPRegenDelay", "hpRegenDelay");
        bindAdjust(events, "#HPRegenDelayDown", "adjust:hpRegenDelayDown");
        bindAdjust(events, "#HPRegenDelayUp", "adjust:hpRegenDelayUp");
        bindNumField(events, "#HPRegenOnyxium", "hpRegenOnyxium");
        bindAdjust(events, "#HPRegenOnyxiumDown", "adjust:hpRegenOnyxiumDown");
        bindAdjust(events, "#HPRegenOnyxiumUp", "adjust:hpRegenOnyxiumUp");
        bindNumField(events, "#HPRegenPrisma", "hpRegenPrisma");
        bindAdjust(events, "#HPRegenPrismaDown", "adjust:hpRegenPrismaDown");
        bindAdjust(events, "#HPRegenPrismaUp", "adjust:hpRegenPrismaUp");
    }

    // ==================== CRAFTING ====================

    private void populateCrafting(UICommandBuilder cmd, UIEventBuilder events, EndgameConfig config) {
        // Simple category toggles
        // Portal Hedera Dungeon and Portal Golem Void removed from UI (not vanilla portal keys)

        // Portal Hedera Dungeon and Portal Golem Void removed from UI

        // Recipe search bar — ValueChanged stores text, button applies filter
        cmd.set("#RecipeSearch.Value", recipeSearchFilter);
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RecipeSearch",
                EventData.of("Action", "craft:searchInput").append("@SearchInput", "#RecipeSearch.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Validating, "#RecipeSearch",
                EventData.of("Action", "craft:search"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeSearchBtn",
                EventData.of("Action", "craft:search"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RecipeSearchClear",
                EventData.of("Action", "craft:clearSearch"), false);

        // Recipe override editor
        populateRecipeList(cmd, events);
        populateRecipeDetail(cmd, events);

        // Category buttons
        for (int i = 0; i < CAT_IDS.length; i++) {
            boolean active = CAT_IDS[i].equals(craftCategory);
            cmd.set(CAT_BTN_IDS[i] + ".Style.Default.Background", active ? "#1a3d2e" : "#2b3542");
            cmd.set(CAT_BTN_IDS[i] + ".Style.Default.LabelStyle.TextColor", active ? "#4aff7f" : "#96a9be");
            events.addEventBinding(CustomUIEventBindingType.Activating, CAT_BTN_IDS[i],
                    EventData.of("Action", "craft:cat:" + CAT_IDS[i]), false);
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CraftPrev", EventData.of("Action", "craft:prev"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CraftNext", EventData.of("Action", "craft:next"), false);
    }

    private void populateRecipeList(UICommandBuilder cmd, UIEventBuilder events) {
        var overrides = plugin.getRecipeOverrideConfig().get().getRecipeOverrides();
        filteredRecipeIds = new java.util.ArrayList<>();
        String searchLower = recipeSearchFilter.toLowerCase();
        for (var entry : overrides.entrySet()) {
            if (matchesCategory(entry.getKey(), entry.getValue(), craftCategory)
                    && matchesSearch(entry.getKey(), entry.getValue(), searchLower)) {
                filteredRecipeIds.add(entry.getKey());
            }
        }

        int totalPages = Math.max(1, (filteredRecipeIds.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE);
        if (craftPage >= totalPages) craftPage = totalPages - 1;
        cmd.set("#CraftPageLabel.Text", (craftPage + 1) + " / " + totalPages);

        // Show list or detail
        cmd.set("#CraftList.Visible", selectedRecipeId == null);
        cmd.set("#CraftDetail.Visible", selectedRecipeId != null);

        int start = craftPage * RECIPES_PER_PAGE;
        for (int i = 0; i < RECIPES_PER_PAGE; i++) {
            int idx = start + i;
            String rowPrefix = "#Row" + i;
            if (idx < filteredRecipeIds.size()) {
                String id = filteredRecipeIds.get(idx);
                var entry = overrides.get(id);
                cmd.set(rowPrefix + ".Visible", true);
                cmd.set(rowPrefix + "Name.Text", formatRecipeName(id));
                setToggleValue(cmd, rowPrefix + "Toggle", entry.isEnabled(), true);
                events.addEventBinding(CustomUIEventBindingType.Activating, rowPrefix + "Toggle",
                        EventData.of("Action", "craft:toggle:" + idx), false);
                events.addEventBinding(CustomUIEventBindingType.Activating, rowPrefix + "Edit",
                        EventData.of("Action", "craft:edit:" + idx), false);
            } else {
                cmd.set(rowPrefix + ".Visible", false);
            }
        }
    }

    private void populateRecipeDetail(UICommandBuilder cmd, UIEventBuilder events) {
        if (selectedRecipeId == null) return;
        var overrides = plugin.getRecipeOverrideConfig().get().getRecipeOverrides();
        var entry = overrides.get(selectedRecipeId);
        if (entry == null) { selectedRecipeId = null; return; }

        cmd.set("#DetailName.Text", formatRecipeName(selectedRecipeId));
        setToggleValue(cmd, "#DetailToggle", entry.isEnabled(), true);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DetailToggle",
                EventData.of("Action", "craft:detail:toggle"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DetailBack",
                EventData.of("Action", "craft:back"), false);

        // Output — TextField for item name
        String outputVal = entry.getOutput() != null ? entry.getOutput() : selectedRecipeId;
        cmd.set("#DetailOutputField.Value", outputVal);
        cmd.set("#DetailOutputQty.Text", "x" + entry.getOutputQuantity());
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DetailOutputField",
                EventData.of("@OutputItem", "#DetailOutputField.Value"), false);
        bindAdjust(events, "#DetailOutQtyDown", "craft:detail:outQtyDown");
        bindAdjust(events, "#DetailOutQtyUp", "craft:detail:outQtyUp");

        // Inputs — TextField for each ingredient name
        RecipeOverrideConfig.Ingredient[] inputs = entry.getInputs();
        for (int i = 0; i < MAX_INPUTS; i++) {
            String prefix = "#DetailIn" + i;
            if (inputs != null && i < inputs.length) {
                cmd.set(prefix + ".Visible", true);
                cmd.set(prefix + "Field.Value", inputs[i].getItem() != null ? inputs[i].getItem() : "");
                cmd.set(prefix + "Qty.Text", String.valueOf(inputs[i].getQuantity()));
                events.addEventBinding(CustomUIEventBindingType.ValueChanged, prefix + "Field",
                        EventData.of("@In" + i + "Item", prefix + "Field.Value"), false);
                bindAdjust(events, prefix + "Down", "craft:detail:in" + i + "Down");
                bindAdjust(events, prefix + "Up", "craft:detail:in" + i + "Up");
            } else {
                cmd.set(prefix + ".Visible", false);
            }
        }

        // Bench — TextField for bench ID
        if (entry.getBenchId() != null) {
            cmd.set("#DetailBenchRow.Visible", true);
            cmd.set("#DetailBenchField.Value", entry.getBenchId());
            cmd.set("#DetailBenchTier.Text", "T" + entry.getBenchTier());
            events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DetailBenchField",
                    EventData.of("@BenchId", "#DetailBenchField.Value"), false);
            bindAdjust(events, "#DetailTierDown", "craft:detail:tierDown");
            bindAdjust(events, "#DetailTierUp", "craft:detail:tierUp");
        } else {
            cmd.set("#DetailBenchRow.Visible", false);
        }

        // Craft Time
        cmd.set("#DetailCraftTime.Text", String.format("%.1fs", entry.getCraftTime()));
        bindAdjust(events, "#DetailTimeDown", "craft:detail:timeDown");
        bindAdjust(events, "#DetailTimeUp", "craft:detail:timeUp");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#DetailSave",
                EventData.of("Action", "craft:detail:save"), false);
    }

    private boolean matchesCategory(String id, RecipeOverrideConfig.RecipeEntry entry, String category) {
        if ("All".equals(category)) return true;
        String lower = id.toLowerCase();
        String output = entry.getOutput() != null ? entry.getOutput().toLowerCase() : lower;
        return switch (category) {
            case "Portals" -> lower.contains("portal") || lower.contains("key") || lower.contains("challenge")
                    || lower.contains("warden") || output.contains("portal") || output.contains("challenge");
            case "Weapons" -> lower.contains("weapon") || lower.contains("sword") || lower.contains("dagger")
                    || lower.contains("staff") || lower.contains("spear") || lower.contains("mace")
                    || lower.contains("shield") || lower.contains("shortbow") || lower.contains("longsword")
                    || lower.contains("battleaxe") || lower.contains("pickaxe");
            case "Armor" -> lower.contains("armor");
            default -> {
                // "Other" = doesn't match any specific category
                boolean isPortal = lower.contains("portal") || lower.contains("key") || lower.contains("challenge") || lower.contains("warden");
                boolean isWeapon = lower.contains("weapon") || lower.contains("sword") || lower.contains("dagger")
                        || lower.contains("staff") || lower.contains("spear") || lower.contains("mace")
                        || lower.contains("shield") || lower.contains("shortbow") || lower.contains("longsword")
                        || lower.contains("battleaxe") || lower.contains("pickaxe");
                boolean isArmor = lower.contains("armor");
                yield !isPortal && !isWeapon && !isArmor;
            }
        };
    }

    private static boolean matchesSearch(String id, RecipeOverrideConfig.RecipeEntry entry, String lowerFilter) {
        if (lowerFilter.isEmpty()) return true;
        if (formatRecipeName(id).toLowerCase().contains(lowerFilter)) return true;
        if (entry.getOutput() != null && entry.getOutput().toLowerCase().contains(lowerFilter)) return true;
        if (entry.getInputs() != null) {
            for (var input : entry.getInputs()) {
                if (input.getItem() != null && input.getItem().toLowerCase().contains(lowerFilter)) return true;
            }
        }
        return false;
    }

    private static String formatRecipeName(String id) {
        return id.replace("_Recipe_Generated_0", "").replace("_Generated_0", "")
                .replace("Endgame_", "").replace("Weapon_", "").replace("Armor_", "")
                .replace("Tool_", "").replace("Ingredient_", "").replace('_', ' ');
    }

    private static String cleanName(String s) {
        return s != null ? s.replace('_', ' ') : "";
    }

    // ==================== MISC ====================

    private void populateMisc(UICommandBuilder cmd, UIEventBuilder events, EndgameConfig config) {
        setToggleValue(cmd, "#TogglePvp", config.isPvpEnabled());
        setToggleValue(cmd, "#ToggleDungeonProt", config.isEnableDungeonBlockProtection());
        setToggleValue(cmd, "#ToggleCombo", config.isComboEnabled());
        setToggleValue(cmd, "#ToggleComboTierEff", config.isComboTierEffectsEnabled());
        setToggleValue(cmd, "#ToggleComboDecay", config.isComboDecayEnabled());
        cmd.set("#ComboTimer.Value", String.format("%.0f", config.getComboTimerSeconds()));
        setToggleValue(cmd, "#ToggleBounty", config.isBountyEnabled());
        setToggleValue(cmd, "#ToggleBountyStreak", config.isBountyStreakEnabled());
        setToggleValue(cmd, "#ToggleBountyWeekly", config.isBountyWeeklyEnabled());
        cmd.set("#BountyRefresh.Value", String.valueOf(config.getBountyRefreshHours()));
        setToggleValue(cmd, "#ToggleWarden", config.isWardenTrialEnabled());
        setToggleValue(cmd, "#ToggleVorthak", config.isVorthakEnabled(), true);
        setToggleValue(cmd, "#ToggleTemporalPortal", config.getTemporalPortalConfig().isEnabled());
        boolean anyRespawnInside = config.getTemporalPortalConfig().getDungeons().values().stream()
                .anyMatch(endgame.plugin.systems.portal.DungeonDefinition::isAllowRespawnInside);
        setToggleValue(cmd, "#ToggleRespawnInside", anyRespawnInside);
        setToggleValue(cmd, "#TogglePets", config.pets().isEnabled());
        cmd.set("#MaxPortals.Value", String.valueOf(config.getTemporalPortalConfig().getMaxConcurrentPortals()));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#TogglePvp", EventData.of("Action", "toggle:pvp"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleDungeonProt", EventData.of("Action", "toggle:dungeonProt"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleCombo", EventData.of("Action", "toggle:combo"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleComboTierEff", EventData.of("Action", "toggle:comboTierEff"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleComboDecay", EventData.of("Action", "toggle:comboDecay"), false);
        bindNumField(events, "#ComboTimer", "comboTimer");
        bindAdjust(events, "#ComboTimerDown", "adjust:comboTimerDown");
        bindAdjust(events, "#ComboTimerUp", "adjust:comboTimerUp");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleBounty", EventData.of("Action", "toggle:bounty"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleBountyStreak", EventData.of("Action", "toggle:bountyStreak"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleBountyWeekly", EventData.of("Action", "toggle:bountyWeekly"), false);
        bindNumField(events, "#BountyRefresh", "bountyRefresh");
        bindAdjust(events, "#BountyRefreshDown", "adjust:bountyRefreshDown");
        bindAdjust(events, "#BountyRefreshUp", "adjust:bountyRefreshUp");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleWarden", EventData.of("Action", "toggle:warden"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleVorthak", EventData.of("Action", "toggle:vorthak"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleTemporalPortal", EventData.of("Action", "toggle:temporalPortal"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleRespawnInside", EventData.of("Action", "toggle:respawnInside"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TogglePets", EventData.of("Action", "toggle:pets"), false);
        bindNumField(events, "#MaxPortals", "maxPortals");
        bindAdjust(events, "#MaxPortalsDown", "adjust:maxPortalsDown");
        bindAdjust(events, "#MaxPortalsUp", "adjust:maxPortalsUp");
    }

    // ==================== INTEGRATION ====================

    private void populateIntegration(UICommandBuilder cmd, UIEventBuilder events, EndgameConfig config) {
        EndgameQoL p = plugin;

        // RPG Leveling
        boolean rpgPresent = p.isRPGLevelingModPresent();
        setToggleValue(cmd, "#ToggleRPGLeveling", config.isRPGLevelingEnabled(), true);
        cmd.set("#IntegRPGStatus.Text", rpgPresent ? "DETECTED" : "NOT FOUND");
        cmd.set("#IntegRPGStatus.Style.TextColor", rpgPresent ? "#4aff7f" : "#ff5555");
        cmd.set("#IntegRPGAccent.Background.Color", rpgPresent ? "#55ccff" : "#333333");

        // Endless Leveling
        boolean elPresent = p.isEndlessLevelingModPresent();
        setToggleValue(cmd, "#ToggleEndlessLeveling", config.isEndlessLevelingEnabled(), true);
        cmd.set("#IntegELStatus.Text", elPresent ? "DETECTED" : "NOT FOUND");
        cmd.set("#IntegELStatus.Style.TextColor", elPresent ? "#4aff7f" : "#ff5555");
        cmd.set("#IntegELAccent.Background.Color", elPresent ? "#E8A93B" : "#333333");

        // OrbisGuard
        boolean ogPresent = p.isOrbisGuardModPresent();
        setToggleValue(cmd, "#ToggleOrbisGuard", config.isOrbisGuardEnabled(), true);
        cmd.set("#IntegOGStatus.Text", ogPresent ? "DETECTED" : "NOT FOUND");
        cmd.set("#IntegOGStatus.Style.TextColor", ogPresent ? "#4aff7f" : "#ff5555");
        cmd.set("#IntegOGAccent.Background.Color", ogPresent ? "#55ff88" : "#333333");

        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleRPGLeveling", EventData.of("Action", "toggle:rpgLeveling"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleEndlessLeveling", EventData.of("Action", "toggle:endlessLeveling"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleOrbisGuard", EventData.of("Action", "toggle:orbisGuard"), false);

        // Addons section — Pets Reforged
        boolean prPresent = p.isPetsReforgedModPresent();
        cmd.set("#AddonPR.Visible", prPresent);
        cmd.set("#AddonEmpty.Visible", !prPresent);
        if (prPresent) {
            cmd.set("#AddonPRStatus.Text", "ACTIVE");
            cmd.set("#AddonPRStatus.Style.TextColor", "#4aff7f");
        }
    }

    // ==================== EVENT HANDLING ====================

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ConfigEventData data) {
        // Handle text field ValueChanged events (no action string)
        if (handleTextFieldChange(data)) return;

        if (data.action == null || data.action.isEmpty()) return;
        String action = data.action;
        EndgameConfig config = plugin.getConfig().get();

        // Global search
        if (action.startsWith("gsearch:")) {
            String gsAction = action.substring(8);
            if ("input".equals(gsAction)) {
                globalSearchText = data.searchInput != null ? data.searchInput.trim() : "";
                return; // No rebuild — preserves focus
            }
            if ("apply".equals(gsAction)) {
                // Validating event sends the text; button uses stored text
                if (data.searchInput != null) globalSearchText = data.searchInput.trim();
                globalSearchActive = !globalSearchText.isEmpty();
                refreshPage();
                return;
            }
            if ("clear".equals(gsAction)) {
                globalSearchText = "";
                globalSearchActive = false;
                refreshPage();
                return;
            }
        }

        // If search is active and user clicks a toggle from results, apply it
        if (globalSearchActive && action.startsWith("toggle:")) {
            handleToggle(action.substring(7), config);
            save();
            refreshPage();
            return;
        }

        // Tab switch
        if (action.startsWith("tab:")) {
            String tab = action.substring(4);
            activeTab = tab;
            globalSearchActive = false;
            globalSearchText = "";
            this.rebuild();
            return;
        }

        // Preset
        if (action.startsWith("preset:")) {
            config.setDifficulty(Difficulty.fromString(action.substring(7)));
            save();
            refreshPage();
            return;
        }

        // Toggles
        if (action.startsWith("toggle:")) {
            handleToggle(action.substring(7), config);
            save();
            refreshPage();
            return;
        }

        // Adjustments
        if (action.startsWith("adjust:")) {
            handleAdjust(action.substring(7), config);
            save();
            refreshPage();
            return;
        }

        // Boss scaling
        if (action.startsWith("boss:")) {
            handleBossAdjust(action.substring(5), config);
            save();
            refreshPage();
            return;
        }

        // Apply
        if (action.startsWith("apply:")) {
            save();
            if (plugin.getBossHealthManager() != null) {
                plugin.getBossHealthManager().refreshAllBossStats();
            }
            refreshPage();
            return;
        }

        // Direct numeric input from TextFields
        if (action.startsWith("numSet:")) {
            if (data.numInput != null) {
                handleNumericSet(action.substring(7), data.numInput.trim(), config);
                save();
            }
            return;
        }

        // Craft recipe actions
        if (action.startsWith("craft:")) {
            handleCraftAction(action.substring(6), data);
            return;
        }
    }

    private void handleCraftAction(String action, ConfigEventData data) {
        // Search — ValueChanged just stores, button applies
        if ("searchInput".equals(action)) {
            recipeSearchFilter = data != null && data.searchInput != null ? data.searchInput.trim() : "";
            return; // No rebuild — preserves focus
        }
        if ("search".equals(action)) {
            // Apply stored search filter
            craftPage = 0;
            refreshPage();
            return;
        }
        if ("clearSearch".equals(action)) {
            recipeSearchFilter = "";
            craftPage = 0;
            refreshPage();
            return;
        }
        // Category switch
        if (action.startsWith("cat:")) {
            craftCategory = action.substring(4);
            craftPage = 0;
            selectedRecipeId = null;
            refreshPage();
            return;
        }
        // Pagination
        if ("prev".equals(action) && craftPage > 0) { craftPage--; refreshPage(); return; }
        if ("next".equals(action)) {
            int totalPages = Math.max(1, (filteredRecipeIds.size() + RECIPES_PER_PAGE - 1) / RECIPES_PER_PAGE);
            if (craftPage < totalPages - 1) { craftPage++; refreshPage(); }
            return;
        }
        // Toggle recipe from list — sendUpdate to preserve scroll
        if (action.startsWith("toggle:")) {
            int idx = Integer.parseInt(action.substring(7));
            if (idx >= 0 && idx < filteredRecipeIds.size()) {
                var entry = plugin.getRecipeOverrideConfig().get().getRecipeOverrides().get(filteredRecipeIds.get(idx));
                if (entry != null) {
                    entry.setEnabled(!entry.isEnabled());
                    plugin.getRecipeOverrideConfig().save();
                    // Update just the toggle button instead of full rebuild
                    int rowIdx = idx - craftPage * RECIPES_PER_PAGE;
                    UICommandBuilder cmd = new UICommandBuilder();
                    setToggleValue(cmd, "#Row" + rowIdx + "Toggle", entry.isEnabled(), true);
                    this.sendUpdate(cmd, new UIEventBuilder(), false);
                }
            }
            return;
        }
        // Edit recipe — open detail
        if (action.startsWith("edit:")) {
            int idx = Integer.parseInt(action.substring(5));
            if (idx >= 0 && idx < filteredRecipeIds.size()) {
                selectedRecipeId = filteredRecipeIds.get(idx);
            }
            refreshPage();
            return;
        }
        // Back to list
        if ("back".equals(action)) { selectedRecipeId = null; refreshPage(); return; }
        // Detail actions
        if (action.startsWith("detail:")) {
            handleCraftDetailAction(action.substring(7));
            return;
        }
    }

    private void handleCraftDetailAction(String field) {
        if (selectedRecipeId == null) return;
        var entry = plugin.getRecipeOverrideConfig().get().getRecipeOverrides().get(selectedRecipeId);
        if (entry == null) return;
        entry.setModified(true);

        switch (field) {
            case "toggle" -> entry.setEnabled(!entry.isEnabled());
            case "outQtyUp" -> entry.setOutputQuantity(Math.min(99, entry.getOutputQuantity() + 1));
            case "outQtyDown" -> entry.setOutputQuantity(Math.max(1, entry.getOutputQuantity() - 1));
            case "tierUp" -> entry.setBenchTier(Math.min(10, entry.getBenchTier() + 1));
            case "tierDown" -> entry.setBenchTier(Math.max(0, entry.getBenchTier() - 1));
            case "timeUp" -> entry.setCraftTime(Math.min(120f, entry.getCraftTime() + 0.5f));
            case "timeDown" -> entry.setCraftTime(Math.max(0.5f, entry.getCraftTime() - 0.5f));
            case "save" -> { plugin.getRecipeOverrideConfig().save(); return; }
            default -> {
                if (field.length() >= 4 && field.startsWith("in")) {
                    int inputIdx = field.charAt(2) - '0';
                    boolean up = field.endsWith("Up");
                    if (entry.getInputs() != null && inputIdx >= 0 && inputIdx < entry.getInputs().length) {
                        var input = entry.getInputs()[inputIdx];
                        if (up) input.setQuantity(Math.min(999, input.getQuantity() + 1));
                        else input.setQuantity(Math.max(1, input.getQuantity() - 1));
                    }
                }
            }
        }
        // sendUpdate instead of rebuild — preserves scroll position
        sendCraftDetailUpdate(entry);
    }

    private void sendCraftDetailUpdate(RecipeOverrideConfig.RecipeEntry entry) {
        UICommandBuilder cmd = new UICommandBuilder();
        setToggleValue(cmd, "#DetailToggle", entry.isEnabled(), true);
        cmd.set("#DetailOutputQty.Text", "x" + entry.getOutputQuantity());
        if (entry.getInputs() != null) {
            for (int i = 0; i < entry.getInputs().length && i < MAX_INPUTS; i++) {
                cmd.set("#DetailIn" + i + "Qty.Text", String.valueOf(entry.getInputs()[i].getQuantity()));
            }
        }
        if (entry.getBenchId() != null) {
            cmd.set("#DetailBenchTier.Text", "T" + entry.getBenchTier());
        }
        cmd.set("#DetailCraftTime.Text", String.format("%.1fs", entry.getCraftTime()));
        this.sendUpdate(cmd, new UIEventBuilder(), false);
    }

    private boolean handleTextFieldChange(ConfigEventData data) {
        if (selectedRecipeId == null) return false;
        var entry = plugin.getRecipeOverrideConfig().get().getRecipeOverrides().get(selectedRecipeId);
        if (entry == null) return false;

        boolean handled = false;
        if (data.outputItem != null) {
            entry.setOutput(data.outputItem.trim());
            entry.setModified(true);
            handled = true;
        }
        String[] inItems = {data.in0Item, data.in1Item, data.in2Item, data.in3Item, data.in4Item, data.in5Item};
        for (int i = 0; i < inItems.length; i++) {
            if (inItems[i] != null && entry.getInputs() != null && i < entry.getInputs().length) {
                entry.getInputs()[i].setItem(inItems[i].trim());
                entry.setModified(true);
                handled = true;
            }
        }
        if (data.benchId != null) {
            entry.setBenchId(data.benchId.trim());
            entry.setModified(true);
            handled = true;
        }
        return handled;
    }

    private void handleNumericSet(String fieldId, String rawValue, EndgameConfig config) {
        // Strip common prefixes/suffixes users might type
        String cleaned = rawValue.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty()) return;
        try {
            // Boss fields: "boss:BossId:hp/dmg/scale"
            if (fieldId.startsWith("boss:")) {
                String[] parts = fieldId.split(":");
                if (parts.length == 3) {
                    BossType bt = null;
                    for (int i = 0; i < BOSS_IDS.length; i++) {
                        if (BOSS_IDS[i].equals(parts[1])) { bt = BOSS_TYPES[i]; break; }
                    }
                    if (bt == null) return;
                    BossConfig bc = config.getBossConfig(bt);
                    switch (parts[2]) {
                        case "hp" -> bc.setHealthOverride(Math.max(0, Integer.parseInt(cleaned)));
                        case "dmg" -> bc.setDamageMultiplier(Math.max(0f, Float.parseFloat(cleaned)));
                        case "scale" -> bc.setPlayerScaling(Math.max(0, Math.min(200, Integer.parseInt(cleaned))));
                    }
                }
                return;
            }
            // Direct config fields
            switch (fieldId) {
                case "healthMult" -> config.setCustomHealthMultiplier(Math.max(0f, Math.min(10f, Float.parseFloat(cleaned))));
                case "damageMult" -> config.setCustomDamageMultiplier(Math.max(0f, Math.min(10f, Float.parseFloat(cleaned))));
                case "hederaPoisonDmg" -> config.setHederaDaggerPoisonDamage(Math.max(0f, Math.min(50f, Float.parseFloat(cleaned))));
                case "hederaPoisonTicks" -> config.setHederaDaggerPoisonTicks(Math.max(0, Math.min(20, Integer.parseInt(cleaned))));
                case "hederaLifesteal" -> config.setHederaDaggerLifestealPercent(Math.max(0f, Math.min(0.5f, Float.parseFloat(cleaned) / 100f)));
                case "blazefistDmg" -> config.setBlazefistBurnDamage(Math.max(0f, Math.min(200f, Float.parseFloat(cleaned))));
                case "blazefistTicks" -> config.setBlazefistBurnTicks(Math.max(0, Math.min(10, Integer.parseInt(cleaned))));
                case "manaRegenMithril" -> config.setManaRegenMithrilPerPiece(Math.max(0f, Math.min(5f, Float.parseFloat(cleaned))));
                case "manaRegenOnyxium" -> config.setManaRegenOnyxiumPerPiece(Math.max(0f, Math.min(5f, Float.parseFloat(cleaned))));
                case "manaRegenPrisma" -> config.setManaRegenPrismaPerPiece(Math.max(0f, Math.min(5f, Float.parseFloat(cleaned))));
                case "hpRegenDelay" -> config.setArmorHPRegenDelaySec(Math.max(1f, Math.min(60f, Float.parseFloat(cleaned))));
                case "hpRegenOnyxium" -> config.setArmorHPRegenOnyxiumPerPiece(Math.max(0f, Math.min(5f, Float.parseFloat(cleaned))));
                case "hpRegenPrisma" -> config.setArmorHPRegenPrismaPerPiece(Math.max(0f, Math.min(5f, Float.parseFloat(cleaned))));
                case "comboTimer" -> config.setComboTimerSeconds(Math.max(1f, Math.min(30f, Float.parseFloat(cleaned))));
                case "bountyRefresh" -> config.setBountyRefreshHours(Math.max(1, Math.min(168, Integer.parseInt(cleaned))));
                case "maxPortals" -> config.getTemporalPortalConfig().setMaxConcurrentPortals(Integer.parseInt(cleaned));
            }
        } catch (NumberFormatException ignored) {}
    }

    private void handleToggle(String field, EndgameConfig config) {
        switch (field) {
            case "bosses" -> config.setDifficultyAffectsBosses(!config.isDifficultyAffectsBosses());
            case "mobs" -> config.setDifficultyAffectsMobs(!config.isDifficultyAffectsMobs());
            case "prismaArmor" -> config.setPrismaArmorVulnerabilityEnabled(!config.isPrismaArmorVulnerabilityEnabled());
            case "prismaWeapon" -> config.setPrismaWeaponBossBlockEnabled(!config.isPrismaWeaponBossBlockEnabled());
            case "hederaPoison" -> config.setEnableHederaDaggerPoison(!config.isEnableHederaDaggerPoison());
            case "hederaLifesteal" -> config.setEnableHederaDaggerLifesteal(!config.isEnableHederaDaggerLifesteal());
            case "blazefist" -> config.setBlazefistBurnEnabled(!config.isBlazefistBurnEnabled());
            case "manaRegen" -> config.setManaRegenArmorEnabled(!config.isManaRegenArmorEnabled());
            case "hpRegen" -> config.setArmorHPRegenEnabled(!config.isArmorHPRegenEnabled());
            case "pvp" -> { config.setPvpEnabled(!config.isPvpEnabled()); plugin.applyPvpToAllWorlds(config.isPvpEnabled()); }
            case "dungeonProt" -> config.setEnableDungeonBlockProtection(!config.isEnableDungeonBlockProtection());
            case "combo" -> config.setComboEnabled(!config.isComboEnabled());
            case "comboTierEff" -> config.setComboTierEffectsEnabled(!config.isComboTierEffectsEnabled());
            case "comboDecay" -> config.setComboDecayEnabled(!config.isComboDecayEnabled());
            case "bounty" -> config.setBountyEnabled(!config.isBountyEnabled());
            case "bountyStreak" -> config.setBountyStreakEnabled(!config.isBountyStreakEnabled());
            case "bountyWeekly" -> config.setBountyWeeklyEnabled(!config.isBountyWeeklyEnabled());
            case "warden" -> config.setWardenTrialEnabled(!config.isWardenTrialEnabled());
            case "vorthak" -> config.setVorthakEnabled(!config.isVorthakEnabled());
            case "temporalPortal" -> config.getTemporalPortalConfig().setEnabled(!config.getTemporalPortalConfig().isEnabled());
            case "respawnInside" -> {
                // Apply to all dungeons (single master toggle, per-dungeon overrides available via config JSON)
                boolean anyOn = config.getTemporalPortalConfig().getDungeons().values().stream()
                        .anyMatch(endgame.plugin.systems.portal.DungeonDefinition::isAllowRespawnInside);
                boolean target = !anyOn;
                config.getTemporalPortalConfig().getDungeons().values()
                        .forEach(d -> d.setAllowRespawnInside(target));
            }
            case "pets" -> config.pets().setEnabled(!config.pets().isEnabled());
            case "rpgLeveling" -> {
                boolean newVal = !config.isRPGLevelingEnabled();
                config.setRPGLevelingEnabled(newVal);
                if (!newVal) config.misc().setRPGLevelingAutoDetected("disabled");
            }
            case "endlessLeveling" -> {
                boolean newVal = !config.isEndlessLevelingEnabled();
                config.setEndlessLevelingEnabled(newVal);
                if (!newVal) config.misc().setEndlessLevelingAutoDetected("disabled");
            }
            case "orbisGuard" -> config.setOrbisGuardEnabled(!config.isOrbisGuardEnabled());
        }
    }

    private void handleAdjust(String field, EndgameConfig config) {
        switch (field) {
            // Difficulty custom
            case "healthUp" -> config.setCustomHealthMultiplier(Math.min(10f, config.getCustomHealthMultiplier() + 0.1f));
            case "healthDown" -> config.setCustomHealthMultiplier(Math.max(0f, config.getCustomHealthMultiplier() - 0.1f));
            case "damageUp" -> config.setCustomDamageMultiplier(Math.min(10f, config.getCustomDamageMultiplier() + 0.1f));
            case "damageDown" -> config.setCustomDamageMultiplier(Math.max(0f, config.getCustomDamageMultiplier() - 0.1f));
            // Weapons
            case "hederaPoisonDmgUp" -> config.setHederaDaggerPoisonDamage(Math.min(50f, config.getHederaDaggerPoisonDamage() + 1f));
            case "hederaPoisonDmgDown" -> config.setHederaDaggerPoisonDamage(Math.max(0f, config.getHederaDaggerPoisonDamage() - 1f));
            case "hederaPoisonTicksUp" -> config.setHederaDaggerPoisonTicks(Math.min(20, config.getHederaDaggerPoisonTicks() + 1));
            case "hederaPoisonTicksDown" -> config.setHederaDaggerPoisonTicks(Math.max(0, config.getHederaDaggerPoisonTicks() - 1));
            case "hederaLifestealUp" -> config.setHederaDaggerLifestealPercent(Math.min(0.5f, config.getHederaDaggerLifestealPercent() + 0.01f));
            case "hederaLifestealDown" -> config.setHederaDaggerLifestealPercent(Math.max(0f, config.getHederaDaggerLifestealPercent() - 0.01f));
            case "blazefistDmgUp" -> config.setBlazefistBurnDamage(Math.min(200f, config.getBlazefistBurnDamage() + 5f));
            case "blazefistDmgDown" -> config.setBlazefistBurnDamage(Math.max(0f, config.getBlazefistBurnDamage() - 5f));
            case "blazefistTicksUp" -> config.setBlazefistBurnTicks(Math.min(10, config.getBlazefistBurnTicks() + 1));
            case "blazefistTicksDown" -> config.setBlazefistBurnTicks(Math.max(0, config.getBlazefistBurnTicks() - 1));
            // Armor
            case "manaRegenMithrilUp" -> config.setManaRegenMithrilPerPiece(Math.min(5f, config.getManaRegenMithrilPerPiece() + 0.25f));
            case "manaRegenMithrilDown" -> config.setManaRegenMithrilPerPiece(Math.max(0f, config.getManaRegenMithrilPerPiece() - 0.25f));
            case "manaRegenOnyxiumUp" -> config.setManaRegenOnyxiumPerPiece(Math.min(5f, config.getManaRegenOnyxiumPerPiece() + 0.25f));
            case "manaRegenOnyxiumDown" -> config.setManaRegenOnyxiumPerPiece(Math.max(0f, config.getManaRegenOnyxiumPerPiece() - 0.25f));
            case "manaRegenPrismaUp" -> config.setManaRegenPrismaPerPiece(Math.min(5f, config.getManaRegenPrismaPerPiece() + 0.25f));
            case "manaRegenPrismaDown" -> config.setManaRegenPrismaPerPiece(Math.max(0f, config.getManaRegenPrismaPerPiece() - 0.25f));
            case "hpRegenDelayUp" -> config.setArmorHPRegenDelaySec(Math.min(60f, config.getArmorHPRegenDelaySec() + 1f));
            case "hpRegenDelayDown" -> config.setArmorHPRegenDelaySec(Math.max(1f, config.getArmorHPRegenDelaySec() - 1f));
            case "hpRegenOnyxiumUp" -> config.setArmorHPRegenOnyxiumPerPiece(Math.min(5f, config.getArmorHPRegenOnyxiumPerPiece() + 0.25f));
            case "hpRegenOnyxiumDown" -> config.setArmorHPRegenOnyxiumPerPiece(Math.max(0f, config.getArmorHPRegenOnyxiumPerPiece() - 0.25f));
            case "hpRegenPrismaUp" -> config.setArmorHPRegenPrismaPerPiece(Math.min(5f, config.getArmorHPRegenPrismaPerPiece() + 0.25f));
            case "hpRegenPrismaDown" -> config.setArmorHPRegenPrismaPerPiece(Math.max(0f, config.getArmorHPRegenPrismaPerPiece() - 0.25f));
            // Misc
            case "comboTimerUp" -> config.setComboTimerSeconds(Math.min(30f, config.getComboTimerSeconds() + 1f));
            case "comboTimerDown" -> config.setComboTimerSeconds(Math.max(1f, config.getComboTimerSeconds() - 1f));
            case "bountyRefreshUp" -> config.setBountyRefreshHours(Math.min(168, config.getBountyRefreshHours() + 1));
            case "bountyRefreshDown" -> config.setBountyRefreshHours(Math.max(1, config.getBountyRefreshHours() - 1));
            case "maxPortalsUp" -> config.getTemporalPortalConfig().setMaxConcurrentPortals(config.getTemporalPortalConfig().getMaxConcurrentPortals() + 1);
            case "maxPortalsDown" -> config.getTemporalPortalConfig().setMaxConcurrentPortals(config.getTemporalPortalConfig().getMaxConcurrentPortals() - 1);
        }
    }

    private void handleBossAdjust(String bossAction, EndgameConfig config) {
        // Format: "DragonFrost:hpUp"
        int sep = bossAction.indexOf(':');
        if (sep < 0) return;
        String bossId = bossAction.substring(0, sep);
        String field = bossAction.substring(sep + 1);

        BossType bossType = null;
        for (int i = 0; i < BOSS_IDS.length; i++) {
            if (BOSS_IDS[i].equals(bossId)) { bossType = BOSS_TYPES[i]; break; }
        }
        if (bossType == null) return;
        BossConfig bc = config.getBossConfig(bossType);

        switch (field) {
            case "hpUp" -> bc.setHealthOverride(bc.getHealthOverrideRaw() + 100);
            case "hpDown" -> bc.setHealthOverride(Math.max(0, bc.getHealthOverrideRaw() - 100));
            case "dmgUp" -> bc.setDamageMultiplier(Math.min(10f, bc.getDamageMultiplierRaw() + 0.1f));
            case "dmgDown" -> bc.setDamageMultiplier(Math.max(0f, bc.getDamageMultiplierRaw() - 0.1f));
            case "scaleUp" -> bc.setPlayerScaling(Math.min(200, bc.getPlayerScaling() + 10));
            case "scaleDown" -> bc.setPlayerScaling(Math.max(0, bc.getPlayerScaling() - 10));
        }
    }

    // ==================== HELPERS ====================

    private void setToggleValue(UICommandBuilder cmd, String selector, boolean value) {
        setToggleValue(cmd, selector, value, false);
    }

    /**
     * Set toggle button visual state.
     * @param requiresRestart if true, appends "*" and uses amber tint to indicate restart needed
     */
    private void setToggleValue(UICommandBuilder cmd, String selector, boolean value, boolean requiresRestart) {
        String suffix = requiresRestart ? "*" : "";
        cmd.set(selector + ".Text", (value ? "ON" : "OFF") + suffix);
        if (requiresRestart) {
            // Amber tint for restart-required settings
            cmd.set(selector + ".Style.Default.Background", value ? "#2e3d1a" : "#3d2e1a");
            cmd.set(selector + ".Style.Default.LabelStyle.TextColor", value ? "#aaff4a" : "#ffaa4a");
            cmd.set(selector + ".Style.Hovered.Background", value ? "#3e4d2a" : "#4d3e2a");
            cmd.set(selector + ".Style.Hovered.LabelStyle.TextColor", value ? "#ccff6a" : "#ffcc6a");
        } else {
            cmd.set(selector + ".Style.Default.Background", value ? "#1a3d2e" : "#3d1a1a");
            cmd.set(selector + ".Style.Default.LabelStyle.TextColor", value ? "#4aff7f" : "#ff4a4a");
            cmd.set(selector + ".Style.Hovered.Background", value ? "#2a4d3e" : "#4d2a2a");
            cmd.set(selector + ".Style.Hovered.LabelStyle.TextColor", value ? "#6aff9f" : "#ff6a6a");
        }
    }

    private void bindAdjust(UIEventBuilder events, String selector, String action) {
        events.addEventBinding(CustomUIEventBindingType.Activating, selector,
                EventData.of("Action", action), false);
    }

    private void bindNumField(UIEventBuilder events, String selector, String fieldId) {
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector,
                EventData.of("Action", "numSet:" + fieldId).append("@NumInput", selector + ".Value"), false);
    }

    private void save() {
        plugin.getConfig().save();
    }

    public static void open(PlayerRef playerRef, Store<EntityStore> store, EndgameQoL plugin) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store, new NativeConfigPage(playerRef, plugin));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[NativeConfig] Failed to open");
        }
    }

    public static class ConfigEventData {
        public static final BuilderCodec<ConfigEventData> CODEC = BuilderCodec
                .builder(ConfigEventData.class, ConfigEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, true),
                        (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("@OutputItem", Codec.STRING, true),
                        (d, v) -> d.outputItem = v, d -> d.outputItem).add()
                .append(new KeyedCodec<>("@In0Item", Codec.STRING, true),
                        (d, v) -> d.in0Item = v, d -> d.in0Item).add()
                .append(new KeyedCodec<>("@In1Item", Codec.STRING, true),
                        (d, v) -> d.in1Item = v, d -> d.in1Item).add()
                .append(new KeyedCodec<>("@In2Item", Codec.STRING, true),
                        (d, v) -> d.in2Item = v, d -> d.in2Item).add()
                .append(new KeyedCodec<>("@In3Item", Codec.STRING, true),
                        (d, v) -> d.in3Item = v, d -> d.in3Item).add()
                .append(new KeyedCodec<>("@In4Item", Codec.STRING, true),
                        (d, v) -> d.in4Item = v, d -> d.in4Item).add()
                .append(new KeyedCodec<>("@In5Item", Codec.STRING, true),
                        (d, v) -> d.in5Item = v, d -> d.in5Item).add()
                .append(new KeyedCodec<>("@BenchId", Codec.STRING, true),
                        (d, v) -> d.benchId = v, d -> d.benchId).add()
                .append(new KeyedCodec<>("@SearchInput", Codec.STRING, true),
                        (d, v) -> d.searchInput = v, d -> d.searchInput).add()
                .append(new KeyedCodec<>("@NumInput", Codec.STRING, true),
                        (d, v) -> d.numInput = v, d -> d.numInput).add()
                .build();
        String action;
        String outputItem;
        String in0Item, in1Item, in2Item, in3Item, in4Item, in5Item;
        String benchId;
        String searchInput;
        String numInput;
    }
}
