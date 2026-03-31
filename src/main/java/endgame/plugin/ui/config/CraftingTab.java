package endgame.plugin.ui.config;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.RecipeOverrideConfig;
import endgame.plugin.ui.ConfigUI;
import endgame.plugin.utils.HtmlUtil;
import endgame.plugin.utils.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Crafting tab for the ConfigUI.
 *
 * All recipes are shown expanded with editable fields. A search bar at the top
 * filters recipes by name (re-renders on each keystroke).
 * Field changes save to RecipeOverrides.json immediately (server restart required).
 */
public class CraftingTab extends ConfigTabBuilder {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ConfigUI");

    /** Recipes that passed the search filter — used by both buildHtml and registerListeners. */
    private List<String> visibleIds;
    /** All recipe IDs for stable indexing (element IDs stay consistent across searches). */
    private List<String> allIds;
    private String searchFilter;
    private final AtomicLong lastRecipeSaveTime = new AtomicLong(0);
    private static final long RECIPE_SAVE_DEBOUNCE_MS = 2000;

    public CraftingTab(EndgameQoL plugin, ConfigSaveManager saveManager, String searchFilter) {
        super(plugin, saveManager);
        this.searchFilter = searchFilter != null ? searchFilter : "";
    }

    private void debouncedRecipeSave() {
        long now = System.currentTimeMillis();
        long last = lastRecipeSaveTime.get();
        if (now - last < RECIPE_SAVE_DEBOUNCE_MS) return;
        if (!lastRecipeSaveTime.compareAndSet(last, now)) return;
        plugin.getRecipeOverrideConfig().save();
    }

    @Override
    public String buildHtml(String locale) {
        RecipeOverrideConfig overrideConfig = plugin.getRecipeOverrideConfig().get();
        Map<String, RecipeOverrideConfig.RecipeEntry> overrides = overrideConfig.getRecipeOverrides();

        allIds = new ArrayList<>(overrides.keySet());

        // Filter by search
        String lowerFilter = searchFilter.toLowerCase();
        visibleIds = new ArrayList<>();
        for (String id : allIds) {
            if (lowerFilter.isEmpty() || matchesSearch(id, overrides.get(id), lowerFilter)) {
                visibleIds.add(id);
            }
        }

        StringBuilder sb = new StringBuilder();

        sb.append(String.format("""
                <p class="section-header">
                    <span data-hyui-color="#FFD700" data-hyui-bold="true">%s</span>
                </p>
                <p class="section-hint">
                    <span data-hyui-color="#ff6666">%s </span><span data-hyui-color="#888888">%s</span>
                </p>
                """,
                HtmlUtil.escape(I18n.getFor(locale, "ui.crafting.title")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.crafting.restart_hint")),
                HtmlUtil.escape(I18n.getFor(locale, "ui.crafting.restart_hint2").trim())));

        // Search bar
        sb.append(String.format("""
                <div class="card-row" style="margin-bottom: 8;">
                    <p style="anchor-width: 90; font-size: 12;"><span data-hyui-color="#888888">%s</span></p>
                    <input type="text" id="craft_search" value="%s" style="anchor-width: 300; anchor-height: 28;"/>
                    <p style="padding-left: 12; font-size: 11;"><span data-hyui-color="#666666">%s</span></p>
                </div>
                """, HtmlUtil.escape(I18n.getFor(locale, "ui.crafting.search")),
                esc(searchFilter),
                HtmlUtil.escape(I18n.getFor(locale, "ui.crafting.recipes_count", visibleIds.size(), allIds.size()))));

        // Categorize visible recipes
        List<String> portalIds = new ArrayList<>();
        List<String> weaponIds = new ArrayList<>();
        List<String> armorIds = new ArrayList<>();
        List<String> potionIds = new ArrayList<>();
        List<String> otherIds = new ArrayList<>();

        for (String id : visibleIds) {
            String lower = id.toLowerCase();
            RecipeOverrideConfig.RecipeEntry entry = overrides.get(id);
            String output = entry.getOutput() != null ? entry.getOutput().toLowerCase() : lower;

            if (lower.contains("portal") || lower.contains("key") || lower.contains("challenge") || lower.contains("warden")
                    || output.contains("portal") || output.contains("challenge") || output.contains("warden")) {
                portalIds.add(id);
            } else if (lower.contains("weapon") || lower.contains("sword") || lower.contains("dagger")
                    || lower.contains("staff") || lower.contains("spear") || lower.contains("mace")
                    || lower.contains("shield") || lower.contains("shortbow") || lower.contains("longsword")
                    || lower.contains("battleaxe") || lower.contains("pickaxe")) {
                weaponIds.add(id);
            } else if (lower.contains("armor")) {
                armorIds.add(id);
            } else if (lower.contains("potion")) {
                potionIds.add(id);
            } else {
                otherIds.add(id);
            }
        }

        buildGroup(sb, I18n.getFor(locale, "ui.crafting.portals"), "#bb44ff", portalIds, overrides, locale);
        buildGroup(sb, I18n.getFor(locale, "ui.crafting.weapons"), "#ff8866", weaponIds, overrides, locale);
        buildGroup(sb, I18n.getFor(locale, "ui.crafting.armor"), "#4ade80", armorIds, overrides, locale);
        buildGroup(sb, I18n.getFor(locale, "ui.crafting.potions"), "#88aaff", potionIds, overrides, locale);
        buildGroup(sb, I18n.getFor(locale, "ui.crafting.other"), "#FFD700", otherIds, overrides, locale);

        return sb.toString();
    }

    private void buildGroup(StringBuilder sb, String title, String color,
                            List<String> ids,
                            Map<String, RecipeOverrideConfig.RecipeEntry> overrides,
                            String locale) {
        if (ids.isEmpty()) return;

        sb.append(String.format("""
                <div class="card">
                    <p class="group-header">
                        <span data-hyui-color="%s" data-hyui-bold="true">%s</span>
                        <span data-hyui-color="#666666"> (%d)</span>
                    </p>
                """, color, title, ids.size()));

        for (String id : ids) {
            RecipeOverrideConfig.RecipeEntry entry = overrides.get(id);
            String displayName = formatDisplayName(id);
            int idx = allIds.indexOf(id);
            String checkboxId = "recipe_" + idx;
            String checked = entry.isEnabled() ? "checked" : "";

            // Recipe header row (checkbox + name)
            sb.append(String.format("""
                    <div class="craft-row">
                        <input type="checkbox" id="%s" %s/>
                        <label class="craft-label">%s</label>
                    </div>
                    """, checkboxId, checked, esc(displayName)));

            // Always show detail panel
            sb.append(buildDetailPanel(id, idx, entry, locale));
        }

        sb.append("</div>");
    }

    /**
     * Build an editable detail panel for one recipe.
     */
    private String buildDetailPanel(String id, int idx, RecipeOverrideConfig.RecipeEntry entry, String locale) {
        StringBuilder d = new StringBuilder();

        d.append("""
                <div class="combat-section" style="margin-left: 36; margin-bottom: 4; padding: 8;">
                """);

        // Output row (label + quantity input)
        String outputName = entry.getOutput() != null ? clean(entry.getOutput()) : clean(id);
        d.append(String.format("""
                    <div class="combat-row">
                        <p class="combat-label"><span data-hyui-color="#888888" data-hyui-bold="true">%s</span></p>
                        <p style="flex-weight: 1;"><span data-hyui-color="#ffffff">%s</span></p>
                        <p style="anchor-width: 55; horizontal-align: right;"><span data-hyui-color="#888888">%s</span></p>
                        <input type="number" id="craft_outqty_%d" value="%d" min="1" max="99" style="anchor-width: 55; anchor-height: 26;"/>
                    </div>
                """, I18n.getFor(locale, "ui.crafting.output"), esc(outputName),
                I18n.getFor(locale, "ui.crafting.qty"), idx, entry.getOutputQuantity()));

        // Input rows (label + quantity input per ingredient)
        RecipeOverrideConfig.Ingredient[] inputs = entry.getInputs();
        if (inputs != null) {
            for (int i = 0; i < inputs.length; i++) {
                String itemName = inputs[i].getItem() != null ? clean(inputs[i].getItem()) : "?";
                d.append(String.format("""
                    <div class="combat-row">
                        <p class="combat-label"><span data-hyui-color="#888888" data-hyui-bold="true">%s</span></p>
                        <p style="flex-weight: 1;"><span data-hyui-color="#cccccc">%s</span></p>
                        <p style="anchor-width: 55; horizontal-align: right;"><span data-hyui-color="#888888">%s</span></p>
                        <input type="number" id="craft_inqty_%d_%d" value="%d" min="1" max="999" style="anchor-width: 55; anchor-height: 26;"/>
                    </div>
                    """, I18n.getFor(locale, "ui.crafting.input", i + 1), esc(itemName),
                    I18n.getFor(locale, "ui.crafting.qty"), idx, i, inputs[i].getQuantity()));
            }
        }

        // Bench row (label + tier input)
        if (entry.getBenchId() != null) {
            String benchName = clean(entry.getBenchId());
            d.append(String.format("""
                    <div class="combat-row">
                        <p class="combat-label"><span data-hyui-color="#888888" data-hyui-bold="true">%s</span></p>
                        <p style="flex-weight: 1;"><span data-hyui-color="#cccccc">%s</span></p>
                        <p style="anchor-width: 55; horizontal-align: right;"><span data-hyui-color="#888888">%s</span></p>
                        <input type="number" id="craft_tier_%d" value="%d" min="0" max="10" style="anchor-width: 55; anchor-height: 26;"/>
                    </div>
                """, I18n.getFor(locale, "ui.crafting.bench"), esc(benchName),
                I18n.getFor(locale, "ui.crafting.tier"), idx, entry.getBenchTier()));
        }

        // Craft time row
        d.append(String.format("""
                    <div class="combat-row">
                        <p class="combat-label"><span data-hyui-color="#888888" data-hyui-bold="true">%s</span></p>
                        <input type="number" id="craft_time_%d" value="%.1f" min="0.1" max="120" step="0.5" data-hyui-max-decimal-places="1" style="anchor-width: 70; anchor-height: 26;"/>
                        <p class="combat-hint"><span data-hyui-color="#666666">%s</span></p>
                    </div>
                """, I18n.getFor(locale, "ui.crafting.craft_time"), idx, entry.getCraftTime(),
                I18n.getFor(locale, "ui.crafting.seconds")));

        d.append("</div>");
        return d.toString();
    }

    @Override
    public void registerListeners(PageBuilder builder, PlayerRef playerRef,
                                  Store<EntityStore> store) {
        if (visibleIds == null) return;

        RecipeOverrideConfig overrideConfig = plugin.getRecipeOverrideConfig().get();
        Map<String, RecipeOverrideConfig.RecipeEntry> overrides = overrideConfig.getRecipeOverrides();

        // Search bar — re-render on change
        builder.addEventListener("craft_search", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            String filter = data != null ? data.toString().trim() : "";
            this.searchFilter = filter;
            ctx.updatePage(true);
        });

        // Register listeners only for visible (filtered) recipes
        for (String id : visibleIds) {
            int idx = allIds.indexOf(id);
            final String recipeId = id;

            // Checkbox: enable/disable recipe
            builder.addEventListener("recipe_" + idx, CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                boolean enabled = Boolean.parseBoolean(data.toString());
                RecipeOverrideConfig.RecipeEntry entry = overrides.get(recipeId);
                if (entry != null) {
                    entry.setEnabled(enabled);
                    debouncedRecipeSave();
                    LOGGER.atFine().log("[ConfigUI] Recipe '%s' set to %s", recipeId, enabled);
                }
            });

            RecipeOverrideConfig.RecipeEntry entry = overrides.get(id);
            if (entry == null) continue;

            // Output quantity
            builder.addEventListener("craft_outqty_" + idx, CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                RecipeOverrideConfig.RecipeEntry e = overrides.get(recipeId);
                if (e != null) {
                    e.setOutputQuantity(parseInt(data, e.getOutputQuantity()));
                    e.setModified(true);
                    debouncedRecipeSave();
                    LOGGER.atFine().log("[ConfigUI] Recipe '%s' output qty set to %d", recipeId, e.getOutputQuantity());
                }
            });

            // Input quantities
            RecipeOverrideConfig.Ingredient[] inputs = entry.getInputs();
            if (inputs != null) {
                for (int i = 0; i < inputs.length; i++) {
                    final int inputIdx = i;
                    builder.addEventListener("craft_inqty_" + idx + "_" + i, CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                        if (data == null) return;
                        RecipeOverrideConfig.RecipeEntry e = overrides.get(recipeId);
                        if (e != null && e.getInputs() != null && inputIdx < e.getInputs().length) {
                            e.getInputs()[inputIdx].setQuantity(parseInt(data, e.getInputs()[inputIdx].getQuantity()));
                            e.setModified(true);
                            debouncedRecipeSave();
                            LOGGER.atFine().log("[ConfigUI] Recipe '%s' input[%d] qty set to %d", recipeId, inputIdx, e.getInputs()[inputIdx].getQuantity());
                        }
                    });
                }
            }

            // Bench tier
            if (entry.getBenchId() != null) {
                builder.addEventListener("craft_tier_" + idx, CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                    if (data == null) return;
                    RecipeOverrideConfig.RecipeEntry e = overrides.get(recipeId);
                    if (e != null) {
                        e.setBenchTier(parseInt(data, e.getBenchTier()));
                        e.setModified(true);
                        debouncedRecipeSave();
                        LOGGER.atFine().log("[ConfigUI] Recipe '%s' bench tier set to %d", recipeId, e.getBenchTier());
                    }
                });
            }

            // Craft time
            builder.addEventListener("craft_time_" + idx, CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
                if (data == null) return;
                RecipeOverrideConfig.RecipeEntry e = overrides.get(recipeId);
                if (e != null) {
                    e.setCraftTime(parseFloat(data, e.getCraftTime()));
                    e.setModified(true);
                    debouncedRecipeSave();
                    LOGGER.atFine().log("[ConfigUI] Recipe '%s' craft time set to %.1f", recipeId, e.getCraftTime());
                }
            });
        }
    }

    // === Search matching ===

    private static boolean matchesSearch(String id, RecipeOverrideConfig.RecipeEntry entry, String lowerFilter) {
        // Match against recipe ID (cleaned) and output name
        String cleanId = formatDisplayName(id).toLowerCase();
        if (cleanId.contains(lowerFilter)) return true;
        if (entry.getOutput() != null && entry.getOutput().toLowerCase().contains(lowerFilter)) return true;
        // Match against ingredient names
        if (entry.getInputs() != null) {
            for (RecipeOverrideConfig.Ingredient input : entry.getInputs()) {
                if (input.getItem() != null && input.getItem().toLowerCase().contains(lowerFilter)) return true;
            }
        }
        return false;
    }

    // === Display helpers ===

    private static String formatDisplayName(String id) {
        String base = id.replace("_Recipe_Generated_0", "")
                .replace("_Generated_0", "")
                .replace("Endgame_", "")
                .replace("Weapon_", "")
                .replace("Armor_", "")
                .replace("Tool_", "")
                .replace("Ingredient_", "");
        return base.replace('_', ' ');
    }

    /** Replace underscores with spaces for display. */
    private static String clean(String s) {
        return s != null ? s.replace('_', ' ') : "";
    }

    private static String esc(String t) {
        if (t == null) return "";
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

}
