package endgame.plugin.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

/**
 * Lightweight ECS system that tracks block mining for bounty + achievement hooks.
 * Listens on BreakBlockEvent (same event as BlockProtectionSystem and PrismaPickaxe).
 * Only fires for endgame ores (Mithril, Adamantite) and tracks total blocks for mining achievements.
 */
public class MiningTracker extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final Set<String> ENDGAME_ORE_TYPES = Set.of("OreMithril", "OreAdamantite");
    private static final Set<String> ALL_ORE_TYPES = Set.of(
            "OreCopper", "OreIron", "OreSilver", "OreGold",
            "OreThorium", "OreCobalt", "OreAdamantite", "OreMithril");

    private final EndgameQoL plugin;

    public MiningTracker(EndgameQoL plugin) {
        super(BreakBlockEvent.class);
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        ComponentType<EntityStore, Player> playerType = Player.getComponentType();
        if (playerType == null) return Query.any();
        return Query.and(playerType);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {
        if (event.isCancelled()) return;

        // Get block gather type
        BlockType blockType = event.getBlockType();
        if (blockType == null) return;

        BlockGathering gathering = blockType.getGathering();
        if (gathering == null) return;

        BlockBreakingDropType breaking = gathering.getBreaking();
        if (breaking == null) return;

        String gatherType = breaking.getGatherType();
        if (gatherType == null) return;

        // Only track ores (for bounties: endgame ores only; for achievements: all ores + total blocks)
        boolean isAnyOre = ALL_ORE_TYPES.contains(gatherType);
        boolean isEndgameOre = ENDGAME_ORE_TYPES.contains(gatherType);
        if (!isAnyOre) return;

        // Resolve player UUID
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        UUID playerUuid = null;
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef != null && ref.equals(pRef.getReference())) {
                playerUuid = pRef.getUuid();
                break;
            }
        }
        if (playerUuid == null) return;

        // Bounty hook — only endgame ores count for MINE_ORE bounties
        if (isEndgameOre) {
            var bountyManager = plugin.getBountyManager();
            if (bountyManager != null) {
                bountyManager.onBlockMined(playerUuid, gatherType);
            }
        }

        // Achievement hook — tracks endgame ores, specific types, and total blocks
        var achievementManager = plugin.getAchievementManager();
        if (achievementManager != null) {
            achievementManager.onBlockMined(playerUuid, gatherType);
        }

        plugin.getLogger().atFine().log("[MiningTracker] %s mined %s (%s)", playerUuid, blockType.getId(), gatherType);
    }
}
