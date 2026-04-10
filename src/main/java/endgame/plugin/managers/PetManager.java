package endgame.plugin.managers;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.builtin.mounts.MountPlugin;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PetOwnerComponent;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.PetConfig;
import endgame.plugin.config.PetData;
import endgame.plugin.config.PetTier;
import endgame.plugin.events.domain.GameEvent;
import endgame.plugin.systems.pet.PetBuffManager;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages pet lifecycle: spawn, despawn, boss-kill unlock.
 * Applies tier-based scaling at spawn: nametag, health, scale.
 * Thread-safe: ConcurrentHashMap for pet ref cache, world.execute() for entity ops.
 */
public class PetManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Pet");

    private static final String[][] BOSS_PET_MAP = {
            {"dragon_frost", "ice_dragon", "Endgame_Pet_Dragon_Frost", "Dragon Frost"},
            {"dragon_fire", "fire_dragon", "Endgame_Pet_Dragon_Fire", "Dragon Fire"},
            {"golem_void", null, "Endgame_Pet_Golem_Void", "Golem Void"},
            {"hedera", null, "Endgame_Pet_Hedera", "Hedera"}
    };

    // Base scales from model definitions (Server/Models/Pets/*.json)
    private static final Map<String, Float> BASE_SCALES = Map.of(
            "Endgame_Pet_Dragon_Frost", 0.45f,
            "Endgame_Pet_Dragon_Fire", 0.45f,
            "Endgame_Pet_Golem_Void", 0.40f,
            "Endgame_Pet_Hedera", 0.70f
    );

    // Base health per pet (invulnerable, but displayed in nametag)
    private static final Map<String, Float> BASE_HEALTH = Map.of(
            "Endgame_Pet_Dragon_Frost", 100f,
            "Endgame_Pet_Dragon_Fire", 100f,
            "Endgame_Pet_Golem_Void", 200f,
            "Endgame_Pet_Hedera", 100f
    );

    // Display names for nametag
    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "Endgame_Pet_Dragon_Frost", "Dragon Frost",
            "Endgame_Pet_Dragon_Fire", "Dragon Fire",
            "Endgame_Pet_Golem_Void", "Golem Void",
            "Endgame_Pet_Hedera", "Hedera"
    );

    private final EndgameQoL plugin;
    private final ConcurrentHashMap<UUID, Ref<EntityStore>> petsByOwner = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> respawnCooldowns = new ConcurrentHashMap<>();
    private final PetBuffManager buffManager;

    private static final long RESPAWN_COOLDOWN_MS = 30_000; // 30 seconds after death

    // Mount Y offsets (where the player sits on each pet model)
    private static final Map<String, Float> MOUNT_Y_OFFSETS = Map.of(
            "Endgame_Pet_Dragon_Frost", 2.5f,
            "Endgame_Pet_Dragon_Fire", 2.0f,
            "Endgame_Pet_Golem_Void", 3.5f,
            "Endgame_Pet_Hedera", 1.8f
    );

    public PetManager(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
        this.buffManager = new PetBuffManager(plugin);
    }

    // =========================================================================
    // Spawn / Despawn
    // =========================================================================

    /**
     * Spawn a pet NPC near the player with tier-based scaling.
     * Applies: nametag, health scaling, PetOwnerComponent.
     * Must be called from the player's world thread.
     */
    public void spawnPet(@Nonnull Store<EntityStore> store, @Nonnull UUID ownerUuid,
                         @Nonnull String petId, @Nonnull Vector3d position) {
        if (!plugin.getConfig().get().pets().isEnabled()) return;

        // Check respawn cooldown
        Long cooldownEnd = respawnCooldowns.get(ownerUuid);
        if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
            long remaining = (cooldownEnd - System.currentTimeMillis()) / 1000;
            PlayerRef pr = findPlayerRef(ownerUuid);
            if (pr != null) {
                pr.sendMessage(Message.raw("[Pets] Respawn cooldown: " + remaining + "s remaining.").color("#ff6644"));
            }
            return;
        }

        // Despawn old pet first
        despawnPet(ownerUuid);

        // Get tier info
        PlayerEndgameComponent comp = plugin.getPlayerComponent(ownerUuid);
        PetTier tier = PetTier.D;
        if (comp != null) {
            tier = comp.getPetData().getPetTier(petId);
        }

        try {
            Vector3f rotation = new Vector3f(0, 0, 0);
            var result = NPCPlugin.get().spawnNPC(store, petId, null, position, rotation);

            if (result != null && result.left() != null && result.left().isValid()) {
                Ref<EntityStore> petRef = result.left();
                petsByOwner.put(ownerUuid, petRef);

                // Attach PetOwnerComponent to the NPC entity
                attachPetOwnerComponent(store, petRef, ownerUuid, petId);

                // Apply tier-based enhancements
                applyTierScale(store, petRef, petId, tier);
                applyNametag(store, petRef, petId, tier);
                applyHealthScaling(store, petRef, petId, tier);
                applyMotionController(store, petRef, petId, tier);

                LOGGER.atInfo().log("[Pet] Spawned %s (Tier %s) for %s", petId, tier.getLabel(), ownerUuid);
            }

            if (comp != null) {
                comp.getPetData().setActivePetId(petId);
            }

            // Apply tier-based passive buffs to owner
            buffManager.onPetSummoned(ownerUuid, petId, tier);

        } catch (Exception e) {
            LOGGER.atWarning().log("[Pet] Failed to spawn %s: %s", petId, e.getMessage());
        }
    }

    /**
     * Attach PetOwnerComponent to the spawned pet NPC.
     * Required for PetDamageScalingSystem and PetAPI to identify pet entities.
     */
    private void attachPetOwnerComponent(Store<EntityStore> store, Ref<EntityStore> petRef,
                                          UUID ownerUuid, String petId) {
        try {
            var compType = PetOwnerComponent.getComponentType();
            if (compType != null) {
                store.addComponent(petRef, compType, new PetOwnerComponent(ownerUuid, petId));
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[Pet] Failed to attach PetOwnerComponent: %s", e.getMessage());
        }
    }

    /**
     * Apply tier-based visual scale to the pet model.
     * Uses Model.createScaledModel() to resize the NPC (same as NPCEntity.setAppearance).
     */
    private void applyTierScale(Store<EntityStore> store, Ref<EntityStore> petRef,
                                 String petId, PetTier tier) {
        if (tier.getScaleBonus() <= 0f) return; // D tier = base scale, no change
        try {
            // ModelAsset name matches the NPC role Appearance field (= petId)
            ModelAsset modelAsset = ModelAsset.getAssetMap().getAsset(petId);
            if (modelAsset == null) return;

            float baseScale = BASE_SCALES.getOrDefault(petId, 0.45f);
            float newScale = baseScale + tier.getScaleBonus();

            Model model = Model.createScaledModel(modelAsset, newScale);
            store.putComponent(petRef, ModelComponent.getComponentType(), new ModelComponent(model));

            // Update motion controllers for new bounding box
            NPCEntity npc = store.getComponent(petRef, NPCEntity.getComponentType());
            if (npc != null && npc.getRole() != null) {
                npc.getRole().updateMotionControllers(petRef, model, model.getBoundingBox(), store);
            }

            LOGGER.atFine().log("[Pet] Scale applied: %s = %.2f (base %.2f + tier bonus %.2f)",
                    petId, newScale, baseScale, tier.getScaleBonus());
        } catch (Exception e) {
            LOGGER.atFine().log("[Pet] Failed to apply scale: %s", e.getMessage());
        }
    }

    /**
     * Force Walk controller on pets that have Fly if tier < B.
     * Dragon Frost starts in Fly (InitialMotionController in JSON) — override to Walk for D/C.
     */
    private void applyMotionController(Store<EntityStore> store, Ref<EntityStore> petRef,
                                        String petId, PetTier tier) {
        // Only Dragon Frost/Fire have Fly controllers
        if (!petId.contains("Dragon")) return;
        try {
            NPCEntity npc = store.getComponent(petRef, NPCEntity.getComponentType());
            if (npc == null || npc.getRole() == null) return;

            if (tier.ordinal() < PetTier.B.ordinal()) {
                // Tier D or C — force Walk (no flying)
                npc.getRole().setActiveMotionController(petRef, npc, "Walk", store);
            }
            // Tier B+ — keep whatever the JSON role sets (Fly for Dragon Frost)
        } catch (Exception e) {
            LOGGER.atFine().log("[Pet] Failed to set motion controller: %s", e.getMessage());
        }
    }

    /**
     * Add NPCMountComponent at spawn if tier is high enough.
     * The vanilla engine shows the mount prompt automatically when the component exists.
     * Dragon Frost/Fire: mountable at Tier C. Golem Void: mountable at Tier S. Hedera: not mountable.
     */
    private void applyMountComponent(Store<EntityStore> store, Ref<EntityStore> petRef,
                                      String petId, PetTier tier, UUID ownerUuid) {
        // Hedera is support archetype — not mountable
        if (petId.contains("Hedera")) return;

        PetTier requiredTier = petId.contains("Golem_Void") ? PetTier.S : PetTier.C;
        if (tier.ordinal() < requiredTier.ordinal()) return;

        try {
            PlayerRef ownerPlayerRef = findPlayerRef(ownerUuid);
            if (ownerPlayerRef == null) return;

            float yOffset = MOUNT_Y_OFFSETS.getOrDefault(petId, 2.0f);
            // Scale Y offset with tier size growth
            float baseScale = BASE_SCALES.getOrDefault(petId, 0.45f);
            float currentScale = baseScale + tier.getScaleBonus();
            yOffset *= (currentScale / baseScale);

            NPCMountComponent mountComp = new NPCMountComponent();
            mountComp.setOwnerPlayerRef(ownerPlayerRef);
            mountComp.setAnchor(0.0f, yOffset, 0.0f);

            NPCEntity npc = store.getComponent(petRef, NPCEntity.getComponentType());
            if (npc != null) {
                mountComp.setOriginalRoleIndex(npc.getRoleIndex());
            }

            store.addComponent(petRef, NPCMountComponent.getComponentType(), mountComp);

            // Add Interactable so the engine shows the mount prompt on hover
            store.ensureAndGetComponent(petRef, Interactable.getComponentType());

            LOGGER.atFine().log("[Pet] Mount component added: %s (yOffset=%.1f)", petId, yOffset);
        } catch (Exception e) {
            LOGGER.atFine().log("[Pet] Failed to add mount component: %s", e.getMessage());
        }
    }

    /**
     * Set pet nametag to "[Tier X] DisplayName".
     */
    private void applyNametag(Store<EntityStore> store, Ref<EntityStore> petRef,
                               String petId, PetTier tier) {
        try {
            Nameplate nameplate = store.getComponent(petRef, Nameplate.getComponentType());
            if (nameplate == null) {
                nameplate = new Nameplate("");
                store.addComponent(petRef, Nameplate.getComponentType(), nameplate);
            }
            String displayName = DISPLAY_NAMES.getOrDefault(petId, petId);
            nameplate.setText("[" + tier.getLabel() + "] " + displayName);
        } catch (Exception e) {
            LOGGER.atFine().log("[Pet] Failed to set nametag: %s", e.getMessage());
        }
    }

    /**
     * Scale pet health by tier: baseHealth * (1 + tier.ordinal() * 0.2).
     * Tier D = 1.0x, C = 1.2x, B = 1.4x, A = 1.6x, S = 1.8x, SS = 2.0x.
     */
    private void applyHealthScaling(Store<EntityStore> store, Ref<EntityStore> petRef,
                                     String petId, PetTier tier) {
        try {
            float baseHp = BASE_HEALTH.getOrDefault(petId, 100f);
            float scaledHp = baseHp * (1.0f + tier.ordinal() * 0.2f);

            EntityStatMap statMap = store.getComponent(petRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                statMap.setStatValue(DefaultEntityStatTypes.getHealth(), scaledHp);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[Pet] Failed to scale health: %s", e.getMessage());
        }
    }

    /**
     * Despawn the player's active pet. Thread-safe.
     */
    public void despawnPet(@Nonnull UUID ownerUuid) {
        Ref<EntityStore> petRef = petsByOwner.remove(ownerUuid);
        if (petRef != null && petRef.isValid()) {
            try {
                World petWorld = petRef.getStore().getExternalData().getWorld();
                if (petWorld != null && petWorld.isAlive()) {
                    petWorld.execute(() -> {
                        try {
                            if (petRef.isValid()) {
                                petRef.getStore().removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                                LOGGER.atFine().log("[Pet] Despawned pet for %s via world.execute", ownerUuid);
                            }
                        } catch (Exception ex) {
                            LOGGER.atFine().log("[Pet] Despawn in execute failed: %s", ex.getMessage());
                        }
                    });
                } else {
                    // World not available — try direct removal as fallback
                    try {
                        if (petRef.isValid()) {
                            petRef.getStore().removeEntity(petRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
                            LOGGER.atFine().log("[Pet] Despawned pet for %s via direct removal", ownerUuid);
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                LOGGER.atFine().log("[Pet] Despawn cleanup: %s", e.getMessage());
            }
        }

        PlayerEndgameComponent comp = plugin.getPlayerComponent(ownerUuid);
        if (comp != null) {
            comp.getPetData().setActivePetId("");
        }

        // Remove passive buffs
        buffManager.onPetDespawned(ownerUuid);
    }

    // =========================================================================
    // Mount System (Tier C+)
    // =========================================================================

    /**
     * Mount the player on their active pet. Adds NPCMountComponent to the pet NPC.
     * Must be called on the pet's world thread.
     */
    public boolean mountPet(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef,
                             @Nonnull UUID ownerUuid, @Nonnull PlayerRef ownerPlayerRef) {
        Ref<EntityStore> petRef = petsByOwner.get(ownerUuid);
        if (petRef == null || !petRef.isValid()) return false;

        // Check tier >= C for mount permission
        PlayerEndgameComponent comp = plugin.getPlayerComponent(ownerUuid);
        if (comp == null) return false;
        String petId = comp.getPetData().getActivePetId();
        if (petId == null || petId.isEmpty()) return false;

        PetTier tier = comp.getPetData().getPetTier(petId);
        // Dragon Frost/Fire: mount at tier C. Golem Void: mount at tier S.
        PetTier requiredTier = petId.contains("Golem_Void") ? PetTier.S : PetTier.C;
        if (tier.ordinal() < requiredTier.ordinal()) {
            ownerPlayerRef.sendMessage(Message.raw("[Pets] Requires Tier " + requiredTier.getLabel() + " to mount.").color("#ff6644"));
            return false;
        }

        try {
            Store<EntityStore> petStore = petRef.getStore();
            float yOffset = MOUNT_Y_OFFSETS.getOrDefault(petId, 2.0f);

            // Scale Y offset with tier bonus
            float scaleMultiplier = 1.0f + tier.getScaleBonus() / BASE_SCALES.getOrDefault(petId, 0.45f);
            yOffset *= scaleMultiplier;

            NPCMountComponent mountComp = new NPCMountComponent();
            mountComp.setOwnerPlayerRef(ownerPlayerRef);
            mountComp.setAnchor(0.0f, yOffset, 0.0f);

            // Get original role index for restoration on dismount
            NPCEntity npc = petStore.getComponent(petRef, NPCEntity.getComponentType());
            if (npc != null) {
                mountComp.setOriginalRoleIndex(npc.getRoleIndex());
            }

            petStore.addComponent(petRef, NPCMountComponent.getComponentType(), mountComp);

            LOGGER.atInfo().log("[Pet] Player %s mounted %s (yOffset=%.1f)", ownerUuid, petId, yOffset);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("[Pet] Mount failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Dismount the player from their pet. Removes NPCMountComponent.
     */
    public void dismountPet(@Nonnull UUID ownerUuid) {
        Ref<EntityStore> petRef = petsByOwner.get(ownerUuid);
        if (petRef == null || !petRef.isValid()) return;

        try {
            Store<EntityStore> petStore = petRef.getStore();
            NPCMountComponent mountComp = petStore.getComponent(petRef, NPCMountComponent.getComponentType());
            if (mountComp != null) {
                petStore.removeComponent(petRef, NPCMountComponent.getComponentType());
                LOGGER.atFine().log("[Pet] Player %s dismounted", ownerUuid);
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[Pet] Dismount failed: %s", e.getMessage());
        }
    }

    /**
     * Check if player is currently mounted on their pet.
     */
    public boolean isMounted(@Nonnull UUID ownerUuid) {
        Ref<EntityStore> petRef = petsByOwner.get(ownerUuid);
        if (petRef == null || !petRef.isValid()) return false;
        try {
            return petRef.getStore().getComponent(petRef, NPCMountComponent.getComponentType()) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // Cooldown System
    // =========================================================================

    /**
     * Set a respawn cooldown for a player (called when pet dies).
     */
    public void setRespawnCooldown(@Nonnull UUID ownerUuid) {
        respawnCooldowns.put(ownerUuid, System.currentTimeMillis() + RESPAWN_COOLDOWN_MS);
    }

    /**
     * Check if player has an active respawn cooldown.
     */
    public boolean hasRespawnCooldown(@Nonnull UUID ownerUuid) {
        Long end = respawnCooldowns.get(ownerUuid);
        if (end == null) return false;
        if (System.currentTimeMillis() >= end) {
            respawnCooldowns.remove(ownerUuid);
            return false;
        }
        return true;
    }

    public long getRemainingCooldownMs(@Nonnull UUID ownerUuid) {
        Long end = respawnCooldowns.get(ownerUuid);
        if (end == null) return 0;
        return Math.max(0, end - System.currentTimeMillis());
    }

    // =========================================================================
    // Ref Cache
    // =========================================================================

    public void registerPet(@Nonnull UUID ownerUuid, @Nonnull Ref<EntityStore> petRef) {
        petsByOwner.put(ownerUuid, petRef);
    }

    public void clearPet(@Nonnull UUID ownerUuid) {
        petsByOwner.remove(ownerUuid);
    }

    @Nullable
    public Ref<EntityStore> getCachedPetRef(@Nonnull UUID ownerUuid) {
        Ref<EntityStore> ref = petsByOwner.get(ownerUuid);
        if (ref != null && !ref.isValid()) {
            petsByOwner.remove(ownerUuid, ref);
            return null;
        }
        return ref;
    }

    // =========================================================================
    // Player Lifecycle
    // =========================================================================

    public void onPlayerConnect(@Nonnull UUID uuid, @Nonnull PlayerEndgameComponent comp) {
        if (!plugin.getConfig().get().pets().isEnabled()) return;

        String activePetId = comp.getPetData().getActivePetId();
        if (activePetId != null && !activePetId.isEmpty()) {
            comp.getPetData().setActivePetId("");
            LOGGER.atFine().log("[Pet] Cleared stale active pet %s for %s on reconnect", activePetId, uuid);
        }
        petsByOwner.remove(uuid);
        respawnCooldowns.remove(uuid);
    }

    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        despawnPet(uuid);
    }

    // =========================================================================
    // Boss Kill → Pet Unlock
    // =========================================================================

    public void handleBossKill(@Nonnull GameEvent.BossKillEvent event) {
        PetConfig config = plugin.getConfig().get().pets();
        LOGGER.atInfo().log("[Pet] BossKillEvent received: bossTypeId=%s, enabled=%b, players=%d",
                event.bossTypeId(), config.isEnabled(), event.creditedPlayers().size());

        if (!config.isEnabled()) return;

        String bossTypeId = event.bossTypeId();
        String petId = mapBossToPetId(bossTypeId);
        LOGGER.atInfo().log("[Pet] Mapped boss '%s' → pet '%s'", bossTypeId, petId);
        if (petId == null) return;

        float chance = config.getChanceForBoss(bossTypeId);
        LOGGER.atInfo().log("[Pet] Unlock chance for '%s': %.2f", bossTypeId, chance);
        if (chance <= 0f) return;

        String displayName = mapBossToDisplayName(bossTypeId);

        for (UUID playerUuid : event.creditedPlayers()) {
            PlayerEndgameComponent comp = plugin.getPlayerComponent(playerUuid);
            if (comp == null) continue;

            PetData petData = comp.getPetData();
            if (petData.isUnlocked(petId)) continue;

            if (ThreadLocalRandom.current().nextFloat() < chance) {
                petData.unlock(petId);

                PlayerRef pr = findPlayerRef(playerUuid);
                if (pr != null) {
                    pr.sendMessage(Message.join(
                            Message.raw("[EndgameQoL] ").color("#ffaa00"),
                            Message.raw("Pet Unlocked: " + displayName + "! ").color("#4ade80"),
                            Message.raw("Use /eg pet to summon it.").color("#aaaaaa")
                    ));
                }

                LOGGER.atInfo().log("[Pet] %s unlocked pet %s from boss %s", playerUuid, petId, bossTypeId);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Nullable
    private static String mapBossToPetId(String bossTypeId) {
        if (bossTypeId == null) return null;
        String lower = bossTypeId.toLowerCase();
        for (String[] entry : BOSS_PET_MAP) {
            if (lower.contains(entry[0])) return entry[2];
            if (entry[1] != null && lower.contains(entry[1])) return entry[2];
        }
        return null;
    }

    @Nonnull
    private static String mapBossToDisplayName(String bossTypeId) {
        if (bossTypeId == null) return "Unknown";
        String lower = bossTypeId.toLowerCase();
        for (String[] entry : BOSS_PET_MAP) {
            if (lower.contains(entry[0])) return entry[3];
            if (entry[1] != null && lower.contains(entry[1])) return entry[3];
        }
        return "Unknown";
    }

    @Nullable
    private PlayerRef findPlayerRef(@Nonnull UUID uuid) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p == null) continue;
            UUID pUuid = EntityUtils.getUuid(p);
            if (uuid.equals(pUuid)) return p;
        }
        return null;
    }

    public static float getBaseScale(@Nonnull String petId) {
        return BASE_SCALES.getOrDefault(petId, 0.45f);
    }

    public ConcurrentHashMap<UUID, Ref<EntityStore>> getPetsByOwner() {
        return petsByOwner;
    }

    public PetBuffManager getBuffManager() { return buffManager; }

    public void forceClear() {
        petsByOwner.clear();
        respawnCooldowns.clear();
        buffManager.clear();
    }
}
