package endgame.plugin.systems.trial;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.ComboMeterManager;
import endgame.plugin.managers.GauntletManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * INSPECT group: handles Gauntlet lifesteal + second wind + thorns.
 *
 * Lifesteal: when player damages NPC, heal by lifesteal% of damage.
 * Second Wind: when player would die, survive with 30% HP (once).
 * Thorns: when NPC damages player, deal flat damage back to NPC.
 */
public class GauntletLifestealSystem extends DamageEventSystem {

    private final GauntletManager gauntletManager;
    private final EndgameQoL plugin;
    private final ComboMeterManager comboManager;

    public GauntletLifestealSystem(EndgameQoL plugin, GauntletManager gauntletManager, ComboMeterManager comboManager) {
        this.plugin = plugin;
        this.gauntletManager = gauntletManager;
        this.comboManager = comboManager;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        // INSPECT group = after damage applied (for lifesteal healing)
        // Second wind uses FILTER group but we handle it here too since
        // the engine processes both
        return null; // Default = inspect group
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.or(Player.getComponentType(), EntityStatMap.getComponentType());
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {

        if (!(damage.getSource() instanceof Damage.EntitySource es)) return;

        Ref<EntityStore> attackerRef = es.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);

        // Case 1: Player attacking NPC → lifesteal (Gauntlet + Combo)
        Player attackerPlayer = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer != null) {
            NPCEntity targetNpc = store.getComponent(targetRef, NPCEntity.getComponentType());
            if (targetNpc != null) {
                UUID uuid = findPlayerUuid(attackerRef);
                if (uuid != null) {
                    float totalLifesteal = 0f;
                    // Gauntlet lifesteal
                    if (gauntletManager.hasActiveGauntlet(uuid)) {
                        totalLifesteal += gauntletManager.getLifestealPercent(uuid);
                    }
                    if (comboManager != null) {
                        totalLifesteal += comboManager.getLifestealPercent(uuid);
                    }
                    if (totalLifesteal > 0) {
                        float healAmount = damage.getAmount() * totalLifesteal;
                        EntityStatMap attackerStats = attackerRef.getStore().getComponent(attackerRef, EntityStatMap.getComponentType());
                        if (attackerStats != null) {
                            attackerStats.addStatValue(DefaultEntityStatTypes.getHealth(), healAmount);
                        }
                    }
                }
            }
        }

        // Case 2: NPC attacking Player → wave modifier effects
        Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer != null) {
            NPCEntity attackerNpc = attackerRef.getStore().getComponent(attackerRef, NPCEntity.getComponentType());
            if (attackerNpc != null) {
                UUID uuid = findPlayerUuid(targetRef);
                if (uuid != null && gauntletManager.hasActiveGauntlet(uuid)) {
                    // G1: Vampiric wave modifier — enemies heal 5% of damage dealt
                    if (gauntletManager.isVampiricWave(uuid)) {
                        float healAmount = damage.getAmount() * plugin.getConfig().get().getGauntletVampiricHealPercent();
                        EntityStatMap npcStats = attackerRef.getStore().getComponent(attackerRef, EntityStatMap.getComponentType());
                        if (npcStats != null) {
                            npcStats.addStatValue(DefaultEntityStatTypes.getHealth(), healAmount);
                        }
                    }
                }
            }
        }
    }

    private UUID findPlayerUuid(Ref<EntityStore> playerEntityRef) {
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef == null) continue;
            Ref<EntityStore> ref = pRef.getReference();
            if (ref != null && ref.equals(playerEntityRef)) {
                return pRef.getUuid();
            }
        }
        return null;
    }
}
