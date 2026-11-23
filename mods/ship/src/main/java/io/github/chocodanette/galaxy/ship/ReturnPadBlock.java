package io.github.chocodanette.galaxy.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin glowing pad placed where players land. Right-click it to beam back up to the ship.
 * It sends sparks up so it's easy to find again from across the planet.
 */
public final class ReturnPadBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public ReturnPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ShipCrew.beamAboard(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.2,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    0.0, 0.12 + random.nextDouble() * 0.08, 0.0);
        }
    }
}
