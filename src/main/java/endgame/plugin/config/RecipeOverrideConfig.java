package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config file for recipe overrides. Stored as RecipeOverrides.json.
 * Admins can change ingredients, quantities, bench, craft time, or disable recipes.
 */
public class RecipeOverrideConfig {

    // === Ingredient (input material) ===

    public static class Ingredient {
        private String item;
        private int quantity = 1;

        @Nonnull
        public static final BuilderCodec<Ingredient> CODEC = BuilderCodec
                .builder(Ingredient.class, Ingredient::new)
                .append(new KeyedCodec<String>("Item", Codec.STRING),
                        (i, value) -> i.item = value,
                        i -> i.item)
                .add()
                .append(new KeyedCodec<Integer>("Quantity", Codec.INTEGER),
                        (i, value) -> i.quantity = value != null ? value : 1,
                        i -> i.quantity)
                .add()
                .build();

        public Ingredient() {}

        public Ingredient(String item, int quantity) {
            this.item = item;
            this.quantity = quantity;
        }

        public String getItem() { return item; }
        public void setItem(String item) { this.item = item; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    // === RecipeEntry (one recipe override) ===

    public static class RecipeEntry {
        private boolean enabled = true;
        private boolean modified = false;
        private Ingredient[] inputs;
        private String output;
        private int outputQuantity = 1;
        private String benchId;
        private int benchTier = 0;
        private float craftTime = 5.0f;

        @Nonnull
        public static final BuilderCodec<RecipeEntry> CODEC = BuilderCodec
                .builder(RecipeEntry.class, RecipeEntry::new)
                .append(new KeyedCodec<Boolean>("Enabled", Codec.BOOLEAN),
                        (r, value) -> r.enabled = value != null ? value : true,
                        r -> r.enabled)
                .add()
                .append(new KeyedCodec<Boolean>("Modified", Codec.BOOLEAN),
                        (r, value) -> r.modified = value != null ? value : false,
                        r -> r.modified)
                .add()
                .append(new KeyedCodec<Ingredient[]>("Inputs",
                                new ArrayCodec<>(Ingredient.CODEC, Ingredient[]::new)),
                        (r, value) -> r.inputs = value,
                        r -> r.inputs)
                .add()
                .append(new KeyedCodec<String>("Output", Codec.STRING),
                        (r, value) -> r.output = value,
                        r -> r.output)
                .add()
                .append(new KeyedCodec<Integer>("OutputQuantity", Codec.INTEGER),
                        (r, value) -> r.outputQuantity = value != null ? value : 1,
                        r -> r.outputQuantity)
                .add()
                .append(new KeyedCodec<String>("BenchId", Codec.STRING),
                        (r, value) -> r.benchId = value,
                        r -> r.benchId)
                .add()
                .append(new KeyedCodec<Integer>("BenchTier", Codec.INTEGER),
                        (r, value) -> r.benchTier = value != null ? value : 0,
                        r -> r.benchTier)
                .add()
                .append(new KeyedCodec<Float>("CraftTime", Codec.FLOAT),
                        (r, value) -> r.craftTime = value != null ? value : 5.0f,
                        r -> r.craftTime)
                .add()
                .build();

        public RecipeEntry() {}

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isModified() { return modified; }
        public void setModified(boolean modified) { this.modified = modified; }
        public Ingredient[] getInputs() { return inputs; }
        public void setInputs(Ingredient[] inputs) { this.inputs = inputs; }
        public String getOutput() { return output; }
        public void setOutput(String output) { this.output = output; }
        public int getOutputQuantity() { return outputQuantity; }
        public void setOutputQuantity(int outputQuantity) { this.outputQuantity = outputQuantity; }
        public String getBenchId() { return benchId; }
        public void setBenchId(String benchId) { this.benchId = benchId; }
        public int getBenchTier() { return benchTier; }
        public void setBenchTier(int benchTier) { this.benchTier = benchTier; }
        public float getCraftTime() { return craftTime; }
        public void setCraftTime(float craftTime) { this.craftTime = craftTime; }
    }

    // === Top-level config ===

    @Nonnull
    public static final BuilderCodec<RecipeOverrideConfig> CODEC = BuilderCodec
            .builder(RecipeOverrideConfig.class, RecipeOverrideConfig::new)
            .append(new KeyedCodec<Integer>("ConfigVersion", Codec.INTEGER),
                    (config, value) -> config.configVersion = value != null ? value : 0,
                    config -> config.configVersion)
            .add()
            .append(new KeyedCodec<Map<String, RecipeEntry>>("RecipeOverrides",
                            new MapCodec<>(RecipeEntry.CODEC, LinkedHashMap::new, false)),
                    (config, value) -> {
                        if (value != null) {
                            config.recipeOverrides.putAll(value);
                        }
                    },
                    config -> config.recipeOverrides)
            .add()
            .build();

    private int configVersion = 0;
    private final Map<String, RecipeEntry> recipeOverrides = new LinkedHashMap<>();

    public int getConfigVersion() { return configVersion; }
    public void setConfigVersion(int configVersion) { this.configVersion = configVersion; }

    @Nonnull
    public Map<String, RecipeEntry> getRecipeOverrides() {
        return recipeOverrides;
    }
}
