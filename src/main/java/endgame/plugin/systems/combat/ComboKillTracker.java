package endgame.plugin.systems.combat;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.ComboMeterManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Death system that detects player kills on NPCs for the Combo Meter.
 * Uses DeathComponent.getDeathInfo().getSource() for race-free kill attribution.
 *
 * Also calls bountyManager.onNPCKill() when the bounty system is active.
 */
public class ComboKillTracker extends DeathSystems.OnDeathSystem {

    private final EndgameQoL plugin;
    private final ComboMeterManager comboManager;

    // Bounty callback — set after bounty system is initialized
    private volatile NpcKillCallback npcKillCallback;

    @FunctionalInterface
    public interface NpcKillCallback {
        void onNpcKill(UUID playerUuid, String npcTypeId);
    }

    public ComboKillTracker(EndgameQoL plugin, ComboMeterManager comboManager) {
        this.plugin = plugin;
        this.comboManager = comboManager;
    }

    public void setNpcKillCallback(NpcKillCallback callback) {
        this.npcKillCallback = callback;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // Get the killer from death info
            Damage deathInfo = component.getDeathInfo();
            if (deathInfo == null) return;
            if (!(deathInfo.getSource() instanceof Damage.EntitySource es)) return;

            Ref<EntityStore> killerRef = es.getRef();
            if (killerRef == null || !killerRef.isValid()) return;

            // Check if killer is a player
            Player player = killerRef.getStore().getComponent(killerRef, Player.getComponentType());
            if (player == null) return;

            // Find player UUID via PlayerRef
            UUID playerUuid = findPlayerUuid(killerRef);
            if (playerUuid == null) return;

            // Notify combo manager
            comboManager.onPlayerKill(playerUuid);

            // C1: Adrenaline — heal 2% max HP on kill at x3+ tier
            float healPercent = comboManager.getHealOnKillPercent(playerUuid);
            if (healPercent > 0) {
                try {
                    EntityStatMap killerStats = killerRef.getStore().getComponent(killerRef, EntityStatMap.getComponentType());
                    if (killerStats != null) {
                        EntityStatValue health = killerStats.get(DefaultEntityStatTypes.getHealth());
                        if (health != null) {
                            killerStats.addStatValue(DefaultEntityStatTypes.getHealth(), health.getMax() * healPercent);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().atFine().log("[ComboKillTracker] Heal error: %s", e.getMessage());
                }
            }

            // Notify bounty system if callback is set
            NpcKillCallback cb = npcKillCallback;
            if (cb != null) {
                NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
                if (npcEntity != null) {
                    String npcTypeId = npcEntity.getNPCTypeId();
                    if (npcTypeId != null) {
                        cb.onNpcKill(playerUuid, npcTypeId);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[ComboKillTracker] Error: %s", e.getMessage());
        }
    }

    private UUID findPlayerUuid(Ref<EntityStore> playerEntityRef) {
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef == null) continue;
            Ref<EntityStore> ref = pRef.getReference();
            if (ref != null && ref.equals(playerEntityRef)) {
                return endgame.plugin.utils.EntityUtils.getUuid(pRef);
            }
        }
        return null;
    }
}
