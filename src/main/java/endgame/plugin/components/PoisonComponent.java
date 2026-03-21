package endgame.plugin.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Component that stores poison effect data on an entity.
 * When attached, the PoisonTickSystem will deal periodic damage.
 */
public class PoisonComponent implements Component<EntityStore> {

    public enum PoisonSource {
        BOSS,
        PLAYER
    }

    private float damagePerTick;
    private float tickInterval;
    private int remainingTicks;
    private float elapsedTime;
    private PoisonSource source;

    public PoisonComponent() {
        this(8f, 1.0f, 5);
    }

    public PoisonComponent(float damagePerTick, float tickInterval, int totalTicks) {
        this(damagePerTick, tickInterval, totalTicks, PoisonSource.BOSS);
    }

    public PoisonComponent(float damagePerTick, float tickInterval, int totalTicks, PoisonSource source) {
        this.damagePerTick = damagePerTick;
        this.tickInterval = tickInterval;
        this.remainingTicks = totalTicks;
        this.elapsedTime = 0f;
        this.source = source;
    }

    public PoisonComponent(PoisonComponent other) {
        this.damagePerTick = other.damagePerTick;
        this.tickInterval = other.tickInterval;
        this.remainingTicks = other.remainingTicks;
        this.elapsedTime = other.elapsedTime;
        this.source = other.source;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new PoisonComponent(this);
    }

    public float getDamagePerTick() {
        return damagePerTick;
    }

    public float getTickInterval() {
        return tickInterval;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public float getElapsedTime() {
        return elapsedTime;
    }

    public PoisonSource getSource() {
        return source;
    }

    public void addElapsedTime(float dt) {
        this.elapsedTime += dt;
    }

    public void resetElapsedTime() {
        this.elapsedTime = 0f;
    }

    public void decrementRemainingTicks() {
        this.remainingTicks--;
    }

    public boolean isExpired() {
        return this.remainingTicks <= 0;
    }

    public void refresh(float damagePerTick, float tickInterval, int totalTicks, PoisonSource source) {
        this.damagePerTick = Math.max(this.damagePerTick, damagePerTick);
        this.tickInterval = tickInterval;
        this.remainingTicks = Math.max(this.remainingTicks, totalTicks);
        this.source = source;
    }
}
