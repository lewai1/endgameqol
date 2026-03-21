package endgame.plugin.ui.snake;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * Event data CODEC for snake game UI events (button clicks and key presses).
 */
public class SnakeEventData {

    public static final BuilderCodec<SnakeEventData> CODEC = BuilderCodec
            .builder(SnakeEventData.class, SnakeEventData::new)
            .append(new KeyedCodec<String>("Direction", Codec.STRING),
                    (e, s) -> e.direction = s, e -> e.direction)
            .add()
            .append(new KeyedCodec<String>("Retry", Codec.STRING),
                    (e, s) -> e.retry = "true".equalsIgnoreCase(s),
                    e -> e.retry != null && e.retry ? "true" : null)
            .add()
            .append(new KeyedCodec<String>("@KeyCode", Codec.STRING),
                    (e, s) -> e.keyCode = s, e -> e.keyCode)
            .add()
            .build();

    @Nullable String direction;
    @Nullable Boolean retry;
    @Nullable String keyCode;
}
