package endgame.plugin.utils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reverse lookup cache for {@link PlayerRef}, keyed by UUID and entity {@link Ref}.
 *
 * Avoids the O(n) scan of {@code Universe.get().getPlayers()} that was duplicated
 * across multiple hot-path systems (boss damage handlers, pet tick, accessory tick).
 *
 * Populated on {@code PlayerReadyEvent} via {@link #register(PlayerRef)}, cleared on
 * {@code PlayerDisconnectEvent} via {@link #unregister(UUID)}. Lookups are O(1).
 *
 * Fallback: if a lookup misses (player ready before register fires, or cache was
 * cleared), callers should fall back to {@link #lookupFallback(UUID)} which does
 * the one-shot O(n) scan and re-populates the cache.
 */
public final class PlayerRefCache {

    private static final ConcurrentHashMap<UUID, PlayerRef> BY_UUID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Ref<EntityStore>, PlayerRef> BY_REF = new ConcurrentHashMap<>();

    private PlayerRefCache() {}

    public static void register(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (uuid != null) BY_UUID.put(uuid, playerRef);
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref != null) BY_REF.put(ref, playerRef);
    }

    public static void unregister(@Nonnull UUID uuid) {
        PlayerRef removed = BY_UUID.remove(uuid);
        if (removed != null) {
            Ref<EntityStore> ref = removed.getReference();
            if (ref != null) BY_REF.remove(ref);
        }
    }

    @Nullable
    public static PlayerRef getByUuid(@Nonnull UUID uuid) {
        PlayerRef cached = BY_UUID.get(uuid);
        if (cached != null) return cached;
        return lookupFallback(uuid);
    }

    @Nullable
    public static PlayerRef getByRef(@Nonnull Ref<EntityStore> ref) {
        PlayerRef cached = BY_REF.get(ref);
        if (cached != null) return cached;
        return lookupFallbackByRef(ref);
    }

    public static void clear() {
        BY_UUID.clear();
        BY_REF.clear();
    }

    @Nullable
    private static PlayerRef lookupFallback(@Nonnull UUID uuid) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            if (uuid.equals(p.getUuid())) {
                register(p);
                return p;
            }
        }
        return null;
    }

    @Nullable
    private static PlayerRef lookupFallbackByRef(@Nonnull Ref<EntityStore> ref) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            Ref<EntityStore> pr = p.getReference();
            if (pr != null && ref.equals(pr)) {
                register(p);
                return p;
            }
        }
        return null;
    }
}
