package endgame.plugin.managers;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateRecipes;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages recipe enable/disable state and client sync.
 * Tiers and recipe values come from the JSON assets — this class never modifies them.
 */
public class RecipeManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.RecipeManager");

    // Track which recipes are currently disabled (for client-side removal sync)
    private static final Set<String> disabledRecipeIds = ConcurrentHashMap.newKeySet();

    public static void reset() {
        disabledRecipeIds.clear();
    }

    /**
     * Enable or disable a recipe. Disabled recipes are hidden from the client via Remove packets.
     * Recipe tiers/values are NEVER modified — the JSON asset is the sole source of truth.
     */
    public static void updateRecipe(String recipeId, boolean enabled) {
        Map<String, CraftingRecipe> recipeMap = CraftingRecipe.getAssetMap().getAssetMap();
        List<CraftingRecipe> updatedRecipes = new ArrayList<>();
        List<String> removedRecipeIds = new ArrayList<>();

        // Try direct lookup by recipe ID first
        CraftingRecipe directMatch = recipeMap.get(recipeId);
        if (directMatch != null) {
            handleRecipe(directMatch, enabled, updatedRecipes, removedRecipeIds);
        } else {
            // Fall back to linear scan for output ID matching
            for (CraftingRecipe recipe : recipeMap.values()) {
                MaterialQuantity output = recipe.getPrimaryOutput();
                String outputId = (output != null) ? output.getItemId() : null;
                if (outputId != null && recipeId.equals(outputId)) {
                    LOGGER.atFine().log("Found target recipe by output: %s (Output: %s)", recipe.getId(), outputId);
                    handleRecipe(recipe, enabled, updatedRecipes, removedRecipeIds);
                }
            }
        }

        if (updatedRecipes.isEmpty() && removedRecipeIds.isEmpty()) {
            LOGGER.atWarning().log("Recipe '%s' not found in asset map (direct lookup and output scan both failed)", recipeId);
        }

        // Sync changes to players: AddOrUpdate for enabled, Remove for disabled
        if (!updatedRecipes.isEmpty()) {
            sendRecipeUpdatesToAllPlayers(updatedRecipes);
        }
        if (!removedRecipeIds.isEmpty()) {
            sendRecipeRemovalToAllPlayers(removedRecipeIds);
        }
    }

    private static void handleRecipe(CraftingRecipe recipe, boolean enabled,
                                      List<CraftingRecipe> updatedRecipes, List<String> removedRecipeIds) {
        String id = recipe.getId();
        LOGGER.atFine().log("Processing recipe: %s (enabled=%b)", id, enabled);

        if (enabled) {
            updatedRecipes.add(recipe);
            disabledRecipeIds.remove(id);
        } else {
            removedRecipeIds.add(id);
            disabledRecipeIds.add(id);
            LOGGER.atFine().log("Disabling recipe: %s (client removal only)", id);
        }
    }

    public static void syncRecipesToPlayer(PlayerRef player) {
        if (disabledRecipeIds.isEmpty()) return;

        Map<String, CraftingRecipe> recipeMap = CraftingRecipe.getAssetMap().getAssetMap();
        List<String> recipesToRemove = new ArrayList<>();

        for (String id : disabledRecipeIds) {
            if (recipeMap.containsKey(id)) {
                recipesToRemove.add(id);
            }
        }

        if (!recipesToRemove.isEmpty()) {
            sendRecipeRemovalToPlayer(player, recipesToRemove);
        }
    }

    private static void sendRecipeUpdatesToAllPlayers(List<CraftingRecipe> recipes) {
        try {
            UpdateRecipes packet = new UpdateRecipes();
            packet.type = UpdateType.AddOrUpdate;
            packet.recipes = new Object2ObjectOpenHashMap<>();

            for (CraftingRecipe recipe : recipes) {
                String id = recipe.getId();
                if (id != null && packet.recipes != null) {
                    packet.recipes.put(id, recipe.toPacket(id));
                }
            }

            // Broadcast to all online players
            Universe.get().getWorlds().forEach((worldName, world) -> {
                for (PlayerRef player : world.getPlayerRefs()) {
                    try {
                        player.getPacketHandler().write(packet);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Failed to send recipe update to player: %s",
                                player.getUsername());
                    }
                }
            });

            LOGGER.atFine().log("Synced %d recipes to all clients", recipes.size());

        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to construct/send recipe update packet");
        }
    }

    /**
     * Send recipe removal packets using UpdateType.Remove.
     * CRITICAL: Must use Remove type (not AddOrUpdate) — the client handler for AddOrUpdate
     * iterates the recipes map without a null check, causing NullReferenceException when
     * recipes is null. Remove type only processes removedRecipes.
     */
    private static void sendRecipeRemovalToAllPlayers(List<String> recipeIds) {
        try {
            UpdateRecipes packet = new UpdateRecipes();
            packet.type = UpdateType.Remove;
            packet.removedRecipes = recipeIds.toArray(new String[0]);

            Universe.get().getWorlds().forEach((worldName, world) -> {
                for (PlayerRef player : world.getPlayerRefs()) {
                    try {
                        player.getPacketHandler().write(packet);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Failed to send recipe removal to player: %s",
                                player.getUsername());
                    }
                }
            });

            LOGGER.atFine().log("Sent recipe removal (%d recipes) to all clients", recipeIds.size());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to construct/send recipe removal packet");
        }
    }

    private static void sendRecipeRemovalToPlayer(PlayerRef player, List<String> recipeIds) {
        try {
            UpdateRecipes packet = new UpdateRecipes();
            packet.type = UpdateType.Remove;
            packet.removedRecipes = recipeIds.toArray(new String[0]);

            player.getPacketHandler().write(packet);
            LOGGER.atFine().log("Sent recipe removal (%d recipes) to player %s",
                    recipeIds.size(), player.getUsername());
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to send recipe removal to player %s",
                    player.getUsername());
        }
    }
}
