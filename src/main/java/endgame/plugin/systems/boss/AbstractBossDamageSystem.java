package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Abstract base class for boss damage systems in EndgameQoL.
 * Provides shared helper methods for resolving attackers, NPC entities,
 * and matching entity refs to PlayerRefs.
 *
 * <p>All three boss damage systems (GolemVoidDamageSystem, GenericBossDamageSystem,
 * BossDamageFilterSystem) extend this class to eliminate duplicated resolution logic.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>{@code Damage.ProjectileSource extends Damage.EntitySource} — a single
 *       {@code instanceof EntitySource} catches both melee and projectile sources.</li>
 *   <li>Attacker component lookups always use {@code attackerRef.getStore()}, never
 *       the handle() store parameter, for cross-world/instance safety.</li>
 * </ul>
 */
public abstract class AbstractBossDamageSystem extends DamageEventSystem {

    /** Shared Query.any() used by GolemVoidDamageSystem and GenericBossDamageSystem. */
    @Nonnull
    protected static final Query<EntityStore> QUERY_ANY = Query.any();

    /**
     * All boss damage systems run in the FilterDamageGroup so they can modify/cancel
     * damage before it is applied.
     */
    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    // ── Attacker resolution ──────────────────────────────────────────────

    /**
     * Extracts the attacker entity ref from a damage event's source.
     * Handles both melee ({@code EntitySource}) and projectile ({@code ProjectileSource})
     * sources, since ProjectileSource extends EntitySource.
     *
     * @param damage the damage event
     * @return the attacker ref, or {@code null} if the source is not an entity
     *         or the ref is invalid
     */
    @Nullable
    protected Ref<EntityStore> resolveAttacker(@Nonnull Damage damage) {
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return null;
        }
        Ref<EntityStore> ref = entitySource.getRef();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref;
    }

    // ── NPC resolution ───────────────────────────────────────────────────

    /**
     * Resolves the {@link NPCEntity} component for the given entity ref.
     * Uses the ref's own store for cross-world safety (the handle() store belongs
     * to the damage target, which may be in a different world/instance).
     *
     * @param ref   the entity ref to look up
     * @param store fallback store (unused — ref.getStore() is always preferred).
     *              Kept in signature for clarity at call sites.
     * @return the NPCEntity component, or {@code null} if the ref is not an NPC
     */
    @Nullable
    protected NPCEntity resolveNPC(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) return null;
        return ref.getStore().getComponent(ref, npcType);
    }

    /**
     * Resolves the NPC type ID string for the given entity ref.
     * Uses the ref's own store for cross-world safety.
     *
     * @param ref   the entity ref to look up
     * @param store fallback store (unused — ref.getStore() is always preferred)
     * @return the NPC type ID (e.g. "Endgame_Golem_Void"), or {@code null}
     *         if the ref is not an NPC or has no type ID
     */
    @Nullable
    protected String resolveNPCTypeId(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        NPCEntity npc = resolveNPC(ref, store);
        if (npc == null) return null;
        return npc.getNPCTypeId();
    }

    // ── Player matching ──────────────────────────────────────────────────

    /**
     * Matches an attacker entity ref to a connected player's {@link PlayerRef}.
     * Iterates all online players and compares using {@code .equals()} (never {@code ==}).
     *
     * @param attackerRef the entity ref of the attacker
     * @return the matching PlayerRef, or {@code null} if the attacker is not a player
     */
    @Nullable
    protected PlayerRef findPlayerRef(@Nonnull Ref<EntityStore> attackerRef) {
        return endgame.plugin.utils.PlayerRefCache.getByRef(attackerRef);
    }
}
