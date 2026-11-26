package io.github.chocodanette.galaxy.gravity.mixin;

import java.util.EnumMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import io.github.chocodanette.galaxy.gravity.GravityWorld;
import io.github.chocodanette.galaxy.gravity.PlanetFluids;

/**
 * Water and lava that flow toward the planet instead of toward world down.
 *
 * Vanilla's rules are kept, only turned: each liquid block falls along its own "down"
 * ({@link PlanetFluids#downAt}) and spreads over the four directions sideways to it. Where two
 * blocks next to each other fall different ways (the edge of a planet face), a block takes
 * liquid from a neighbor exactly when that neighbor would spread into it by the neighbor's own
 * rule, so a stream poured over an edge stays and a cut-off stream still dries up.
 *
 * Only dimensions with planets are changed; everywhere else vanilla runs untouched.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Unique
    private static final float GALAXY$MAX_HEIGHT = 0.8888889F;

    @Shadow
    protected abstract int getDropOff(LevelReader level);

    @Shadow
    protected abstract int getSlopeFindDistance(LevelReader level);

    @Shadow
    public abstract Fluid getFlowing();

    @Shadow
    public abstract FluidState getFlowing(int amount, boolean falling);

    @Shadow
    public abstract FluidState getSource(boolean falling);

    @Shadow
    protected abstract void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction,
            FluidState target);

    @Shadow
    protected abstract int getSpreadDelay(Level level, BlockPos pos, FluidState oldFluidState,
            FluidState newFluidState);

    @Shadow
    private boolean canMaybePassThrough(BlockGetter level, BlockPos sourcePos, BlockState sourceState,
            Direction direction, BlockPos testPos, BlockState testState, FluidState testFluidState) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isSourceBlockOfThisType(FluidState state) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean canPassThroughWall(Direction direction, BlockGetter level, BlockPos sourcePos,
            BlockState sourceState, BlockPos targetPos, BlockState targetState) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean canHoldFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid newFluid) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean canHoldSpecificFluid(BlockGetter level, BlockPos pos, BlockState state, Fluid newFluid) {
        throw new AssertionError();
    }

    @Unique
    private Fluid galaxy$self() {
        return (Fluid) (Object) this;
    }

    @Unique
    private boolean galaxy$same(FluidState state) {
        return state.getType().isSame(galaxy$self());
    }

    // ---------------------------------------------------------------- ticking (server)

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void galaxy$tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState,
            CallbackInfo ci) {
        GravityWorld world = PlanetFluids.worldOf(level);
        if (world == null) {
            return;
        }
        ci.cancel();
        if (!fluidState.isSource()) {
            FluidState newState = galaxy$newLiquid(level, world, pos, level.getBlockState(pos));
            int tickDelay = getSpreadDelay(level, pos, fluidState, newState);
            if (newState.isEmpty()) {
                fluidState = newState;
                blockState = Blocks.AIR.defaultBlockState();
                level.setBlockAndUpdate(pos, blockState);
            } else if (newState != fluidState) {
                fluidState = newState;
                blockState = fluidState.createLegacyBlock();
                level.setBlockAndUpdate(pos, blockState);
                level.scheduleTick(pos, fluidState.getType(), tickDelay);
            }
        }
        galaxy$spread(level, world, pos, blockState, fluidState);
    }

    @Unique
    private void galaxy$spread(ServerLevel level, GravityWorld world, BlockPos pos, BlockState state,
            FluidState fluidState) {
        if (fluidState.isEmpty()) {
            return;
        }
        Direction down = PlanetFluids.downAt(world, pos);
        BlockPos belowPos = pos.relative(down);
        BlockState belowState = level.getBlockState(belowPos);
        FluidState belowFluid = belowState.getFluidState();
        if (canMaybePassThrough(level, pos, state, down, belowPos, belowState, belowFluid)) {
            FluidState newBelow = galaxy$newLiquid(level, world, belowPos, belowState);
            Fluid newBelowType = newBelow.getType();
            if (!newBelow.isEmpty()
                    && belowFluid.canBeReplacedWith(level, belowPos, newBelowType, Direction.DOWN)
                    && canHoldSpecificFluid(level, belowPos, belowState, newBelowType)) {
                spreadTo(level, belowPos, belowState, Direction.DOWN, newBelow);
                if (galaxy$sourceNeighbors(level, pos, down) >= 3) {
                    galaxy$spreadToSides(level, world, pos, fluidState, state, down);
                }
                return;
            }
        }
        if (fluidState.isSource() || !galaxy$isHole(level, pos, state, down)) {
            galaxy$spreadToSides(level, world, pos, fluidState, state, down);
        }
    }

    @Unique
    private void galaxy$spreadToSides(ServerLevel level, GravityWorld world, BlockPos pos, FluidState fluidState,
            BlockState state, Direction down) {
        int neighbor = fluidState.getAmount() - getDropOff(level);
        if (fluidState.getValue(FlowingFluid.FALLING)) {
            neighbor = 7;
        }
        if (neighbor <= 0) {
            return;
        }
        for (Map.Entry<Direction, FluidState> entry : galaxy$getSpread(level, world, pos, state, down).entrySet()) {
            BlockPos neighborPos = pos.relative(entry.getKey());
            spreadTo(level, neighborPos, level.getBlockState(neighborPos),
                    PlanetFluids.uprightName(entry.getKey(), down), entry.getValue());
        }
    }

    /** Vanilla's getSpread, turned: the sideways moves that lead soonest to a drop. */
    @Unique
    private Map<Direction, FluidState> galaxy$getSpread(ServerLevel level, GravityWorld world, BlockPos pos,
            BlockState state, Direction down) {
        int lowest = 1000;
        Map<Direction, FluidState> result = new EnumMap<>(Direction.class);
        for (Direction direction : PlanetFluids.sides(down)) {
            BlockPos testPos = pos.relative(direction);
            BlockState testState = level.getBlockState(testPos);
            FluidState testFluid = testState.getFluidState();
            if (!canMaybePassThrough(level, pos, state, direction, testPos, testState, testFluid)) {
                continue;
            }
            FluidState newFluid = galaxy$newLiquid(level, world, testPos, testState);
            if (newFluid.isEmpty() || !canHoldSpecificFluid(level, testPos, testState, newFluid.getType())) {
                continue;
            }
            int distance = galaxy$isHole(level, testPos, testState, down)
                    ? 0
                    : galaxy$slopeDistance(level, testPos, 1, direction.getOpposite(), testState, down);
            if (distance < lowest) {
                result.clear();
            }
            if (distance <= lowest) {
                if (testFluid.canBeReplacedWith(level, testPos, newFluid.getType(),
                        PlanetFluids.uprightName(direction, down))) {
                    result.put(direction, newFluid);
                }
                lowest = distance;
            }
        }
        return result;
    }

    @Unique
    private int galaxy$slopeDistance(LevelReader level, BlockPos pos, int pass, Direction from, BlockState state,
            Direction down) {
        int lowest = 1000;
        for (Direction direction : PlanetFluids.sides(down)) {
            if (direction == from) {
                continue;
            }
            BlockPos testPos = pos.relative(direction);
            BlockState testState = level.getBlockState(testPos);
            FluidState testFluid = testState.getFluidState();
            if (!canMaybePassThrough(level, pos, state, direction, testPos, testState, testFluid)
                    || !canHoldSpecificFluid(level, testPos, testState, getFlowing())) {
                continue;
            }
            if (galaxy$isHole(level, testPos, testState, down)) {
                return pass;
            }
            if (pass < getSlopeFindDistance(level)) {
                lowest = Math.min(lowest,
                        galaxy$slopeDistance(level, testPos, pass + 1, direction.getOpposite(), testState, down));
            }
        }
        return lowest;
    }

    /** Whether liquid at this spot could fall further (vanilla's isWaterHole, turned). */
    @Unique
    private boolean galaxy$isHole(BlockGetter level, BlockPos pos, BlockState state, Direction down) {
        BlockPos belowPos = pos.relative(down);
        BlockState belowState = level.getBlockState(belowPos);
        if (!canPassThroughWall(down, level, pos, state, belowPos, belowState)) {
            return false;
        }
        return galaxy$same(belowState.getFluidState()) || canHoldFluid(level, belowPos, belowState, getFlowing());
    }

    @Unique
    private int galaxy$sourceNeighbors(LevelReader level, BlockPos pos, Direction down) {
        int count = 0;
        for (Direction direction : PlanetFluids.sides(down)) {
            if (isSourceBlockOfThisType(level.getFluidState(pos.relative(direction)))) {
                count++;
            }
        }
        return count;
    }

    /**
     * What liquid a block should hold, from what its neighbors feed it. A neighbor feeds it
     * when, by the neighbor's own down, the block is beside it (spreading) or under it
     * (falling). Falling keeps the full amount only between blocks that fall the same way;
     * anything else loses some on the way, so liquid can never feed itself in a loop.
     */
    @Unique
    private FluidState galaxy$newLiquid(ServerLevel level, GravityWorld world, BlockPos pos, BlockState state) {
        Direction down = PlanetFluids.downAt(world, pos);
        int highest = 0;
        int sources = 0;
        boolean falling = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = cursor.setWithOffset(pos, direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            FluidState neighborFluid = neighborState.getFluidState();
            if (!galaxy$same(neighborFluid)) {
                continue;
            }
            Direction neighborDown = PlanetFluids.downAt(world, neighborPos);
            Direction toward = direction.getOpposite();
            if (toward == neighborDown.getOpposite()) {
                // This block is above the neighbor: liquid doesn't climb.
                continue;
            }
            if (!canPassThroughWall(direction, level, pos, state, neighborPos, neighborState)) {
                continue;
            }
            if (toward == neighborDown && neighborDown == down) {
                falling = true;
                continue;
            }
            if (neighborFluid.isSource() && EventHooks.canCreateFluidSource(level, neighborPos, neighborState)) {
                sources++;
            }
            highest = Math.max(highest, neighborFluid.getAmount());
        }
        if (sources >= 2) {
            BlockState belowState = level.getBlockState(cursor.setWithOffset(pos, down));
            if (belowState.isSolid() || isSourceBlockOfThisType(belowState.getFluidState())) {
                return getSource(false);
            }
        }
        if (falling) {
            return getFlowing(8, true);
        }
        int amount = highest - getDropOff(level);
        return amount <= 0 ? Fluids.EMPTY.defaultFluidState() : getFlowing(amount, false);
    }

    // ---------------------------------------------------------------- flow and height (both sides)

    /** The current that pushes swimmers and items, along the planet face. */
    @Inject(method = "getFlow", at = @At("HEAD"), cancellable = true)
    private void galaxy$getFlow(BlockGetter level, BlockPos pos, FluidState fluidState,
            CallbackInfoReturnable<Vec3> cir) {
        GravityWorld world = PlanetFluids.worldOf(level);
        if (world == null) {
            return;
        }
        Direction down = PlanetFluids.downAt(world, pos);
        if (down == Direction.DOWN) {
            return;
        }
        Direction up = down.getOpposite();
        float own = fluidState.getOwnHeight();
        Vec3 flow = Vec3.ZERO;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : PlanetFluids.sides(down)) {
            cursor.setWithOffset(pos, direction);
            FluidState neighbor = level.getFluidState(cursor);
            if (!neighbor.isEmpty() && !galaxy$same(neighbor)) {
                continue;
            }
            float height = neighbor.getOwnHeight();
            float distance = 0.0F;
            if (height == 0.0F) {
                if (!level.getBlockState(cursor).is(BlockTags.BLOCKS_FLUID_FLOW)) {
                    FluidState under = level.getFluidState(cursor.relative(down));
                    if (under.isEmpty() || galaxy$same(under)) {
                        height = under.getOwnHeight();
                        if (height > 0.0F) {
                            distance = own - (height - GALAXY$MAX_HEIGHT);
                        }
                    }
                }
            } else if (height > 0.0F) {
                distance = own - height;
            }
            if (distance != 0.0F) {
                flow = flow.add(direction.getStepX() * distance, direction.getStepY() * distance,
                        direction.getStepZ() * distance);
            }
        }
        if (fluidState.getValue(FlowingFluid.FALLING)) {
            for (Direction direction : PlanetFluids.sides(down)) {
                BlockPos side = pos.relative(direction);
                if (galaxy$solidFace(level, side, direction) || galaxy$solidFace(level, side.relative(up), direction)) {
                    flow = flow.normalize().add(down.getStepX() * 6.0, down.getStepY() * 6.0, down.getStepZ() * 6.0);
                    break;
                }
            }
        }
        cir.setReturnValue(flow.normalize());
    }

    @Unique
    private boolean galaxy$solidFace(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState state = level.getBlockState(pos);
        if (galaxy$same(state.getFluidState())) {
            return false;
        }
        return !(state.getBlock() instanceof IceBlock) && state.isFaceSturdy(level, pos, direction);
    }

    /** A liquid block with more of it on its planet-up side is full to the brim. */
    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void galaxy$getHeight(FluidState fluidState, BlockGetter level, BlockPos pos,
            CallbackInfoReturnable<Float> cir) {
        GravityWorld world = PlanetFluids.worldOf(level);
        if (world == null) {
            return;
        }
        Direction down = PlanetFluids.downAt(world, pos);
        if (down == Direction.DOWN) {
            return;
        }
        boolean sameAbove = galaxy$same(level.getFluidState(pos.relative(down.getOpposite())));
        cir.setReturnValue(sameAbove ? 1.0F : fluidState.getOwnHeight());
    }
}
