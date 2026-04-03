package endgame.plugin;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import endgame.plugin.components.FrostwalkerIceComponent;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.commands.EgCommand;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.config.PlayerLocaleStorage;
import endgame.plugin.config.RecipeOverrideConfig;
import endgame.plugin.config.AccessoryPouchStorage;
import endgame.plugin.config.GauntletLeaderboard;
import endgame.plugin.config.BountyData;
import endgame.plugin.config.AchievementData;
import endgame.plugin.config.BestiaryData;
import endgame.plugin.managers.AchievementManager;
import endgame.plugin.managers.BossHealthManager;
import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.ComboMeterManager;
import endgame.plugin.managers.GauntletManager;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.managers.boss.GolemVoidBossManager;
import endgame.plugin.migration.LegacyDataMigration;
import endgame.plugin.systems.boss.DangerZoneTickSystem;
import endgame.plugin.systems.trial.WardenTrialManager;
import endgame.plugin.systems.trial.StartWardenTrialInteraction;
import endgame.plugin.systems.trial.StartGauntletInteraction;
import endgame.plugin.utils.I18n;
import endgame.plugin.watchers.ForgottenTempleWatcher;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class EndgameQoL extends JavaPlugin {

    private static volatile EndgameQoL instance;

    public static EndgameQoL getInstance() {
        return instance;
    }

    private final Config<EndgameConfig> config;
    private final Config<AccessoryPouchStorage> accessoryPouchConfig;
    private final Config<RecipeOverrideConfig> recipeOverrideConfig;
    private final Config<GauntletLeaderboard> gauntletLeaderboard;
    private final Config<BountyData> bountyData;
    private final Config<PlayerLocaleStorage> playerLocaleConfig;
    private final Config<endgame.plugin.database.DatabaseConfig> databaseConfig;
    private final Config<AchievementData> achievementData;
    private final Config<BestiaryData> bestiaryData;

    private ComponentType<ChunkStore, FrostwalkerIceComponent> frostwalkerIceComponentType;
    private ComponentType<EntityStore, PlayerEndgameComponent> playerEndgameComponentType;

    private SystemRegistry systemRegistry;
    private EventRegistry eventRegistry;
    private DatabaseInitializer databaseInitializer;
    private LegacyDataMigration legacyMigration;

    private final endgame.plugin.events.domain.GameEventBus gameEventBus = new endgame.plugin.events.domain.GameEventBus();

    private endgame.plugin.events.RecipeOverrideSystem recipeOverrideSystem;
    private ForgottenTempleWatcher forgottenTempleWatcher;
    private endgame.plugin.systems.portal.TemporalPortalManager temporalPortalManager;
    private endgame.plugin.integration.rpgleveling.RPGLevelingBridge rpgLevelingBridge;
    private endgame.plugin.integration.endlessleveling.EndlessLevelingBridge endlessLevelingBridge;
    private endgame.plugin.integration.orbisguard.OrbisGuardBridge orbisGuardBridge;
    private AchievementManager achievementManager;

    // Per-player component cache — populated by PlayerEventHandler.onPlayerReady(), cleared on disconnect
    private final ConcurrentHashMap<UUID, PlayerEndgameComponent> playerComponents = new ConcurrentHashMap<>();

    // Cached mod detection results (Class.forName is expensive)
    private volatile Boolean cachedRpgLevelingPresent;
    private volatile Boolean cachedEndlessLevelingPresent;
    private volatile Boolean cachedOrbisGuardPresent;

    public EndgameQoL(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
        this.config = this.withConfig("EndgameConfig", EndgameConfig.CODEC);
        this.accessoryPouchConfig = this.withConfig("EndgameAccessories", AccessoryPouchStorage.CODEC);
        this.recipeOverrideConfig = this.withConfig("RecipeOverrides", RecipeOverrideConfig.CODEC);
        this.gauntletLeaderboard = this.withConfig("GauntletLeaderboard", GauntletLeaderboard.CODEC);
        this.bountyData = this.withConfig("BountyData", BountyData.CODEC);
        this.playerLocaleConfig = this.withConfig("PlayerLocales", PlayerLocaleStorage.CODEC);
        this.databaseConfig = this.withConfig("DatabaseConfig", endgame.plugin.database.DatabaseConfig.CODEC);
        this.achievementData = this.withConfig("AchievementData", AchievementData.CODEC);
        this.bestiaryData = this.withConfig("BestiaryData", BestiaryData.CODEC);
    }

    @Override
    protected void setup() {
        this.getLogger().atInfo().log("[EndgameQoL] Setup started...");

        endgame.plugin.services.SoundService.register(this, gameEventBus);

        // Item ID migrations (old ID -> new ID, applied on player connect + chunk load)
        endgame.plugin.migration.ItemIdMigration.register("Big_Rex_Cave_Leather", "Alpha_Rex_Leather");
        endgame.plugin.migration.ItemIdMigration.register("Endgame_Portal_Golem_Void", "Endgame_Portal_Void_Realm");
        this.getEventRegistry().registerGlobal(
                com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent.class,
                endgame.plugin.migration.ItemIdMigration::onChunkLoaded);

        this.config.save();
        this.databaseConfig.save();
        // Per-player configs (voidPocket, bounty, achievement, bestiary, accessoryPouch)
        // are NO LONGER saved here — data lives in PlayerEndgameComponent (ECS auto-persist).
        // Old files stay on disk as read-only backup for LegacyDataMigration.

        // Database initialization
        this.databaseInitializer = new DatabaseInitializer(this);
        this.databaseInitializer.initialize(this.databaseConfig);

        I18n.init(this, this.playerLocaleConfig);

        // Custom CAE conditions
        endgame.plugin.systems.boss.HealthPercentBelowCondition.register();

        // Custom interaction types
        this.getCodecRegistry(Interaction.CODEC).register(
                "EndgameStanceChange", endgame.plugin.systems.weapon.EndgameStanceChangeInteraction.class,
                endgame.plugin.systems.weapon.EndgameStanceChangeInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                "StartWardenTrial", StartWardenTrialInteraction.class, StartWardenTrialInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                "EndgameSpawnNPC", endgame.plugin.systems.npc.EndgameSpawnNPCInteraction.class,
                endgame.plugin.systems.npc.EndgameSpawnNPCInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                "StartGauntlet", StartGauntletInteraction.class, StartGauntletInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                "Unlock", endgame.plugin.systems.block.UnlockInteraction.class,
                endgame.plugin.systems.block.UnlockInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                "EndgameAccessoryPouch", endgame.plugin.ui.OpenAccessoryPouchInteraction.class,
                endgame.plugin.ui.OpenAccessoryPouchInteraction.CODEC);
        com.hypixel.hytale.server.npc.NPCPlugin.get().registerCoreComponentType(
                "EndgameOpenTradeUI", endgame.plugin.ui.BuilderActionOpenTradeUI::new);
        com.hypixel.hytale.server.npc.NPCPlugin.get().registerCoreComponentType(
                "SetMotionController", endgame.plugin.systems.boss.BuilderActionSetMotionController::new);
        com.hypixel.hytale.server.npc.NPCPlugin.get().registerCoreComponentType(
                "EnterTemporalPortal", endgame.plugin.systems.portal.BuilderActionEnterTemporalPortal::new);

        // Boss knockback suppression (INSPECT damage group)
        this.getEntityStoreRegistry().registerSystem(
                new endgame.plugin.systems.boss.BossKnockbackSuppressionSystem());

        // Commands
        this.getCommandRegistry().registerCommand(new EgCommand(this));


        // Frostwalker ice block component (ChunkStore)
        this.frostwalkerIceComponentType = this.getChunkStoreRegistry()
                .registerComponent(FrostwalkerIceComponent.class, "FrostwalkerIce", FrostwalkerIceComponent.CODEC);

        // Persistent per-player ECS component (auto-saved to universe/players/{UUID}.bson)
        this.playerEndgameComponentType = this.getEntityStoreRegistry()
                .registerComponent(PlayerEndgameComponent.class, "EndgamePlayerData", PlayerEndgameComponent.CODEC);
        PlayerEndgameComponent.setComponentType(this.playerEndgameComponentType);

        // Legacy data migration (keeps references to old configs for per-player migration on connect)
        this.legacyMigration = new LegacyDataMigration(
                this.achievementData, this.bestiaryData, this.bountyData,
                null, this.accessoryPouchConfig, this.playerLocaleConfig);

        // ECS systems & managers
        this.systemRegistry = new SystemRegistry(this);
        this.systemRegistry.register(this.gauntletLeaderboard,
                this.playerEndgameComponentType);

        // Achievement + Bestiary system
        this.achievementManager = new AchievementManager(this);

        // Wire database sync references
        this.databaseInitializer.setSyncReferences(this.gauntletLeaderboard);

        // Event handlers
        this.eventRegistry = new EventRegistry(this, this.systemRegistry, this.databaseInitializer);
        this.eventRegistry.register();

        initRPGLevelingIntegration();
        initEndlessLevelingIntegration();
        initOrbisGuardIntegration();
        endgame.plugin.integration.ClaimProtectionBridge.get().init();

        // HStats analytics (hstats.dev)
        new HStats("00a9cb44-fac7-4aae-bdd7-8fb5e8291280", "4.2.0");

        this.getLogger().atInfo().log("[EndgameQoL] Setup finished!");
    }

    private void initRPGLevelingIntegration() {
        // Register config defaults via RPG Leveling's API (0.3.3+).
        endgame.plugin.integration.rpgleveling.RPGLevelingConfigPatcher.register();

        // Auto-detect: enable when mod is newly detected, respect manual disable.
        // States: "pending" (fresh), "absent" (mod not found), "present" (mod found and auto-enabled), "disabled" (admin manually disabled)
        boolean modPresent = isClassAvailable("org.zuxaw.plugin.api.RPGLevelingAPI");
        String state = config.get().getRPGLevelingAutoDetected();
        if (modPresent && !config.get().isRPGLevelingEnabled() && !"disabled".equals(state)) {
            config.get().setRPGLevelingEnabled(true);
            config.get().setRPGLevelingAutoDetected("present");
            config.save();
            this.getLogger().atInfo().log("[EndgameQoL] RPG Leveling detected — auto-enabled");
        } else if (!modPresent && !"absent".equals(state)) {
            config.get().setRPGLevelingAutoDetected("absent");
            config.save();
        }

        if (!config.get().isRPGLevelingEnabled()) {
            this.getLogger().atInfo().log("[EndgameQoL] RPG Leveling integration disabled in config");
            return;
        }
        try {
            Class.forName("org.zuxaw.plugin.api.RPGLevelingAPI");
            rpgLevelingBridge = new endgame.plugin.integration.rpgleveling.RPGLevelingBridge(this);
            if (rpgLevelingBridge.init()) {
                getEntityStoreRegistry().registerSystem(
                        new endgame.plugin.integration.rpgleveling.BossKillXPSystem(this, rpgLevelingBridge));
                this.getLogger().atInfo().log("[EndgameQoL] RPG Leveling integration active — boss kills award XP");
            } else {
                rpgLevelingBridge = null;
                this.getLogger().atWarning().log("[EndgameQoL] RPG Leveling API not available (mod loaded but API init failed)");
            }
        } catch (ClassNotFoundException e) {
            rpgLevelingBridge = null;
            this.getLogger().atWarning().log("[EndgameQoL] RPG Leveling enabled in config but mod not found — skipping integration");
        }
    }

    private void initEndlessLevelingIntegration() {
        // Auto-detect: enable when mod is newly detected, respect manual disable.
        boolean modPresent = isClassAvailable("com.airijko.endlessleveling.api.EndlessLevelingAPI");
        String state = config.get().getEndlessLevelingAutoDetected();
        if (modPresent && !config.get().isEndlessLevelingEnabled() && !"disabled".equals(state)) {
            config.get().setEndlessLevelingEnabled(true);
            config.get().setEndlessLevelingAutoDetected("present");
            config.save();
            this.getLogger().atInfo().log("[EndgameQoL] Endless Leveling detected — auto-enabled");
        } else if (!modPresent && !"absent".equals(state)) {
            config.get().setEndlessLevelingAutoDetected("absent");
            config.save();
        }

        if (!config.get().isEndlessLevelingEnabled()) {
            this.getLogger().atInfo().log("[EndgameQoL] Endless Leveling integration disabled in config");
            return;
        }
        try {
            Class.forName("com.airijko.endlessleveling.api.EndlessLevelingAPI");
            endlessLevelingBridge = new endgame.plugin.integration.endlessleveling.EndlessLevelingBridge(this);
            if (endlessLevelingBridge.init()) {
                getEntityStoreRegistry().registerSystem(
                        new endgame.plugin.integration.endlessleveling.EndlessLevelingXPSystem(this, endlessLevelingBridge));
                this.getLogger().atInfo().log("[EndgameQoL] Endless Leveling integration active — boss kills award XP");
            } else {
                endlessLevelingBridge = null;
                this.getLogger().atWarning().log("[EndgameQoL] Endless Leveling API not available (mod loaded but API init failed)");
            }
        } catch (ClassNotFoundException e) {
            endlessLevelingBridge = null;
            this.getLogger().atWarning().log("[EndgameQoL] Endless Leveling enabled in config but mod not found — skipping integration");
        }
    }

    private void initOrbisGuardIntegration() {
        // OrbisGuard: detect but never auto-enable (disabled by default, admin must opt-in)
        boolean modPresent = isClassAvailable("com.orbisguard.api.OrbisGuardAPI");
        String state = config.get().getOrbisGuardAutoDetected();
        if (!modPresent && !"absent".equals(state)) {
            config.get().setOrbisGuardAutoDetected("absent");
            config.save();
        } else if (modPresent && "pending".equals(state)) {
            config.get().setOrbisGuardAutoDetected("detected");
            config.save();
            this.getLogger().atInfo().log("[EndgameQoL] OrbisGuard detected — disabled by default. Enable in /egconfig Integration tab.");
        }

        if (!config.get().isOrbisGuardEnabled()) {
            this.getLogger().atInfo().log("[EndgameQoL] OrbisGuard integration disabled in config");
            return;
        }
        try {
            Class.forName("com.orbisguard.api.OrbisGuardAPI");
            orbisGuardBridge = new endgame.plugin.integration.orbisguard.OrbisGuardBridge(this);
            if (orbisGuardBridge.init()) {
                orbisGuardBridge.start();
                this.getLogger().atInfo().log("[EndgameQoL] OrbisGuard integration active — instances will be auto-protected");
            } else {
                orbisGuardBridge = null;
                this.getLogger().atWarning().log("[EndgameQoL] OrbisGuard API not available (mod loaded but API init failed)");
            }
        } catch (ClassNotFoundException e) {
            orbisGuardBridge = null;
            this.getLogger().atWarning().log("[EndgameQoL] OrbisGuard enabled in config but mod not found — skipping integration");
        }
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Whether the RPG Leveling bridge is active (mod present + enabled + API init succeeded).
     */
    public boolean isRPGLevelingActive() {
        return rpgLevelingBridge != null && rpgLevelingBridge.isActive();
    }

    /**
     * Get the RPG Leveling bridge (may be null if integration is disabled or mod not present).
     */
    public endgame.plugin.integration.rpgleveling.RPGLevelingBridge getRpgLevelingBridge() {
        return rpgLevelingBridge;
    }

    /**
     * Whether the RPG Leveling mod JAR is present on the classpath.
     */
    public boolean isRPGLevelingModPresent() {
        Boolean cached = cachedRpgLevelingPresent;
        if (cached != null) return cached;
        try {
            Class.forName("org.zuxaw.plugin.api.RPGLevelingAPI");
            cachedRpgLevelingPresent = true;
            return true;
        } catch (ClassNotFoundException e) {
            cachedRpgLevelingPresent = false;
            return false;
        }
    }

    /**
     * Whether the Endless Leveling bridge is active (mod present + enabled + API init succeeded).
     */
    public boolean isEndlessLevelingActive() {
        return endlessLevelingBridge != null && endlessLevelingBridge.isActive();
    }

    /**
     * Get the Endless Leveling bridge (may be null if integration is disabled or mod not present).
     */
    public endgame.plugin.integration.endlessleveling.EndlessLevelingBridge getEndlessLevelingBridge() {
        return endlessLevelingBridge;
    }

    /**
     * Whether the Endless Leveling mod JAR is present on the classpath.
     */
    public boolean isEndlessLevelingModPresent() {
        Boolean cached = cachedEndlessLevelingPresent;
        if (cached != null) return cached;
        try {
            Class.forName("com.airijko.endlessleveling.api.EndlessLevelingAPI");
            cachedEndlessLevelingPresent = true;
            return true;
        } catch (ClassNotFoundException e) {
            cachedEndlessLevelingPresent = false;
            return false;
        }
    }

    /**
     * Whether the OrbisGuard bridge is active (mod present + enabled + API init succeeded).
     */
    public boolean isOrbisGuardActive() {
        return orbisGuardBridge != null && orbisGuardBridge.isActive();
    }

    /**
     * Whether the OrbisGuard mod JAR is present on the classpath.
     */
    public boolean isOrbisGuardModPresent() {
        Boolean cached = cachedOrbisGuardPresent;
        if (cached != null) return cached;
        try {
            Class.forName("com.orbisguard.api.OrbisGuardAPI");
            cachedOrbisGuardPresent = true;
            return true;
        } catch (ClassNotFoundException e) {
            cachedOrbisGuardPresent = false;
            return false;
        }
    }

    @Override
    protected void start() {
        this.getLogger().atInfo().log("[EndgameQoL] Plugin started!");

        // Migrate accessory pouches: clear slots with deleted accessory IDs
        migrateDeletedAccessories();

        // Apply recipe overrides from RecipeOverrides.json (before any player connects)
        if (this.recipeOverrideSystem != null) {
            this.recipeOverrideSystem.apply(this.recipeOverrideConfig);
        }

        // Start the Forgotten Temple watcher for Vorthak merchant
        this.getLogger().atInfo().log("[EndgameQoL] Starting ForgottenTempleWatcher...");
        this.forgottenTempleWatcher = new ForgottenTempleWatcher(this);
        this.forgottenTempleWatcher.start();

        // Start the Temporal Portal system
        this.temporalPortalManager = new endgame.plugin.systems.portal.TemporalPortalManager(this);
        this.temporalPortalManager.start();
    }

    @Override
    protected void shutdown() {
        this.getLogger().atInfo().log("[EndgameQoL] Plugin shutting down...");

        // CRITICAL: Clean up all systems to prevent handlers from doubling on reload
        if (this.systemRegistry != null) {
            this.systemRegistry.shutdownAll();
        }

        if (this.temporalPortalManager != null) {
            this.temporalPortalManager.stop();
            this.getLogger().atInfo().log("[EndgameQoL] Stopped TemporalPortalManager");
        }

        if (this.forgottenTempleWatcher != null) {
            this.forgottenTempleWatcher.stop();
            this.getLogger().atInfo().log("[EndgameQoL] Stopped ForgottenTempleWatcher");
        }

        if (this.achievementManager != null) {
            this.achievementManager.forceClear();
            this.getLogger().atInfo().log("[EndgameQoL] Cleared AchievementManager state");
        }

        // Clear player component cache
        this.playerComponents.clear();

        if (this.rpgLevelingBridge != null) {
            this.rpgLevelingBridge.shutdown();
            this.getLogger().atInfo().log("[EndgameQoL] RPG Leveling bridge shut down");
        }

        if (this.endlessLevelingBridge != null) {
            this.endlessLevelingBridge.shutdown();
            this.getLogger().atInfo().log("[EndgameQoL] Endless Leveling bridge shut down");
        }

        if (this.orbisGuardBridge != null) {
            this.orbisGuardBridge.shutdown();
            this.getLogger().atInfo().log("[EndgameQoL] OrbisGuard bridge shut down");
        }

        if (this.databaseInitializer != null) {
            this.databaseInitializer.shutdown();
        }

        // Clear event bus and migrations
        gameEventBus.clear();
        endgame.plugin.migration.ItemIdMigration.clear();
        endgame.plugin.events.PlayerEventHandler.reset();
        endgame.plugin.managers.RecipeManager.reset();

        this.getLogger().atInfo().log("[EndgameQoL] Shutdown complete!");
    }

    // --- Player ECS Component Cache ---

    /**
     * Called by PlayerEventHandler.onPlayerReady() when a player's component is ready.
     * Caches the component for fast access by managers. Idempotent — safe to call multiple times.
     */
    public void onPlayerComponentReady(@Nonnull UUID uuid, @Nonnull PlayerEndgameComponent comp) {
        playerComponents.put(uuid, comp);

        // Notify managers to cache the component
        if (achievementManager != null) {
            achievementManager.onPlayerConnect(uuid, comp);
        }
        if (systemRegistry != null && systemRegistry.getBountyManager() != null) {
            systemRegistry.getBountyManager().onPlayerConnect(uuid, comp);
        }
        if (systemRegistry != null && systemRegistry.getComboMeterManager() != null) {
            systemRegistry.getComboMeterManager().onPlayerConnect(uuid, comp);
        }
    }

    /**
     * Called on player disconnect to uncache the component.
     */
    public void onPlayerComponentRemoved(@Nonnull UUID uuid) {
        playerComponents.remove(uuid);

        // Notify managers
        if (achievementManager != null) {
            achievementManager.onPlayerDisconnect(uuid);
        }
        if (systemRegistry != null && systemRegistry.getBountyManager() != null) {
            systemRegistry.getBountyManager().onPlayerDisconnect(uuid);
        }
        if (systemRegistry != null && systemRegistry.getComboMeterManager() != null) {
            systemRegistry.getComboMeterManager().onPlayerDisconnect(uuid);
        }
    }

    /**
     * Get the cached player component, or null if the player isn't connected.
     */
    @Nullable
    public PlayerEndgameComponent getPlayerComponent(@Nonnull UUID uuid) {
        return playerComponents.get(uuid);
    }

    /**
     * Get the legacy migration helper (for PlayerDataEnsureSystem).
     */
    @Nullable
    public LegacyDataMigration getLegacyMigration() {
        return legacyMigration;
    }

    public ComponentType<EntityStore, PlayerEndgameComponent> getPlayerEndgameComponentType() {
        return playerEndgameComponentType;
    }

    // --- Config getters ---

    public endgame.plugin.events.domain.GameEventBus getGameEventBus() {
        return gameEventBus;
    }

    public Config<EndgameConfig> getConfig() {
        return config;
    }

    public Config<AccessoryPouchStorage> getAccessoryPouchConfig() {
        return accessoryPouchConfig;
    }

    public Config<RecipeOverrideConfig> getRecipeOverrideConfig() {
        return recipeOverrideConfig;
    }

    public void setRecipeOverrideSystem(endgame.plugin.events.RecipeOverrideSystem system) {
        this.recipeOverrideSystem = system;
    }

    public endgame.plugin.events.RecipeOverrideSystem getRecipeOverrideSystem() {
        return recipeOverrideSystem;
    }

    public Config<GauntletLeaderboard> getGauntletLeaderboard() {
        return gauntletLeaderboard;
    }

    public Config<endgame.plugin.database.DatabaseConfig> getDatabaseConfig() {
        return databaseConfig;
    }

    // --- Delegating getters to SystemRegistry ---

    public GolemVoidBossManager getGolemVoidBossManager() {
        return systemRegistry != null ? systemRegistry.getGolemVoidBossManager() : null;
    }

    public GenericBossManager getGenericBossManager() {
        return systemRegistry != null ? systemRegistry.getGenericBossManager() : null;
    }

    public DangerZoneTickSystem getDangerZoneTickSystem() {
        return systemRegistry != null ? systemRegistry.getDangerZoneTickSystem() : null;
    }

    public BossHealthManager getBossHealthManager() {
        return systemRegistry != null ? systemRegistry.getBossHealthManager() : null;
    }

    public WardenTrialManager getWardenTrialManager() {
        return systemRegistry != null ? systemRegistry.getWardenTrialManager() : null;
    }

    public ComboMeterManager getComboMeterManager() {
        return systemRegistry != null ? systemRegistry.getComboMeterManager() : null;
    }

    public GauntletManager getGauntletManager() {
        return systemRegistry != null ? systemRegistry.getGauntletManager() : null;
    }

    public BountyManager getBountyManager() {
        return systemRegistry != null ? systemRegistry.getBountyManager() : null;
    }

    public AchievementManager getAchievementManager() {
        return achievementManager;
    }

    // --- Delegating getter to DatabaseInitializer ---

    public endgame.plugin.database.DatabaseSyncService getDatabaseSyncService() {
        return databaseInitializer != null ? databaseInitializer.getDatabaseSyncService() : null;
    }

    /**
     * One-time migration: clear pouch slots containing deleted accessory item IDs.
     * Also renames Hedera_Charm → Hedera_Seed.
     */
    private void migrateDeletedAccessories() {
        try {
            java.util.Set<String> deletedIds = java.util.Set.of(
                    "Endgame_Accessory_Mithril_Band",
                    "Endgame_Accessory_Storm_Talisman",
                    "Endgame_Accessory_Prisma_Ring",
                    "Endgame_Accessory_Lucky_Horseshoe",
                    "Endgame_Accessory_Combo_Signet"
            );
            var storage = this.accessoryPouchConfig.get();
            boolean changed = false;
            for (var pouch : storage.getAllAccessoryPouches().values()) {
                for (int i = 0; i < endgame.plugin.config.AccessoryPouchData.MAX_SLOTS; i++) {
                    if (pouch.isSlotOccupied(i)) {
                        String itemId = pouch.getItemId(i);
                        if (deletedIds.contains(itemId)) {
                            pouch.clearSlot(i);
                            changed = true;
                        } else if ("Endgame_Accessory_Hedera_Charm".equals(itemId)) {
                            pouch.setItem(i, "Endgame_Accessory_Hedera_Seed", pouch.getCount(i), pouch.getDurability(i));
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                // No save() — cleaned data stays in memory for ECS migration via LegacyDataMigration
                this.getLogger().atInfo().log("[EndgameQoL] Cleaned deleted accessories from legacy pouches (in-memory for ECS migration)");
            }
        } catch (Exception e) {
            this.getLogger().atWarning().log("[EndgameQoL] Accessory migration failed (non-critical): %s", e.getMessage());
        }
    }

    public ComponentType<ChunkStore, FrostwalkerIceComponent> getFrostwalkerIceComponentType() {
        return frostwalkerIceComponentType;
    }

    public void applyPvpToAllWorlds(boolean enabled) {
        // Thread-safe: iterate worlds directly (no ECS store access), defer mutation to world thread
        for (World world : new java.util.ArrayList<>(Universe.get().getWorlds().values())) {
            if (world != null && isEndgameInstance(world) && world.isAlive()) {
                world.execute(() -> {
                    WorldConfig wc = world.getWorldConfig();
                    if (wc.isPvpEnabled() != enabled) {
                        wc.setPvpEnabled(enabled);
                        wc.markChanged();
                    }
                });
            }
        }
        getLogger().atInfo().log("[EndgameQoL] PvP %s for endgame instances", enabled ? "enabled" : "disabled");
    }

    static boolean isEndgameInstance(World world) {
        String name = world.getName().toLowerCase();
        return name.contains("instance-endgame");
    }

    public static boolean isFrozenDungeonInstance(World world) {
        String name = world.getName().toLowerCase();
        return name.contains("endgame_frozen_dungeon") || name.contains("endgame-frozen-dungeon");
    }

    public static boolean isSwampDungeonInstance(World world) {
        String name = world.getName().toLowerCase();
        return name.contains("endgame_swamp_dungeon") || name.contains("endgame-swamp-dungeon");
    }

    public static boolean isGolemVoidInstance(World world) {
        String name = world.getName().toLowerCase();
        return name.contains("endgame_void_realm") || name.contains("endgame-void-realm")
                || name.contains("endgame_golem_void") || name.contains("endgame-golem-void");
    }

    public endgame.plugin.systems.portal.TemporalPortalManager getTemporalPortalManager() {
        return temporalPortalManager;
    }

    public void resetVorthakShopStock() {
        resetShopStock("Endgame_Vorthak", "Vorthak");
    }

    void resetKorvynShopStock() {
        resetShopStock("Endgame_Korvyn", "Korvyn");
    }

    void resetMorghulShopStock() {
        resetShopStock("Endgame_Morghul", "Morghul");
    }

    private void resetShopStock(String shopAssetId, String logLabel) {
        try {
            var assetMap = com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopAsset
                    .getAssetStore().getAssetMap();
            var asset = assetMap.getAsset(shopAssetId);
            if (asset == null) {
                getLogger().atFine().log("[EndgameQoL] %s shop asset not found, skipping stock reset", logLabel);
                return;
            }
            // Use ZERO_YEAR as game time — Instant.now() (real year 2026) causes "restocks in 739,676 days"
            // because the engine calculates days from WorldTimeResource.ZERO_YEAR (year 1 CE).
            // Using ZERO_YEAR makes the nextRefreshTime immediately "past due" so the engine
            // recalculates it correctly with actual game time on next player interaction.
            java.time.Instant gameTime = com.hypixel.hytale.server.core.modules.time.WorldTimeResource.ZERO_YEAR;
            var shopState = com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopState.get()
                    .getOrCreateShopState(asset, gameTime);
            shopState.resetStockAndResolve(asset);
            shopState.setNextRefreshTime(gameTime);
            com.hypixel.hytale.builtin.adventure.shop.barter.BarterShopState.save();
            getLogger().atFine().log("[EndgameQoL] %s shop stock reset", logLabel);
        } catch (Exception e) {
            getLogger().atWarning().log("[EndgameQoL] Failed to reset %s shop stock: %s", logLabel, e.getMessage());
        }
    }
}