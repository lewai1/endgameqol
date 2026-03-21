package endgame.plugin.ui.config;

import au.ellie.hyui.builders.PageBuilder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;

/**
 * Base class for ConfigUI tab builders.
 * Each tab is self-contained: produces its own HTML fragment and registers its own listeners.
 */
public abstract class ConfigTabBuilder {

    protected final EndgameQoL plugin;
    protected final EndgameConfig config;
    protected final ConfigSaveManager saveManager;

    protected ConfigTabBuilder(EndgameQoL plugin, ConfigSaveManager saveManager) {
        this.plugin = plugin;
        this.config = plugin.getConfig().get();
        this.saveManager = saveManager;
    }

    /** Build the HTML fragment for this tab's content (no wrapping div needed). */
    public abstract String buildHtml(String locale);

    /** Register event listeners for this tab's interactive elements. */
    public abstract void registerListeners(PageBuilder builder, PlayerRef playerRef,
                                           Store<EntityStore> store);

    // === Shared parse helpers ===

    protected static int parseInt(Object value, int defaultValue) {
        try {
            String str = value.toString();
            if (str.contains(".")) str = str.substring(0, str.indexOf("."));
            return Math.max(0, Integer.parseInt(str));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    protected static float parseFloat(Object value, float defaultValue) {
        try {
            return Math.max(0.1f, Float.parseFloat(value.toString()));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    protected static float parseMultiplier(Object value) {
        try {
            float val = Float.parseFloat(value.toString());
            if (val == 0f) return 0f;
            float multiplier = 1.0f + (val / 100.0f);
            return Math.max(0.1f, Math.min(10.0f, multiplier));
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    protected static int multiplierToPercent(float multiplier) {
        if (multiplier == 0f) return 0;
        return Math.round((multiplier - 1.0f) * 100);
    }

    protected static float parsePercentageToMultiplier(Object value) {
        try {
            String str = value.toString().trim();
            if (str.isEmpty()) return 1.0f;
            float floatVal = Float.parseFloat(str);
            int pct = Math.max(10, Math.min(1000, Math.round(floatVal)));
            return pct / 100.0f;
        } catch (NumberFormatException e) {
            return 1.0f;
        }
    }

    // === Validation feedback ===

    /**
     * Send a clamped-value notification to the player when their input was adjusted.
     * Call after the setter has clamped the value. Compares raw input to actual stored value.
     *
     * @param playerRef player to notify
     * @param fieldName human-readable field name (e.g. "Dragon Frost HP")
     * @param inputValue the raw value the user typed
     * @param actualValue the value after setter clamping
     */
    protected static void notifyIfClamped(PlayerRef playerRef, String fieldName,
                                           float inputValue, float actualValue) {
        if (Math.abs(inputValue - actualValue) > 0.01f) {
            playerRef.sendMessage(
                    Message.raw(String.format("[EndgameQoL] %s clamped to %.1f (input: %.1f)",
                            fieldName, actualValue, inputValue)).color("#ffaa00"));
        }
    }

    /**
     * Integer overload for notifyIfClamped.
     */
    protected static void notifyIfClamped(PlayerRef playerRef, String fieldName,
                                           int inputValue, int actualValue) {
        if (inputValue != actualValue) {
            playerRef.sendMessage(
                    Message.raw(String.format("[EndgameQoL] %s clamped to %d (input: %d)",
                            fieldName, actualValue, inputValue)).color("#ffaa00"));
        }
    }

    /**
     * Long overload for notifyIfClamped.
     */
    protected static void notifyIfClamped(PlayerRef playerRef, String fieldName,
                                           long inputValue, long actualValue) {
        if (inputValue != actualValue) {
            playerRef.sendMessage(
                    Message.raw(String.format("[EndgameQoL] %s clamped to %d (input: %d)",
                            fieldName, actualValue, inputValue)).color("#ffaa00"));
        }
    }
}
