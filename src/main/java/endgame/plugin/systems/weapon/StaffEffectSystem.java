package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies on-hit effects when Adamantite+ staff projectiles damage entities.
 * Detects projectile hits from players wielding high-tier staffs and applies
 * the appropriate elemental effect (burn, chill, void resonance, void corruption).
 */
public class StaffEffectSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(
            EffectControllerComponent.getComponentType()
    );

    private final EndgameQoL plugin;
    private static final java.util.concurrent.ConcurrentHashMap<String, EntityEffect> cachedEffects = new java.util.concurrent.ConcurrentHashMap<>();

    public StaffEffectSystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage
    ) {
        // Only process projectile hits from players
        if (!(damage.getSource() instanceof Damage.ProjectileSource projectileSource)) {
            return;
        }

        Ref<EntityStore> shooterRef = projectileSource.getRef();
        if (shooterRef == null || !shooterRef.isValid()) {
            return;
        }

        // Check if shooter is a player — use shooter's own store
        Player player = shooterRef.getStore().getComponent(shooterRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        // Get the player's held weapon
        String staffType = getStaffType(shooterRef.getStore(), shooterRef);
        if (staffType == null) {
            return;
        }

        // Get the effect to apply based on staff type
        String effectId = getEffectForStaff(staffType);
        if (effectId == null) {
            return;
        }

        // Get target ref and effect controller
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        EffectControllerComponent effectController = store.getComponent(
                targetRef, EffectControllerComponent.getComponentType()
        );
        if (effectController == null) {
            return;
        }

        EntityEffect effect = cachedEffects.get(effectId);
        if (effect == null) {
            effect = EntityEffect.getAssetMap().getAsset(effectId);
            if (effect == null) {
                plugin.getLogger().atFine().log("[StaffEffect] Unknown effect: %s", effectId);
                return;
            }
            cachedEffects.put(effectId, effect);
        }

        float duration = getDurationForStaff(staffType);
        boolean applied = effectController.addEffect(
                targetRef,
                effect,
                duration,
                OverlapBehavior.OVERWRITE,
                store
        );

        if (applied) {
            plugin.getLogger().atFine().log(
                    "[StaffEffectSystem] Applied %s (%.1fs) from %s staff projectile",
                    effectId, duration, staffType
            );
        }
    }

    @Nullable
    private String getStaffType(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return null;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem == null || ItemStack.isEmpty(activeItem)) return null;

        Item item = activeItem.getItem();
        if (item == null) return null;

        String id = item.getId();
        if (id == null) return null;

        // Check for staff types that get on-hit effects (Adamantite+)
        if (id.contains("Staff_Adamantite")) return "Adamantite";
        if (id.contains("Staff_Crystal_Ice")) return "Crystal_Ice";
        if (id.contains("Staff_Crystal_Flame")) return "Crystal_Flame";
        if (id.contains("Staff_Mithril")) return "Mithril";
        if (id.contains("Staff_Onyxium")) return "Onyxium";

        return null;
    }

    @Nullable
    private String getEffectForStaff(String staffType) {
        return switch (staffType) {
            case "Adamantite" -> "Endgame_Staff_Fire_Burn";
            case "Crystal_Ice" -> "Endgame_Frost_Chill";
            case "Crystal_Flame" -> "Endgame_Staff_Fire_Burn";
            case "Mithril" -> "Endgame_Prisma_Resonance";
            case "Onyxium" -> "Endgame_Void_Corruption";
            default -> null;
        };
    }

    private float getDurationForStaff(String staffType) {
        return switch (staffType) {
            case "Crystal_Ice" -> 5.0f;
            default -> 3.0f;
        };
    }
}
