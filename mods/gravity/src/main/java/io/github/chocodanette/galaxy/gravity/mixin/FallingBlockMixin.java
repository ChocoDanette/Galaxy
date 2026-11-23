package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityMath;
import io.github.chocodanette.galaxy.gravity.PlanetGravityData;

/**
 * Sand and gravel on a planet only fall when there is space toward the planet, not toward
 * world down. Otherwise sand on a planet's side would fall off forever.
 */
@Mixin(FallingBlock.class)
public abstract class FallingBlockMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;below()Lnet/minecraft/core/BlockPos;"))
    private BlockPos galaxy$towardPlanet(BlockPos pos, BlockState state, ServerLevel level, BlockPos tickPos, RandomSource random) {
        var pull = GravityMath.pull(PlanetGravityData.get(level).world(), Vec3.atCenterOf(pos), Double.NaN);
        if (pull.isEmpty()) {
            return pos.below();
        }
        Vec3 down = pull.get().down();
        return pos.relative(Direction.getApproximateNearest(down.x, down.y, down.z));
    }
}
