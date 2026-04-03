package endgame.plugin.systems.boss;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import javax.annotation.Nonnull;

/**
 * Builder for SetMotionController action.
 * JSON: { "Type": "SetMotionController", "MotionController": "Walk" }
 */
public class BuilderActionSetMotionController extends BuilderActionBase {

    @Nonnull
    protected final StringHolder motionControllerHolder = new StringHolder();

    @Nonnull
    public String getShortDescription() {
        return "Set active motion controller";
    }

    @Nonnull
    public String getLongDescription() {
        return "Instantly switch the NPC's active motion controller (Walk, Fly, etc.)";
    }

    @Nonnull
    public Action build(@Nonnull BuilderSupport builderSupport) {
        return new ActionSetMotionController(this, builderSupport);
    }

    @Nonnull
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }

    @Nonnull
    public BuilderActionSetMotionController readConfig(@Nonnull JsonElement data) {
        this.requireString(data, "MotionController", this.motionControllerHolder, null,
                BuilderDescriptorState.Stable,
                "Name of the motion controller to activate (Walk, Fly)", null);
        return this;
    }

    public String getMotionController(@Nonnull BuilderSupport support) {
        return this.motionControllerHolder.get(support.getExecutionContext());
    }
}
