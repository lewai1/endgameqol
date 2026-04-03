package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * NPC Action that instantly switches the active motion controller.
 * JSON: { "Type": "SetMotionController", "MotionController": "Walk" }
 * Pattern from MonsterExpansion mod (Saksolm).
 */
public class ActionSetMotionController extends ActionBase {

    @Nonnull
    private final String motionControllerName;

    public ActionSetMotionController(@Nonnull BuilderActionSetMotionController builder,
                                     @Nonnull BuilderSupport support) {
        super(builder);
        this.motionControllerName = builder.getMotionController(support);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);
        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity != null) {
            role.setActiveMotionController(ref, npcEntity, motionControllerName, store);
        }
        return true;
    }
}
