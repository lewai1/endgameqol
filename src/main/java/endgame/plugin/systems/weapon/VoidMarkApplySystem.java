package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.VoidMarkComponent;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.utils.VoidMarkTracker;
import endgame.plugin.utils.WeaponUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Applies Void Mark on entities hit by Prisma Daggers basic attacks (non-signature).
 * Marks are tracked both as an ECS component on the target and in VoidMarkTracker
 * for cross-system lookup by DaggerVanishSystem.
 *
 * Runs in the default (inspect) damage group — after damage is applied.
 */
public class VoidMarkApplySystem extends DamageEventSystem {

    private static final String PRISMA_DAGGER_ID = "Endgame_Daggers_Prisma";

    private final EndgameQoL plugin;
    private final ComponentType<EntityStore, VoidMarkComponent> voidMarkType;
    private final VoidMarkTracker tracker;

    public VoidMarkApplySystem(EndgameQoL plugin,
                                ComponentType<EntityStore, VoidMarkComponent> voidMarkType,
                                VoidMarkTracker tracker) {
        this.plugin = plugin;
        this.voidMarkType = voidMarkType;
        this.tracker = tracker;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        // Default (inspect) group — runs after damage applied
        return null;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return EntityStatMap.getComponentType();
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage
    ) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isVoidMarkEnabled()) return;

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Use attacker's own store — handle() store belongs to the target
        Store<EntityStore> attackerStore = attackerRef.getStore();

        Player attacker = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) return;

        if (!isWieldingPrismaDaggers(attackerStore, attackerRef)) return;

        // Only on basic hits — skip if signature is active
        int signatureChainId = WeaponUtils.getActiveSignatureChainId(attackerStore, attackerRef);
        if (signatureChainId != -1) return;

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;
        if (attackerRef.equals(targetRef)) return;

        UUID attackerUuid = endgame.plugin.utils.EntityUtils.getUuid(attackerStore, attackerRef);
        if (attackerUuid == null) return;

        int durationMs = config.getVoidMarkDurationMs();
        long expiry = System.currentTimeMillis() + durationMs;

        // Use commandBuffer.run() to defer check-then-add to buffer consumption time.
        // Prevents duplicate addComponent crash when multiple Prisma Dagger users hit the same target in one tick.
        commandBuffer.run(s -> {
            VoidMarkComponent existing = s.getComponent(targetRef, voidMarkType);
            if (existing != null) {
                existing.refresh(attackerUuid, durationMs);
            } else {
                VoidMarkComponent mark = new VoidMarkComponent(attackerUuid, System.currentTimeMillis(), durationMs);
                s.addComponent(targetRef, voidMarkType, mark);
            }
        });

        // Update tracker with current target position
        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
        Vector3d targetPos = targetTransform != null ? targetTransform.getPosition() : null;
        if (targetPos != null) {
            tracker.addMark(targetRef, attackerUuid, expiry, targetPos);
        }

        plugin.getLogger().atFine().log("[VoidMarkApply] Marked target (duration=%dms)", durationMs);
    }

    private boolean isWieldingPrismaDaggers(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return false;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem != null && !ItemStack.isEmpty(activeItem)) {
            Item item = activeItem.getItem();
            if (item != null && PRISMA_DAGGER_ID.equals(item.getId())) {
                return true;
            }
        }
        return false;
    }

}
