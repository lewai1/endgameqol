package endgame.plugin.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Component that marks an entity as a Void Mark target.
 * Applied by Prisma Daggers basic attacks. When the dagger user triggers
 * a signature attack, they blink to the nearest marked target instead of
 * the current damage target. Consumed on blink.
 */
public class VoidMarkComponent implements Component<EntityStore> {

    private UUID markerUuid;
    private long markedAt;
    private int markDurationMs;

    public VoidMarkComponent() {
        this(null, System.currentTimeMillis(), 10000);
    }

    public VoidMarkComponent(UUID markerUuid, long markedAt, int markDurationMs) {
        this.markerUuid = markerUuid;
        this.markedAt = markedAt;
        this.markDurationMs = markDurationMs;
    }

    public VoidMarkComponent(VoidMarkComponent other) {
        this.markerUuid = other.markerUuid;
        this.markedAt = other.markedAt;
        this.markDurationMs = other.markDurationMs;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new VoidMarkComponent(this);
    }

    public UUID getMarkerUuid() {
        return markerUuid;
    }

    public void setMarkerUuid(UUID markerUuid) {
        this.markerUuid = markerUuid;
    }

    public long getMarkedAt() {
        return markedAt;
    }

    public int getMarkDurationMs() {
        return markDurationMs;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - markedAt > markDurationMs;
    }

    public void refresh(UUID markerUuid, int durationMs) {
        this.markerUuid = markerUuid;
        this.markedAt = System.currentTimeMillis();
        this.markDurationMs = durationMs;
    }
}
