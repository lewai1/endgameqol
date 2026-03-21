package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Prevents Prisma swords and daggers from dealing damage to Dragon Frost and Hedera.
 * Runs in the FilterDamageGroup (inherited from AbstractBossDamageSystem) to cancel
 * damage before it is applied.
 *
 * Query targets all entities (damage target = boss NPC), then checks if the attacker
 * is a player wielding a Prisma sword or dagger.
 *
 * Thread-safe: no mutable state, all reads are from immutable entity components.
 */
public class PrismaWeaponBossFilterSystem extends AbstractBossDamageSystem {

    private static final Set<String> IMMUNE_BOSSES = Set.of(
            "Endgame_Dragon_Frost",
            "Endgame_Hedera"
    );

    private static final Set<String> BLOCKED_WEAPON_FRAGMENTS = Set.of(
            "Sword_Prisma",
            "Daggers_Prisma",
            "Daggers_Hedera"
    );

    private final EndgameQoL plugin;

    public PrismaWeaponBossFilterSystem(EndgameQoL plugin) {
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

        if (!plugin.getConfig().get().isPrismaWeaponBossBlockEnabled()) return;

        // 1. Check if the damage target is an immune boss
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        String targetNpcId = resolveNPCTypeId(targetRef, store);
        if (targetNpcId == null || !IMMUNE_BOSSES.contains(targetNpcId)) return;

        // 2. Check if the attacker is a player
        Ref<EntityStore> attackerRef = resolveAttacker(damage);
        if (attackerRef == null) return;

        Store<EntityStore> attackerStore = attackerRef.getStore();
        Player player = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (player == null) return;

        // 3. Check if the player is holding a blocked Prisma weapon
        InventoryComponent.Hotbar hotbar = attackerStore.getComponent(attackerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem == null || ItemStack.isEmpty(activeItem)) return;

        Item item = activeItem.getItem();
        if (item == null || item.getId() == null) return;

        String itemId = item.getId();
        for (String fragment : BLOCKED_WEAPON_FRAGMENTS) {
            if (itemId.contains(fragment)) {
                damage.setCancelled(true);
                plugin.getLogger().atFine().log(
                        "[PrismaWeaponBossFilter] Blocked %s damage on %s", itemId, targetNpcId);
                return;
            }
        }
    }
}
