package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Filters damage from boss entities based on difficulty settings and player count.
 * Runs in the FilterDamageGroup to modify damage before it's applied.
 *
 * When a boss deals damage to a PLAYER, this system multiplies it by:
 * - Difficulty multiplier (from config)
 * - Enrage multiplier (if boss is enraged)
 * - Player scaling: +15% per additional player in the same world
 *
 * Query targets Players (damage recipients), then checks if source is a boss.
 */
public class BossDamageFilterSystem extends AbstractBossDamageSystem {


    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(
            Player.getComponentType());

    private final EndgameQoL plugin;
    private final EnrageTracker enrageTracker;

    public BossDamageFilterSystem(EndgameQoL plugin, EnrageTracker enrageTracker) {
        this.plugin = plugin;
        this.enrageTracker = enrageTracker;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        // Resolve attacker from damage source (handles null/validity)
        Ref<EntityStore> sourceRef = resolveAttacker(damage);
        if (sourceRef == null) {
            return;
        }

        // Check if attacker is an NPC boss (uses attacker's own store for cross-world safety)
        String npcTypeId = resolveNPCTypeId(sourceRef, store);
        if (npcTypeId == null) {
            return;
        }

        BossType bossType = BossType.fromTypeId(npcTypeId);
        if (bossType == null) return;

        // SKIP: Golem Void damage is handled by GolemVoidDamageSystem (applies 15x * difficulty)
        if (bossType == BossType.GOLEM_VOID) return;

        // Data-driven: one-liner replaces 7-case switch
        float damageMultiplier = plugin.getConfig().get().getEffectiveBossDamageMultiplier(bossType);

        // Stack enrage multiplier if boss is enraged
        float enrageMult = enrageTracker.getEnrageMultiplier(sourceRef);

        // Player scaling: +15% damage per additional player in the boss's world
        float playerScalingMult = 1.0f;
        World bossWorld = getBossWorld(sourceRef);
        if (bossWorld != null) {
            int playerCount = countPlayersInWorld(bossWorld);
            if (playerCount > 1) {
                float scalingPerExtra = plugin.getConfig().get().getDifficultyConfig().getBossDamagePlayerScaling();
                playerScalingMult = 1.0f + (playerCount - 1) * scalingPerExtra;
            }
        }

        float totalMultiplier = damageMultiplier * enrageMult * playerScalingMult;

        // Only modify if not 1.0x
        if (Math.abs(totalMultiplier - 1.0f) < 0.001f) {
            return;
        }

        float originalDamage = damage.getAmount();
        float modifiedDamage = originalDamage * totalMultiplier;
        damage.setAmount(modifiedDamage);

        plugin.getLogger().atFine().log(
                "[BossDamageFilter] %s damage: %.1f -> %.1f (diff=%.2fx, enrage=%.2fx, players=%.2fx)",
                bossType.getDisplayName(), originalDamage, modifiedDamage,
                damageMultiplier, enrageMult, playerScalingMult);
    }

    /**
     * Get the world the boss NPC is in.
     */
    @Nullable
    private World getBossWorld(@Nonnull Ref<EntityStore> bossRef) {
        try {
            return bossRef.getStore().getExternalData().getWorld();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Count players in a specific world.
     */
    private int countPlayersInWorld(@Nonnull World world) {
        int count = 0;
        for (PlayerRef player : Universe.get().getPlayers()) {
            try {
                if (player == null) continue;
                Ref<EntityStore> playerRef = player.getReference();
                if (playerRef == null || !playerRef.isValid()) continue;
                World playerWorld = playerRef.getStore().getExternalData().getWorld();
                if (playerWorld != null && playerWorld.equals(world)) {
                    count++;
                }
            } catch (Exception e) {
                plugin.getLogger().atFine().log("[BossDamageFilter] Error counting player: %s", e.getMessage());
            }
        }
        return Math.max(1, count);
    }
}
