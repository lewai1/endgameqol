package endgame.plugin.systems.weapon;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.I18n;

import javax.annotation.Nonnull;

/**
 * Custom interaction type for switching item states (stance switch).
 * Used by Prisma Daggers to toggle between Combat and Blink modes.
 *
 * Preserves SignatureEnergy across the state switch and sends a subtitle
 * notification to the player indicating the new mode.
 *
 * JSON usage: { "Type": "EndgameStanceChange", "TargetState": "Blink" }
 */
public class EndgameStanceChangeInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<EndgameStanceChangeInteraction> CODEC = BuilderCodec
            .builder(EndgameStanceChangeInteraction.class, EndgameStanceChangeInteraction::new,
                    SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("TargetState", Codec.STRING),
                    EndgameStanceChangeInteraction::setTargetState,
                    EndgameStanceChangeInteraction::getTargetState).add()
            .build();

    private String targetState;

    public String getTargetState() {
        return targetState;
    }

    public void setTargetState(String targetState) {
        this.targetState = targetState;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        try {
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin == null) return;

            if (targetState == null || targetState.isEmpty()) {
                plugin.getLogger().atWarning().log("[EndgameStanceChange] Missing TargetState");
                return;
            }

            ItemStack heldItem = context.getHeldItem();
            if (heldItem == null || heldItem.isEmpty()) return;

            ItemContainer container = context.getHeldItemContainer();
            byte slot = context.getHeldItemSlot();
            if (container == null) return;

            // Save SignatureEnergy before state switch (stat modifiers get recalculated)
            float savedSignatureEnergy = 0.0f;
            CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
            Ref<EntityStore> entityRef = context.getEntity();
            EntityStatMap statMap = null;
            int signatureEnergyIndex = EntityStatType.getAssetMap().getIndex("SignatureEnergy");

            if (commandBuffer != null && entityRef != null) {
                statMap = commandBuffer.getComponent(entityRef, EntityStatMap.getComponentType());
                if (statMap != null) {
                    EntityStatValue signatureEnergy = statMap.get(signatureEnergyIndex);
                    if (signatureEnergy != null) {
                        savedSignatureEnergy = signatureEnergy.get();
                    }
                }
            }

            // Switch item state
            container.setItemStackForSlot((short) slot, heldItem.withState(targetState));

            // Recalculate stat modifiers and restore SignatureEnergy
            if (statMap != null && entityRef != null) {
                statMap.getStatModifiersManager().scheduleRecalculate();

                if (savedSignatureEnergy > 0.0f) {
                    statMap.setStatValue(signatureEnergyIndex, savedSignatureEnergy);
                }
            }

            // Send chat notification to player
            if (commandBuffer != null && entityRef != null) {
                PlayerRef playerRef = commandBuffer.getComponent(entityRef, PlayerRef.getComponentType());
                if (playerRef != null) {
                    String itemId = heldItem.getItemId();
                    String itemLabel = "";
                    String modeText = "";
                    String color = "#b060ff";

                    if (itemId != null && itemId.contains("Daggers_Prisma")) {
                        // Dagger-specific: check config
                        if (!plugin.getConfig().get().isDaggerBlinkEnabled()) return;
                        itemLabel = "Prisma Daggers";
                        boolean isBlinkMode = "Blink".equals(targetState);
                        modeText = isBlinkMode ? I18n.getForPlayer(playerRef, "prisma.stance_blink") : I18n.getForPlayer(playerRef, "prisma.stance_combat");
                        color = isBlinkMode ? "#40d0ff" : "#b060ff";
                    } else if (itemId != null && itemId.contains("Pickaxe_Prisma")) {
                        itemLabel = "Prisma Pickaxe";
                        boolean isAreaBreak = "AreaBreak".equals(targetState);
                        modeText = isAreaBreak ? "3x3 Area Break" : "Normal";
                        color = isAreaBreak ? "#ff8800" : "#b060ff";
                        // Track mode for reliable detection in PrismaPickaxeAreaBreakSystem
                        java.util.UUID uuid = endgame.plugin.utils.EntityUtils.getUuid(playerRef);
                        if (uuid != null) {
                            endgame.plugin.systems.block.PrismaPickaxeAreaBreakSystem.setAreaBreakMode(uuid, isAreaBreak);
                        }
                    } else if (itemId != null && itemId.contains("Hatchet_Prisma")) {
                        itemLabel = "Prisma Hatchet";
                        boolean isAreaBreak = "AreaBreak".equals(targetState);
                        modeText = isAreaBreak ? "3x3 Area Break" : "Normal";
                        color = isAreaBreak ? "#ff8800" : "#b060ff";
                    } else {
                        itemLabel = "Prisma";
                        modeText = targetState;
                    }

                    playerRef.sendMessage(Message.raw("[" + itemLabel + "] " + modeText).color(color));
                }
            }

            plugin.getLogger().atFine().log("[EndgameStanceChange] Switched to state '%s'", targetState);
        } catch (Exception e) {
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin != null) {
                plugin.getLogger().atWarning().log("[EndgameStanceChange] Error: %s", e.getMessage());
            }
        }
    }
}
