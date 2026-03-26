package endgame.plugin.rifts;

// HyUI imports — kept for future migration back to HUD overlay
import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIHud;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Rift compass tracker. Sends chat messages to the player holding a Rift Compass
 * that intensify granularly (both text and color) as they approach a portal.
 * Only active in the overworld — disabled inside instance worlds.
 *
 * <p>HyUI HUD methods are preserved below for future migration once the
 * pre-release rendering issue is resolved.</p>
 */
public class RiftCompassTracker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClassFull();
    private static final long TICK_INTERVAL_MS = 500;

    private final RiftConfig config;
    private final RiftManager portalManager;
    private volatile boolean running = false;

    private final Map<UUID, PlayerTrackingState> trackedPlayers = new ConcurrentHashMap<>();

    public RiftCompassTracker(RiftConfig config, RiftManager portalManager) {
        this.config = config;
        this.portalManager = portalManager;
    }

    public void start() {
        if (running) return;
        running = true;
        ((HytaleLogger.Api) LOGGER.atInfo()).log(
                "[HyRifts] Compass tracker started (chat mode, range=%.0f)", config.riftCompassRange);
        scheduleNextTick();
    }

    public void stop() {
        running = false;
        trackedPlayers.clear();
    }

    private void scheduleNextTick() {
        if (!running) return;
        HytaleServer.SCHEDULED_EXECUTOR.schedule(this::tick, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void tick() {
        if (!running) return;
        try {
            processPlayers();
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                    "[HyRifts] Compass tracker error: %s", e.getMessage());
        } finally {
            scheduleNextTick();
        }
    }

    private void processPlayers() {
        int activeCount = portalManager.activeCount();
        if (activeCount == 0) {
            if (!trackedPlayers.isEmpty()) trackedPlayers.clear();
            return;
        }

        List<PlayerRef> players;
        try {
            players = Universe.get().getPlayers();
        } catch (Exception e) {
            return;
        }

        if (players == null || players.isEmpty()) {
            trackedPlayers.clear();
            return;
        }

        Set<UUID> onlineUuids = new HashSet<>();

        for (PlayerRef playerRef : players) {
            try {
                UUID uuid = playerRef.getUuid();
                if (uuid == null) continue;
                onlineUuids.add(uuid);

                UUID worldUuid = playerRef.getWorldUuid();
                if (worldUuid == null) continue;
                World world = Universe.get().getWorld(worldUuid);
                if (world == null) continue;

                // Skip instance worlds — compass only works in the overworld
                String worldName = world.getName();
                if (worldName != null && worldName.startsWith("instance-")) {
                    PlayerTrackingState existing = trackedPlayers.get(uuid);
                    if (existing != null && existing.currentTier != IntensityTier.NONE) {
                        existing.currentTier = IntensityTier.NONE;
                        existing.lastShownIntensity = 0;
                    }
                    continue;
                }

                world.execute(() -> {
                    try {
                        PlayerTrackingState state = trackedPlayers.computeIfAbsent(uuid,
                                k -> new PlayerTrackingState(playerRef));

                        boolean holdingCompass = isHoldingCompass(playerRef);
                        if (!holdingCompass) {
                            if (state.currentTier != IntensityTier.NONE) {
                                state.currentTier = IntensityTier.NONE;
                                state.lastShownIntensity = 0;
                            }
                            state.lastPosition = null;
                            return;
                        }

                        Transform transform = playerRef.getTransform();
                        if (transform == null) return;
                        Vector3d currentPos = transform.getPosition();
                        if (currentPos == null) return;

                        RiftManager.CompassReading reading =
                                portalManager.getCompassReading(worldName, currentPos);

                        if (reading == null) {
                            if (state.currentTier != IntensityTier.NONE) {
                                state.currentTier = IntensityTier.NONE;
                                state.lastShownIntensity = 0;
                            }
                            state.lastPosition = currentPos;
                            return;
                        }

                        double baseIntensity = reading.intensity();

                        boolean movingToward = false;
                        if (state.lastPosition != null) {
                            double prevDx = reading.portal().position().getX() - state.lastPosition.getX();
                            double prevDz = reading.portal().position().getZ() - state.lastPosition.getZ();
                            double prevDistSq = prevDx * prevDx + prevDz * prevDz;

                            double currDx = reading.portal().position().getX() - currentPos.getX();
                            double currDz = reading.portal().position().getZ() - currentPos.getZ();
                            double currDistSq = currDx * currDx + currDz * currDz;

                            movingToward = currDistSq < prevDistSq;
                        }
                        state.lastPosition = currentPos;

                        double intensity;
                        if (movingToward) {
                            intensity = baseIntensity;
                        } else {
                            intensity = Math.max(baseIntensity * 0.8, state.lastShownIntensity * 0.7);
                            if (intensity < 0.02) intensity = 0;
                        }
                        state.lastShownIntensity = intensity;

                        IntensityTier newTier = IntensityTier.fromIntensity(intensity);

                        if (newTier != state.currentTier) {
                            state.currentTier = newTier;
                            if (newTier != IntensityTier.NONE) {
                                sendCompassMessage(state.playerRef, newTier);
                            }
                        }
                    } catch (Exception e) {
                        ((HytaleLogger.Api) LOGGER.atWarning()).log(
                                "[HyRifts-Compass] Error processing player: %s", e.getMessage());
                    }
                });

            } catch (Exception e) {
                ((HytaleLogger.Api) LOGGER.atWarning()).log(
                        "[HyRifts-Compass] Player dispatch error: %s", e.toString());
            }
        }

        trackedPlayers.keySet().removeIf(uuid -> !onlineUuids.contains(uuid));
    }

    private boolean isHoldingCompass(PlayerRef playerRef) {
        try {
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) return false;

            var store = ref.getStore();
            if (store == null) return false;

            LivingEntity entity = (LivingEntity) EntityUtils.getEntity(ref, store);
            if (entity != null) {
                Inventory inventory = entity.getInventory();

                if (inventory != null) {
                    ItemStack mainHand = inventory.getItemInHand();
                    if (mainHand != null && mainHand.getItem() != null) {
                        String id = mainHand.getItem().getId();
                        if (id != null && id.toLowerCase().contains("rift_compass")) {
                            return true;
                        }
                    }

                    ItemStack offHand = inventory.getUtilityItem();
                    if (offHand != null && offHand.getItem() != null) {
                        String id = offHand.getItem().getId();
                        if (id != null && id.toLowerCase().contains("rift_compass")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Failsafe in case of API shifts
        }
        return false;
    }

    /** Send a colored chat message to the player with the compass reading. */
    private void sendCompassMessage(PlayerRef playerRef, IntensityTier tier) {
        try {
            playerRef.sendMessage(Message.raw(tier.text).color(tier.color));
        } catch (Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log(
                    "[HyRifts-Compass] Failed to send message: %s", e.getMessage());
        }
    }

    // ── Granular Intensity Tiers with Colors ────────────────────────────────

    enum IntensityTier {
        NONE("", "#ffffff"),
        TRACE("A faint hum resonates through the air...", "#7e57c2"),
        DISTANT("You sense a distant rift pulsing.", "#4fc3f7"),
        APPROACHING("The air crackles with rift energy.", "#81c784"),
        STRONG("Rift energy surges violently.", "#fff176"),
        INTENSE("The fabric of reality weakens here.", "#ffee58"),
        VERY_CLOSE("The rift tears at reality around you.", "#ffb300"),
        IMMINENT("The anomaly is right in front of you!", "#ff3d00"),
        ON_TOP("You are standing on the precipice of the rift!", "#ff1744");

        final String text;
        final String color;

        IntensityTier(String text, String color) {
            this.text = text;
            this.color = color;
        }

        static IntensityTier fromIntensity(double intensity) {
            if (intensity > 0.96) return ON_TOP;
            if (intensity > 0.90) return IMMINENT;
            if (intensity > 0.82) return VERY_CLOSE;
            if (intensity > 0.70) return INTENSE;
            if (intensity > 0.50) return STRONG;
            if (intensity > 0.30) return APPROACHING;
            if (intensity > 0.10) return DISTANT;
            if (intensity > 0.0)  return TRACE;
            return NONE;
        }
    }

    private static class PlayerTrackingState {
        final PlayerRef playerRef;
        Vector3d lastPosition;
        double lastShownIntensity;
        IntensityTier currentTier = IntensityTier.NONE;

        PlayerTrackingState(PlayerRef playerRef) {
            this.playerRef = playerRef;
        }
    }

    // ── HyUI HUD methods (preserved for future migration) ───────────────────
    // These are currently unused. When HyUI rendering is fixed in a future
    // pre-release, swap sendCompassMessage() back to updateOrShowHud() and
    // restore the HyUIHud field in PlayerTrackingState.

    @SuppressWarnings("unused")
    private void updateOrShowHud(PlayerTrackingState state, IntensityTier tier, HyUIHud[] hudHolder) {
        try {
            if (hudHolder[0] == null || state.currentTier != tier) {
                if (hudHolder[0] != null) {
                    try { hudHolder[0].remove(); } catch (Exception ignored) {}
                    hudHolder[0] = null;
                }
                String html = buildHudHtml(tier.text, tier.color);
                hudHolder[0] = ((HudBuilder) HudBuilder.hudForPlayer(state.playerRef)
                        .fromHtml(html))
                        .show();
            }
        } catch (NoClassDefFoundError | Exception e) {
            ((HytaleLogger.Api) LOGGER.atWarning()).log("[HyRifts-Compass] HUD error: %s", e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private String buildHudHtml(String text, String color) {
        return String.format("""
                <style>
                    #rift-container {
                        anchor-left: 50%%;
                        anchor-bottom: 180;
                        anchor-width: 600;
                        anchor-height: 50;
                        horizontal-align: center;
                    }
                    #rift-text {
                        font-size: 22;
                        text-align: center;
                        anchor-width: 100%%;
                        anchor-height: 50;
                        vertical-align: center;
                    }
                </style>
                <div id="rift-container">
                    <p id="rift-text"><span data-hyui-color="%s">%s</span></p>
                </div>
                """, color, text);
    }
}
