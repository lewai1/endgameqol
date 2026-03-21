package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persisted storage for per-player locale overrides.
 * Saved as PlayerLocales.json alongside EndgameConfig.json.
 *
 * Players who set a manual locale via /eg lang have their UUID → locale stored here.
 * Players using auto-detection (default) have no entry.
 */
public class PlayerLocaleStorage {

    @Nonnull
    public static final BuilderCodec<PlayerLocaleStorage> CODEC = BuilderCodec
            .builder(PlayerLocaleStorage.class, PlayerLocaleStorage::new)
            .append(new KeyedCodec<Map<String, String>>("PlayerLocales",
                            new MapCodec<>(Codec.STRING, HashMap::new, false)),
                    (storage, value) -> {
                        if (value != null) {
                            storage.locales.putAll(value);
                        }
                    },
                    storage -> storage.locales)
            .add()
            .build();

    private final Map<String, String> locales = new ConcurrentHashMap<>();

    @Nullable
    public String getOverride(@Nonnull String uuid) {
        return locales.get(uuid);
    }

    public void setOverride(@Nonnull String uuid, @Nonnull String locale) {
        locales.put(uuid, locale);
    }

    public void removeOverride(@Nonnull String uuid) {
        locales.remove(uuid);
    }
}
