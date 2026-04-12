package endgame.plugin.systems.accessory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.MovementSettings;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.AccessoryBlockUtils;
import endgame.plugin.utils.AccessoryUtils;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies passive effects from equipped accessories every 200ms:
 * - Frostwalkers: Walk on water (temporary ice blocks)
 * - Ocean Striders: 2x swim speed in water
 * - Pocket Garden: +1 HP/sec passive regen
 */
public class AccessoryPassiveSystem extends DelayedEntitySystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.AccessoryPassive");
    private static final float INTERVAL_SEC = 0.2f;

    // Pocket Garden: 1 HP/sec = 0.2 HP per 200ms tick
    private static final float POCKET_GARDEN_HP_PER_TICK = 2.0f;
    private static final int POCKET_GARDEN_RADIUS = 5;
    private static final String ICE_BLOCK_ID = "Endgame_Frostwalker_Ice";
    private static final int FROSTWALKER_RADIUS = 2;

    private final EndgameQoL plugin;
    private final ConcurrentHashMap<UUID, Boolean> oceanStriderActive = new ConcurrentHashMap<>();

    public AccessoryPassiveSystem(EndgameQoL plugin) {
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
        if (!plugin.getConfig().get().isAccessoriesEnabled()) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        UUID uuid = EntityUtils.getUuid(store, ref);
        if (uuid == null) return;

        // Find PlayerRef via O(1) cache lookup
        PlayerRef playerRef = endgame.plugin.utils.PlayerRefCache.getByRef(ref);
        if (playerRef == null) return;

        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) return;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        // --- Frostwalkers: place ice on water below player ---
        if (AccessoryUtils.hasAccessory(plugin, uuid, "Endgame_Accessory_Frostwalkers", store, ref)) {
            tickFrostwalkers(world, transform);
        }

        // --- Ocean Striders: 2x swim speed in water ---
        tickOceanStriders(uuid, ref, store, playerRef, world, transform);

        // --- Pocket Garden: +1 HP/sec regen near planted crops + auto-fertilize ---
        if (AccessoryUtils.hasAccessory(plugin, uuid, "Endgame_Accessory_Pocket_Garden", store, ref)) {
            int px = (int) Math.floor(transform.getPosition().x);
            int ppY = (int) Math.floor(transform.getPosition().y);
            int ppZ = (int) Math.floor(transform.getPosition().z);

            boolean nearFarm = AccessoryBlockUtils.hasFarmingBlockNearby(world, px, ppY, ppZ, POCKET_GARDEN_RADIUS);
            if (nearFarm) {
                // Regen only near planted crops
                EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
                if (statMap != null) {
                    int healthIndex = DefaultEntityStatTypes.getHealth();
                    EntityStatValue health = statMap.get(healthIndex);
                    if (health != null && health.get() < health.getMax()) {
                        statMap.addStatValue(healthIndex, POCKET_GARDEN_HP_PER_TICK);
                    }
                }

                // Auto-fertilize nearby tilled soil (x2 growth via vanilla Fertilizer modifier)
                world.execute(() -> AccessoryBlockUtils.fertilizeNearby(world, px, ppY, ppZ, POCKET_GARDEN_RADIUS));
            }
        }
    }

    private void tickFrostwalkers(World world, TransformComponent transform) {
        int px = (int) Math.floor(transform.getPosition().x);
        int py = (int) Math.floor(transform.getPosition().y) - 1;
        int pz = (int) Math.floor(transform.getPosition().z);

        Fluid waterSource = Fluid.getAssetMap().getAsset("Water_Source");
        if (waterSource == null) return;

        for (int dx = -FROSTWALKER_RADIUS; dx <= FROSTWALKER_RADIUS; dx++) {
            for (int dz = -FROSTWALKER_RADIUS; dz <= FROSTWALKER_RADIUS; dz++) {
                int wx = px + dx;
                int wz = pz + dz;
                Fluid fluid = AccessoryBlockUtils.getFluid(world, wx, py, wz);
                if (fluid == waterSource) {
                    world.execute(() -> AccessoryBlockUtils.placeTickingBlock(world, ICE_BLOCK_ID, wx, py, wz));
                }
            }
        }
    }

    private void tickOceanStriders(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store,
                                   PlayerRef playerRef, World world, TransformComponent transform) {
        boolean hasAccessory = AccessoryUtils.hasAccessory(plugin, uuid, "Endgame_Accessory_Ocean_Striders", store, ref);
        boolean wasActive = oceanStriderActive.getOrDefault(uuid, false);

        if (hasAccessory) {
            // Check if player is submerged in water (fluid at foot level AND head level)
            int px = (int) Math.floor(transform.getPosition().x);
            int py = (int) Math.floor(transform.getPosition().y);
            int pz = (int) Math.floor(transform.getPosition().z);

            Fluid waterSource = Fluid.getAssetMap().getAsset("Water_Source");
            if (waterSource == null) return;

            Fluid fluidFeet = AccessoryBlockUtils.getFluid(world, px, py, pz);
            Fluid fluidHead = AccessoryBlockUtils.getFluid(world, px, py + 1, pz);
            boolean inWater = waterSource.equals(fluidFeet) && waterSource.equals(fluidHead);

            if (inWater && !wasActive) {
                if (applySpeedModifier(ref, store, playerRef, 2.0f)) {
                    oceanStriderActive.put(uuid, true);
                }
            } else if (!inWater && wasActive) {
                if (applySpeedModifier(ref, store, playerRef, 1.0f)) {
                    oceanStriderActive.put(uuid, false);
                }
                // If reset fails, keep wasActive=true so next tick retries
            }
        } else if (wasActive) {
            if (applySpeedModifier(ref, store, playerRef, 1.0f)) {
                oceanStriderActive.remove(uuid);
            }
            // If reset fails, keep in map so next tick retries
        }
    }

    private boolean applySpeedModifier(Ref<EntityStore> ref, Store<EntityStore> store,
                                       PlayerRef playerRef, float multiplier) {
        try {
            MovementManager movement = store.getComponent(ref, EntityModule.get().getMovementManagerComponentType());
            if (movement == null) return false;
            MovementSettings settings = movement.getSettings();
            MovementSettings defaults = movement.getDefaultSettings();
            if (settings == null || defaults == null) return false;
            settings.baseSpeed = defaults.baseSpeed * multiplier;
            movement.update(playerRef.getPacketHandler());
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("[AccessoryPassive] Failed to apply speed modifier (%.1fx): %s",
                    multiplier, e.getMessage());
            return false;
        }
    }

    public void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid != null) {
            oceanStriderActive.remove(playerUuid);
        }
    }

    public void forceClear() {
        oceanStriderActive.clear();
    }
}
