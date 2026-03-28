package endgame.plugin.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grants passive HP regeneration to players wearing endgame armor,
 * but ONLY after they haven't taken damage for a configurable delay (default 15s).
 *
 * Per-piece bonus (default, configurable):
 *   Onyxium:    +0.5 HP/sec per piece (+2.0/sec full set)
 *   Prisma:     +0.75 HP/sec per piece (+3.0/sec full set)
 *
 * Damage tracking is handled by {@link ArmorHPRegenDamageTracker}.
 */
public class ArmorHPRegenSystem extends DelayedEntitySystem<EntityStore> {

    private static final float INTERVAL_SEC = 1.0f;
    private final ConcurrentHashMap<Ref<EntityStore>, Long> lastDamageTime = new ConcurrentHashMap<>();

    private volatile long lastEvictionTime = 0;
    private static final long EVICTION_INTERVAL_MS = 60_000;

    // Singleton reference for static access from ArmorHPRegenDamageTracker
    private static volatile ArmorHPRegenSystem instance;

    private final EndgameQoL plugin;

    public ArmorHPRegenSystem(EndgameQoL plugin) {
        super(INTERVAL_SEC);
        this.plugin = plugin;
        instance = this;
    }

    /**
     * Called by {@link ArmorHPRegenDamageTracker} when a player takes damage.
     */
    public static void recordDamage(Ref<EntityStore> ref) {
        ArmorHPRegenSystem inst = instance;
        if (inst != null) inst.lastDamageTime.put(ref, System.currentTimeMillis());
    }

    public static void removePlayer(Ref<EntityStore> ref) {
        ArmorHPRegenSystem inst = instance;
        if (inst != null) inst.lastDamageTime.remove(ref);
    }

    public void forceClear() {
        lastDamageTime.clear();
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
        if (!config.isArmorHPRegenEnabled()) return;

        // Periodic eviction of invalid refs to prevent memory leak
        long evictionNow = System.currentTimeMillis();
        if (evictionNow - lastEvictionTime > EVICTION_INTERVAL_MS) {
            lastEvictionTime = evictionNow;
            lastDamageTime.entrySet().removeIf(e -> {
                try { return !e.getKey().isValid(); } catch (Exception ex) { return true; }
            });
        }

        var ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        Long lastDamage = lastDamageTime.get(ref);
        long now = System.currentTimeMillis();
        float delaySec = config.getArmorHPRegenDelaySec();

        if (lastDamage != null && (now - lastDamage) < (long) (delaySec * 1000)) {
            return; // Still within damage cooldown
        }

        InventoryComponent.Armor armorComponent = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (armorComponent == null) return;

        ItemContainer armor = armorComponent.getInventory();
        if (armor == null) return;

        // Sum HP regen from equipped armor pieces
        float regenPerSec = 0f;
        short capacity = armor.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = armor.getItemStack(slot);
            if (itemStack == null || itemStack.isEmpty()) continue;
            if (itemStack.getItem() == null) continue;
            String itemId = itemStack.getItem().getId();
            if (itemId == null) continue;

            if (itemId.startsWith("Armor_Prisma_")) {
                regenPerSec += config.getArmorHPRegenPrismaPerPiece();
            } else if (itemId.startsWith("Armor_Onyxium_")) {
                regenPerSec += config.getArmorHPRegenOnyxiumPerPiece();
            }
        }

        if (regenPerSec <= 0f) return;

        // Get player's health stat
        ComponentType<EntityStore, EntityStatMap> statMapType = EntityStatMap.getComponentType();
        if (statMapType == null) return;

        EntityStatMap statMap = store.getComponent(ref, statMapType);
        if (statMap == null) return;

        int healthIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthValue = statMap.get(healthIndex);
        if (healthValue == null) return;

        // Skip if at max health
        if (healthValue.get() >= healthValue.getMax()) return;

        float hpToAdd = regenPerSec * dt;
        statMap.addStatValue(healthIndex, hpToAdd);

        plugin.getLogger().atFine().log(
                "[ArmorHPRegen] +%.1f HP (%.1f/sec, current=%.0f/%.0f, no-dmg=%.0fs)",
                hpToAdd, regenPerSec, healthValue.get(), healthValue.getMax(),
                lastDamage != null ? (now - lastDamage) / 1000f : 999f);
    }
}
