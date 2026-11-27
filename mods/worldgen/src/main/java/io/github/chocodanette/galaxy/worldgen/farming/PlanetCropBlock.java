package io.github.chocodanette.galaxy.worldgen.farming;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A vanilla crop (wheat, carrots, potatoes) that grows out of any side of a block. FACING
 * is the way it grows, away from the {@link PlanetFarmlandBlock} holding it. It copies the
 * vanilla rules along its own "up": it needs light 9 to grow, grows faster on wet farmland
 * and with farmland around it, slower in crowded rows, and bone meal speeds it up. It drops
 * the same items as the vanilla crop.
 */
public class PlanetCropBlock extends Block implements BonemealableBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_7;

    private final Supplier<Item> seed;
    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);

    public PlanetCropBlock(Supplier<Item> seed, Properties properties) {
        super(properties);
        this.seed = seed;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(getAgeProperty(), 0));
        int steps = getMaxAge() + 1;
        for (int age = 0; age < steps; age++) {
            // Same heights as vanilla: 2/16 tall for a fresh crop, 2/16 more per step.
            double height = 2 + age * 2;
            Map<Direction, VoxelShape> turned = Shapes.rotateAll(Block.box(0, 0, 16 - height, 16, 16, 16));
            for (Map.Entry<Direction, VoxelShape> entry : turned.entrySet()) {
                shapes.computeIfAbsent(entry.getKey(), d -> new VoxelShape[steps])[age] = entry.getValue();
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AGE);
    }

    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    public int getMaxAge() {
        return 7;
    }

    public int getAge(BlockState state) {
        return state.getValue(getAgeProperty());
    }

    public boolean isMaxAge(BlockState state) {
        return getAge(state) >= getMaxAge();
    }

    /** A freshly planted crop growing out of this side. */
    public BlockState planted(Direction up) {
        return defaultBlockState().setValue(FACING, up);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING))[getAge(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.AIR || type == PathComputationType.LAND;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction up = state.getValue(FACING);
        BlockState soil = level.getBlockState(pos.relative(up.getOpposite()));
        return soil.getBlock() instanceof PlanetFarmlandBlock && soil.getValue(PlanetFarmlandBlock.FACING) == up;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !isMaxAge(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1) || level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        int age = getAge(state);
        if (age >= getMaxAge()) {
            return;
        }
        float speed = growthSpeed(state, level, pos);
        boolean grows = random.nextInt((int) (25.0F / speed) + 1) == 0;
        if (net.neoforged.neoforge.common.CommonHooks.canCropGrow(level, pos, state, grows)) {
            level.setBlock(pos, state.setValue(getAgeProperty(), age + 1), Block.UPDATE_CLIENTS);
            net.neoforged.neoforge.common.CommonHooks.fireCropGrowPost(level, pos, state);
        }
    }

    /** Vanilla's growth speed, with "below" and "around" measured on this crop's face. */
    static float growthSpeed(BlockState crop, BlockGetter level, BlockPos pos) {
        Direction up = crop.getValue(FACING);
        float speed = 1.0F;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                BlockState soil = level.getBlockState(FaceGrid.offset(pos, up, a, b, -1));
                float soilSpeed = 0.0F;
                if (soil.getBlock() instanceof PlanetFarmlandBlock && soil.getValue(PlanetFarmlandBlock.FACING) == up) {
                    soilSpeed = PlanetFarmlandBlock.moisture(soil) > 0 ? 3.0F : 1.0F;
                }
                if (a != 0 || b != 0) {
                    soilSpeed /= 4.0F;
                }
                speed += soilSpeed;
            }
        }
        Block type = crop.getBlock();
        Direction first = FaceGrid.first(up);
        Direction second = FaceGrid.second(up);
        boolean alongFirst = level.getBlockState(pos.relative(first)).is(type)
                || level.getBlockState(pos.relative(first.getOpposite())).is(type);
        boolean alongSecond = level.getBlockState(pos.relative(second)).is(type)
                || level.getBlockState(pos.relative(second.getOpposite())).is(type);
        if (alongFirst && alongSecond) {
            speed /= 2.0F;
        } else {
            boolean diagonal = false;
            for (int a = -1; a <= 1; a += 2) {
                for (int b = -1; b <= 1; b += 2) {
                    diagonal |= level.getBlockState(FaceGrid.offset(pos, up, a, b, 0)).is(type);
                }
            }
            if (diagonal) {
                speed /= 2.0F;
            }
        }
        return speed;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(seed.get());
    }

    /** The seed item that plants this crop. */
    public Item seed() {
        return seed.get();
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return !isMaxAge(state);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state,
            BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state,
            BonemealSource source) {
        int age = Math.min(getMaxAge(), getAge(state) + bonemealGrowth(random));
        level.setBlock(pos, state.setValue(getAgeProperty(), age), Block.UPDATE_CLIENTS);
    }

    protected int bonemealGrowth(RandomSource random) {
        return Mth.nextInt(random, 2, 5);
    }

    /** Beetroots: same rules, but only 4 steps of growth like vanilla. */
    public static class Beetroot extends PlanetCropBlock {
        public static final IntegerProperty AGE_3 = BlockStateProperties.AGE_3;

        public Beetroot(Supplier<Item> seed, Properties properties) {
            super(seed, properties);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, AGE_3);
        }

        @Override
        protected IntegerProperty getAgeProperty() {
            return AGE_3;
        }

        @Override
        public int getMaxAge() {
            return 3;
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            // Like vanilla beetroots: a third of their growth chances are skipped.
            if (random.nextInt(3) != 0) {
                super.randomTick(state, level, pos, random);
            }
        }

        @Override
        protected int bonemealGrowth(RandomSource random) {
            return random.nextInt(3) == 0 ? 2 : 1;
        }
    }
}
