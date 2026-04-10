package endgame.plugin.config;

import javax.annotation.Nonnull;

/**
 * Definition of a pet ability unlocked at a specific tier.
 * Display-only in Phase 1 — mechanics added in Phase 2.
 */
public record PetAbility(
    @Nonnull String id,
    @Nonnull String name,
    @Nonnull String description,
    @Nonnull PetTier requiredTier,
    @Nonnull AbilityType type
) {
    public enum AbilityType { COMBAT, PASSIVE, AURA, UTILITY, MOBILITY }
}
