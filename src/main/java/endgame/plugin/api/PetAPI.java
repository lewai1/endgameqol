package endgame.plugin.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PetOwnerComponent;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.PetTier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Public API for external mods (EndlessLeveling, etc.) to query pet ownership and stats.
 * All methods are O(1) — direct component lookup on the entity ref.
 *
 * Usage from external mod:
 *   UUID owner = PetAPI.getPetOwner(npcRef);
 *   float dmg = PetAPI.getPetDamage(npcRef);
 *   int kills = PetAPI.getPetKill(npcRef);
 */
public final class PetAPI {

    private PetAPI() {}

    /**
     * Get the owner UUID of a pet NPC, or null if the entity is not a pet.
     */
    @Nullable
    public static UUID getPetOwner(@Nullable Ref<EntityStore> npcRef) {
        PetOwnerComponent comp = getComponent(npcRef);
        return comp != null ? comp.getOwnerUuid() : null;
    }

    /**
     * Get the total damage dealt by this pet since it was spawned.
     * Returns 0 if the entity is not a pet.
     */
    public static float getPetDamage(@Nullable Ref<EntityStore> npcRef) {
        PetOwnerComponent comp = getComponent(npcRef);
        return comp != null ? comp.getTotalDamageDealt() : 0f;
    }

    /**
     * Get the total kill count of this pet since it was spawned.
     * Returns 0 if the entity is not a pet.
     */
    public static int getPetKill(@Nullable Ref<EntityStore> npcRef) {
        PetOwnerComponent comp = getComponent(npcRef);
        return comp != null ? comp.getTotalKills() : 0;
    }

    // =========================================================================
    // Player-level queries (by owner UUID)
    // =========================================================================

    /**
     * Get the tier of a player's pet. Returns null if pet not unlocked or player not found.
     */
    @Nullable
    public static PetTier getPetTier(@Nonnull UUID ownerUuid, @Nonnull String petId) {
        PlayerEndgameComponent comp = EndgameQoL.getInstance().getPlayerComponent(ownerUuid);
        if (comp == null || !comp.getPetData().isUnlocked(petId)) return null;
        return comp.getPetData().getPetTier(petId);
    }

    /**
     * Check if a pet is unlocked for a player.
     */
    public static boolean isPetUnlocked(@Nonnull UUID ownerUuid, @Nonnull String petId) {
        PlayerEndgameComponent comp = EndgameQoL.getInstance().getPlayerComponent(ownerUuid);
        return comp != null && comp.getPetData().isUnlocked(petId);
    }

    /**
     * Get the currently active (summoned) pet ID for a player. Empty string if none.
     */
    @Nonnull
    public static String getActivePetId(@Nonnull UUID ownerUuid) {
        PlayerEndgameComponent comp = EndgameQoL.getInstance().getPlayerComponent(ownerUuid);
        return comp != null ? comp.getPetData().getActivePetId() : "";
    }

    @Nullable
    private static PetOwnerComponent getComponent(@Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) return null;
        var type = PetOwnerComponent.getComponentType();
        if (type == null) return null;
        return ref.getStore().getComponent(ref, type);
    }
}
