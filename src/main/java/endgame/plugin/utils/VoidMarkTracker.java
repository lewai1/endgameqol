package endgame.plugin.utils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared cross-system tracker for Void Mark targets.
 * DamageEventSystems can't iterate archetypes, so this provides a lookup structure
 * for finding the nearest marked entity from a DaggerVanishSystem context.
 */
public final class VoidMarkTracker {

    private static final VoidMarkTracker INSTANCE = new VoidMarkTracker();

    public static VoidMarkTracker getInstance() {
        return INSTANCE;
    }

    public record MarkedEntity(
            Ref<EntityStore> ref,
            UUID markerUuid,
            long expiry,
            Vector3d lastPos
    ) {}

    // Key: Ref<EntityStore> directly — equals()/hashCode() are stable across instances
    private final ConcurrentHashMap<Ref<EntityStore>, MarkedEntity> marks = new ConcurrentHashMap<>();

    private VoidMarkTracker() {}

    public void addMark(Ref<EntityStore> ref, UUID markerUuid, long expiry, Vector3d position) {
        marks.put(ref, new MarkedEntity(ref, markerUuid, expiry, position));
    }

    public void removeMark(Ref<EntityStore> ref) {
        marks.remove(ref);
    }

    /**
     * Find the nearest marked entity belonging to the given marker player,
     * within maxRange blocks. Returns null if none found.
     */
    @Nullable
    public MarkedEntity findNearestMark(UUID markerUuid, Vector3d fromPos, double maxRange) {
        long now = System.currentTimeMillis();
        double maxRangeSq = maxRange * maxRange;
        MarkedEntity nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (MarkedEntity mark : marks.values()) {
            if (!markerUuid.equals(mark.markerUuid)) continue;
            if (now > mark.expiry) continue;
            if (mark.ref == null || !mark.ref.isValid()) continue;
            if (mark.lastPos == null) continue;

            double dx = mark.lastPos.x - fromPos.x;
            double dy = mark.lastPos.y - fromPos.y;
            double dz = mark.lastPos.z - fromPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= maxRangeSq && distSq < nearestDistSq) {
                nearest = mark;
                nearestDistSq = distSq;
            }
        }
        return nearest;
    }

    /**
     * Remove expired or invalid entries.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        marks.entrySet().removeIf(e -> {
            MarkedEntity m = e.getValue();
            return now > m.expiry || m.ref == null || !m.ref.isValid();
        });
    }

    public void clear() {
        marks.clear();
    }
}
