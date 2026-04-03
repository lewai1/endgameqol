package endgame.plugin.systems.boss;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.decisionmaker.core.EvaluationContext;
import com.hypixel.hytale.server.npc.decisionmaker.core.conditions.base.Condition;
import com.hypixel.hytale.server.npc.decisionmaker.core.conditions.base.SimpleCondition;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * CAE Condition: returns true when NPC's health percentage is below a threshold.
 * Works with any HP scaling (Endless Leveling, player count, config overrides).
 *
 * JSON: { "Type": "HealthPercentBelow", "Threshold": 0.66 }
 *
 * Portable — can be extracted to EndgameCore.
 */
@SuppressWarnings("unchecked")
public class HealthPercentBelowCondition extends SimpleCondition {

    public static final BuilderCodec<HealthPercentBelowCondition> CODEC;

    double threshold = 0.5;
    int healthStatIndex;

    protected HealthPercentBelowCondition() {
    }

    @Override
    protected boolean evaluate(int selfIndex,
                               @Nonnull ArchetypeChunk<EntityStore> chunk,
                               Ref<EntityStore> target,
                               @Nonnull CommandBuffer<EntityStore> commandBuffer,
                               EvaluationContext context) {
        EntityStatMap statMap = chunk.getComponent(selfIndex,
                EntityStatsModule.get().getEntityStatMapComponentType());
        if (statMap == null) return false;

        EntityStatValue healthStat = statMap.get(this.healthStatIndex);
        if (healthStat == null) return false;

        return healthStat.asPercentage() < this.threshold;
    }

    @Override
    public int getSimplicity() {
        return 5; // Very cheap — evaluate early for fast rejection
    }

    @Nonnull
    @Override
    public String toString() {
        return "HealthPercentBelowCondition{threshold=" + this.threshold + "} " + super.toString();
    }

    /**
     * Register this condition type on the global Condition.CODEC.
     * Call in plugin setup() before assets are loaded.
     */
    public static void register() {
        Condition.CODEC.register("HealthPercentBelow",
                HealthPercentBelowCondition.class, CODEC);
    }

    static {
        CODEC = ((BuilderCodec.Builder)((BuilderCodec.Builder)BuilderCodec.builder(
                HealthPercentBelowCondition.class, HealthPercentBelowCondition::new, ABSTRACT_CODEC)
                .documentation("Returns true when the NPC's health percentage is below the threshold."))
                .appendInherited(new KeyedCodec("Threshold", Codec.DOUBLE),
                        (condition, v) -> ((HealthPercentBelowCondition) condition).threshold = (Double) v,
                        (condition) -> ((HealthPercentBelowCondition) condition).threshold,
                        (condition, parent) -> ((HealthPercentBelowCondition) condition).threshold = ((HealthPercentBelowCondition) parent).threshold)
                .addValidator(Validators.range(0.0, 1.0))
                .documentation("Health percentage threshold (0.0 to 1.0). Condition is true when HP% < threshold.")
                .add())
                .afterDecode((condition) -> ((HealthPercentBelowCondition) condition).healthStatIndex = EntityStatType.getAssetMap().getIndex("Health"))
                .build();
    }
}
