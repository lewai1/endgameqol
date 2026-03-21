package endgame.plugin.ui.snake;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Pure snake game logic with direction queue, bonus food, and progressive speed.
 */
public class SnakeGame {

    public static final int GRID_SIZE = 20;
    public static final int INITIAL_LENGTH = 3;
    private static final int MAX_QUEUED_DIRECTIONS = 3;
    private static final int BONUS_FOOD_DURATION = 30; // ticks before bonus food vanishes
    private static final int BONUS_FOOD_CHANCE = 15;   // percent chance after eating food
    private static final int BONUS_FOOD_SCORE = 30;

    public static final int EMPTY = 0;
    public static final int SNAKE_BODY = 1;
    public static final int SNAKE_HEAD = 2;
    public static final int FOOD = 3;
    public static final int BONUS_FOOD = 4;

    public static final int UP = 0;
    public static final int DOWN = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

    private final int[][] grid = new int[GRID_SIZE][GRID_SIZE];
    private final Deque<int[]> snake = new ArrayDeque<>();
    private final Deque<Integer> directionQueue = new ArrayDeque<>();
    private final Random random = new Random();

    private int direction = RIGHT;
    private int foodX, foodY;
    private int bonusFoodX = -1, bonusFoodY = -1;
    private int bonusFoodTimer;
    private int score;
    private int tickCount;
    private boolean gameOver;

    public SnakeGame() {
        reset();
    }

    public void reset() {
        for (int[] row : grid) Arrays.fill(row, EMPTY);
        snake.clear();
        directionQueue.clear();

        int startX = GRID_SIZE / 2;
        int startY = GRID_SIZE / 2;
        for (int i = 0; i < INITIAL_LENGTH; i++) {
            int x = startX - i;
            snake.addLast(new int[]{x, startY});
            grid[startY][x] = (i == 0) ? SNAKE_HEAD : SNAKE_BODY;
        }

        direction = RIGHT;
        score = 0;
        tickCount = 0;
        gameOver = false;
        bonusFoodX = -1;
        bonusFoodY = -1;
        bonusFoodTimer = 0;
        spawnFood();
    }

    public void setDirection(int dir) {
        int lastDir = directionQueue.isEmpty() ? direction : directionQueue.peekLast();
        if ((dir == UP && lastDir != DOWN) ||
            (dir == DOWN && lastDir != UP) ||
            (dir == LEFT && lastDir != RIGHT) ||
            (dir == RIGHT && lastDir != LEFT)) {
            if (dir != lastDir && directionQueue.size() < MAX_QUEUED_DIRECTIONS) {
                directionQueue.addLast(dir);
            }
        }
    }

    /**
     * Advance one tick. Returns list of changed cells: {x, y, newState}.
     */
    public List<int[]> tick() {
        if (gameOver) return List.of();

        tickCount++;

        // Consume one direction from queue
        if (!directionQueue.isEmpty()) {
            direction = directionQueue.pollFirst();
        }

        // Bonus food timeout
        if (bonusFoodTimer > 0) {
            bonusFoodTimer--;
            if (bonusFoodTimer == 0 && bonusFoodX >= 0) {
                grid[bonusFoodY][bonusFoodX] = EMPTY;
                bonusFoodX = -1;
                bonusFoodY = -1;
            }
        }

        List<int[]> changes = new ArrayList<>();

        int[] head = snake.peekFirst();
        int newX = head[0];
        int newY = head[1];

        switch (direction) {
            case UP -> newY--;
            case DOWN -> newY++;
            case LEFT -> newX--;
            case RIGHT -> newX++;
        }

        if (newX < 0 || newX >= GRID_SIZE || newY < 0 || newY >= GRID_SIZE) {
            gameOver = true;
            return List.of();
        }

        if (grid[newY][newX] == SNAKE_BODY) {
            gameOver = true;
            return List.of();
        }

        boolean ateFood = (grid[newY][newX] == FOOD);
        boolean ateBonusFood = (grid[newY][newX] == BONUS_FOOD);

        // Old head becomes body
        grid[head[1]][head[0]] = SNAKE_BODY;

        // New head
        snake.addFirst(new int[]{newX, newY});
        grid[newY][newX] = SNAKE_HEAD;

        if (ateFood) {
            score += 10;
            spawnFood();
            changes.add(new int[]{foodX, foodY, FOOD});
            // Chance to spawn bonus food
            if (bonusFoodX < 0 && random.nextInt(100) < BONUS_FOOD_CHANCE) {
                spawnBonusFood();
                if (bonusFoodX >= 0) {
                    changes.add(new int[]{bonusFoodX, bonusFoodY, BONUS_FOOD});
                }
            }
        } else if (ateBonusFood) {
            score += BONUS_FOOD_SCORE;
            bonusFoodX = -1;
            bonusFoodY = -1;
            bonusFoodTimer = 0;
            // No tail removal = snake grows
        } else {
            // Remove tail
            int[] tail = snake.removeLast();
            grid[tail[1]][tail[0]] = EMPTY;
            changes.add(new int[]{tail[0], tail[1], EMPTY});
        }

        return changes;
    }

    private void spawnFood() {
        List<int[]> emptyCells = getEmptyCells();
        if (emptyCells.isEmpty()) return;
        int[] cell = emptyCells.get(random.nextInt(emptyCells.size()));
        foodX = cell[0];
        foodY = cell[1];
        grid[foodY][foodX] = FOOD;
    }

    private void spawnBonusFood() {
        List<int[]> emptyCells = getEmptyCells();
        if (emptyCells.isEmpty()) return;
        int[] cell = emptyCells.get(random.nextInt(emptyCells.size()));
        bonusFoodX = cell[0];
        bonusFoodY = cell[1];
        grid[bonusFoodY][bonusFoodX] = BONUS_FOOD;
        bonusFoodTimer = BONUS_FOOD_DURATION;
    }

    private List<int[]> getEmptyCells() {
        List<int[]> emptyCells = new ArrayList<>();
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                if (grid[y][x] == EMPTY) emptyCells.add(new int[]{x, y});
            }
        }
        return emptyCells;
    }

    /** Ordered head-first, tail-last. */
    public List<int[]> getSnakePositions() {
        return new ArrayList<>(snake);
    }

    /** Tick delay in ms — gets faster as score increases. Min 60ms. */
    public long getTickDelay() {
        return Math.max(60, 140 - (long)(score / 10));
    }

    public int getScore() { return score; }
    public int getLength() { return snake.size(); }
    public boolean isGameOver() { return gameOver; }
    public int[][] getGrid() { return grid; }
    public int getTickCount() { return tickCount; }
    public boolean hasBonusFood() { return bonusFoodX >= 0; }
    public int getBonusFoodX() { return bonusFoodX; }
    public int getBonusFoodY() { return bonusFoodY; }
    public int getBonusFoodTimer() { return bonusFoodTimer; }
}
