package endgame.plugin.integration.endlessleveling;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * OnDeathSystem that awards Endless Leveling XP when a boss NPC dies.
 * Only registered when Endless Leveling integration is active.
 */
public class EndlessLevelingXPSystem extends DeathSystems.OnDeathSystem {

    private final EndgameQoL plugin;
    private final EndlessLevelingBridge bridge;

    public EndlessLevelingXPSystem(EndgameQoL plugin, EndlessLevelingBridge bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cmd) {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) return;

        String typeId = npc.getNPCTypeId();
        if (typeId == null) return;

        BossType bossType = BossType.fromTypeId(typeId);
        if (bossType == null) return;

        // Award XP only to the player who killed the boss
        try {
            Damage deathInfo = component.getDeathInfo();
            if (deathInfo == null) return;
            if (!(deathInfo.getSource() instanceof Damage.EntitySource es)) return;

            Ref<EntityStore> killerRef = es.getRef();
            if (killerRef == null || !killerRef.isValid()) return;

            Player player = killerRef.getStore().getComponent(killerRef, Player.getComponentType());
            if (player == null) return;

            UUID killerUuid = findPlayerUuid(killerRef);
            if (killerUuid != null) {
                bridge.addBossKillXP(killerUuid, bossType);
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[EndlessLevelingXP] Error awarding XP: %s", e.getMessage());
        }

        // Clear any EL mob level override on death (prevent memory leak)
        try {
            bridge.clearMobEntityLevel(ref.getIndex());
        } catch (Exception ignored) {}
    }

    private UUID findPlayerUuid(Ref<EntityStore> playerRef) {
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef != null && playerRef.equals(pRef.getReference())) {
                return pRef.getUuid();
            }
        }
        return null;
    }
}
