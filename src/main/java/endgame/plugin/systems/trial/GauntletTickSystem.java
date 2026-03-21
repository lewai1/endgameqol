package endgame.plugin.systems.trial;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.ComboMeterManager;
import endgame.plugin.managers.GauntletManager;

import javax.annotation.Nonnull;

/**
 * Ticks the GauntletManager and ComboMeterManager each frame.
 * Uses Player query + rate limiting (500ms for Gauntlet, 200ms for Combo).
 */
public class GauntletTickSystem extends EntityTickingSystem<EntityStore> {

    private final EndgameQoL plugin;
    private final GauntletManager gauntletManager;
    private final ComboMeterManager comboManager;

    private final java.util.concurrent.ConcurrentHashMap<Store<EntityStore>, Long> lastGauntletTicks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Store<EntityStore>, Long> lastComboTicks = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long GAUNTLET_INTERVAL_MS = 500;
    private static final long COMBO_INTERVAL_MS = 200;

    private static final Query<EntityStore> QUERY = Query.and(
            TransformComponent.getComponentType(),
            EntityStatMap.getComponentType(),
            Player.getComponentType());

    public GauntletTickSystem(EndgameQoL plugin, GauntletManager gauntletManager, ComboMeterManager comboManager) {
        this.plugin = plugin;
        this.gauntletManager = gauntletManager;
        this.comboManager = comboManager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        long now = System.currentTimeMillis();

        Long lastGauntlet = lastGauntletTicks.get(store);
        if (lastGauntlet == null || now - lastGauntlet >= GAUNTLET_INTERVAL_MS) {
            lastGauntletTicks.put(store, now);
            try {
                gauntletManager.tick(store);
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[GauntletTick] Error: %s", e.getMessage());
            }
        }

        Long lastCombo = lastComboTicks.get(store);
        if (lastCombo == null || now - lastCombo >= COMBO_INTERVAL_MS) {
            lastComboTicks.put(store, now);
            try {
                comboManager.tick();
            } catch (Exception e) {
                plugin.getLogger().atWarning().log("[ComboTick] Error: %s", e.getMessage());
            }
        }
    }

    public void forceClear() {
        gauntletManager.forceClear();
        comboManager.forceClear();
    }
}
