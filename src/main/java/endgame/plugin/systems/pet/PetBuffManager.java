package endgame.plugin.systems.pet;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.PetAbility;
import endgame.plugin.config.PetAbilityRegistry;
import endgame.plugin.config.PetTier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages passive buffs applied to the pet owner based on pet tier.
 * Tier S: applies an infinite EntityEffect to the owner (ice/fire/physical/poison resistance).
 * Tier SS: notifies about aura (actual AOE handled by future PetAuraSystem).
 *
 * Buffs are applied on pet summon and removed on despawn.
 */
public class PetBuffManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.PetBuff");

    // Tier S effect IDs — map petId to vanilla EntityEffect asset ID
    // These use vanilla effects; custom effects can be added as JSON assets later
    private static final Map<String, String> TIER_S_EFFECTS = Map.of(
            "Endgame_Pet_Dragon_Frost", "Ice_Resistance",
            "Endgame_Pet_Dragon_Fire", "Fire_Resistance",
            "Endgame_Pet_Golem_Void", "Endgame_Accessory_Invuln",
            "Endgame_Pet_Hedera", "Poison_Immunity"
    );

    private static final Map<String, String> TIER_S_PERK_NAMES = Map.of(
            "Endgame_Pet_Dragon_Frost", "Frost Ward",
            "Endgame_Pet_Dragon_Fire", "Flame Ward",
            "Endgame_Pet_Golem_Void", "Void Armor",
            "Endgame_Pet_Hedera", "Nature's Guard"
    );

    // Track active buffs: ownerUuid -> petId (for cleanup)
    private final ConcurrentHashMap<UUID, String> activeBuffs = new ConcurrentHashMap<>();
    private final EndgameQoL plugin;

    public PetBuffManager(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Apply passive buffs to the pet owner based on pet tier.
     * Called when pet is summoned.
     */
    public void onPetSummoned(@Nonnull UUID ownerUuid, @Nonnull String petId, @Nonnull PetTier tier) {
        if (tier.ordinal() < PetTier.S.ordinal()) return;

        activeBuffs.put(ownerUuid, petId);
        String perkName = TIER_S_PERK_NAMES.getOrDefault(petId, "Unknown");

        // Apply Tier S effect to player
        String effectId = TIER_S_EFFECTS.get(petId);
        if (effectId != null) {
            applyEffectToOwner(ownerUuid, effectId);
        }

        // Notify
        PlayerRef pr = findPlayerRef(ownerUuid);
        if (pr != null) {
            pr.sendMessage(Message.join(
                    Message.raw("[Pets] ").color("#ffaa00"),
                    Message.raw("Perk active: " + perkName).color("#88ccff")
            ));
        }

        LOGGER.atFine().log("[PetBuff] Applied Tier S perk '%s' (effect: %s) to %s",
                perkName, effectId, ownerUuid);

        // Tier SS aura notification
        if (tier == PetTier.SS) {
            PetAbility ssAbility = null;
            for (PetAbility ab : PetAbilityRegistry.getAbilities(petId)) {
                if (ab.requiredTier() == PetTier.SS) { ssAbility = ab; break; }
            }
            if (ssAbility != null && pr != null) {
                pr.sendMessage(Message.join(
                        Message.raw("[Pets] ").color("#ffaa00"),
                        Message.raw("Aura active: " + ssAbility.name()).color("#ff5555")
                ));
            }
        }
    }

    /**
     * Remove all passive buffs from the pet owner.
     */
    public void onPetDespawned(@Nonnull UUID ownerUuid) {
        String petId = activeBuffs.remove(ownerUuid);
        if (petId == null) return;

        // Remove effect from player
        String effectId = TIER_S_EFFECTS.get(petId);
        if (effectId != null) {
            removeEffectFromOwner(ownerUuid, effectId);
        }

        LOGGER.atFine().log("[PetBuff] Removed buffs for %s (pet: %s)", ownerUuid, petId);
    }

    /**
     * Apply an infinite EntityEffect to the player.
     */
    private void applyEffectToOwner(@Nonnull UUID ownerUuid, @Nonnull String effectId) {
        PlayerRef pr = findPlayerRef(ownerUuid);
        if (pr == null) return;

        Ref<EntityStore> ref = pr.getReference();
        if (ref == null || !ref.isValid()) return;

        try {
            World world = ref.getStore().getExternalData().getWorld();
            if (world == null) return;

            world.execute(() -> {
                if (!ref.isValid()) return;
                Store<EntityStore> store = ref.getStore();

                EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
                if (effect == null) {
                    LOGGER.atFine().log("[PetBuff] Effect '%s' not found — skipping (may be vanilla-only)", effectId);
                    return;
                }

                EffectControllerComponent ec = store.ensureAndGetComponent(
                        ref, EffectControllerComponent.getComponentType());
                if (ec != null) {
                    int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
                    ec.addInfiniteEffect(ref, effectIndex, effect, store);
                    LOGGER.atFine().log("[PetBuff] Applied infinite effect '%s' to %s", effectId, ownerUuid);
                }
            });
        } catch (Exception e) {
            LOGGER.atFine().log("[PetBuff] Failed to apply effect: %s", e.getMessage());
        }
    }

    /**
     * Remove an EntityEffect from the player.
     */
    private void removeEffectFromOwner(@Nonnull UUID ownerUuid, @Nonnull String effectId) {
        PlayerRef pr = findPlayerRef(ownerUuid);
        if (pr == null) return;

        Ref<EntityStore> ref = pr.getReference();
        if (ref == null || !ref.isValid()) return;

        try {
            World world = ref.getStore().getExternalData().getWorld();
            if (world == null) return;

            world.execute(() -> {
                if (!ref.isValid()) return;
                Store<EntityStore> store = ref.getStore();

                EffectControllerComponent ec = store.getComponent(
                        ref, EffectControllerComponent.getComponentType());
                if (ec != null) {
                    int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
                    if (effectIndex != Integer.MIN_VALUE) {
                        ec.removeEffect(ref, effectIndex, RemovalBehavior.COMPLETE, store);
                        LOGGER.atFine().log("[PetBuff] Removed effect '%s' from %s", effectId, ownerUuid);
                    }
                }
            });
        } catch (Exception e) {
            LOGGER.atFine().log("[PetBuff] Failed to remove effect: %s", e.getMessage());
        }
    }

    public boolean hasActiveBuff(@Nonnull UUID ownerUuid) {
        return activeBuffs.containsKey(ownerUuid);
    }

    public void clear() {
        activeBuffs.clear();
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
