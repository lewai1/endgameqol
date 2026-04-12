package endgame.plugin.ui.snake;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Snake minigame page — arcade machine easter egg.
 * Features: body gradient, progressive speed, bonus food, food pulse, high score.
 */
public class SnakeGamePage extends InteractiveCustomUIPage<SnakeEventData> {

    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "EndgameQoL-Snake");
                t.setDaemon(true);
                return t;
            });

    private static final long START_DELAY_MS = 2000;

    private final SnakeGame game = new SnakeGame();
    private volatile ScheduledFuture<?> tickTask;
    private volatile boolean started;
    private volatile int lastProcessedLength;
    private volatile int highScore;

    public SnakeGamePage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, SnakeEventData.CODEC);
    }

    // --- Color engine ---

    /** Snake body gradient: bright green (#33ff66) at head → dark green (#004411) at tail. */
    private static String bodyColor(int index, int length) {
        if (index == 0) return "#33ff66";
        float ratio = (float) index / Math.max(1, length - 1);
        int g = (int) (0xff * (1f - ratio) + 0x44 * ratio);
        int b = (int) (0x41 * (1f - ratio) + 0x11 * ratio);
        return String.format("#00%02x%02x", g, b);
    }

    /** Food color pulses between red shades. */
    private static String foodColor(int tick) {
        return (tick % 4 < 2) ? "#ff3333" : "#ff5555";
    }

    /** Bonus food pulses gold/yellow. */
    private static String bonusFoodColor(int tick) {
        return (tick % 4 < 2) ? "#ffaa00" : "#ffdd33";
    }

    // --- Page lifecycle ---

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/SnakeGame.ui");

        // Set initial grid colors
        int[][] grid = game.getGrid();
        for (int y = 0; y < SnakeGame.GRID_SIZE; y++) {
            for (int x = 0; x < SnakeGame.GRID_SIZE; x++) {
                int cell = grid[y][x];
                String color = switch (cell) {
                    case SnakeGame.FOOD -> "#ff3333";
                    default -> "#111118";
                };
                cmd.set("#Cx" + x + "y" + y + ".Background", color);
            }
        }

        // Render initial snake with gradient
        List<int[]> positions = game.getSnakePositions();
        for (int i = 0; i < positions.size(); i++) {
            int[] pos = positions.get(i);
            cmd.set("#Cx" + pos[0] + "y" + pos[1] + ".Background", bodyColor(i, positions.size()));
        }

        cmd.set("#Score.Text", "Score: 0  |  Best: " + highScore);
        cmd.set("#BtnRetry.Visible", false);

        // "GET READY" countdown
        cmd.set("#GameOverLabel.Text", "GET READY...");
        cmd.set("#GameOverLabel.Visible", true);

        // Button controls
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnUp",
                EventData.of("Direction", "up"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnDown",
                EventData.of("Direction", "down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnLeft",
                EventData.of("Direction", "left"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnRight",
                EventData.of("Direction", "right"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BtnRetry",
                EventData.of("Retry", "true"));

        // Keyboard via TextField
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#KeyInput",
                EventData.of("@KeyCode", "#KeyInput.Value"), false);

        lastProcessedLength = 0;
        startGameLoop();
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull SnakeEventData data) {
        synchronized (game) {
            if (data.direction != null) {
                switch (data.direction.toLowerCase()) {
                    case "up" -> game.setDirection(SnakeGame.UP);
                    case "down" -> game.setDirection(SnakeGame.DOWN);
                    case "left" -> game.setDirection(SnakeGame.LEFT);
                    case "right" -> game.setDirection(SnakeGame.RIGHT);
                }
            }

            if (data.keyCode != null && data.keyCode.length() > lastProcessedLength) {
                for (int i = lastProcessedLength; i < data.keyCode.length(); i++) {
                    switch (Character.toLowerCase(data.keyCode.charAt(i))) {
                        case 'w' -> game.setDirection(SnakeGame.UP);
                        case 's' -> game.setDirection(SnakeGame.DOWN);
                        case 'a' -> game.setDirection(SnakeGame.LEFT);
                        case 'd' -> game.setDirection(SnakeGame.RIGHT);
                    }
                }
                lastProcessedLength = data.keyCode.length();
            }

            if (data.retry != null && data.retry && game.isGameOver()) {
                game.reset();
                started = false;
                lastProcessedLength = 0;
                rebuild();
                startGameLoop();
            }
        }
    }

    // --- Game loop with progressive speed ---

    private void startGameLoop() {
        cancelGameLoop();
        started = false;
        tickTask = SCHEDULER.schedule(this::gameTick, START_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void scheduleNextTick() {
        long delay;
        synchronized (game) {
            delay = game.getTickDelay();
        }
        tickTask = SCHEDULER.schedule(this::gameTick, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelGameLoop() {
        ScheduledFuture<?> task = tickTask;
        if (task != null) {
            task.cancel(false);
            tickTask = null;
        }
    }

    private void gameTick() {
        try {
            // First tick: hide GET READY
            if (!started) {
                started = true;
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#GameOverLabel.Visible", false);
                sendUpdate(cmd, null, false);
                scheduleNextTick();
                return;
            }

            List<int[]> changes;
            boolean isOver;
            int score;
            int tick;
            List<int[]> snakePositions;
            boolean hasBonusFood;
            int bonusX = 0, bonusY = 0;

            synchronized (game) {
                changes = game.tick();
                isOver = game.isGameOver();
                score = game.getScore();
                tick = game.getTickCount();
                snakePositions = game.getSnakePositions();
                hasBonusFood = game.hasBonusFood();
                if (hasBonusFood) {
                    bonusX = game.getBonusFoodX();
                    bonusY = game.getBonusFoodY();
                }
            }

            UICommandBuilder cmd = new UICommandBuilder();

            if (isOver) {
                cancelGameLoop();
                if (score > highScore) highScore = score;

                // Flash snake red on death
                for (int[] pos : snakePositions) {
                    cmd.set("#Cx" + pos[0] + "y" + pos[1] + ".Background", "#661111");
                }

                String msg = score > 0 && score >= highScore
                        ? "NEW BEST!  Score: " + score
                        : "GAME OVER  -  Score: " + score;
                cmd.set("#GameOverLabel.Text", msg);
                cmd.set("#GameOverLabel.Visible", true);
                cmd.set("#BtnRetry.Visible", true);
                cmd.set("#Score.Text", "Score: " + score + "  |  Best: " + highScore);
            } else {
                // Clear removed cells (tail, expired bonus food)
                for (int[] change : changes) {
                    if (change[2] == SnakeGame.EMPTY) {
                        cmd.set("#Cx" + change[0] + "y" + change[1] + ".Background", "#111118");
                    }
                }

                // Render food (new spawns + pulse existing)
                for (int[] change : changes) {
                    if (change[2] == SnakeGame.FOOD) {
                        cmd.set("#Cx" + change[0] + "y" + change[1] + ".Background", foodColor(tick));
                    }
                    if (change[2] == SnakeGame.BONUS_FOOD) {
                        cmd.set("#Cx" + change[0] + "y" + change[1] + ".Background", bonusFoodColor(tick));
                    }
                }

                // Pulse existing bonus food
                if (hasBonusFood) {
                    cmd.set("#Cx" + bonusX + "y" + bonusY + ".Background", bonusFoodColor(tick));
                }

                // Render full snake with gradient (every tick for smooth gradient shift)
                int len = snakePositions.size();
                for (int i = 0; i < len; i++) {
                    int[] pos = snakePositions.get(i);
                    cmd.set("#Cx" + pos[0] + "y" + pos[1] + ".Background", bodyColor(i, len));
                }

                cmd.set("#Score.Text", "Score: " + score + "  |  Best: " + highScore);
            }

            sendUpdate(cmd, null, false);

            // Schedule next tick with progressive speed
            if (!isOver) {
                scheduleNextTick();
            }
        } catch (Exception ignored) {
            // Prevent scheduler death
        }
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        cancelGameLoop();
    }
}
