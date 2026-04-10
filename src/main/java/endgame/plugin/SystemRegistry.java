package endgame.plugin;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.components.BurnComponent;
import endgame.plugin.components.PoisonComponent;
import endgame.plugin.components.VoidMarkComponent;
import endgame.plugin.config.GauntletLeaderboard;
import endgame.plugin.managers.BossHealthManager;
import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.ComboMeterManager;
import endgame.plugin.managers.GauntletManager;
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
import endgame.plugin.systems.trial.GauntletDamageBoostSystem;
import endgame.plugin.systems.trial.GauntletDeathSystem;
import endgame.plugin.systems.trial.GauntletLifestealSystem;
import endgame.plugin.systems.trial.GauntletTickSystem;
import endgame.plugin.systems.trial.WardenTrialDeathSystem;
import endgame.plugin.systems.trial.WardenTrialManager;
import endgame.plugin.systems.trial.WardenTrialTickSystem;
import endgame.plugin.systems.weapon.BlinkTrailDamageSystem;
import endgame.plugin.systems.weapon.DaggerVanishSystem;
import endgame.plugin.systems.weapon.HederaDaggerEffectSystem;
import endgame.plugin.systems.weapon.LongswordChargedStaminaSystem;
import endgame.plugin.systems.weapon.PrismaManaCostSystem;
import endgame.plugin.systems.weapon.PrismaMirageCleanupSystem;
import endgame.plugin.systems.weapon.PrismaMirageSystem;
import endgame.plugin.systems.weapon.VoidMarkApplySystem;
import endgame.plugin.systems.weapon.VoidMarkExpirySystem;
import endgame.plugin.systems.accessory.AccessoryAttackSystem;
import endgame.plugin.systems.accessory.AccessoryDefenseSystem;
import endgame.plugin.systems.accessory.AccessoryPassiveSystem;
import endgame.plugin.systems.accessory.FrostwalkerBlockInitializer;
import endgame.plugin.systems.accessory.FrostwalkerBlockTickSystem;
import endgame.plugin.utils.VoidMarkTracker;

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
    private WardenTrialManager wardenTrialManager;
    private ComboMeterManager comboMeterManager;
    private GauntletManager gauntletManager;
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
    private DaggerVanishSystem daggerVanishSystem;
    private PrismaManaCostSystem prismaManaCostSystem;
    private FrostDragonSkyBoltSystem frostDragonSkyBoltSystem;
    private PrismaMirageSystem prismaMirageSystem;
    private PrismaMirageCleanupSystem prismaMirageCleanupSystem;
    private VoidMarkExpirySystem voidMarkExpirySystem;
    private BlinkTrailDamageSystem blinkTrailDamageSystem;
    private endgame.plugin.systems.ArmorHPRegenSystem armorHPRegenSystem;
    private WardenTrialTickSystem wardenTrialTickSystem;
    private ComboKillTracker comboKillTracker;

    public SystemRegistry(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all managers, systems, and components. Called from setup().
     */
    public void register(Config<GauntletLeaderboard> gauntletLeaderboard,
                         ComponentType<EntityStore, PlayerEndgameComponent> playerEndgameComponentType) {
        registerManagers();
        registerPlayerDataSystem(playerEndgameComponentType);
        registerBossSystems();
        registerWeaponSystems();
        wireCleanupReferences();
        registerTrialSystems();
        registerComboSystems();
        registerGauntletSystems(gauntletLeaderboard);
        registerBountySystems();
        registerAccessorySystems();
        registerPetSystems();
    }

    private void registerPlayerDataSystem(ComponentType<EntityStore, PlayerEndgameComponent> playerEndgameComponentType) {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering PlayerDataEnsureSystem...");
        plugin.getEntityStoreRegistry().registerSystem(
                new endgame.plugin.systems.PlayerDataEnsureSystem(playerEndgameComponentType));
    }

    private void wireCleanupReferences() {
        if (this.dangerZoneTickSystem != null) {
            this.dangerZoneTickSystem.setWeaponSystems(
                    this.daggerVanishSystem, this.prismaManaCostSystem, this.prismaMirageSystem);
        }
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

        this.daggerVanishSystem = new DaggerVanishSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(this.daggerVanishSystem);

        this.blinkTrailDamageSystem = new BlinkTrailDamageSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(this.blinkTrailDamageSystem);
        this.daggerVanishSystem.setBlinkTrailSystem(this.blinkTrailDamageSystem);

        this.prismaManaCostSystem = new PrismaManaCostSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(this.prismaManaCostSystem);

        plugin.getEntityStoreRegistry().registerSystem(new LongswordChargedStaminaSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.weapon.FrostSlowSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.weapon.FireBurnSystem(plugin));
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.weapon.StaffEffectSystem(plugin));

        // Prisma Mirage (Sword signature clones)
        this.prismaMirageSystem = new PrismaMirageSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(this.prismaMirageSystem);
        this.prismaMirageCleanupSystem = new PrismaMirageCleanupSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(this.prismaMirageCleanupSystem);

        // Mana Regen Armor (Mithril/Onyxium/Prisma bonus regen)
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.ManaRegenArmorSystem(plugin));

        // Armor HP Regen (passive HP regen after no-damage delay)
        this.armorHPRegenSystem = new endgame.plugin.systems.ArmorHPRegenSystem(plugin);
        plugin.getEntityStoreRegistry().registerSystem(this.armorHPRegenSystem);
        plugin.getEntityStoreRegistry().registerSystem(new endgame.plugin.systems.ArmorHPRegenDamageTracker());

        // Void Mark (Dagger mark + blink execution)
        ComponentType<EntityStore, VoidMarkComponent> voidMarkType =
                plugin.getEntityStoreRegistry().registerComponent(VoidMarkComponent.class, VoidMarkComponent::new);
        VoidMarkTracker tracker = VoidMarkTracker.getInstance();
        plugin.getEntityStoreRegistry().registerSystem(new VoidMarkApplySystem(plugin, voidMarkType, tracker));
        this.voidMarkExpirySystem = new VoidMarkExpirySystem(plugin, voidMarkType, tracker);
        plugin.getEntityStoreRegistry().registerSystem(this.voidMarkExpirySystem);
        this.daggerVanishSystem.setVoidMarkSupport(voidMarkType, tracker);
    }

    private void registerTrialSystems() {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering trial systems...");

        if (NPCEntity.getComponentType() == null) {
            plugin.getLogger().atWarning().log("[EndgameQoL] NPCEntity not available - trial systems disabled.");
            return;
        }

        this.wardenTrialManager = new WardenTrialManager(plugin);
        this.wardenTrialTickSystem = new WardenTrialTickSystem(plugin, this.wardenTrialManager);
        plugin.getEntityStoreRegistry().registerSystem(this.wardenTrialTickSystem);
        plugin.getEntityStoreRegistry().registerSystem(new WardenTrialDeathSystem(plugin, this.wardenTrialManager));
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

    private void registerGauntletSystems(Config<GauntletLeaderboard> gauntletLeaderboard) {
        plugin.getLogger().atInfo().log("[EndgameQoL] Registering gauntlet systems...");

        if (NPCEntity.getComponentType() == null) {
            plugin.getLogger().atWarning().log("[EndgameQoL] NPCEntity not available - gauntlet systems disabled.");
            return;
        }

        this.gauntletManager = new GauntletManager(plugin, gauntletLeaderboard, this.comboMeterManager);
        plugin.getEntityStoreRegistry().registerSystem(new GauntletTickSystem(plugin, this.gauntletManager, this.comboMeterManager));
        plugin.getEntityStoreRegistry().registerSystem(new GauntletDeathSystem(plugin, this.gauntletManager));
        plugin.getEntityStoreRegistry().registerSystem(new GauntletDamageBoostSystem(plugin, this.gauntletManager));
        plugin.getEntityStoreRegistry().registerSystem(new GauntletLifestealSystem(plugin, this.gauntletManager, this.comboMeterManager));
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

        if (this.daggerVanishSystem != null) {
            this.daggerVanishSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared DaggerVanishSystem state");
        }

        if (this.blinkTrailDamageSystem != null) {
            this.blinkTrailDamageSystem.forceClear();
        }

        if (this.prismaManaCostSystem != null) {
            this.prismaManaCostSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared PrismaManaCostSystem state");
        }

        if (this.frostDragonSkyBoltSystem != null) {
            this.frostDragonSkyBoltSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared FrostDragonSkyBoltSystem state");
        }

        if (this.prismaMirageSystem != null) {
            this.prismaMirageSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared PrismaMirageSystem state");
        }

        if (this.prismaMirageCleanupSystem != null) {
            this.prismaMirageCleanupSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared PrismaMirageCleanupSystem state");
        }

        if (this.voidMarkExpirySystem != null) {
            this.voidMarkExpirySystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared VoidMarkExpirySystem state");
        }

        if (this.armorHPRegenSystem != null) {
            this.armorHPRegenSystem.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared ArmorHPRegenSystem state");
        }

        if (this.wardenTrialManager != null) {
            this.wardenTrialManager.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared WardenTrialManager state");
        }

        if (this.comboMeterManager != null) {
            this.comboMeterManager.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared ComboMeterManager state");
        }

        if (this.gauntletManager != null) {
            this.gauntletManager.forceClear();
            plugin.getLogger().atInfo().log("[EndgameQoL] Cleared GauntletManager state");
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

        VoidMarkTracker.getInstance().clear();

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

    public DaggerVanishSystem getDaggerVanishSystem() {
        return daggerVanishSystem;
    }

    public PrismaManaCostSystem getPrismaManaCostSystem() {
        return prismaManaCostSystem;
    }

    public FrostDragonSkyBoltSystem getFrostDragonSkyBoltSystem() {
        return frostDragonSkyBoltSystem;
    }

    public PrismaMirageSystem getPrismaMirageSystem() {
        return prismaMirageSystem;
    }

    public PrismaMirageCleanupSystem getPrismaMirageCleanupSystem() {
        return prismaMirageCleanupSystem;
    }

    public VoidMarkExpirySystem getVoidMarkExpirySystem() {
        return voidMarkExpirySystem;
    }

    public BlinkTrailDamageSystem getBlinkTrailDamageSystem() {
        return blinkTrailDamageSystem;
    }

    public AccessoryPassiveSystem getAccessoryPassiveSystem() {
        return accessoryPassiveSystem;
    }

    public endgame.plugin.systems.ArmorHPRegenSystem getArmorHPRegenSystem() {
        return armorHPRegenSystem;
    }

    public WardenTrialManager getWardenTrialManager() {
        return wardenTrialManager;
    }

    public WardenTrialTickSystem getWardenTrialTickSystem() {
        return wardenTrialTickSystem;
    }

    public ComboMeterManager getComboMeterManager() {
        return comboMeterManager;
    }

    public ComboKillTracker getComboKillTracker() {
        return comboKillTracker;
    }

    public GauntletManager getGauntletManager() {
        return gauntletManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }
}
