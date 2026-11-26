package io.github.chocodanette.galaxy.gravity;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Which way water and lava fall on a planet.
 *
 * Every liquid block gets its own "down": the block direction closest to the planet's pull
 * at its middle. On a planet's face that is straight into the face, so a liquid there spreads
 * and falls just like it does on flat ground in vanilla, only turned. The flow rules themselves
 * live in {@code FlowingFluidMixin}; the drawing in {@code FluidRendererMixin}.
 *
 * The server decides where liquids go. Players draw them with the same rule from their synced
 * copy of the planets, so both sides agree without sending anything extra.
 */
public final class PlanetFluids {
    /** Remembered "down"s per thread, dropped whenever the planets change. */
    private static final ThreadLocal<DownCache> CACHE = ThreadLocal.withInitial(DownCache::new);
    private static final int CACHE_LIMIT = 1 << 16;

    /** For each "down", the four directions that are sideways to it. */
    private static final Direction[][] SIDES = new Direction[6][];

    /**
     * For each "down", the rotation that turns vanilla's upright liquid into this one: a 3x3
     * matrix written row by row, so world = M * upright. Each one turns (0, -1, 0) into "down".
     */
    private static final int[][] TURNS = new int[6][];

    static {
        for (Direction down : Direction.values()) {
            SIDES[down.ordinal()] = java.util.Arrays.stream(Direction.values())
                    .filter(d -> d.getAxis() != down.getAxis())
                    .toArray(Direction[]::new);
        }
        TURNS[Direction.DOWN.ordinal()] = new int[] {1, 0, 0, 0, 1, 0, 0, 0, 1};
        TURNS[Direction.UP.ordinal()] = new int[] {1, 0, 0, 0, -1, 0, 0, 0, -1};
        TURNS[Direction.NORTH.ordinal()] = new int[] {1, 0, 0, 0, 0, -1, 0, 1, 0};
        TURNS[Direction.SOUTH.ordinal()] = new int[] {1, 0, 0, 0, 0, 1, 0, -1, 0};
        TURNS[Direction.WEST.ordinal()] = new int[] {0, 1, 0, -1, 0, 0, 0, 0, 1};
        TURNS[Direction.EAST.ordinal()] = new int[] {0, -1, 0, 1, 0, 0, 0, 0, 1};
    }

    private PlanetFluids() {
    }

    /**
     * The planets for a level whose liquids should follow them, or null to leave vanilla
     * liquids alone (no planets there, or a getter that isn't a real level).
     */
    public static @Nullable GravityWorld worldOf(BlockGetter level) {
        GravityWorld world;
        if (level instanceof ServerLevel server) {
            world = PlanetGravityData.get(server).world();
        } else if (level instanceof Level client && client.isClientSide()) {
            world = ClientPlanets.world();
        } else {
            return null;
        }
        return world.fields().isEmpty() ? null : world;
    }

    /** The planets on the player's side, for drawing liquids, or null when there are none. */
    public static @Nullable GravityWorld clientWorld() {
        GravityWorld world = ClientPlanets.world();
        return world.fields().isEmpty() ? null : world;
    }

    /** Which way a liquid in this block falls. */
    public static Direction downAt(GravityWorld world, BlockPos pos) {
        DownCache cache = CACHE.get();
        if (cache.world != world) {
            cache.world = world;
            cache.downs.clear();
        } else if (cache.downs.size() > CACHE_LIMIT) {
            cache.downs.clear();
        }
        long key = pos.asLong();
        byte known = cache.downs.get(key);
        if (known >= 0) {
            return Direction.from3DDataValue(known);
        }
        Direction down = computeDown(world, pos);
        cache.downs.put(key, (byte) down.get3DDataValue());
        return down;
    }

    /** {@link #downAt} without the cache, for tests. */
    static Direction computeDown(GravityWorld world, BlockPos pos) {
        Vec3 down = GravityMath.downDirection(world, Vec3.atCenterOf(pos));
        return Direction.getApproximateNearest(down.x, down.y, down.z);
    }

    /** The four directions a liquid spreads in when it can't fall. */
    public static Direction[] sides(Direction down) {
        return SIDES[down.ordinal()];
    }

    /**
     * The direction vanilla's liquid code should be told about. Water and lava only care
     * whether a move is "down" (lava turns water below it into stone, for one), so a move along
     * the block's own down is DOWN, its opposite is UP, and any sideways move is NORTH.
     */
    public static Direction uprightName(Direction world, Direction down) {
        if (world == down) {
            return Direction.DOWN;
        }
        if (world == down.getOpposite()) {
            return Direction.UP;
        }
        return world.getAxis() == Direction.Axis.Y ? Direction.NORTH : world;
    }

    /** Turns an offset from upright (vanilla) space into the world, for a liquid falling this way. */
    public static int[] turn(Direction down, int x, int y, int z) {
        int[] m = TURNS[down.ordinal()];
        return new int[] {
                m[0] * x + m[1] * y + m[2] * z,
                m[3] * x + m[4] * y + m[5] * z,
                m[6] * x + m[7] * y + m[8] * z};
    }

    /** Same for a float vector (vertex positions and normals). */
    public static float[] turn(Direction down, float x, float y, float z) {
        int[] m = TURNS[down.ordinal()];
        return new float[] {
                m[0] * x + m[1] * y + m[2] * z,
                m[3] * x + m[4] * y + m[5] * z,
                m[6] * x + m[7] * y + m[8] * z};
    }

    /** A block position seen from upright space around {@code origin}, in the world. */
    public static BlockPos turnAround(Direction down, BlockPos origin, BlockPos upright) {
        int[] d = turn(down, upright.getX() - origin.getX(), upright.getY() - origin.getY(),
                upright.getZ() - origin.getZ());
        return origin.offset(d[0], d[1], d[2]);
    }

    private static final class DownCache {
        private GravityWorld world;
        private final Long2ByteOpenHashMap downs = new Long2ByteOpenHashMap();

        private DownCache() {
            downs.defaultReturnValue((byte) -1);
        }
    }
}
