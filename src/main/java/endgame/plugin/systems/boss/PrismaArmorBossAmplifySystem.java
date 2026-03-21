package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Doubles damage taken by players wearing Prisma armor when hit by
 * Dragon Frost or Hedera. Runs in FilterDamageGroup to modify damage
 * before it is applied.
 *
 * Logic:
 *   1. Check if the damage target is a player wearing any Prisma armor piece
 *   2. Check if the attacker is Dragon Frost or Hedera
 *   3. If both, double the damage
 *
 * Thread-safe: no mutable state, all reads from immutable entity components.
 */
public class PrismaArmorBossAmplifySystem extends AbstractBossDamageSystem {

    private static final Set<String> AMPLIFY_BOSSES = Set.of(
            "Endgame_Dragon_Frost",
            "Endgame_Hedera"
    );

    private static final float DAMAGE_MULTIPLIER = 2.0f;

    private final EndgameQoL plugin;

    public PrismaArmorBossAmplifySystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY_ANY;
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {

        if (!plugin.getConfig().get().isPrismaArmorVulnerabilityEnabled()) return;

        // 1. Check if the damage target is a player
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        Store<EntityStore> targetStore = targetRef.getStore();
        Player player = targetStore.getComponent(targetRef, Player.getComponentType());
        if (player == null) return;

        // 2. Check if the attacker is an amplify boss
        Ref<EntityStore> attackerRef = resolveAttacker(damage);
        if (attackerRef == null) return;

        String attackerNpcId = resolveNPCTypeId(attackerRef, store);
        if (attackerNpcId == null || !AMPLIFY_BOSSES.contains(attackerNpcId)) return;

        // 3. Check if the player is wearing any Prisma armor
        InventoryComponent.Armor armorComponent = targetStore.getComponent(targetRef, InventoryComponent.Armor.getComponentType());
        if (armorComponent == null) return;

        ItemContainer armor = armorComponent.getInventory();
        if (armor == null) return;

        boolean wearsPrisma = false;
        short capacity = armor.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = armor.getItemStack(slot);
            if (itemStack == null || itemStack.isEmpty()) continue;
            if (itemStack.getItem() == null) continue;
            String itemId = itemStack.getItem().getId();
            if (itemId != null && itemId.startsWith("Armor_Prisma_")) {
                wearsPrisma = true;
                break;
            }
        }

        if (!wearsPrisma) return;

        // 4. Double the damage
        float original = damage.getAmount();
        damage.setAmount(original * DAMAGE_MULTIPLIER);
        plugin.getLogger().atFine().log(
                "[PrismaArmorBossAmplify] %s hit player wearing Prisma armor: %.1f -> %.1f",
                attackerNpcId, original, original * DAMAGE_MULTIPLIER);
    }
}
