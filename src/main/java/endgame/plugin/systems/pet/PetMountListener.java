package endgame.plugin.systems.pet;

import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.MountNPC;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PetOwnerComponent;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.PetTier;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Packet filter that handles pet mounting/dismounting.
 * Intercepts SyncInteractionChains (right-click on entity) and MountNPC packets.
 * Pattern: identical to Mounts+ MountInteractListener.
 *
 * Registered via PacketAdapters.registerInbound() in SystemRegistry.
 */
public class PetMountListener implements PlayerPacketFilter {

    private final EndgameQoL plugin;
    private final Map<UUID, Long> lastMountInteraction = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> mountedEntities = new ConcurrentHashMap<>();

    // Mount tiers: Dragons at C, Golem at S, Hedera not mountable
    private static final Map<String, PetTier> MOUNT_TIERS = Map.of(
            "Endgame_Pet_Dragon_Frost", PetTier.C,
            "Endgame_Pet_Dragon_Fire", PetTier.C,
            "Endgame_Pet_Golem_Void", PetTier.S
    );

    private static final Map<String, Float> MOUNT_Y_OFFSETS = Map.of(
            "Endgame_Pet_Dragon_Frost", 1.4f,
            "Endgame_Pet_Dragon_Fire", 1.5f,
            "Endgame_Pet_Golem_Void", 2.2f
    );

    public PetMountListener(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean test(PlayerRef playerRef, Packet packet) {
        // Intercept MountNPC packets (client sending mount requests)
        if (packet instanceof MountNPC) {
            lastMountInteraction.put(playerRef.getUuid(), System.currentTimeMillis());
            return false;
        }

        // Intercept right-click interaction on entities
        if (packet instanceof SyncInteractionChains sync) {
            if (sync.updates != null) {
                for (SyncInteractionChain update : sync.updates) {
                    if (update != null && update.data != null && update.data.entityId >= 0) {
                        int entityId = update.data.entityId;

                        // Check if already riding this entity → dismount
                        Integer mountedId = mountedEntities.get(playerRef.getUuid());
                        boolean isRidingTarget = mountedId != null && mountedId == entityId;

                        if (update.interactionType == InteractionType.Use && update.initial) {
                            Ref<EntityStore> playerEntityRef = playerRef.getReference();
                            if (playerEntityRef != null && playerEntityRef.isValid()) {
                                Store<EntityStore> store = playerEntityRef.getStore();
                                World world = store.getExternalData().getWorld();
                                if (world != null) {
                                    world.execute(() -> handleInteraction(world, playerRef, entityId));
                                }
                            }
                            if (isRidingTarget) return true; // Block further processing when dismounting
                        }
                    }
                }
            }
        }

        return false;
    }

    private void handleInteraction(World world, PlayerRef playerRef, int entityId) {
        // Debounce
        Long lastMount = lastMountInteraction.get(playerRef.getUuid());
        if (lastMount != null && System.currentTimeMillis() - lastMount < 250L) return;
        lastMountInteraction.put(playerRef.getUuid(), System.currentTimeMillis());

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) return;

        Store<EntityStore> store = playerEntityRef.getStore();
        Ref<EntityStore> targetRef = world.getEntityStore().getRefFromNetworkId(entityId);
        if (targetRef == null || !targetRef.isValid()) return;
        if (Objects.equals(targetRef, playerEntityRef)) return;

        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null) return;

        // Check if target is one of our pets
        PetOwnerComponent petOwner = targetStore.getComponent(targetRef, PetOwnerComponent.getComponentType());
        if (petOwner == null) return; // Not a pet — ignore

        // Check if this player owns the pet
        if (!playerRef.getUuid().equals(petOwner.getOwnerUuid())) return;

        String petId = petOwner.getPetId();

        // Check mount tier requirement
        PetTier requiredTier = MOUNT_TIERS.get(petId);
        if (requiredTier == null) return; // Hedera not mountable

        PlayerEndgameComponent comp = plugin.getPlayerComponent(playerRef.getUuid());
        if (comp == null) return;
        PetTier currentTier = comp.getPetData().getPetTier(petId);
        if (currentTier.ordinal() < requiredTier.ordinal()) return; // Tier too low

        // Check if already mounted → dismount
        NPCMountComponent existingMount = targetStore.getComponent(targetRef, NPCMountComponent.getComponentType());
        MovementStatesComponent moveStates = store.getComponent(playerEntityRef, MovementStatesComponent.getComponentType());

        if (existingMount != null && existingMount.getOwnerPlayerRef() != null) {
            if (Objects.equals(existingMount.getOwnerPlayerRef(), playerRef)) {
                // === DISMOUNT ===
                mountedEntities.remove(playerRef.getUuid());
                targetStore.removeComponent(targetRef, NPCMountComponent.getComponentType());

                Player playerComp = store.getComponent(playerEntityRef, Player.getComponentType());
                if (playerComp != null) {
                    playerComp.setMountEntityId(-1);
                }

                MountNPC dismountPacket = new MountNPC(0, 0, 0, -1);
                playerRef.getPacketHandler().write(dismountPacket);

                if (moveStates != null) {
                    MovementStates states = moveStates.getMovementStates();
                    if (states != null) {
                        states.mounting = false;
                        states.idle = true;
                    }
                }

                MovementManager movementManager = store.getComponent(playerEntityRef, MovementManager.getComponentType());
                if (movementManager != null) {
                    movementManager.resetDefaultsAndUpdate(playerEntityRef, store);
                }

                plugin.getLogger().atFine().log("[PetMount] %s dismounted from %s", playerRef.getUsername(), petId);
                return;
            }
            return; // Someone else is mounted
        }

        // === MOUNT ===
        int roleIndex = -1;
        NPCEntity npcEntity = targetStore.getComponent(targetRef, NPCEntity.getComponentType());
        if (npcEntity != null) {
            roleIndex = npcEntity.getRoleIndex();
        }

        float yOffset = MOUNT_Y_OFFSETS.getOrDefault(petId, 1.2f);
        // Scale Y offset with pet size growth
        float baseScale = endgame.plugin.managers.PetManager.getBaseScale(petId);
        float currentScale = baseScale + currentTier.getScaleBonus();
        yOffset *= (currentScale / baseScale);

        NPCMountComponent mountComp = new NPCMountComponent();
        mountComp.setOriginalRoleIndex(roleIndex);
        mountComp.setOwnerPlayerRef(playerRef);
        mountComp.setAnchor(0, yOffset, 0);
        targetStore.addComponent(targetRef, NPCMountComponent.getComponentType(), mountComp);

        try {
            NetworkId networkIdComponent = targetStore.getComponent(targetRef, NetworkId.getComponentType());
            if (networkIdComponent != null) {
                int networkId = networkIdComponent.getId();
                mountedEntities.put(playerRef.getUuid(), networkId);

                MountNPC mountPacket = new MountNPC(0, yOffset, 0, networkId);
                playerRef.getPacketHandler().write(mountPacket);

                Player playerComp = store.getComponent(playerEntityRef, Player.getComponentType());
                if (playerComp != null) {
                    playerComp.setMountEntityId(networkId);
                }

                if (moveStates != null) {
                    MovementStates states = moveStates.getMovementStates();
                    if (states != null) {
                        states.mounting = true;
                    }
                }

                plugin.getLogger().atInfo().log("[PetMount] %s mounted %s (yOffset=%.1f, networkId=%d)",
                        playerRef.getUsername(), petId, yOffset, networkId);
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[PetMount] Failed to send mount packet: %s", e.getMessage());
        }
    }

    /**
     * Clean up when player disconnects.
     */
    public void onPlayerDisconnect(UUID playerUuid) {
        lastMountInteraction.remove(playerUuid);
        mountedEntities.remove(playerUuid);
    }
}
