package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The glowing middle of a linked Launch Bloom leaf: pollen drifts off it. */
public class BloomPadBlock extends Block {
    public BloomPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) {
            return;
        }
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 0.0, 0.0, 0.0);
    }
}
