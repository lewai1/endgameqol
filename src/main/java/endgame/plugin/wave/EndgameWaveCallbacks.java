package endgame.plugin.wave;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.PlayerRefCache;
import endgame.wavearena.WaveArenaCallbacks;
import endgame.wavearena.WaveArenaConfig;
import endgame.wavearena.WaveArenaEngine;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * EndgameQoL's implementation of WaveArenaCallbacks.
 * Handles XP rewards, bounty hooks, chat messages, sounds, item drops.
 */
public class EndgameWaveCallbacks implements WaveArenaCallbacks {

    private final EndgameQoL plugin;

    public EndgameWaveCallbacks(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onCountdown(@Nonnull UUID playerUuid, @Nonnull String arenaId, int secondsRemaining) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        String color = config != null ? config.getDisplayColor() : "#FFD700";
        pr.sendMessage(Message.raw("[" + name + "] Prepare yourself! Starting in " + secondsRemaining + "s...").color(color));
    }

    @Override
    public void onWaveStart(@Nonnull UUID playerUuid, @Nonnull String arenaId, int waveIndex, int totalWaves) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        pr.sendMessage(Message.raw("[" + name + "] Wave " + (waveIndex + 1) + "/" + totalWaves + " — Fight!").color("#FF5555"));
    }

    @Override
    public void onWaveClear(@Nonnull UUID playerUuid, @Nonnull String arenaId, int waveIndex, int totalWaves) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        int interval = config != null ? config.getIntervalSeconds() : 8;

        if (waveIndex + 1 < totalWaves) {
            pr.sendMessage(Message.raw("[" + name + "] Wave cleared! Next wave in " + interval + "s...").color("#55FF55"));
        }

        // Per-wave XP
        if (config != null && config.isXpPerWave() && config.getXpReward() > 0) {
            awardXp(playerUuid, config.getXpReward(), config.getXpSource());
        }
    }

    @Override
    public void onArenaCompleted(@Nonnull UUID playerUuid, @Nonnull String arenaId, int wavesCleared) {
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);

        // Chat
        if (pr != null) {
            String name = config != null ? config.getDisplayName() : arenaId;
            pr.sendMessage(Message.raw("[" + name + "] CHALLENGE COMPLETE! Rewards incoming.").color("#55FF55"));
        }

        if (config == null) return;

        // XP (completion-based, not per-wave)
        if (!config.isXpPerWave() && config.getXpReward() > 0) {
            int tier = config.getBountyTier();
            int xp = tier > 0 ? tier * config.getXpReward() : config.getXpReward();
            awardXp(playerUuid, xp, config.getXpSource());
        }

        // Drop table rewards
        if (config.getRewardDropTable() != null && pr != null) {
            giveDropTableRewards(pr, config.getRewardDropTable());
        }

        // Bounty hook
        if (config.getBountyHook() != null && config.getBountyTier() > 0) {
            try {
                var bounty = plugin.getBountyManager();
                if (bounty != null) {
                    bounty.onTrialComplete(playerUuid, config.getBountyTier());
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onArenaFailed(@Nonnull UUID playerUuid, @Nonnull String arenaId,
                               int wavesCleared, @Nonnull FailReason reason) {
        PlayerRef pr = PlayerRefCache.getByUuid(playerUuid);
        if (pr == null) return;
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        String name = config != null ? config.getDisplayName() : arenaId;
        String msg = switch (reason) {
            case PLAYER_DEATH -> "You died!";
            case TIMEOUT -> "Time's up!";
            case DISCONNECT -> "Disconnected!";
            case MANUAL -> "Challenge cancelled.";
        };
        pr.sendMessage(Message.raw("[" + name + "] " + msg + " (Wave " + (wavesCleared + 1) + " reached)").color("#FF5555"));
    }

    @Override
    public void onMobSpawned(@Nonnull Ref<EntityStore> npcRef, @Nonnull String npcType,
                              @Nonnull UUID ownerUuid, @Nonnull String arenaId) {
        // Set RPG Leveling mob level if active
        WaveArenaConfig config = plugin.getWaveArenaEngine().getConfig(arenaId);
        if (config != null && config.getMobLevel() > 0 && plugin.isRPGLevelingActive()) {
            try {
                Ref<EntityStore> ref = npcRef;
                if (ref.isValid()) {
                    plugin.getRpgLevelingBridge().setMobLevel(
                            ref.getStore(), ref, config.getMobLevel());
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onMobKilled(@Nonnull Ref<EntityStore> npcRef, @Nonnull UUID killerUuid,
                             @Nonnull String arenaId) {
        // No per-kill logic needed currently — death tracking is engine-side
    }

    private void awardXp(UUID playerUuid, int xp, String source) {
        try {
            if (plugin.isRPGLevelingActive()) {
                plugin.getRpgLevelingBridge().addXP(playerUuid, xp, source);
            }
            if (plugin.isEndlessLevelingActive()) {
                plugin.getEndlessLevelingBridge().addXP(playerUuid, xp, source);
            }
        } catch (Exception ignored) {}
    }

    private void giveDropTableRewards(PlayerRef playerRef, String dropTable) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return;
            World world = ref.getStore().getExternalData().getWorld();
            if (world == null || !world.isAlive()) return;

            world.execute(() -> {
                try {
                    Ref<EntityStore> pRef = playerRef.getReference();
                    if (pRef == null || !pRef.isValid()) return;
                    var store = pRef.getStore();
                    var player = store.getComponent(pRef,
                            com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                    if (player == null) return;

                    List<ItemStack> rewards = ItemModule.get().getRandomItemDrops(dropTable);
                    if (rewards == null) return;

                    for (ItemStack item : rewards) {
                        var tx = player.giveItem(item, pRef, store);
                        if (tx.getRemainder() != null && !tx.getRemainder().isEmpty()) {
                            com.hypixel.hytale.server.core.entity.ItemUtils.dropItem(pRef, tx.getRemainder(), store);
                        }
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }
}
