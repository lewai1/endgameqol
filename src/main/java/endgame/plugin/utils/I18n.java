package endgame.plugin.utils;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.UpdateType;
import com.hypixel.hytale.protocol.packets.assets.UpdateTranslations;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.Config;
import endgame.plugin.config.PlayerLocaleStorage;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side internationalization system for chat messages.
 *
 * Hytale's Message.translatable() only works for client-side translation (items, UI
 * labels in .lang files). For server-side chat messages sent via playerRef.sendMessage(),
 * we need our own translation system.
 *
 * Loads translations from .properties files at:
 *   Server/Languages/{locale}/messages.properties
 *
 * Supported locales: en-US (default), fr-FR, es-ES, pt-BR, ru-RU
 *
 * Usage:
 *   I18n.get("commands.eg.help")                        // English default
 *   I18n.get("combo.new_record", 15)                    // With format args
 *   I18n.getFor("fr-FR", "commands.eg.help")            // Locale-specific
 *   I18n.getForPlayer(playerRef, "commands.eg.help")    // Player-aware (auto-detect or override)
 */
public final class I18n {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.I18n");

    /** Default locale used when no locale is specified or the requested key is missing. */
    public static final String DEFAULT_LOCALE = "en-US";

    /** All supported locales. */
    private static final String[] SUPPORTED_LOCALES = {"en-US", "fr-FR", "es-ES", "pt-BR", "ru-RU"};

    /** Locale -> Properties map. Thread-safe for reads after init(). */
    private static final Map<String, Properties> TRANSLATIONS = new ConcurrentHashMap<>();

    /** Runtime cache: player UUID -> resolved locale. Cleared on disconnect, invalidated on override change. */
    private static final ConcurrentHashMap<UUID, String> PLAYER_LOCALES = new ConcurrentHashMap<>();

    /** Persisted locale overrides (UUID -> locale). Set during init(). */
    private static volatile Config<PlayerLocaleStorage> localeConfig;

    private I18n() {
        // Utility class — no instantiation
    }

    /**
     * Initialize the i18n system. Must be called once during plugin setup().
     *
     * @param plugin the plugin instance, used to access the class loader for JAR resources
     * @param playerLocaleConfig persisted player locale overrides config
     */
    public static void init(JavaPlugin plugin, Config<PlayerLocaleStorage> playerLocaleConfig) {
        TRANSLATIONS.clear();
        PLAYER_LOCALES.clear();
        localeConfig = playerLocaleConfig;

        ClassLoader classLoader = plugin.getClass().getClassLoader();

        for (String locale : SUPPORTED_LOCALES) {
            String resourcePath = "Server/Languages/" + locale + "/messages.properties";
            try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    if (DEFAULT_LOCALE.equals(locale)) {
                        LOGGER.atWarning().log("[I18n] Default locale file not found: %s", resourcePath);
                    } else {
                        LOGGER.atFine().log("[I18n] Locale file not found (optional): %s", resourcePath);
                    }
                    continue;
                }
                Properties props = new Properties();
                // Java 9+ Properties.load(Reader) supports UTF-8 natively
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                TRANSLATIONS.put(locale, props);
                LOGGER.atInfo().log("[I18n] Loaded %d keys for locale %s", props.size(), locale);
            } catch (Exception e) {
                LOGGER.atWarning().log("[I18n] Failed to load locale %s: %s", locale, e.getMessage());
            }
        }

        if (!TRANSLATIONS.containsKey(DEFAULT_LOCALE)) {
            LOGGER.atWarning().log("[I18n] WARNING: Default locale %s has no translations loaded!", DEFAULT_LOCALE);
        }
    }

    /**
     * Get a translated string using the default locale (en-US).
     *
     * @param key  the translation key (e.g. "commands.eg.help")
     * @param args optional String.format() arguments (%s, %d, %.1f, etc.)
     * @return the formatted translated string, or the key itself if not found
     */
    public static String get(String key, Object... args) {
        return getFor(DEFAULT_LOCALE, key, args);
    }

    /**
     * Get a translated string for a specific locale.
     * Falls back to en-US if the key is missing in the requested locale.
     * Falls back to the key itself if missing everywhere.
     *
     * @param locale the locale code (e.g. "fr-FR")
     * @param key    the translation key
     * @param args   optional String.format() arguments
     * @return the formatted translated string
     */
    public static String getFor(String locale, String key, Object... args) {
        if (key == null) return "";

        // Try the requested locale first
        String value = lookupKey(locale, key);

        // Fall back to default locale if not found
        if (value == null && !DEFAULT_LOCALE.equals(locale)) {
            value = lookupKey(DEFAULT_LOCALE, key);
        }

        // Fall back to the key itself
        if (value == null) {
            return key;
        }

        // Apply format arguments if provided
        if (args != null && args.length > 0) {
            try {
                return String.format(value, args);
            } catch (Exception e) {
                LOGGER.atFine().log("[I18n] Format error for key '%s': %s", key, e.getMessage());
                return value;
            }
        }

        return value;
    }

    /**
     * Get a translated string for a specific player, resolving their locale automatically.
     * Uses persisted override > client language > en-US fallback.
     *
     * @param playerRef the player
     * @param key       the translation key
     * @param args      optional String.format() arguments
     * @return the formatted translated string
     */
    public static String getForPlayer(PlayerRef playerRef, String key, Object... args) {
        return getFor(resolveLocale(playerRef), key, args);
    }

    /**
     * Resolve the effective locale for a player.
     * Priority: persisted override > client language > en-US default.
     *
     * @param playerRef the player
     * @return the locale string (e.g. "fr-FR")
     */
    public static String resolveLocale(PlayerRef playerRef) {
        if (playerRef == null) return DEFAULT_LOCALE;

        UUID uuid = playerRef.getUuid();
        if (uuid == null) return DEFAULT_LOCALE;

        // Check runtime cache first
        String cached = PLAYER_LOCALES.get(uuid);
        if (cached != null) return cached;

        // Check persisted override — ECS component first, fall back to legacy config
        String locale = null;
        endgame.plugin.EndgameQoL pluginInstance = endgame.plugin.EndgameQoL.getInstance();
        if (pluginInstance != null) {
            endgame.plugin.components.PlayerEndgameComponent comp = pluginInstance.getPlayerComponent(uuid);
            if (comp != null && !comp.getLocale().isEmpty()) {
                locale = comp.getLocale();
            }
        }
        if (locale == null && localeConfig != null) {
            locale = localeConfig.get().getOverride(uuid.toString());
        }

        // Fall back to client language
        if (locale == null) {
            try {
                String clientLang = playerRef.getLanguage();
                if (clientLang != null && !clientLang.isEmpty() && isSupported(clientLang)) {
                    locale = clientLang;
                }
            } catch (Exception e) {
                LOGGER.atFine().log("[I18n] Failed to get client language for %s: %s", uuid, e.getMessage());
            }
        }

        // Fall back to default
        if (locale == null) {
            locale = DEFAULT_LOCALE;
        }

        // Cache the result
        PLAYER_LOCALES.put(uuid, locale);
        return locale;
    }

    /**
     * Set a player's locale override. Persists to disk and invalidates the runtime cache.
     *
     * @param playerRef the player
     * @param locale    the locale code, or null to remove the override (revert to auto-detect)
     */
    public static void setPlayerOverride(PlayerRef playerRef, String locale) {
        if (playerRef == null) return;
        UUID uuid = playerRef.getUuid();
        if (uuid == null) return;

        // Save to ECS component (auto-persisted to player BSON)
        endgame.plugin.EndgameQoL pluginInstance = endgame.plugin.EndgameQoL.getInstance();
        if (pluginInstance != null) {
            endgame.plugin.components.PlayerEndgameComponent comp = pluginInstance.getPlayerComponent(uuid);
            if (comp != null) {
                comp.setLocale(locale != null ? locale : "");
            }
        }

        // Invalidate cache so next resolveLocale() picks up the change
        PLAYER_LOCALES.remove(uuid);
    }

    /**
     * Send an UpdateTranslations packet to the player with all server-side translations
     * for their resolved locale. Keys are prefixed with "server." so they don't collide
     * with vanilla client translations.
     *
     * @param playerRef the player to send translations to
     */
    public static void sendUpdateTranslationsPacket(PlayerRef playerRef) {
        if (playerRef == null) return;
        try {
            String locale = resolveLocale(playerRef);
            Map<String, String> merged = new HashMap<>();

            // Always include en-US as base
            Properties enProps = TRANSLATIONS.get(DEFAULT_LOCALE);
            if (enProps != null) {
                for (String key : enProps.stringPropertyNames()) {
                    merged.put("server." + key, enProps.getProperty(key));
                }
            }

            // Override with target locale (if not en-US)
            if (!DEFAULT_LOCALE.equals(locale)) {
                Properties localeProps = TRANSLATIONS.get(locale);
                if (localeProps != null) {
                    for (String key : localeProps.stringPropertyNames()) {
                        merged.put("server." + key, localeProps.getProperty(key));
                    }
                }
            }

            if (!merged.isEmpty()) {
                UpdateTranslations packet = new UpdateTranslations(UpdateType.AddOrUpdate, merged);
                playerRef.getPacketHandler().writeNoCache(packet);
                LOGGER.atFine().log("[I18n] Sent %d translations to %s (locale: %s)",
                        merged.size(), playerRef.getUsername(), locale);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[I18n] Failed to send translations to %s: %s",
                    playerRef.getUsername(), e.getMessage());
        }
    }

    /**
     * Clean up runtime cache on player disconnect. Persisted overrides are retained.
     *
     * @param playerUuid the disconnecting player's UUID
     */
    public static void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid != null) {
            PLAYER_LOCALES.remove(playerUuid);
        }
    }

    /**
     * Check if a translation key exists in any loaded locale.
     */
    public static boolean hasKey(String key) {
        for (Properties props : TRANSLATIONS.values()) {
            if (props.containsKey(key)) return true;
        }
        return false;
    }

    /**
     * Get the number of loaded locales.
     */
    public static int getLoadedLocaleCount() {
        return TRANSLATIONS.size();
    }

    /**
     * Get the list of supported locale codes.
     */
    public static List<String> getSupportedLocales() {
        return List.of(SUPPORTED_LOCALES);
    }

    /**
     * Check if a locale code is supported.
     */
    public static boolean isSupported(String locale) {
        if (locale == null) return false;
        for (String supported : SUPPORTED_LOCALES) {
            if (supported.equals(locale)) return true;
        }
        return false;
    }

    private static String lookupKey(String locale, String key) {
        if (locale == null) return null;
        Properties props = TRANSLATIONS.get(locale);
        if (props == null) return null;
        return props.getProperty(key);
    }
}
