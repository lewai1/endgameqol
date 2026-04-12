package endgame.plugin;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.components.BurnComponent;
import endgame.plugin.components.PoisonComponent;
import endgame.plugin.managers.BossHealthManager;
import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.ComboMeterManager;
import endgame.plugin.managers.MobManager;
import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.managers.boss.GolemVoidBossManager;
import endgame.plugin.systems.boss.DangerZoneTickSystem;
import endgame.plugin.systems.boss.FrostDragonSkyBoltSystem;
import endgame.plugin.systems.boss.BossTargetSwitchSystem;
import endgame.plugin.systems.boss.GenericBossDamageSystem;
import endgame.plugin.systems.boss.GenericBossDeathSystem;
import endgame.plugin.systems.boss.GolemVoidDamageSystem;
import endgame.plugin.systems.boss.GolemVoidDeathSystem;
import endgame.plugin.systems.boss.HederaPoisonSystem;
import endgame.plugin.systems.boss.PlayerDeathBossBarSystem;
import endgame.plugin.systems.combat.ComboDamageBoostSystem;
import endgame.plugin.systems.combat.ComboKillTracker;
import endgame.plugin.systems.effect.BurnTickSystem;
import endgame.plugin.systems.effect.PoisonTickSystem;
import endgame.wavearena.WaveArenaConfig;
import endgame.wavearena.WaveArenaDeathSystem;
import endgame.wavearena.WaveArenaEngine;
import endgame.wavearena.WaveArenaTickSystem;
import endgame.wavearena.WaveArenaAPI;
import endgame.plugin.systems.weapon.HederaDaggerEffectSystem;
import endgame.plugin.systems.weapon.LongswordChargedStaminaSystem;
import endgame.plugin.systems.accessory.AccessoryAttackSystem;
import endgame.plugin.systems.accessory.AccessoryDefenseSystem;
import endgame.plugin.systems.accessory.AccessoryPassiveSystem;
import endgame.plugin.systems.accessory.FrostwalkerBlockInitializer;
import endgame.plugin.systems.accessory.FrostwalkerBlockTickSystem;

import com.hypixel.hytale.server.core.util.Config;

/**
 * Manages registration of all ECS systems, components, and managers.
 * Extracted from EndgameQoL to reduce main class size.
 */
public class SystemRegistry {

    private final EndgameQoL plugin;

    // Managers
    private GolemVoidBossManager golemVoidBossManager;
    private GenericBossManager genericBossManager;
    private EnrageTracker enrageTracker;
    private BossHealthManager bossHealthManager;
    private MobManager mobManager;
    private WaveArenaEngine waveArenaEngine;
    private ComboMeterManager comboMeterManager;
    private BountyManager bountyManager;

    // Accessory systems
    private AccessoryPassiveSystem accessoryPassiveSystem;
    private endgame.plugin.managers.PetManager petManager;
    private endgame.plugin.systems.pet.PetMountListener petMountListener;
    private AccessoryDefenseSystem accessoryDefenseSystem;

    // Systems
    private GenericBossDamageSystem genericBossDamageSystem;
    private BossTargetSwitchSystem bossTargetSwitchSystem;
    private DangerZoneTickSystem dangerZoneTickSystem;
    private FrostDragonSkyBoltSystem frostDragonSkyBoltSystem;
    private endgame.plugin.systems.ArmorHPRegenSystem armorHPRegenSystem;
    private ComboKillTracker comboKillTracker;

    public SystemRegistry(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all managers, systems, and components. Called from setup().
     */
    public void register(ComponentType<EntityStore, PlayerEndgameComponent> playerEndgameComponentType) {
        registerManagers();
        registerPlayerDataSystem(playerEndgameComponentType);
        registerBossSystems();
        registerWeaponSystems();
        registerTrialSystems();
        registerComboSystems();
        registerBountySystems();
        registerAccessorySystems();
        registerPetSystems();
    }

    private void registerPlayerDataSystem(ComponentType<EntityStore, PlayerEndgameComponent> playerEndgameComponentType) {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering PlayerDataEnsureSystem...");
        plugin.getEntityStoreRegistry().registerSystem(
                new endgame.plugin.systems.PlayerDataEnsureSystem(playerEndgameComponentType));
    }

    private void registerManagers() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering managers...");
        this.bossHealthManager = new BossHealthManager(plugin);

        this.mobManager = new MobManager(plugin);
        plugin.getEntityStoreRegistry()
                .registerSystem(new MobManager.EntitySetupSystem(plugin, this.mobManager));
        plugin.getEntityStoreRegistry()
                .registerSystem(new MobManager.StatRefreshSystem(plugin, this.mobManager));

        this.golemVoidBossManager = new GolemVoidBossManager(plugin);
        this.genericBossManager = new GenericBossManager(plugin);
        this.enrageTracker = new EnrageTracker(plugin);
        this.mobManager.setGolemVoidBossManager(this.golemVoidBossManager);
    }

    private void registerBossSystems() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering boss systems...");

        if (NPCEntity.getComponentType() == null) {
            plugin.getLogger().atWarning().log("[EndgameQoL] NPCEntity not available - boss systems disabled. Is the NPC module loaded?");
            return;
        }

        // Golem Void boss pipeline
        plugin.getEntityStoreRegistry().registerSystem(new GolemVoidDamageSystem(this.golemVoidBossManager, this.enrageTracker));
        plugin.getEntityStoreRegistry().registerSystem(new PlayerDeathBossBarSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.portal.InstanceRespawnSystem(plugin));
        this.dangerZoneTickSystem = new DangerZoneTickSystem(plugin, this.golemVoidBossManager, this.genericBossManager, this.enrageTracker);
        plugin.getEntityStoreRegistry().registerSystem(this.dangerZoneTickSystem);
        plugin.getEntityStoreRegistry().registerSystem(new GolemVoidDeathSystem(plugin, this.golemVoidBossManager, this.enrageTracker));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.boss.BossDamageFilterSystem(plugin, this.enrageTracker));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.boss.BossFriendlyFireFilterSystem());
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.boss.PrismaWeaponBossFilterSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.boss.PrismaArmorBossAmplifySystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.PvPDamageFilterSystem(plugin));

        // Generic boss pipeline (Frost Dragon, Hedera)
        this.genericBossDamageSystem = new GenericBossDamageSystem(this.genericBossManager, plugin, this.enrageTracker);
        plugin.getEntityStoreRegistry().registerSystem(this.genericBossDamageSystem);
        plugin.getEntityStoreRegistry().registerSystem(new GenericBossDeathSystem(plugin, this.genericBossManager, this.enrageTracker, this.genericBossDamageSystem));

        // Boss target switching (weighted random: nearest 40%, aggro 40%, random 20%)
        this.bossTargetSwitchSystem = new BossTargetSwitchSystem(plugin, this.genericBossManager, this.genericBossDamageSystem);
        plugin.getEntityStoreRegistry().registerSystem(this.bossTargetSwitchSystem);

        // Hedera boss systems (poison + roots)
        ComponentType<EntityStore, PoisonComponent> poisonComponentType =
                plugin.getEntityStoreRegistry().registerComponent(PoisonComponent.class, PoisonComponent::new);
        plugin.getEntityStoreRegistry().registerSystem(new PoisonTickSystem(plugin, poisonComponentType));
        plugin.getEntityStoreRegistry().registerSystem(new HederaPoisonSystem(plugin, poisonComponentType));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.boss.HederaRootsSystem(plugin));
        var hederaRootsAOESystem = new endgame.plugin.systems.boss.HederaRootsAOESystem(plugin);
        hederaRootsAOESystem.setGenericBossManager(this.genericBossManager);
        plugin.getEntityStoreRegistry().registerSystem(hederaRootsAOESystem);

        // Hedera poison cloud (Phase 2+ lingering AOE)
        var hederaPoisonCloudSystem = new endgame.plugin.systems.boss.HederaPoisonCloudSystem(plugin, poisonComponentType);
        hederaPoisonCloudSystem.setGenericBossManager(this.genericBossManager);
        plugin.getEntityStoreRegistry().registerSystem(hederaPoisonCloudSystem);

        // Hedera dagger effect (weapon that applies boss poison)
        plugin.getEntityStoreRegistry().registerSystem(new HederaDaggerEffectSystem(plugin, poisonComponentType));

        // Frost dragon sky bolt (Java-spawned projectile above player)
        this.frostDragonSkyBoltSystem = new FrostDragonSkyBoltSystem(plugin);
        this.frostDragonSkyBoltSystem.setGenericBossManager(this.genericBossManager);
        plugin.getEntityStoreRegistry().registerSystem(this.frostDragonSkyBoltSystem);
    }

    private void registerWeaponSystems() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering weapon systems...");

        plugin.getEntityStoreRegistry().registerSystem(new LongswordChargedStaminaSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.weapon.FrostSlowSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.weapon.FireBurnSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.weapon.StaffEffectSystem(plugin));

        // Mana Regen Armor (Mithril/Onyxium/Prisma bonus regen)
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.ManaRegenArmorSystem(plugin));

        // Armor HP Regen (passive HP regen after no-damage delay)
        this.armorHPRegenSystem = new endgame.plugin.systems.ArmorHPRegenSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(this.armorHPRegenSystem);
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.ArmorHPRegenDamageTracker());
    }

    private void registerTrialSystems() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering wave arena systems...");

        if (NPCEntity.getComponentType() == null) {
            plugin.getLogger().atWarning().log("[EndgameQoL] NPCEntity not available - wave arena disabled.");
            return;
        }

        this.waveArenaEngine = new WaveArenaEngine();
        WaveArenaAPI.init(this.waveArenaEngine);

        // Register EndgameQoL callbacks (XP, bounty, rewards, chat)
        this.waveArenaEngine.addCallbacks(new endgame.plugin.wave.EndgameWaveCallbacks(plugin));

        // Load arena configs from JSON assets
        loadWaveArenaConfigs();

        // Register ECS systems
        plugin.getEntityStoreRegistry().registerSystem(new WaveArenaTickSystem(this.waveArenaEngine));
        plugin.getEntityStoreRegistry().registerSystem(new WaveArenaDeathSystem(this.waveArenaEngine));
    }

    private void loadWaveArenaConfigs() {
        java.util.function.BiFunction<String, Integer, endgame.wavearena.WaveDef.MobEntry> M =
                endgame.wavearena.WaveDef.MobEntry::new;

        waveArenaEngine.registerConfig(WaveArenaConfig.builder("Warden_Trial_I")
                .displayName("Warden Trial — Tier I").displayColor("#ffaa00")
                .waveCount(5).timeLimitSeconds(270).spawnRadius(6f).intervalSeconds(8).countdownSeconds(3).mobLevel(60)
                .rewardDropTable("Endgame_Drop_Warden_Challenge_I").xpReward(150).xpSource("WARDEN_TRIAL")
                .bountyHook("COMPLETE_TRIAL").bountyTier(1)
                .instanceBlacklist(java.util.List.of("instance-")).blockedMessage("You cannot start a Warden Trial inside a dungeon instance.").zoneParticleId("Warden_Trial_Zone").zoneParticleScale(16f).zoneParticleYOffset(-0.3)
                .waves(java.util.List.of(
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Goblin_Scrapper",3), M.apply("Skeleton_Archer",2))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Skeleton_Soldier",2), M.apply("Skeleton_Mage",2), M.apply("Spider",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Hyena",2), M.apply("Goblin_Lobber",2), M.apply("Skeleton_Ranger",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Outlander_Brute",1), M.apply("Skeleton_Archmage",1), M.apply("Skeleton_Soldier",2))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Toad_Rhino",1), M.apply("Outlander_Hunter",2), M.apply("Skeleton_Knight",2)))
                )).build());

        waveArenaEngine.registerConfig(WaveArenaConfig.builder("Warden_Trial_II")
                .displayName("Warden Trial — Tier II").displayColor("#55aaff")
                .waveCount(5).timeLimitSeconds(360).spawnRadius(6f).intervalSeconds(8).countdownSeconds(3).mobLevel(70)
                .rewardDropTable("Endgame_Drop_Warden_Challenge_II").xpReward(150).xpSource("WARDEN_TRIAL")
                .bountyHook("COMPLETE_TRIAL").bountyTier(2)
                .instanceBlacklist(java.util.List.of("instance-")).blockedMessage("You cannot start a Warden Trial inside a dungeon instance.").zoneParticleId("Warden_Trial_Zone").zoneParticleScale(16f).zoneParticleYOffset(-0.3)
                .waves(java.util.List.of(
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Trork_Brawler",2), M.apply("Skeleton_Ranger",2), M.apply("Trork_Hunter",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Outlander_Marauder",2), M.apply("Outlander_Stalker",2), M.apply("Skeleton_Archmage",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Tiger_Sabertooth",2), M.apply("Trork_Sentry",2), M.apply("Skeleton_Mage",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Saurian_Warrior",2), M.apply("Endgame_Ghoul",2), M.apply("Outlander_Sorcerer",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Goblin_Duke",1), M.apply("Endgame_Saurian_Hunter",1), M.apply("Skeleton_Burnt_Wizard",1), M.apply("Endgame_Werewolf",1)))
                )).build());

        waveArenaEngine.registerConfig(WaveArenaConfig.builder("Warden_Trial_III")
                .displayName("Warden Trial — Tier III").displayColor("#ff6600")
                .waveCount(5).timeLimitSeconds(450).spawnRadius(6f).intervalSeconds(8).countdownSeconds(3).mobLevel(80)
                .rewardDropTable("Endgame_Drop_Warden_Challenge_III").xpReward(150).xpSource("WARDEN_TRIAL")
                .bountyHook("COMPLETE_TRIAL").bountyTier(3)
                .instanceBlacklist(java.util.List.of("instance-")).blockedMessage("You cannot start a Warden Trial inside a dungeon instance.").zoneParticleId("Warden_Trial_Zone").zoneParticleScale(16f).zoneParticleYOffset(-0.3)
                .waves(java.util.List.of(
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Saurian_Rogue",2), M.apply("Skeleton_Sand_Mage",2), M.apply("Endgame_Ghoul",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Werewolf",2), M.apply("Skeleton_Burnt_Gunner",2), M.apply("Skeleton_Burnt_Wizard",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Goblin_Duke",1), M.apply("Endgame_Saurian_Warrior",1), M.apply("Skeleton_Sand_Archmage",1), M.apply("Skeleton_Burnt_Gunner",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Shadow_Knight",2), M.apply("Skeleton_Burnt_Gunner",2), M.apply("Golem_Eye_Void",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Necromancer_Void",1), M.apply("Endgame_Shadow_Knight",1), M.apply("Skeleton_Sand_Archmage",1), M.apply("Skeleton_Burnt_Gunner",2)))
                )).build());

        waveArenaEngine.registerConfig(WaveArenaConfig.builder("Warden_Trial_IV")
                .displayName("Warden Trial — Tier IV").displayColor("#d16eff")
                .waveCount(5).timeLimitSeconds(540).spawnRadius(6f).intervalSeconds(8).countdownSeconds(3).mobLevel(90)
                .rewardDropTable("Endgame_Drop_Warden_Challenge_IV").xpReward(150).xpSource("WARDEN_TRIAL")
                .bountyHook("COMPLETE_TRIAL").bountyTier(4)
                .instanceBlacklist(java.util.List.of("instance-")).blockedMessage("You cannot start a Warden Trial inside a dungeon instance.").zoneParticleId("Warden_Trial_Zone").zoneParticleScale(16f).zoneParticleYOffset(-0.3)
                .waves(java.util.List.of(
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Goblin_Duke",1), M.apply("Endgame_Necromancer_Void",1), M.apply("Skeleton_Burnt_Gunner",2), M.apply("Skeleton_Burnt_Wizard",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Alpha_Rex",1), M.apply("Endgame_Werewolf",1), M.apply("Skeleton_Burnt_Wizard",2), M.apply("Golem_Eye_Void",1))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Necromancer_Void",1), M.apply("Alpha_Rex",1), M.apply("Skeleton_Sand_Archmage",2), M.apply("Golem_Eye_Void",2))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Alpha_Rex",2), M.apply("Endgame_Goblin_Duke",1), M.apply("Skeleton_Burnt_Gunner",2))),
                        new endgame.wavearena.WaveDef(java.util.List.of(M.apply("Endgame_Shadow_Knight",1), M.apply("Alpha_Rex",1), M.apply("Skeleton_Sand_Archmage",1), M.apply("Skeleton_Burnt_Gunner",1), M.apply("Golem_Eye_Void",1), M.apply("Endgame_Necromancer_Void",1)))
                )).build());

        waveArenaEngine.registerConfig(WaveArenaConfig.builder("Portal_Wave_Arena")
                .displayName("Temporal Arena").displayColor("#44cc66")
                .waveCount(10).timeLimitSeconds(600).spawnRadius(8f).intervalSeconds(5).countdownSeconds(5)
                .rewardDropTable("Endgame_Drop_Reward_25").xpReward(30).xpSource("WAVE_ARENA").xpPerWave(true)
                .baseCount(4).countScaling(1.2f).bossEveryN(5)
                .mobPool(java.util.List.of(
                        new WaveArenaConfig.PoolEntry("Goblin_Scrapper", 100, 1, false),
                        new WaveArenaConfig.PoolEntry("Skeleton_Archer", 90, 1, false),
                        new WaveArenaConfig.PoolEntry("Skeleton_Soldier", 80, 2, false),
                        new WaveArenaConfig.PoolEntry("Trork_Brawler", 70, 3, false),
                        new WaveArenaConfig.PoolEntry("Skeleton_Ranger", 60, 4, false),
                        new WaveArenaConfig.PoolEntry("Outlander_Marauder", 50, 5, false),
                        new WaveArenaConfig.PoolEntry("Endgame_Saurian_Warrior", 40, 6, false),
                        new WaveArenaConfig.PoolEntry("Endgame_Werewolf", 30, 7, false),
                        new WaveArenaConfig.PoolEntry("Endgame_Goblin_Duke", 15, 8, true),
                        new WaveArenaConfig.PoolEntry("Alpha_Rex", 10, 9, true)
                )).build());

        plugin.getLogger().atInfo().log("[WaveArena] Registered %d arena configs", waveArenaEngine.getRegisteredIds().size());
    }

    private void registerComboSystems() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering combo systems...");

        if (NPCEntity.getComponentType() == null) {
            plugin.getLogger().atWarning().log("[EndgameQoL] NPCEntity not available - combo systems disabled.");
            return;
        }

        this.comboMeterManager = new ComboMeterManager(plugin);
        this.comboKillTracker = new ComboKillTracker(plugin, this.comboMeterManager);
        plugin.getEntityStoreRegistry().registerSystem(this.comboKillTracker);
        plugin.getEntityStoreRegistry().registerSystem(new ComboDamageBoostSystem(plugin, this.comboMeterManager));
    }

    private void registerBountySystems() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering bounty systems...");

        this.bountyManager = new BountyManager(plugin);

        // Hook into combo kill tracker for NPC kill events
        if (this.comboKillTracker != null) {
            this.comboKillTracker.setNpcKillCallback((uuid, npcTypeId) -> {
                this.bountyManager.onNPCKill(uuid, npcTypeId);
                if (plugin.getAchievementManager() != null) {
                    plugin.getAchievementManager().onNPCKill(uuid, npcTypeId);
                }
                // Publish domain event for future listeners
                plugin.getGameEventBus().publish(new endgame.plugin.events.domain.GameEvent.NPCKillEvent(uuid, npcTypeId));
            });
        }

        // Hook into combo meter for tier change events
        if (this.comboMeterManager != null) {
            this.comboMeterManager.setComboTierCallback((uuid, tier) -> this.bountyManager.onComboTier(uuid, tier));
        }

        // Subscribe to domain events — decoupled boss kill handling
        var eventBus = plugin.getGameEventBus();
        eventBus.subscribe(endgame.plugin.events.domain.GameEvent.BossKillEvent.class, event -> {
            for (java.util.UUID playerUuid : event.creditedPlayers()) {
                this.bountyManager.onBossKill(playerUuid, event.bossTypeId(), event.encounterDurationSeconds());
                if (plugin.getAchievementManager() != null) {
                    plugin.getAchievementManager().onBossKill(playerUuid, event.bossTypeId(), event.encounterDurationSeconds());
                }
            }
        });

        // Subscribe to dungeon enter events for exploration bounties + achievements
        eventBus.subscribe(endgame.plugin.events.domain.GameEvent.DungeonEnterEvent.class, event -> {
            this.bountyManager.onDungeonEnter(event.playerUuid(), event.dungeonType());
            if (plugin.getAchievementManager() != null) {
                plugin.getAchievementManager().onDungeonEnter(event.playerUuid(), event.dungeonType());
            }
        });
    }

    private void registerAccessorySystems() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering accessory systems...");
        this.accessoryPassiveSystem = new AccessoryPassiveSystem(plugin);
        this.accessoryDefenseSystem = new AccessoryDefenseSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(this.accessoryPassiveSystem);
        plugin.getEntityStoreRegistry().registerSystem(this.accessoryDefenseSystem);
        ComponentType<EntityStore, BurnComponent> burnComponentType =
                plugin.getEntityStoreRegistry().registerComponent(BurnComponent.class, BurnComponent::new);
        plugin.getEntityStoreRegistry().registerSystem(new BurnTickSystem(plugin, burnComponentType));
        plugin.getEntityStoreRegistry().registerSystem(new AccessoryAttackSystem(plugin, burnComponentType));

        // Frostwalker ChunkStore systems (ice block tick + initializer)
        plugin.getChunkStoreRegistry().registerSystem(new FrostwalkerBlockTickSystem());
        plugin.getChunkStoreRegistry().registerSystem(new FrostwalkerBlockInitializer());
    }

    private void registerPetSystems() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering pet systems...");

        // Register PetOwnerComponent
        var petOwnerType = plugin.getEntityStoreRegistry().registerComponent(
                endgame.plugin.components.PetOwnerComponent.class, "EQoL_PetOwner",
                endgame.plugin.components.PetOwnerComponent.CODEC);
        endgame.plugin.components.PetOwnerComponent.setComponentType(petOwnerType);

        // PetManager
        this.petManager = new endgame.plugin.managers.PetManager(plugin);

        // PetFollowSystem
        plugin.getEntityStoreRegistry().registerSystem(
                new endgame.plugin.systems.pet.PetFollowSystem(plugin, this.petManager));

        // PetCombatSystem (INSPECT damage group — routes player damage target to pet)
        plugin.getEntityStoreRegistry().registerSystem(
                new endgame.plugin.systems.pet.PetCombatSystem(plugin, this.petManager));

        // PetDamageScalingSystem (FILTER damage group — scales pet damage by tier)
        plugin.getEntityStoreRegistry().registerSystem(
                new endgame.plugin.systems.pet.PetDamageScalingSystem(plugin));

        // PetAuraSystem (Tier SS — AOE effects around pet)
        plugin.getEntityStoreRegistry().registerSystem(
                new endgame.plugin.systems.pet.PetAuraSystem(plugin, this.petManager));

        // PetMountListener (intercepts right-click on pet for mounting)
        this.petMountListener = new endgame.plugin.systems.pet.PetMountListener(plugin);
        com.hypixel.hytale.server.core.io.adapter.PacketAdapters.registerInbound(this.petMountListener);

        // Subscribe to BossKillEvent for pet unlock
        plugin.getGameEventBus().subscribe(
                endgame.plugin.events.domain.GameEvent.BossKillEvent.class,
                event -> petManager.handleBossKill(event));
    }

    public endgame.plugin.managers.PetManager getPetManager() { return petManager; }

    // --- Shutdown / Cleanup ---

    /**
     * Clean up all systems to prevent handlers from doubling on reload.
     * Called from EndgameQoL.shutdown().
     */
    public void shutdownAll() {
        if (this.golemVoidBossManager != null) {
            this.golemVoidBossManager.forceClearAllBossBars();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared GolemVoidBossManager state");
        }

        if (this.genericBossManager != null) {
            this.genericBossManager.forceClearAllBossBars();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared GenericBossManager state");
        }

        if (this.bossHealthManager != null) {
            this.bossHealthManager.cleanup();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared BossHealthManager tracking");
        }

        if (this.enrageTracker != null) {
            this.enrageTracker.clear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared EnrageTracker state");
        }

        if (this.genericBossDamageSystem != null) {
            this.genericBossDamageSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared GenericBossDamageSystem tracking");
        }

        if (this.bossTargetSwitchSystem != null) {
            this.bossTargetSwitchSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared BossTargetSwitchSystem tracking");
        }

        if (this.dangerZoneTickSystem != null) {
            this.dangerZoneTickSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared DangerZoneTickSystem tracking");
        }

        if (this.mobManager != null) {
            this.mobManager.cleanup();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared MobManager tracking");
        }

        if (this.frostDragonSkyBoltSystem != null) {
            this.frostDragonSkyBoltSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared FrostDragonSkyBoltSystem state");
        }

        if (this.armorHPRegenSystem != null) {
            this.armorHPRegenSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared ArmorHPRegenSystem state");
        }

        if (this.waveArenaEngine != null) {
            this.waveArenaEngine.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared WaveArenaEngine state");
        }

        if (this.comboMeterManager != null) {
            this.comboMeterManager.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared ComboMeterManager state");
        }

        // BountyManager data auto-persisted via ECS component — no manual save needed

        if (this.accessoryPassiveSystem != null) {
            this.accessoryPassiveSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared AccessoryPassiveSystem state");
        }

        if (this.accessoryDefenseSystem != null) {
            this.accessoryDefenseSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared AccessoryDefenseSystem state");
        }

        if (this.petManager != null) {
            this.petManager.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared PetManager state");
        }
    }

    // --- Getters ---

    public GolemVoidBossManager getGolemVoidBossManager() {
        return golemVoidBossManager;
    }

    public GenericBossManager getGenericBossManager() {
        return genericBossManager;
    }

    public EnrageTracker getEnrageTracker() {
        return enrageTracker;
    }

    public BossHealthManager getBossHealthManager() {
        return bossHealthManager;
    }

    public MobManager getMobManager() {
        return mobManager;
    }

    public DangerZoneTickSystem getDangerZoneTickSystem() {
        return dangerZoneTickSystem;
    }

    public FrostDragonSkyBoltSystem getFrostDragonSkyBoltSystem() {
        return frostDragonSkyBoltSystem;
    }

    public AccessoryPassiveSystem getAccessoryPassiveSystem() {
        return accessoryPassiveSystem;
    }

    public endgame.plugin.systems.ArmorHPRegenSystem getArmorHPRegenSystem() {
        return armorHPRegenSystem;
    }

    public WaveArenaEngine getWaveArenaEngine() {
        return waveArenaEngine;
    }

    public ComboMeterManager getComboMeterManager() {
        return comboMeterManager;
    }

    public ComboKillTracker getComboKillTracker() {
        return comboKillTracker;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }
}
