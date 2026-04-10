package endgame.plugin.config;

import endgame.plugin.config.PetAbility.AbilityType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Static registry of all pet abilities, organized by petId and tier.
 * Abilities are display-only in Phase 1 — mechanics added in Phase 2.
 */
public final class PetAbilityRegistry {

    private PetAbilityRegistry() {}

    private static final Map<String, List<PetAbility>> ABILITIES = Map.of(
        "Endgame_Pet_Dragon_Frost", List.of(
            new PetAbility("frost_claw", "Frost Claw", "Basic melee ice attack", PetTier.D, AbilityType.COMBAT),
            new PetAbility("rideable", "Rideable", "Mount the dragon on the ground", PetTier.C, AbilityType.UTILITY),
            new PetAbility("skyborne", "Skyborne", "Fly/walk transitions, follows in air", PetTier.B, AbilityType.MOBILITY),
            new PetAbility("frost_bolt", "Frost Bolt", "Ranged ice projectile attack", PetTier.A, AbilityType.COMBAT),
            new PetAbility("frost_ward", "Frost Ward", "Owner gets 100% ice resistance", PetTier.S, AbilityType.PASSIVE),
            new PetAbility("frost_aura", "Frost Aura", "Slow enemies within 5 blocks", PetTier.SS, AbilityType.AURA)
        ),
        "Endgame_Pet_Dragon_Fire", List.of(
            new PetAbility("flame_claw", "Flame Claw", "Basic melee fire attack", PetTier.D, AbilityType.COMBAT),
            new PetAbility("fire_breath", "Fire Breath", "Short-range cone fire attack", PetTier.C, AbilityType.COMBAT),
            new PetAbility("skyborne_ride", "Skyborne", "Fly/walk transitions + rideable", PetTier.B, AbilityType.MOBILITY),
            new PetAbility("fireball", "Fireball", "Explosive ranged projectile", PetTier.A, AbilityType.COMBAT),
            new PetAbility("flame_ward", "Flame Ward", "Owner gets 100% fire resistance", PetTier.S, AbilityType.PASSIVE),
            new PetAbility("immolation", "Immolation", "Attackers take fire damage", PetTier.SS, AbilityType.PASSIVE)
        ),
        "Endgame_Pet_Golem_Void", List.of(
            new PetAbility("void_fist", "Void Fist", "Basic melee void attack", PetTier.D, AbilityType.COMBAT),
            new PetAbility("provoke", "Provoke", "Taunt nearby mobs toward pet", PetTier.C, AbilityType.UTILITY),
            new PetAbility("ground_slam", "Ground Slam", "AOE knockback attack", PetTier.B, AbilityType.COMBAT),
            new PetAbility("boulder_throw", "Boulder Throw", "Heavy ranged projectile", PetTier.A, AbilityType.COMBAT),
            new PetAbility("void_armor", "Void Armor", "Owner -20% physical dmg + rideable", PetTier.S, AbilityType.PASSIVE),
            new PetAbility("void_barrier", "Void Barrier", "Shield absorbs 100 HP every 30s", PetTier.SS, AbilityType.PASSIVE)
        ),
        "Endgame_Pet_Hedera", List.of(
            new PetAbility("thorn_scratch", "Thorn Scratch", "Basic melee nature attack", PetTier.D, AbilityType.COMBAT),
            new PetAbility("healing_spore", "Healing Spore", "Heal owner 2 HP/s nearby", PetTier.C, AbilityType.PASSIVE),
            new PetAbility("war_cry", "War Cry", "AOE slow on nearby enemies", PetTier.B, AbilityType.COMBAT),
            new PetAbility("vine_grab", "Vine Grab", "Pull enemy toward pet", PetTier.A, AbilityType.COMBAT),
            new PetAbility("natures_guard", "Nature's Guard", "Owner immune to poison", PetTier.S, AbilityType.PASSIVE),
            new PetAbility("natures_blessing", "Nature's Blessing", "Regen + poison aura", PetTier.SS, AbilityType.AURA)
        )
    );

    @Nonnull
    public static List<PetAbility> getAbilities(@Nonnull String petId) {
        return ABILITIES.getOrDefault(petId, Collections.emptyList());
    }

    @Nonnull
    public static List<PetAbility> getUnlocked(@Nonnull String petId, @Nonnull PetTier currentTier) {
        List<PetAbility> result = new ArrayList<>();
        for (PetAbility a : getAbilities(petId)) {
            if (a.requiredTier().ordinal() <= currentTier.ordinal()) result.add(a);
        }
        return result;
    }

    @Nonnull
    public static List<PetAbility> getLocked(@Nonnull String petId, @Nonnull PetTier currentTier) {
        List<PetAbility> result = new ArrayList<>();
        for (PetAbility a : getAbilities(petId)) {
            if (a.requiredTier().ordinal() > currentTier.ordinal()) result.add(a);
        }
        return result;
    }

    @Nullable
    public static PetAbility getNextUnlock(@Nonnull String petId, @Nonnull PetTier currentTier) {
        PetTier next = currentTier.next();
        if (next == null) return null;
        for (PetAbility a : getAbilities(petId)) {
            if (a.requiredTier() == next) return a;
        }
        return null;
    }
}
