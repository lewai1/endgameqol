package endgame.plugin.systems.pet;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PetOwnerComponent;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.PetTier;
import endgame.plugin.managers.PetManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Global tick system (every 500ms) for pet follow management.
 *
 * Follow system:
 * - LockedTarget set to owner for passive follow (NPC Seek instruction)
 * - PetCombatSystem overrides LockedTarget for combat
 * - Emergency teleport as safety net (>40 blocks)
 * - When combat target dies, LockedTarget reset to owner
 *
 * Thread safety: Uses PlayerRef.getUuid() (no Store access needed for owner lookup).
 * All pet component access via world.execute() on the pet's world thread.
 */
public class PetFollowSystem extends TickingSystem<EntityStore> {

    private static final float INTERVAL_SEC = 0.5f;
    private float timer = 0f;

    private final EndgameQoL plugin;
    private final PetManager petManager;

    public PetFollowSystem(@Nonnull EndgameQoL plugin, @Nonnull PetManager petManager) {
        this.plugin = plugin;
        this.petManager = petManager;
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        timer += dt;
        if (timer < INTERVAL_SEC) return;
        timer = 0f;

        if (!plugin.getConfig().get().pets().isEnabled()) return;

        var entries = new ArrayList<>(petManager.getPetsByOwner().entrySet());
        for (Map.Entry<UUID, Ref<EntityStore>> entry : entries) {
            UUID ownerUuid = entry.getKey();
            Ref<EntityStore> petRef = entry.getValue();

            if (petRef == null || !petRef.isValid()) {
                petManager.clearPet(ownerUuid);
                continue;
            }

            // Find owner
            PlayerRef ownerPlayerRef = findPlayerRef(ownerUuid);
            if (ownerPlayerRef == null) {
                despawnPetSafe(petRef, ownerUuid);
                continue;
            }

            Ref<EntityStore> ownerRef = ownerPlayerRef.getReference();
            if (ownerRef == null || !ownerRef.isValid()) {
                despawnPetSafe(petRef, ownerUuid);
                continue;
            }

            // Check same world
            World petWorld;
            try {
                petWorld = petRef.getStore().getExternalData().getWorld();
            } catch (Exception e) {
                petManager.clearPet(ownerUuid);
                continue;
            }
            if (petWorld == null || !petWorld.isAlive()) {
                petManager.clearPet(ownerUuid);
                continue;
            }

            World ownerWorld;
            try {
                ownerWorld = ownerRef.getStore().getExternalData().getWorld();
            } catch (Exception e) {
                continue;
            }

            if (ownerWorld == null || !ownerWorld.equals(petWorld)) {
                despawnPetSafe(petRef, ownerUuid);
                continue;
            }

            // Execute on pet's world thread
            final Ref<EntityStore> finalOwnerRef = ownerRef;
            petWorld.execute(() -> {
                if (!petRef.isValid() || !finalOwnerRef.isValid()) return;

                try {
                    Store<EntityStore> petStore = petRef.getStore();

                    NPCEntity npc = petStore.getComponent(petRef, NPCEntity.getComponentType());
                    if (npc == null || npc.getRole() == null) return;

                    // === Set FollowTarget to owner (always — for passive follow) ===
                    npc.getRole().getMarkedEntitySupport().setMarkedEntity("FollowTarget", finalOwnerRef);

                    // === Clear dead combat target ===
                    Ref<EntityStore> currentTarget = npc.getRole().getMarkedEntitySupport()
                            .getMarkedEntityRef("LockedTarget");
                    if (currentTarget != null && !currentTarget.isValid()) {
                        // Combat target died — clear it so pet stops attacking
                        npc.getRole().getMarkedEntitySupport().setMarkedEntity("LockedTarget", null);
                        // Switch motion controller based on tier
                        String controller = "Walk";
                        PetOwnerComponent poc = petStore.getComponent(petRef, PetOwnerComponent.getComponentType());
                        if (poc != null && poc.getPetId().contains("Dragon")) {
                            PlayerEndgameComponent pec = plugin.getPlayerComponent(poc.getOwnerUuid());
                            if (pec != null && pec.getPetData().getPetTier(poc.getPetId()).ordinal() >= PetTier.B.ordinal()) {
                                controller = "Fly";
                            }
                        }
                        try {
                            npc.getRole().setActiveMotionController(petRef, npc, controller, petStore);
                        } catch (Exception ignored) {}
                    }

                    // === Emergency teleport (safety net) ===
                    float teleportDistSq = plugin.getConfig().get().pets().getTeleportDistance();
                    teleportDistSq *= teleportDistSq;

                    TransformComponent petTransform = petStore.getComponent(petRef, TransformComponent.getComponentType());
                    TransformComponent ownerTransform = finalOwnerRef.getStore().getComponent(
                            finalOwnerRef, TransformComponent.getComponentType());

                    if (petTransform != null && ownerTransform != null) {
                        double distSq = petTransform.getPosition().distanceSquaredTo(ownerTransform.getPosition());
                        if (distSq > teleportDistSq) {
                            Vector3d ownerPos = ownerTransform.getPosition();
                            Vector3d targetPos = new Vector3d(ownerPos.x + 2, ownerPos.y + 1, ownerPos.z + 2);
                            var teleportType = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType();
                            var teleport = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.createForPlayer(
                                    petWorld, targetPos, new Vector3f(0, 0, 0));
                            petStore.addComponent(petRef, teleportType, teleport);
                        }
                    }
                } catch (Exception ignored) {}
            });
        }
    }

    private void despawnPetSafe(Ref<EntityStore> petRef, UUID ownerUuid) {
        try {
            World world = petRef.getStore().getExternalData().getWorld();
            if (world != null && world.isAlive()) {
                world.execute(() -> {
                    if (petRef.isValid()) {
                        petRef.getStore().removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                    }
                });
            }
        } catch (Exception ignored) {}
        petManager.clearPet(ownerUuid);
    }

    @Nullable
    private PlayerRef findPlayerRef(@Nonnull UUID uuid) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            if (uuid.equals(p.getUuid())) return p;
        }
        return null;
    }
}
