package endgame.plugin.components;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nullable;

/**
 * ChunkStore component for Frostwalker ice blocks.
 * Tracks a countdown timer — when it expires the block reverts to water.
 */
public class FrostwalkerIceComponent implements Component<ChunkStore> {

    public static final BuilderCodec<FrostwalkerIceComponent> CODEC =
            BuilderCodec.builder(FrostwalkerIceComponent.class, FrostwalkerIceComponent::new).build();

    private static final float DEFAULT_TIMER = 3.0f;
    private float timer;

    public FrostwalkerIceComponent() {
        this.timer = DEFAULT_TIMER;
    }

    public FrostwalkerIceComponent(FrostwalkerIceComponent other) {
        this.timer = other.timer;
    }

    public float getTimer() {
        return timer;
    }

    public void setTimer(float timer) {
        this.timer = timer;
    }

    public void decreaseTimer(float dt) {
        this.timer -= dt;
    }

    public boolean isExpired() {
        return this.timer <= 0.0f;
    }

    public void reset() {
        this.timer = DEFAULT_TIMER;
    }

    public static ComponentType<ChunkStore, FrostwalkerIceComponent> getComponentType() {
        return EndgameQoL.getInstance().getFrostwalkerIceComponentType();
    }

    @Nullable
    @Override
    public Component<ChunkStore> clone() {
        return new FrostwalkerIceComponent(this);
    }
}
