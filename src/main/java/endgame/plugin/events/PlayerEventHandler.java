package endgame.plugin.events;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.RecipeOverrideConfig;
import endgame.plugin.managers.RecipeManager;
import endgame.plugin.utils.I18n;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerEventHandler {

    private static final AtomicBoolean recipesInitialized = new AtomicBoolean(false);

    public static void reset() {
        recipesInitialized.set(false);
    }

    public static void onPlayerReady(PlayerReadyEvent event, EndgameQoL plugin) {
        try {
            // First player join: initialize recipe visibility from RecipeOverrides.json
            if (recipesInitialized.compareAndSet(false, true)) {
                Map<String, RecipeOverrideConfig.RecipeEntry> overrides =
                        plugin.getRecipeOverrideConfig().get().getRecipeOverrides();
                for (Map.Entry<String, RecipeOverrideConfig.RecipeEntry> entry : overrides.entrySet()) {
                    RecipeManager.updateRecipe(entry.getKey(), entry.getValue().isEnabled());
                }
            }

            // Thread-safe: find the PlayerRef by matching entity ref from universe player list.
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> entityRef = event.getPlayerRef();
            if (entityRef == null || !entityRef.isValid()) return;
            for (PlayerRef pRef : Universe.get().getPlayers()) {
                if (pRef != null && entityRef.equals(pRef.getReference())) {
                    RecipeManager.syncRecipesToPlayer(pRef);
                    I18n.sendUpdateTranslationsPacket(pRef);
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().withCause(e).log("[PlayerEventHandler] Error in onPlayerReady");
        }
    }

}
