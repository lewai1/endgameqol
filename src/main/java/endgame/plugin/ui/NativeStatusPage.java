package endgame.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.config.RecipeOverrideConfig;
import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.managers.boss.GolemVoidBossManager;
import endgame.wavearena.WaveArenaEngine;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Native .ui status page replacing HyUI StatusUI.
 * Read-only admin dashboard — no interactions.
 */
public class NativeStatusPage extends InteractiveCustomUIPage<NativeStatusPage.StatusEventData> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.NativeStatus");
    private static final String PAGE_FILE = "Pages/EndgameStatusPage.ui";

    private final EndgameQoL plugin;

    public NativeStatusPage(@Nonnull PlayerRef playerRef, @Nonnull EndgameQoL plugin) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, StatusEventData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append(PAGE_FILE);
        populate(cmd);
    }

    private void populate(UICommandBuilder cmd) {
        EndgameConfig config = plugin.getConfig().get();

        // Version
        cmd.set("#Version.Text", "v" + plugin.getManifest().getVersion());

        // System card
        cmd.set("#Difficulty.Text", config.getDifficultyString());

        // Database
        boolean dbEnabled = false;
        String dbEngine = "Disabled";
        String dbColor = "#555555";
        try {
            var dbConfig = plugin.getDatabaseConfig();
            if (dbConfig != null && dbConfig.get().isEnabled()) {
                dbEnabled = true;
                String jdbcUrl = dbConfig.get().getJdbcUrl();
                if (jdbcUrl.contains("sqlite")) dbEngine = "SQLite";
                else if (jdbcUrl.contains("mysql")) dbEngine = "MySQL";
                else if (jdbcUrl.contains("mariadb")) dbEngine = "MariaDB";
                else if (jdbcUrl.contains("postgresql")) dbEngine = "PostgreSQL";
                else dbEngine = "Unknown";

                var dbService = plugin.getDatabaseSyncService();
                boolean healthy = dbService != null && dbService.isHealthy();
                dbColor = healthy ? "#4ade80" : "#ff4444";
                if (!healthy) dbEngine += " (degraded)";
            }
        } catch (Exception ignored) {}
        cmd.set("#Database.Text", dbEngine);
        cmd.set("#Database.Style.TextColor", dbColor);

        // RPG Leveling
        if (plugin.isRPGLevelingActive()) {
            cmd.set("#RPGLeveling.Text", "Active");
            cmd.set("#RPGLeveling.Style.TextColor", "#4ade80");
        } else if (plugin.isRPGLevelingModPresent()) {
            cmd.set("#RPGLeveling.Text", "Detected");
            cmd.set("#RPGLeveling.Style.TextColor", "#ffaa00");
        } else {
            cmd.set("#RPGLeveling.Text", "Missing");
            cmd.set("#RPGLeveling.Style.TextColor", "#555555");
        }

        // Endless Leveling
        if (plugin.isEndlessLevelingActive()) {
            cmd.set("#EndlessLeveling.Text", "Active");
            cmd.set("#EndlessLeveling.Style.TextColor", "#4ade80");
        } else if (plugin.isEndlessLevelingModPresent()) {
            cmd.set("#EndlessLeveling.Text", "Detected");
            cmd.set("#EndlessLeveling.Style.TextColor", "#ffaa00");
        } else {
            cmd.set("#EndlessLeveling.Text", "Missing");
            cmd.set("#EndlessLeveling.Style.TextColor", "#555555");
        }

        // Online players
        int onlinePlayers = 0;
        try { onlinePlayers = Universe.get().getPlayers().size(); } catch (Exception ignored) {}
        cmd.set("#OnlinePlayers.Text", onlinePlayers + " player" + (onlinePlayers != 1 ? "s" : ""));

        // Encounters
        int golemCount = 0, genericCount = 0;
        GolemVoidBossManager golemMgr = plugin.getGolemVoidBossManager();
        GenericBossManager genericMgr = plugin.getGenericBossManager();
        if (golemMgr != null) golemCount = golemMgr.getActiveBosses().size();
        if (genericMgr != null) genericCount = genericMgr.getActiveBosses().size();
        int totalBosses = golemCount + genericCount;
        setEncounter(cmd, "#Bosses", totalBosses, totalBosses > 0 ? golemCount + " Golem, " + genericCount + " Other" : null);

        WaveArenaEngine waveEngine = plugin.getWaveArenaEngine();
        setEncounter(cmd, "#Trials", waveEngine != null ? waveEngine.getActiveSessionCount() : 0, null);

        // Stats
        BountyManager bountyMgr = plugin.getBountyManager();
        cmd.set("#BountyPlayers.Text", (bountyMgr != null ? bountyMgr.getCachedPlayerCount() : 0) + " cached");

        // Features
        setFeature(cmd, "Combo", config.isComboEnabled());
        setFeature(cmd, "Bounty", config.isBountyEnabled());
        setFeature(cmd, "Warden", config.isWardenTrialEnabled());
        setFeature(cmd, "Pvp", config.isPvpEnabled());

        // Metrics
        int recipesLoaded = 0, recipesDisabled = 0;
        try {
            var recipeConfig = plugin.getRecipeOverrideConfig();
            if (recipeConfig != null) {
                Map<String, RecipeOverrideConfig.RecipeEntry> overrides = recipeConfig.get().getRecipeOverrides();
                recipesLoaded = overrides.size();
                for (var entry : overrides.values()) { if (!entry.isEnabled()) recipesDisabled++; }
            }
        } catch (Exception ignored) {}
        cmd.set("#RecipesLoaded.Text", String.valueOf(recipesLoaded));
        cmd.set("#RecipesDisabled.Text", String.valueOf(recipesDisabled));
        cmd.set("#RecipesDisabled.Style.TextColor", recipesDisabled > 0 ? "#ffaa00" : "#c0c0c0");
        cmd.set("#LocalesLoaded.Text", endgame.plugin.utils.I18n.getLoadedLocaleCount() + "/5");
    }

    private void setEncounter(UICommandBuilder cmd, String selector, int count, String detail) {
        String text = count > 0 ? count + " active" : "None";
        if (detail != null && count > 0) text += " (" + detail + ")";
        cmd.set(selector + ".Text", text);
        cmd.set(selector + ".Style.TextColor", count > 0 ? "#ffaa00" : "#4ade80");
    }

    private void setFeature(UICommandBuilder cmd, String name, boolean on) {
        String color = on ? "#4ade80" : "#ff4444";
        cmd.set("#Feat" + name + ".Text", on ? "ON" : "OFF");
        cmd.set("#Feat" + name + ".Style.TextColor", color);
        cmd.set("#Dot" + name + ".Background", color);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull StatusEventData data) {
        // No interactions — read-only page
    }

    public static void open(EndgameQoL plugin, PlayerRef playerRef, Store<EntityStore> store) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            player.getPageManager().openCustomPage(ref, store, new NativeStatusPage(playerRef, plugin));
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("[NativeStatus] Failed to open");
        }
    }

    public static class StatusEventData {
        public static final BuilderCodec<StatusEventData> CODEC = BuilderCodec
                .builder(StatusEventData.class, StatusEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING, true),
                        (d, v) -> d.action = v, d -> d.action).add()
                .build();
        String action;
    }
}
