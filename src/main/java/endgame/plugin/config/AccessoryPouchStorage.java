package endgame.plugin.config;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Separate storage file for per-player Accessory Pouch inventories.
 * Saved as EndgameAccessories.json alongside EndgameConfig.json.
 */
public class AccessoryPouchStorage {

    @Nonnull
    public static final BuilderCodec<AccessoryPouchStorage> CODEC = BuilderCodec
            .builder(AccessoryPouchStorage.class, AccessoryPouchStorage::new)
            .append(new KeyedCodec<Map<String, AccessoryPouchData>>("AccessoryPouches",
                            new MapCodec<>(AccessoryPouchData.CODEC, ConcurrentHashMap::new, false)),
                    (storage, value) -> {
                        if (value != null) {
                            storage.accessoryPouches.putAll(value);
                        }
                    },
                    storage -> new ConcurrentHashMap<>(storage.accessoryPouches))
            .add()
            .build();

    private final Map<String, AccessoryPouchData> accessoryPouches = new ConcurrentHashMap<>();

    @Nonnull
    public AccessoryPouchData getAccessoryPouch(@Nonnull String playerUuid) {
        return accessoryPouches.computeIfAbsent(playerUuid, k -> new AccessoryPouchData());
    }

    public Map<String, AccessoryPouchData> getAllAccessoryPouches() {
        return accessoryPouches;
    }
}
