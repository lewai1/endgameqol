package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * Deducts 2 stamina from the ATTACKER when a charged longsword attack hits.
 * ChangeStat in JSON targets the hit entity (wrong), so this system applies
 * the cost via Java using the attacker's EntityStatMap directly.
 *
 * Detection: Charged attacks deal significantly more damage than normal attacks.
 * For each tier, we set a threshold at 80% of the minimum charged damage.
 * If damage >= threshold, it's a charged attack.
 */
public class LongswordChargedStaminaSystem extends DamageEventSystem {

    private static final String LONGSWORD_ID_PREFIX = "Weapon_Longsword_";
    private static final float STAMINA_COST = -2f;

    // Minimum charged damage threshold per tier (80% of charged swing base damage)
    // Charged swing is the lower of the two charged attacks per tier
    private static final Map<String, Float> CHARGED_THRESHOLDS = Map.of(
            "Weapon_Longsword_Copper", 12f,
            "Weapon_Longsword_Iron", 24f,
            "Weapon_Longsword_Thorium", 40f,
            "Weapon_Longsword_Cobalt", 48f,
            "Weapon_Longsword_Adamantite", 88f,
            "Weapon_Longsword_Mithril", 104f,
            "Weapon_Longsword_Onyxium", 112f
    );

    private final EndgameQoL plugin;

    public LongswordChargedStaminaSystem(EndgameQoL plugin) {
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
        return EntityStatMap.getComponentType();
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage
    ) {
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Use attacker's own store — handle() store belongs to the target
        Store<EntityStore> attackerStore = attackerRef.getStore();

        Player attacker = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) return;

        String weaponId = getWieldedLongswordId(attackerStore, attackerRef);
        if (weaponId == null) return;

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;
        if (attackerRef.equals(targetRef)) return;

        Float threshold = CHARGED_THRESHOLDS.get(weaponId);
        if (threshold == null) return;

        float damageAmount = damage.getAmount();
        if (damageAmount < threshold) return;

        // This is a charged attack — deduct stamina from the attacker
        ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
        if (statType == null) return;

        EntityStatMap attackerStats = attackerStore.getComponent(attackerRef, statType);
        if (attackerStats == null) return;

        int staminaStat = DefaultEntityStatTypes.getStamina();
        EntityStatValue staminaValue = attackerStats.get(staminaStat);
        if (staminaValue == null) return;
        attackerStats.addStatValue(staminaStat, STAMINA_COST);

        plugin.getLogger().atFine().log(
                "[LongswordCharged] Deducted 2 stamina from attacker (weapon: %s, damage: %.1f, threshold: %.1f)",
                weaponId, damageAmount, threshold);
    }

    @Nullable
    private String getWieldedLongswordId(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return null;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem != null && !ItemStack.isEmpty(activeItem)) {
            Item item = activeItem.getItem();
            if (item != null) {
                String id = item.getId();
                if (id != null && id.startsWith(LONGSWORD_ID_PREFIX)) {
                    return id;
                }
            }
        }
        return null;
    }
}
