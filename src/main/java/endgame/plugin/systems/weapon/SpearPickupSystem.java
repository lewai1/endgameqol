package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;

/**
 * Drops the spear item at the landing position when a thrown spear finishes its flight,
 * so players can pick it back up (vanilla spears are lost on throw — this fixes it for endgame spears).
 */
public class SpearPickupSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.SpearPickup");

    private static final Map<String, String> SPEAR_PROJECTILE_TO_ITEM = Map.of(
            "Endgame_Spear_Crude", "Weapon_Spear_Crude",
            "Endgame_Spear_Copper", "Weapon_Spear_Copper",
            "Endgame_Spear_Iron", "Weapon_Spear_Iron",
            "Endgame_Spear_Thorium", "Weapon_Spear_Thorium",
            "Endgame_Spear_Cobalt", "Weapon_Spear_Cobalt",
            "Endgame_Spear_Mithril", "Weapon_Spear_Mithril",
            "Endgame_Spear_Onyxium", "Weapon_Spear_Onyxium",
            "Endgame_Spear_Adamantite", "Weapon_Spear_Adamantite"
    );

    private final Set<Ref<EntityStore>> handled = Collections.newSetFromMap(new WeakHashMap<>());

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
                ProjectileComponent.getComponentType(),
                TransformComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        ProjectileComponent proj = chunk.getComponent(index, ProjectileComponent.getComponentType());
        if (proj == null) return;

        String assetName = proj.getProjectileAssetName();
        if (assetName == null) return;

        String itemId = SPEAR_PROJECTILE_TO_ITEM.get(assetName);
        if (itemId == null) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (handled.contains(ref)) return;

        // Catch both ground hits (Resting/onGround) and entity hits (Impacted/Inactive)
        SimplePhysicsProvider physics = proj.getSimplePhysicsProvider();
        boolean landed = proj.isOnGround()
                || (physics != null && (physics.isImpacted() || physics.isResting()));
        if (!landed) return;

        handled.add(ref);

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3d dropPos = transform.getPosition().clone();
        LOGGER.atFine().log("[SpearPickup] Spawning %s item at %s", itemId, dropPos);
        try {
            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(
                    store,
                    java.util.List.of(new ItemStack(itemId, 1)),
                    dropPos,
                    Vector3f.ZERO);
            buffer.addEntities(holders, AddReason.SPAWN);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[SpearPickup] Failed to spawn item drop");
        }
    }
}
