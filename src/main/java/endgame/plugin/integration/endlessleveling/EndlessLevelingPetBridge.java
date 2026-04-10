package endgame.plugin.integration.endlessleveling;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.hypixel.hytale.logger.HytaleLogger;
import endgame.plugin.EndgameQoL;
import endgame.plugin.api.PetAPI;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Bridge between EndgameQoL pets and EndlessLeveling.
 * Registers PetAPI in EL's manager registry for cross-mod access.
 * Logs EL XP grants for future pet XP sharing.
 *
 * This class is NEVER loaded if EL is absent (Class.forName guard).
 */
public class EndlessLevelingPetBridge {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ELPetBridge");

    private final EndgameQoL plugin;
    private volatile boolean active = false;

    public EndlessLevelingPetBridge(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the bridge. Call after both EndgameQoL and EndlessLeveling are ready.
     */
    public void init() {
        try {
            EndlessLevelingAPI api = EndlessLevelingAPI.get();
            if (api == null) {
                LOGGER.atWarning().log("[ELPetBridge] EndlessLevelingAPI not available");
                return;
            }

            // Register PetAPI class in EL's manager registry
            // External mods: EndlessLevelingAPI.get().getManager("pet_api_eqol")
            api.registerManager("pet_api_eqol", PetAPI.class, false);

            active = true;
            LOGGER.atInfo().log("[ELPetBridge] Pet bridge initialized — PetAPI registered in EL manager registry");
        } catch (Exception e) {
            LOGGER.atWarning().log("[ELPetBridge] Failed to initialize: %s", e.getMessage());
        }
    }

    /**
     * Get EL player level for bonus calculations.
     * Returns 0 if EL is not active or player not found.
     */
    public int getPlayerLevel(@Nonnull UUID playerUuid) {
        if (!active) return 0;
        try {
            return EndlessLevelingAPI.get().getPlayerLevel(playerUuid);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isActive() { return active; }

    public void shutdown() {
        active = false;
    }
}
