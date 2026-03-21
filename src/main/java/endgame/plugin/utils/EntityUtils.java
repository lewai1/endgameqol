package endgame.plugin.utils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Utility methods for common ECS entity operations.
 * Replaces deprecated Entity.getUuid() / PlayerRef.getUuid() with UUIDComponent lookups.
 */
public final class EntityUtils {

    private EntityUtils() {}

    /**
     * Get UUID from an entity ref using UUIDComponent (ECS replacement for Entity.getUuid()).
     */
    @Nullable
    public static UUID getUuid(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        UUIDComponent comp = store.getComponent(ref, UUIDComponent.getComponentType());
        return comp != null ? comp.getUuid() : null;
    }

    /**
     * Get UUID from a PlayerRef using UUIDComponent (ECS replacement for PlayerRef.getUuid()).
     */
    @Nullable
    public static UUID getUuid(PlayerRef playerRef) {
        if (playerRef == null) return null;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return null;
        UUIDComponent comp = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        return comp != null ? comp.getUuid() : null;
    }

    /**
     * Get UUID from an ArchetypeChunk at a given index using UUIDComponent.
     */
    @Nullable
    public static UUID getUuid(ArchetypeChunk<EntityStore> chunk, int index) {
        UUIDComponent comp = chunk.getComponent(index, UUIDComponent.getComponentType());
        return comp != null ? comp.getUuid() : null;
    }
}
