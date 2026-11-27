package io.github.chocodanette.galaxy.worldgen.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.lighting.LightEngine;

import io.github.chocodanette.galaxy.worldgen.PlanetBlocks;
import io.github.chocodanette.galaxy.worldgen.PlanetPlantBlock;

/**
 * A grass block whose grassy top can point any way, so the sides and underside of a
 * planet look like real grass and not only its top. FACING is the grassy side.
 *
 * It behaves like vanilla grass along its own "up": it dies to dirt when covered, spreads
 * onto nearby dirt that is open on the same side, and bone meal grows small plants on it.
 * Pointing up, the plain vanilla grass block is used instead.
 */
public class PlanetGrassBlock extends DirectionalBlock implements BonemealableBlock {
    public PlanetGrassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    /** Grass pointing this way: vanilla grass when it points up, ours otherwise. */
    public static BlockState facing(Direction up) {
        if (up == Direction.UP) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        return PlanetBlocks.PLANET_GRASS_BLOCK.get().defaultBlockState().setValue(FACING, up);
    }

    /** Like vanilla: grass lives while light can reach its top and no water sits on it. */
    static boolean canStayAlive(BlockState state, LevelReader level, BlockPos pos, Direction up) {
        BlockPos above = pos.relative(up);
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.getFluidState().isFull()) {
            return false;
        }
        return LightEngine.getLightDampeningInto(state, aboveState, up, aboveState.getLightDampening()) < 15;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 3)) {
            return;
        }
        Direction up = state.getValue(FACING);
        if (!canStayAlive(state, level, pos, up)) {
            level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
            return;
        }
        if (level.getMaxLocalRawBrightness(pos.relative(up)) < 9) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            BlockPos target = FaceGrid.offset(pos, up, random.nextInt(3) - 1, random.nextInt(3) - 1,
                    random.nextInt(5) - 3);
            if (!level.getBlockState(target).is(Blocks.DIRT)) {
                continue;
            }
            BlockState grass = facing(up);
            Direction targetUp = up;
            if (canStayAlive(grass, level, target, targetUp)
                    && level.getFluidState(target.relative(targetUp)).isEmpty()) {
                level.setBlockAndUpdate(target, grass);
            }
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return level.getBlockState(pos.relative(state.getValue(FACING))).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state,
            BonemealSource source) {
        return true;
    }

    /** Sprinkles planet grass around, on grass that faces the same way. */
    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
            BonemealSource source) {
        Direction up = state.getValue(FACING);
        BlockState plant = PlanetBlocks.PLANET_GRASS.get().defaultBlockState().setValue(PlanetPlantBlock.FACING, up);
        for (int i = 0; i < 24; i++) {
            BlockPos ground = FaceGrid.offset(pos, up, random.nextInt(5) - 2, random.nextInt(5) - 2, 0);
            BlockPos spot = ground.relative(up);
            BlockState groundState = level.getBlockState(ground);
            boolean grassy = groundState.is(this) && groundState.getValue(FACING) == up;
            if (grassy && level.getBlockState(spot).isAir() && plant.canSurvive(level, spot)) {
                level.setBlock(spot, plant, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public Type getType() {
        return Type.NEIGHBOR_SPREADER;
    }

    @Override
    public BlockPos getParticlePos(BlockPos blockPos) {
        return blockPos;
    }
}
