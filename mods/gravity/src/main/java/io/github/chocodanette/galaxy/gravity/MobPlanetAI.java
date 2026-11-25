package io.github.chocodanette.galaxy.gravity;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Glue between vanilla mob AI and planet gravity: which face a mob walks on, what the world
 * looks like to {@link PlanetPathfinder}, and where a mob wanders to.
 *
 * Mobs standing upright (on top of a planet, or off-planet) never reach this: vanilla AI
 * already works for them.
 */
public final class MobPlanetAI {
    /** How many random spots a wandering mob tries before giving up, like vanilla. */
    private static final int WANDER_TRIES = 10;

    private MobPlanetAI() {
    }

    /**
     * The world axis a mob's body stands along when it walks on a planet's side or underside,
     * or null when it is upright and vanilla AI should run.
     */
    public static @Nullable Direction walkingUp(Mob mob) {
        GravityFrame frame = GravityFrames.tilted(mob);
        if (frame == null) {
            return null;
        }
        Direction axis = GravityFrames.boxAxis(frame);
        return axis == Direction.UP ? null : axis;
    }

    /** How many cells tall the mob is. */
    public static int heightInCells(Mob mob) {
        return Math.max(1, Mth.ceil(mob.getBbHeight() - 1.0E-3));
    }

    /** The cell the mob's body starts in (its feet), on any face. */
    public static BlockPos feetCell(Mob mob, Direction up) {
        Vec3 u = up.getUnitVec3();
        return BlockPos.containing(mob.position().add(u.scale(0.5)));
    }

    /** The point a mob's feet should reach to stand in this cell. */
    public static Vec3 standingPoint(BlockPos cell, Direction up) {
        return Vec3.atCenterOf(cell).subtract(up.getUnitVec3().scale(0.5));
    }

    /** The world as the pathfinder sees it. */
    public static PlanetPathfinder.Cells cells(Level level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return new PlanetPathfinder.Cells() {
            @Override
            public boolean passable(int x, int y, int z) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (harmful(state) || state.getFluidState().is(FluidTags.LAVA)) {
                    return false;
                }
                return state.getCollisionShape(level, cursor).isEmpty();
            }

            @Override
            public boolean floor(int x, int y, int z) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (harmful(state)) {
                    return false;
                }
                return !state.getCollisionShape(level, cursor).isEmpty()
                        || state.getFluidState().is(FluidTags.WATER);
            }
        };
    }

    private static boolean harmful(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE) || state.is(BlockTags.CAMPFIRES);
    }

    /** Most cells the search may look at for this mob, from its follow range like vanilla. */
    public static int maxVisited(Mob mob) {
        double range = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        return Mth.clamp(Mth.floor(range * 16.0), 200, 2000);
    }

    /** Finds a path on the mob's face, or null. */
    public static PlanetPathfinder.@Nullable Result findPath(Mob mob, Direction up, Set<BlockPos> targets, int reachRange) {
        int height = heightInCells(mob);
        PlanetPathfinder.Cells cells = cells(mob.level());
        BlockPos start = feetCell(mob, up);
        if (!PlanetPathfinder.standable(cells, start, up, height)) {
            // Standing on a slab or half in the air: try the cell the feet are in.
            BlockPos inside = BlockPos.containing(mob.position().add(up.getUnitVec3().scale(0.1)));
            if (PlanetPathfinder.standable(cells, inside, up, height)) {
                start = inside;
            }
        }
        return PlanetPathfinder.find(cells, start, up, height, targets, reachRange, maxVisited(mob));
    }

    /**
     * A random spot to walk to on the mob's face, like vanilla's random strolling: within
     * {@code flatRange} blocks along the face and {@code upRange} blocks up or down. With a
     * {@code direction}, only spots on that side of the mob count (fleeing or heading
     * somewhere). Returns null if no spot was found.
     */
    public static @Nullable Vec3 wanderTarget(PathfinderMob mob, Direction up, int flatRange, int upRange,
            @Nullable Vec3 direction) {
        RandomSource random = mob.getRandom();
        Direction[] flat = PlanetPathfinder.flatDirections(up);
        // flat[] holds two axes, each in both directions; pick one per axis.
        Direction a = flat[0];
        Direction b = null;
        for (Direction d : flat) {
            if (d.getAxis() != a.getAxis()) {
                b = d;
                break;
            }
        }
        PlanetPathfinder.Cells cells = cells(mob.level());
        int height = heightInCells(mob);
        BlockPos feet = feetCell(mob, up);
        Vec3 bestSpot = null;
        double bestWeight = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < WANDER_TRIES; i++) {
            int da = random.nextInt(2 * flatRange + 1) - flatRange;
            int db = random.nextInt(2 * flatRange + 1) - flatRange;
            int du = random.nextInt(2 * upRange + 1) - upRange;
            BlockPos guess = feet.relative(a, da).relative(b, db).relative(up, du);
            if (direction != null && Vec3.atCenterOf(guess).subtract(mob.position()).dot(direction) <= 0) {
                continue;
            }
            BlockPos spot = settle(cells, guess, up, height, upRange);
            if (spot == null || mob.hasHome() && !mob.isWithinHome(spot)) {
                continue;
            }
            double weight = mob.getWalkTargetValue(spot);
            if (weight > bestWeight) {
                bestWeight = weight;
                bestSpot = standingPoint(spot, up);
            }
        }
        return bestSpot;
    }

    /**
     * For the random-spot mixins: when the mob walks on a planet's side, answers with a spot
     * on its face (or none) and returns true; otherwise leaves vanilla alone.
     */
    public static boolean replaceRandomPos(PathfinderMob mob, int flatRange, int upRange, @Nullable Vec3 direction,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<@Nullable Vec3> cir) {
        Direction up = walkingUp(mob);
        if (up == null) {
            return false;
        }
        cir.setReturnValue(wanderTarget(mob, up, Math.max(1, flatRange), Math.max(1, upRange), direction));
        return true;
    }

    /** Moves a guessed cell up out of the ground, then down onto it, along "up". */
    private static @Nullable BlockPos settle(PlanetPathfinder.Cells cells, BlockPos guess, Direction up, int height,
            int range) {
        BlockPos pos = guess;
        int moved = 0;
        while (!cells.passable(pos.getX(), pos.getY(), pos.getZ())) {
            if (++moved > range + 2) {
                return null;
            }
            pos = pos.relative(up);
        }
        moved = 0;
        while (!PlanetPathfinder.standable(cells, pos, up, height)) {
            BlockPos below = pos.relative(up, -1);
            if (++moved > range + 4 || !cells.passable(below.getX(), below.getY(), below.getZ())) {
                return PlanetPathfinder.standable(cells, pos, up, height) ? pos : null;
            }
            pos = below;
        }
        return pos;
    }
}
