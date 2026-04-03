package endgame.plugin.systems.portal;

import com.google.gson.JsonElement;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.InstructionType;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.instructions.Action;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

/**
 * NPC Action Builder: when a player interacts with a Temporal Portal NPC,
 * this action fires and delegates to TemporalPortalManager for teleportation.
 *
 * JSON: { "Type": "EnterTemporalPortal" }
 * Pattern: same as BuilderActionOpenTradeUI.
 */
public class BuilderActionEnterTemporalPortal extends BuilderActionBase {

    @Nonnull
    public String getShortDescription() {
        return "Enter a Temporal Portal";
    }

    @Nonnull
    public String getLongDescription() {
        return "Teleports the interacting player into the dungeon instance linked to this temporal portal.";
    }

    @Nonnull
    public Action build(@Nonnull BuilderSupport builderSupport) {
        return new ActionEnterTemporalPortal(this);
    }

    @Nonnull
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    public BuilderActionEnterTemporalPortal readConfig(@Nonnull JsonElement data) {
        this.requireInstructionType(EnumSet.of(InstructionType.Interaction));
        return this;
    }

    // =========================================================================
    // Inner Action class
    // =========================================================================

    private static class ActionEnterTemporalPortal extends ActionBase {

        ActionEnterTemporalPortal(@Nonnull BuilderActionEnterTemporalPortal builder) {
            super(builder);
        }

        @Override
        public boolean execute(@Nonnull Ref<EntityStore> npcRef, @Nonnull Role role,
                               @Nullable InfoProvider sensorInfo, double dt,
                               @Nonnull Store<EntityStore> store) {
            super.execute(npcRef, role, sensorInfo, dt, store);

            // Get the interacting player's UUID from the NPC's locked interaction target
            NPCEntity npcEntity = store.getComponent(npcRef, NPCEntity.getComponentType());
            if (npcEntity == null) return true;

            Ref<EntityStore> targetRef = role.getMarkedEntitySupport().getMarkedEntityRef("InteractionTarget");
            if (targetRef == null || !targetRef.isValid()) return true;

            Store<EntityStore> targetStore = targetRef.getStore();
            UUIDComponent uuidComp = targetStore.getComponent(targetRef, UUIDComponent.getComponentType());
            if (uuidComp == null) return true;

            UUID playerUuid = uuidComp.getUuid();

            // Delegate to the manager
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin != null && plugin.getTemporalPortalManager() != null) {
                plugin.getTemporalPortalManager().onPlayerInteract(playerUuid, npcRef);
            }

            return true;
        }
    }
}
