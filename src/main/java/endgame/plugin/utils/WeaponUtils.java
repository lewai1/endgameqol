package endgame.plugin.utils;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Shared utility methods for weapon effect systems.
 */
public final class WeaponUtils {

    private WeaponUtils() {}

    /**
     * Check if the entity has an active signature interaction chain.
     * Returns the chain ID if found, -1 otherwise.
     */
    public static int getActiveSignatureChainId(Store<EntityStore> store, Ref<EntityStore> entityRef) {
        ComponentType<EntityStore, InteractionManager> imType = InteractionModule.get().getInteractionManagerComponent();
        if (imType == null) return -1;

        InteractionManager im = store.getComponent(entityRef, imType);
        if (im == null) return -1;

        for (InteractionChain chain : im.getChains().values()) {
            RootInteraction root = chain.getInitialRootInteraction();
            if (root != null) {
                String id = root.getId();
                if (id != null && id.contains("Signature")) {
                    return chain.getChainId();
                }
            }
        }
        return -1;
    }
}
