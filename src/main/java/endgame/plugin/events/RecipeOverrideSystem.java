package endgame.plugin.events;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.protocol.BenchType;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.RecipeOverrideConfig;
import endgame.plugin.config.RecipeOverrideConfig.Ingredient;
import endgame.plugin.config.RecipeOverrideConfig.RecipeEntry;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified recipe override system.
 *
 * At startup (apply()):
 *   - Auto-populates RecipeOverrides.json with mod recipe defaults
 *   - Applies input/output/bench/craftTime modifications via reflection
 *   - Builds a set of disabled recipe IDs for runtime blocking
 *
 * At runtime (CraftRecipeEvent.Pre):
 *   - Blocks any craft whose recipe ID matches a disabled entry
 *   - Uses startsWith matching so "Warden_Challenge_I" also blocks "Warden_Challenge_I_Generated_0"
 *
 * Changes require a server restart to take effect.
 */
public class RecipeOverrideSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.RecipeOverrides");
    private static final String ENDGAME_PREFIX = "Endgame_";

    /**
     * Non-Endgame_ recipe IDs to auto-populate in RecipeOverrides.json.
     */
    private static final Set<String> EXTRA_RECIPE_IDS = Set.of(
            "Bench_Endgame", "Ore_Mithril", "Ore_Onyxium",
            "Ingredient_Bar_Prisma", "Ingredient_Hide_Prismic",
            "Warden_Challenge_I", "Warden_Challenge_II",
            "Warden_Challenge_III", "Warden_Challenge_IV",
            "Advanced_Glider", "Standard_Glider", "Endgame_Glider",
            "Hedera_Key"
    );

    private static final Set<String> DISABLED_BY_DEFAULT = Set.of("Ore_Mithril", "Ore_Onyxium");

    /** Recipe IDs (or prefixes) that are disabled — populated during apply(). */
    private final Set<String> disabledRecipes = ConcurrentHashMap.newKeySet();

    private final EndgameQoL plugin;

    public RecipeOverrideSystem(EndgameQoL plugin) {
        super(CraftRecipeEvent.Pre.class);
        this.plugin = plugin;
    }

    // =========================================================================
    // Startup: apply overrides from RecipeOverrides.json
    // =========================================================================

    /**
     * Apply recipe overrides. Called from EndgameQoL.start().
     */
    public void apply(@Nonnull Config<RecipeOverrideConfig> config) {
        try {
            RecipeOverrideConfig overrideConfig = config.get();
            Map<String, RecipeEntry> overrides = overrideConfig.getRecipeOverrides();
            Map<String, CraftingRecipe> recipeMap = CraftingRecipe.getAssetMap().getAssetMap();

            // Remove stale overrides for recipes that no longer exist in the asset map
            int removed = purgeStaleOverrides(overrides, recipeMap);

            // Sync existing overrides with current asset defaults (mod update changed a recipe)
            int synced = syncDefaults(overrides, recipeMap);

            // Migration v1: force-disable recipes that should have been disabled by default
            int migrated = 0;
            if (overrideConfig.getConfigVersion() < 1) {
                for (Map.Entry<String, RecipeEntry> entry : overrides.entrySet()) {
                    if (isDisabledByDefault(entry.getKey()) && entry.getValue().isEnabled()) {
                        entry.getValue().setEnabled(false);
                        migrated++;
                    }
                }
                overrideConfig.setConfigVersion(1);
            }

            // Populate defaults for missing recipes (first boot or plugin update)
            int newRecipes = populateDefaults(overrides, recipeMap);
            if (newRecipes > 0 || removed > 0 || synced > 0 || migrated > 0) {
                config.save();
                if (newRecipes > 0)
                    LOGGER.atInfo().log("[EndgameQoL] Added %d new recipe(s) to RecipeOverrides.json", newRecipes);
                if (removed > 0)
                    LOGGER.atInfo().log("[EndgameQoL] Removed %d stale recipe(s) from RecipeOverrides.json", removed);
                if (synced > 0)
                    LOGGER.atInfo().log("[EndgameQoL] Synced %d recipe(s) with updated asset defaults", synced);
                if (migrated > 0)
                    LOGGER.atInfo().log("[EndgameQoL] Migration v1: disabled %d recipe(s) that should have been off by default", migrated);
            }

            // Apply overrides
            int disabled = 0;
            int modified = 0;
            int errors = 0;

            for (Map.Entry<String, RecipeEntry> entry : overrides.entrySet()) {
                String recipeId = entry.getKey();
                RecipeEntry override = entry.getValue();

                try {
                    if (!override.isEnabled()) {
                        // Add to disabled set — runtime event handler will block crafts
                        disabledRecipes.add(recipeId);
                        disabled++;
                    } else {
                        // Try to apply modifications via reflection
                        CraftingRecipe existing = recipeMap.get(recipeId);
                        if (existing != null && hasChanges(existing, override)) {
                            applyOverride(existing, override);
                            modified++;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.atWarning().withCause(e).log("[RecipeOverrides] Error processing recipe '%s'", recipeId);
                    errors++;
                }
            }

            if (disabled > 0 || modified > 0 || errors > 0) {
                LOGGER.atInfo().log("[EndgameQoL] Recipe overrides: %d disabled, %d modified, %d errors",
                        disabled, modified, errors);
            }

        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[EndgameQoL] Failed to apply recipe overrides");
        }
    }

    // =========================================================================
    // Runtime: block disabled recipes via CraftRecipeEvent.Pre
    // =========================================================================

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull CraftRecipeEvent.Pre event) {

        if (disabledRecipes.isEmpty()) return;

        String recipeId = String.valueOf(event.getCraftedRecipe().getId());

        for (String disabled : disabledRecipes) {
            // Exact match OR prefix match for _Generated_N suffixes
            if (recipeId.equals(disabled) || recipeId.startsWith(disabled + "_Generated")) {
                plugin.getLogger().atFine().log("[RecipeOverrides] Blocking disabled recipe: %s (rule: %s)",
                        recipeId, disabled);
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    // =========================================================================
    // Auto-populate defaults
    // =========================================================================

    /**
     * Remove overrides for recipes that no longer exist in the asset map
     * (e.g. recipe was removed from item JSON in a plugin update).
     */
    private static int purgeStaleOverrides(@Nonnull Map<String, RecipeEntry> overrides,
                                           @Nonnull Map<String, CraftingRecipe> recipeMap) {
        int removed = 0;
        var it = overrides.entrySet().iterator();
        while (it.hasNext()) {
            String id = it.next().getKey();
            if (!recipeMap.containsKey(id)) {
                it.remove();
                removed++;
                LOGGER.atFine().log("[RecipeOverrides] Purged stale recipe: %s", id);
            }
        }
        return removed;
    }

    /**
     * Sync existing override entries with current asset values.
     * When the mod developer changes a recipe in JSON (e.g. bench tier, inputs, craft time),
     * the config entry is updated to match the new asset defaults.
     * Only the Enabled flag is preserved (admin choice).
     */
    private static int syncDefaults(@Nonnull Map<String, RecipeEntry> overrides,
                                    @Nonnull Map<String, CraftingRecipe> recipeMap) {
        int synced = 0;
        for (Map.Entry<String, RecipeEntry> entry : overrides.entrySet()) {
            String id = entry.getKey();
            CraftingRecipe recipe = recipeMap.get(id);
            if (recipe == null) continue;

            RecipeEntry existing = entry.getValue();

            // Skip entries that were manually modified by the admin
            if (existing.isModified()) continue;

            boolean changed = false;

            // Sync bench tier
            BenchRequirement[] benchReqs = recipe.getBenchRequirement();
            if (benchReqs != null && benchReqs.length > 0) {
                if (existing.getBenchTier() != benchReqs[0].requiredTierLevel) {
                    existing.setBenchTier(benchReqs[0].requiredTierLevel);
                    changed = true;
                }
                if (existing.getBenchId() == null || !existing.getBenchId().equals(benchReqs[0].id)) {
                    existing.setBenchId(benchReqs[0].id);
                    changed = true;
                }
            }

            // Sync craft time
            if (Math.abs(existing.getCraftTime() - recipe.getTimeSeconds()) > 0.001f) {
                existing.setCraftTime(recipe.getTimeSeconds());
                changed = true;
            }

            // Sync output
            MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
            if (primaryOutput != null) {
                if (existing.getOutputQuantity() != primaryOutput.getQuantity()) {
                    existing.setOutputQuantity(primaryOutput.getQuantity());
                    changed = true;
                }
                String outputId = primaryOutput.getItemId();
                if (outputId != null && !outputId.equals(existing.getOutput())) {
                    existing.setOutput(outputId);
                    changed = true;
                }
            }

            // Sync inputs
            MaterialQuantity[] assetInputs = recipe.getInput();
            if (assetInputs != null) {
                Ingredient[] overrideInputs = existing.getInputs();
                boolean inputsDiffer = overrideInputs == null || overrideInputs.length != assetInputs.length;
                if (!inputsDiffer) {
                    for (int i = 0; i < assetInputs.length; i++) {
                        String assetItemId = assetInputs[i].getItemId();
                        if (assetItemId == null) assetItemId = assetInputs[i].getResourceTypeId();
                        if (!String.valueOf(assetItemId).equals(overrideInputs[i].getItem())
                                || assetInputs[i].getQuantity() != overrideInputs[i].getQuantity()) {
                            inputsDiffer = true;
                            break;
                        }
                    }
                }
                if (inputsDiffer) {
                    Ingredient[] newInputs = new Ingredient[assetInputs.length];
                    for (int i = 0; i < assetInputs.length; i++) {
                        String itemId = assetInputs[i].getItemId();
                        if (itemId == null) itemId = assetInputs[i].getResourceTypeId();
                        newInputs[i] = new Ingredient(
                                itemId != null ? itemId : "Unknown",
                                assetInputs[i].getQuantity());
                    }
                    existing.setInputs(newInputs);
                    changed = true;
                }
            }

            if (changed) {
                LOGGER.atFine().log("[RecipeOverrides] Synced recipe defaults: %s", id);
                synced++;
            }
        }
        return synced;
    }

    private static int populateDefaults(@Nonnull Map<String, RecipeEntry> overrides,
                                        @Nonnull Map<String, CraftingRecipe> recipeMap) {
        int added = 0;
        for (Map.Entry<String, CraftingRecipe> entry : recipeMap.entrySet()) {
            String id = entry.getKey();
            if (id == null) continue;
            if (!id.startsWith(ENDGAME_PREFIX) && !EXTRA_RECIPE_IDS.contains(id) && !matchesExtraRecipe(id)) {
                continue;
            }

            if (overrides.containsKey(id)) continue;

            CraftingRecipe recipe = entry.getValue();
            RecipeEntry re = new RecipeEntry();
            re.setEnabled(!isDisabledByDefault(id));

            // Convert inputs
            MaterialQuantity[] inputs = recipe.getInput();
            if (inputs != null) {
                Ingredient[] ingredients = new Ingredient[inputs.length];
                for (int i = 0; i < inputs.length; i++) {
                    String itemId = inputs[i].getItemId();
                    if (itemId == null) {
                        itemId = inputs[i].getResourceTypeId();
                    }
                    ingredients[i] = new Ingredient(
                            itemId != null ? itemId : "Unknown",
                            inputs[i].getQuantity());
                }
                re.setInputs(ingredients);
            }

            // Convert output
            MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
            if (primaryOutput != null) {
                re.setOutput(primaryOutput.getItemId());
                re.setOutputQuantity(primaryOutput.getQuantity());
            }

            // Convert bench requirement
            BenchRequirement[] benchReqs = recipe.getBenchRequirement();
            if (benchReqs != null && benchReqs.length > 0) {
                re.setBenchId(benchReqs[0].id);
                re.setBenchTier(benchReqs[0].requiredTierLevel);
            }

            re.setCraftTime(recipe.getTimeSeconds());

            overrides.put(id, re);
            added++;
        }
        return added;
    }

    /**
     * Check if an asset map recipe ID matches an EXTRA_RECIPE_IDS entry
     * after stripping _Generated_0 or _Recipe_Generated_0 suffixes.
     */
    private static boolean matchesExtraRecipe(String id) {
        String stripped = id.replace("_Recipe_Generated_0", "")
                .replace("_Generated_0", "");
        return EXTRA_RECIPE_IDS.contains(stripped);
    }

    private static boolean isDisabledByDefault(String id) {
        String stripped = id.replace("_Recipe_Generated_0", "")
                .replace("_Generated_0", "");
        return DISABLED_BY_DEFAULT.contains(stripped);
    }

    // =========================================================================
    // Modification logic (reflection-based)
    // =========================================================================

    private static boolean hasChanges(@Nonnull CraftingRecipe existing, @Nonnull RecipeEntry override) {
        if (Math.abs(existing.getTimeSeconds() - override.getCraftTime()) > 0.001f) {
            return true;
        }

        BenchRequirement[] benchReqs = existing.getBenchRequirement();
        if (benchReqs != null && benchReqs.length > 0) {
            if (override.getBenchId() != null && !override.getBenchId().equals(benchReqs[0].id)) {
                return true;
            }
            if (override.getBenchTier() != benchReqs[0].requiredTierLevel) {
                return true;
            }
        }

        MaterialQuantity primaryOutput = existing.getPrimaryOutput();
        if (primaryOutput != null && override.getOutputQuantity() != primaryOutput.getQuantity()) {
            return true;
        }

        if (primaryOutput != null && override.getOutput() != null
                && !override.getOutput().equals(primaryOutput.getItemId())) {
            return true;
        }

        MaterialQuantity[] existingInputs = existing.getInput();
        Ingredient[] overrideInputs = override.getInputs();
        if (existingInputs != null && overrideInputs != null) {
            if (existingInputs.length != overrideInputs.length) {
                return true;
            }
            for (int i = 0; i < existingInputs.length; i++) {
                String existingItemId = existingInputs[i].getItemId();
                if (existingItemId == null) existingItemId = existingInputs[i].getResourceTypeId();
                String overrideItemId = overrideInputs[i].getItem();

                if (overrideItemId != null && !overrideItemId.equals(existingItemId)) {
                    return true;
                }
                if (existingInputs[i].getQuantity() != overrideInputs[i].getQuantity()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void applyOverride(@Nonnull CraftingRecipe existing, @Nonnull RecipeEntry override) throws Exception {
        Ingredient[] overrideInputs = override.getInputs();
        if (overrideInputs != null) {
            MaterialQuantity[] inputs = new MaterialQuantity[overrideInputs.length];
            for (int i = 0; i < overrideInputs.length; i++) {
                inputs[i] = new MaterialQuantity(
                        overrideInputs[i].getItem(),
                        null, null,
                        overrideInputs[i].getQuantity(),
                        null);
            }
            setField(existing, "input", inputs);
        }

        if (override.getOutput() != null) {
            MaterialQuantity primaryOutput = new MaterialQuantity(
                    override.getOutput(),
                    null, null,
                    override.getOutputQuantity(),
                    null);
            setField(existing, "primaryOutput", primaryOutput);
            setField(existing, "outputs", new MaterialQuantity[]{primaryOutput});
        }

        setField(existing, "primaryOutputQuantity", override.getOutputQuantity());

        if (override.getBenchId() != null) {
            // Preserve existing categories so bench resolution isn't broken
            String[] existingCategories = null;
            BenchRequirement[] oldBenchReqs = existing.getBenchRequirement();
            if (oldBenchReqs != null && oldBenchReqs.length > 0) {
                existingCategories = oldBenchReqs[0].categories;
            }
            BenchRequirement[] benchReqs = new BenchRequirement[]{
                    new BenchRequirement(
                            BenchType.Crafting,
                            override.getBenchId(),
                            existingCategories,
                            override.getBenchTier())
            };
            setField(existing, "benchRequirement", benchReqs);
        }

        setField(existing, "timeSeconds", override.getCraftTime());
    }

    private static void setField(@Nonnull Object target, @Nonnull String fieldName, @Nonnull Object value) throws Exception {
        Field field = CraftingRecipe.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
