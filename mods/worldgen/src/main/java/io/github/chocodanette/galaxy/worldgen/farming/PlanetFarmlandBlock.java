package io.github.chocodanette.galaxy.worldgen.farming;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Farmland whose tilled side can point any way, made by using a hoe on the side or
 * underside of a planet. It works like vanilla farmland measured along its own "up":
 * water within 4 blocks on the face keeps it wet, it turns back into dirt when dry
 * and empty or when something solid covers it, and jumping on it tramples it.
 */
public class PlanetFarmlandBlock extends DirectionalBlock {
    public static final IntegerProperty MOISTURE = BlockStateProperties.MOISTURE;
    /** Vanilla farmland is 15/16 tall; here "tall" is along FACING. Built for facing north. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Block.box(0, 0, 1, 16, 16, 16));

    public PlanetFarmlandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOISTURE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOISTURE);
    }

    /** Farmland tilled on this side: vanilla farmland when it points up, ours otherwise. */
    public static BlockState facing(Direction up) {
        if (up == Direction.UP) {
            return Blocks.FARMLAND.defaultBlockState();
        }
        return io.github.chocodanette.galaxy.worldgen.PlanetBlocks.PLANET_FARMLAND.get().defaultBlockState()
                .setValue(FACING, up);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (directionToNeighbour == state.getValue(FACING) && !state.canSurvive(level, pos)) {
            ticks.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.relative(state.getValue(FACING));
        BlockState aboveState = level.getBlockState(above);
        return !aboveState.isSolid() || aboveState.is(BlockTags.MAINTAINS_FARMLAND);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            turnToDirt(null, state, level, pos);
        }
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int moisture = state.getValue(MOISTURE);
        Direction up = state.getValue(FACING);
        if (!isNearWater(level, pos, up)) {
            if (moisture > 0) {
                level.setBlock(pos, state.setValue(MOISTURE, moisture - 1), Block.UPDATE_CLIENTS);
            } else if (!level.getBlockState(pos.relative(up)).is(BlockTags.MAINTAINS_FARMLAND)
                    && !(level.getBlockState(pos.relative(up)).getBlock() instanceof PlanetCropBlock)) {
                turnToDirt(null, state, level, pos);
            }
        } else if (moisture < 7) {
            level.setBlock(pos, state.setValue(MOISTURE, 7), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        if (level instanceof ServerLevel serverLevel
                && net.neoforged.neoforge.common.CommonHooks.onFarmlandTrample(serverLevel, pos, state, fallDistance, entity)) {
            turnToDirt(entity, state, level, pos);
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    /** Back to plain dirt, popping off the crop on top. */
    public static void turnToDirt(@Nullable Entity source, BlockState state, Level level, BlockPos pos) {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        level.setBlockAndUpdate(pos, dirt);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(source, dirt));
    }

    /** Water within 4 blocks along the face, level with the farmland or one block above it. */
    static boolean isNearWater(LevelReader level, BlockPos pos, Direction up) {
        BlockState farmland = level.getBlockState(pos);
        for (int a = -4; a <= 4; a++) {
            for (int b = -4; b <= 4; b++) {
                for (int h = 0; h <= 1; h++) {
                    BlockPos p = FaceGrid.offset(pos, up, a, b, h);
                    if (farmland.canBeHydrated(level, pos, level.getFluidState(p))) {
                        return true;
                    }
                }
            }
        }
        return net.neoforged.neoforge.common.FarmlandWaterManager.hasBlockWaterTicket(level, pos);
    }

    /** How wet this farmland is, 0 to 7. */
    public static int moisture(BlockState state) {
        return state.getValue(MOISTURE);
    }
}
