package endgame.plugin.config;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Static registry of item requirements for each pet tier transition.
 * Pets tier up by feeding (consuming items from player inventory).
 */
public final class PetTierRequirements {

    private PetTierRequirements() {}

    public record ItemRequirement(@Nonnull String itemId, int quantity) {}

    // Map<petId, Map<targetTier, List<ItemRequirement>>>
    private static final Map<String, Map<PetTier, List<ItemRequirement>>> REQUIREMENTS = Map.of(

        "Endgame_Pet_Dragon_Frost", Map.of(
            PetTier.C, List.of(
                new ItemRequirement("Ingredient_Ice_Essence", 15)),
            PetTier.B, List.of(
                new ItemRequirement("Ingredient_Ice_Essence", 30),
                new ItemRequirement("Ingredient_Bar_Mithril", 5)),
            PetTier.A, List.of(
                new ItemRequirement("Ingredient_Ice_Essence", 50),
                new ItemRequirement("Ingredient_Bar_Mithril", 10),
                new ItemRequirement("Rock_Gem_Sapphire", 3)),
            PetTier.S, List.of(
                new ItemRequirement("Dragon_Heart", 5),
                new ItemRequirement("Ingredient_Bar_Mithril", 15)),
            PetTier.SS, List.of(
                new ItemRequirement("Dragon_Heart", 10),
                new ItemRequirement("Ingredient_Bar_Prisma", 5),
                new ItemRequirement("Rock_Gem_Sapphire", 10))
        ),

        "Endgame_Pet_Dragon_Fire", Map.of(
            PetTier.C, List.of(
                new ItemRequirement("Ingredient_Fire_Essence", 15)),
            PetTier.B, List.of(
                new ItemRequirement("Ingredient_Fire_Essence", 30),
                new ItemRequirement("Ingredient_Bar_Adamantite", 5)),
            PetTier.A, List.of(
                new ItemRequirement("Ingredient_Fire_Essence", 50),
                new ItemRequirement("Ingredient_Bar_Adamantite", 10),
                new ItemRequirement("Ingredient_Hide_Storm", 5)),
            PetTier.S, List.of(
                new ItemRequirement("Ingredient_Fire_Essence", 80),
                new ItemRequirement("Ingredient_Bar_Adamantite", 15)),
            PetTier.SS, List.of(
                new ItemRequirement("Ingredient_Voidheart", 10),
                new ItemRequirement("Ingredient_Bar_Prisma", 5),
                new ItemRequirement("Ingredient_Hide_Storm", 10))
        ),

        "Endgame_Pet_Golem_Void", Map.of(
            PetTier.C, List.of(
                new ItemRequirement("Ingredient_Void_Essence", 15)),
            PetTier.B, List.of(
                new ItemRequirement("Ingredient_Void_Essence", 30),
                new ItemRequirement("Ingredient_Bar_Onyxium", 5)),
            PetTier.A, List.of(
                new ItemRequirement("Ingredient_Void_Essence", 50),
                new ItemRequirement("Ingredient_Bar_Onyxium", 10),
                new ItemRequirement("Rock_Gem_Emerald", 3)),
            PetTier.S, List.of(
                new ItemRequirement("Ingredient_Voidheart", 5),
                new ItemRequirement("Ingredient_Bar_Onyxium", 15)),
            PetTier.SS, List.of(
                new ItemRequirement("Ingredient_Voidheart", 10),
                new ItemRequirement("Ingredient_Bar_Prisma", 5),
                new ItemRequirement("Rock_Gem_Emerald", 10))
        ),

        "Endgame_Pet_Hedera", Map.of(
            PetTier.C, List.of(
                new ItemRequirement("Ingredient_Forest_Essence", 15)),
            PetTier.B, List.of(
                new ItemRequirement("Ingredient_Forest_Essence", 30),
                new ItemRequirement("Endgame_Swamp_Currency", 10)),
            PetTier.A, List.of(
                new ItemRequirement("Ingredient_Forest_Essence", 50),
                new ItemRequirement("Endgame_Swamp_Currency", 20),
                new ItemRequirement("Endgame_Swamp_Ingot", 3)),
            PetTier.S, List.of(
                new ItemRequirement("Hedera_Gem", 5),
                new ItemRequirement("Endgame_Swamp_Currency", 30)),
            PetTier.SS, List.of(
                new ItemRequirement("Hedera_Gem", 10),
                new ItemRequirement("Ingredient_Bar_Prisma", 5),
                new ItemRequirement("Ingredient_Voidheart", 5))
        )
    );

    /**
     * Get the item requirements to reach a target tier. Empty if tier D (no cost) or unknown.
     */
    @Nonnull
    public static List<ItemRequirement> getRequirements(@Nonnull String petId, @Nonnull PetTier targetTier) {
        if (targetTier == PetTier.D) return Collections.emptyList();
        var petReqs = REQUIREMENTS.get(petId);
        if (petReqs == null) return Collections.emptyList();
        return petReqs.getOrDefault(targetTier, Collections.emptyList());
    }

    /**
     * Format an item ID for display (remove prefix, replace underscores).
     */
    @Nonnull
    public static String formatItemName(@Nonnull String itemId) {
        String name = itemId;
        if (name.startsWith("Ingredient_")) name = name.substring(11);
        else if (name.startsWith("Endgame_")) name = name.substring(8);
        else if (name.startsWith("Rock_Gem_")) name = name.substring(9);
        return name.replace("_", " ");
    }
}
