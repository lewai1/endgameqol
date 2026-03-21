package endgame.plugin.ui.config;

import com.hypixel.hytale.logger.HytaleLogger;
import endgame.plugin.EndgameQoL;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batch save manager for ConfigUI.
 * Instead of saving on every field change (46 saves), tabs call markDirty().
 * Save only happens on Apply button click via saveIfDirty().
 */
public class ConfigSaveManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ConfigUI");

    private static final long APPLY_DEBOUNCE_MS = 500;

    private final EndgameQoL plugin;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicLong lastApplyTime = new AtomicLong(0);

    public ConfigSaveManager(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Debounce guard for Apply button clicks.
     * Activating events can fire twice (press + release). Returns true if the
     * click should be processed, false if it's a duplicate within the debounce window.
     */
    public boolean tryApply() {
        long now = System.currentTimeMillis();
        long last = lastApplyTime.get();
        if (now - last < APPLY_DEBOUNCE_MS) return false;
        return lastApplyTime.compareAndSet(last, now);
    }

    /** Mark config as having unsaved changes. */
    public void markDirty() {
        dirty.set(true);
    }

    /** Save config to disk if there are pending changes. Returns true if saved. */
    public boolean saveIfDirty() {
        if (dirty.compareAndSet(true, false)) {
            plugin.getConfig().save();
            LOGGER.atInfo().log("[ConfigUI] Config saved (batch)");
            return true;
        }
        return false;
    }

    /** Force immediate save regardless of dirty flag. */
    public void saveNow() {
        plugin.getConfig().save();
        dirty.set(false);
    }

    public boolean isDirty() { return dirty.get(); }
}
