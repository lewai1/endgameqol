package endgame.plugin.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Component that stores fire burn effect data on an entity.
 * When attached, the BurnTickSystem will deal periodic fire damage.
 */
public class BurnComponent implements Component<EntityStore> {

    private float damagePerTick;
    private float tickInterval;
    private int remainingTicks;
    private float elapsedTime;

    public BurnComponent() {
        this(50f, 1.0f, 3);
    }

    public BurnComponent(float damagePerTick, float tickInterval, int totalTicks) {
        this.damagePerTick = damagePerTick;
        this.tickInterval = tickInterval;
        this.remainingTicks = totalTicks;
        this.elapsedTime = 0f;
    }

    public BurnComponent(BurnComponent other) {
        this.damagePerTick = other.damagePerTick;
        this.tickInterval = other.tickInterval;
        this.remainingTicks = other.remainingTicks;
        this.elapsedTime = other.elapsedTime;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new BurnComponent(this);
    }

    public float getDamagePerTick() { return damagePerTick; }
    public float getTickInterval() { return tickInterval; }
    public int getRemainingTicks() { return remainingTicks; }
    public float getElapsedTime() { return elapsedTime; }

    public void addElapsedTime(float dt) { this.elapsedTime += dt; }
    public void resetElapsedTime() { this.elapsedTime = 0f; }
    public void decrementRemainingTicks() { this.remainingTicks--; }
    public boolean isExpired() { return this.remainingTicks <= 0; }

    public void refresh(float damagePerTick, float tickInterval, int totalTicks) {
        this.damagePerTick = Math.max(this.damagePerTick, damagePerTick);
        this.tickInterval = tickInterval;
        this.remainingTicks = Math.max(this.remainingTicks, totalTicks);
    }
}
