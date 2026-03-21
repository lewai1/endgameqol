package endgame.plugin.utils;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nullable;

/**
 * Block-aware teleport safety checks for Prisma Daggers vanish/blink.
 * All methods must be called on the world thread (inside world.execute()).
 */
public final class TeleportSafety {

    private TeleportSafety() {}

    private static final double STEP_SIZE = 0.5;
    private static final int VERTICAL_SCAN = 3;
    private static final int BACKSTEP_COUNT = 6;

    /**
     * Check if a block at (x, y, z) is solid. Unloaded/out-of-bounds = true (safe default).
     */
    public static boolean isSolid(World world, int x, int y, int z) {
        if (world == null || y < 0 || y >= 320) return true;
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) return true;
        int localX = ChunkUtil.localCoordinate((long) x);
        int localZ = ChunkUtil.localCoordinate((long) z);
        int blockId = chunk.getBlock(localX, y, localZ);
        BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
        return blockType != null && blockType.getMaterial() == BlockMaterial.Solid;
    }

    /**
     * Check if a 2-tall player can stand at (x, y, z) — feet and head block must be clear.
     */
    public static boolean hasClearance(World world, int x, int y, int z) {
        return !isSolid(world, x, y, z) && !isSolid(world, x, y + 1, z);
    }

    /**
     * Find a safe position near the target. Tries:
     * 1. Exact target position
     * 2. Vertical scan ±VERTICAL_SCAN blocks (surface search)
     * 3. Backstep along backDirection in STEP_SIZE increments
     *
     * @param world         the world to check blocks in
     * @param target        desired teleport destination
     * @param backDirection direction to step back toward if target is blocked (origin - target, unnormalized is fine)
     * @return centered safe position, or null if no safe spot found
     */
    @Nullable
    public static Vector3d findSafePosition(World world, Vector3d target, Vector3d backDirection) {
        int tx = (int) Math.floor(target.getX());
        int ty = (int) Math.floor(target.getY());
        int tz = (int) Math.floor(target.getZ());

        // Try exact position
        if (hasClearance(world, tx, ty, tz)) {
            return centered(tx, ty, tz);
        }

        // Vertical scan: try above then below
        for (int dy = 1; dy <= VERTICAL_SCAN; dy++) {
            if (hasClearance(world, tx, ty + dy, tz)) {
                return centered(tx, ty + dy, tz);
            }
            if (hasClearance(world, tx, ty - dy, tz)) {
                return centered(tx, ty - dy, tz);
            }
        }

        // Backstep along backDirection
        double len = Math.sqrt(backDirection.getX() * backDirection.getX()
                + backDirection.getZ() * backDirection.getZ());
        if (len < 0.001) return null;

        double ndx = backDirection.getX() / len;
        double ndz = backDirection.getZ() / len;

        for (int step = 1; step <= BACKSTEP_COUNT; step++) {
            double dist = step * STEP_SIZE;
            int bx = (int) Math.floor(target.getX() + ndx * dist);
            int bz = (int) Math.floor(target.getZ() + ndz * dist);

            // Try at target Y, then scan vertically
            if (hasClearance(world, bx, ty, bz)) {
                return centered(bx, ty, bz);
            }
            for (int dy = 1; dy <= 2; dy++) {
                if (hasClearance(world, bx, ty + dy, bz)) {
                    return centered(bx, ty + dy, bz);
                }
                if (hasClearance(world, bx, ty - dy, bz)) {
                    return centered(bx, ty - dy, bz);
                }
            }
        }

        return null;
    }

    /**
     * Walk along yaw direction from origin, returning the furthest safe position.
     * Follows terrain slopes by scanning ±2 blocks vertically at each step.
     *
     * @param world   the world to check blocks in
     * @param origin  starting position
     * @param yawRad  direction in radians (Hytale convention: sin=X, -cos=Z)
     * @param maxDist maximum blink distance
     * @return furthest safe position along the path, or null if first step is blocked
     */
    @Nullable
    public static Vector3d stepAlongPath(World world, Vector3d origin, double yawRad, double maxDist) {
        double dx = Math.sin(yawRad);
        double dz = -Math.cos(yawRad);

        Vector3d lastSafe = null;
        int currentY = (int) Math.floor(origin.getY());
        int steps = (int) Math.ceil(maxDist / STEP_SIZE);

        for (int step = 1; step <= steps; step++) {
            double dist = step * STEP_SIZE;
            if (dist > maxDist) dist = maxDist;

            int sx = (int) Math.floor(origin.getX() + dx * dist);
            int sz = (int) Math.floor(origin.getZ() + dz * dist);

            // Try at current tracked Y
            if (hasClearance(world, sx, currentY, sz)) {
                lastSafe = centered(sx, currentY, sz);
                continue;
            }

            // Slope following: scan ±2 vertically
            boolean found = false;
            for (int dy = 1; dy <= 2; dy++) {
                if (hasClearance(world, sx, currentY + dy, sz)) {
                    currentY = currentY + dy;
                    lastSafe = centered(sx, currentY, sz);
                    found = true;
                    break;
                }
                if (hasClearance(world, sx, currentY - dy, sz)) {
                    currentY = currentY - dy;
                    lastSafe = centered(sx, currentY, sz);
                    found = true;
                    break;
                }
            }

            // Hit a wall — stop here
            if (!found) break;
        }

        return lastSafe;
    }

    private static Vector3d centered(int x, int y, int z) {
        return new Vector3d(x + 0.5, y, z + 0.5);
    }
}
