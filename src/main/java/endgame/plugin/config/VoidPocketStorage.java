package endgame.plugin.config;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Separate storage file for per-player Void Pocket inventories.
 * Saved as VoidPockets.json alongside EndgameConfig.json.
 */
public class VoidPocketStorage {

    @Nonnull
    public static final BuilderCodec<VoidPocketStorage> CODEC = BuilderCodec
            .builder(VoidPocketStorage.class, VoidPocketStorage::new)
            .append(new KeyedCodec<Map<String, VoidPocketData>>("VoidPockets",
                            new MapCodec<>(VoidPocketData.CODEC, ConcurrentHashMap::new, false)),
                    (storage, value) -> {
                        if (value != null) {
                            storage.voidPockets.putAll(value);
                        }
                    },
                    storage -> new ConcurrentHashMap<>(storage.voidPockets))
            .add()
            .build();

    private final Map<String, VoidPocketData> voidPockets = new ConcurrentHashMap<>();

    @Nonnull
    public VoidPocketData getVoidPocket(@Nonnull String playerUuid) {
        return voidPockets.computeIfAbsent(playerUuid, k -> new VoidPocketData());
    }

    public Map<String, VoidPocketData> getAllVoidPockets() {
        return voidPockets;
    }
}
