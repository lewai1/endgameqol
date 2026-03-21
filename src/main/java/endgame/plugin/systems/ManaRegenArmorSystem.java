package endgame.plugin.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;

import javax.annotation.Nonnull;

/**
 * Grants bonus mana regeneration to players wearing Mithril, Onyxium, or Prisma armor.
 * This bonus is ALWAYS active (even in combat), unlike vanilla mana regen which requires
 * 6 seconds without taking damage.
 *
 * Per-piece bonus (default values, configurable):
 *   Mithril: +0.5 mana/sec per piece (+2.0/sec full set)
 *   Onyxium: +0.75 mana/sec per piece (+3.0/sec full set)
 *   Prisma:  +1.0 mana/sec per piece (+4.0/sec full set)
 *
 * Runs as a DelayedEntitySystem with 1s interval to minimize tick overhead.
 */
public class ManaRegenArmorSystem extends DelayedEntitySystem<EntityStore> {

    private static final float INTERVAL_SEC = 1.0f;

    private final EndgameQoL plugin;

    public ManaRegenArmorSystem(EndgameQoL plugin) {
        super(INTERVAL_SEC);
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        EndgameConfig config = plugin.getConfig().get();
        if (!config.isManaRegenArmorEnabled()) return;

        var ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        InventoryComponent.Armor armorComponent = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (armorComponent == null) return;

        ItemContainer armor = armorComponent.getInventory();
        if (armor == null) return;

        // Sum bonus regen from all equipped armor pieces
        float bonusRegenPerSec = 0f;
        short capacity = armor.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = armor.getItemStack(slot);
            if (itemStack == null || itemStack.isEmpty()) continue;

            if (itemStack.getItem() == null) continue;
            String itemId = itemStack.getItem().getId();
            if (itemId == null) continue;

            if (itemId.startsWith("Armor_Prisma_")) {
                bonusRegenPerSec += config.getManaRegenPrismaPerPiece();
            } else if (itemId.startsWith("Armor_Onyxium_")) {
                bonusRegenPerSec += config.getManaRegenOnyxiumPerPiece();
            } else if (itemId.startsWith("Armor_Mithril_")) {
                bonusRegenPerSec += config.getManaRegenMithrilPerPiece();
            }
        }

        if (bonusRegenPerSec <= 0f) return;

        // Get player's mana stat
        ComponentType<EntityStore, EntityStatMap> statMapType = EntityStatMap.getComponentType();
        if (statMapType == null) return;

        EntityStatMap statMap = store.getComponent(ref, statMapType);
        if (statMap == null) return;

        int manaIndex = DefaultEntityStatTypes.getMana();
        EntityStatValue manaValue = statMap.get(manaIndex);
        if (manaValue == null) return;

        // Skip if already at max mana
        if (manaValue.get() >= manaValue.getMax()) return;

        // Apply bonus regen scaled by actual dt (usually ~1.0s)
        float manaToAdd = bonusRegenPerSec * dt;
        statMap.addStatValue(manaIndex, manaToAdd);

        plugin.getLogger().atFine().log(
                "[ManaRegenArmor] +%.1f mana (%.1f/sec, dt=%.2f, current=%.0f/%.0f)",
                manaToAdd, bonusRegenPerSec, dt, manaValue.get(), manaValue.getMax());
    }
}
