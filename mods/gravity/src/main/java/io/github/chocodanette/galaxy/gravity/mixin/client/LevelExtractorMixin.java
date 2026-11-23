package io.github.chocodanette.galaxy.gravity.mixin.client;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * The "head inside a block" overlay tests points around the eyes, measured along world up
 * from the feet. On a planet's side or underside those points land inside the planet, which
 * covered the screen with the block's texture. Test around the real, tilted eyes instead.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "getViewBlockingStateAndPos", at = @At("HEAD"), cancellable = true)
    private static void galaxy$tiltedViewBlocking(LocalPlayer player, Frustum frustum,
            CallbackInfoReturnable<Pair<BlockState, BlockPos>> cir) {
        GravityFrame frame = GravityFrames.tilted(player);
        if (frame == null) {
            return;
        }
        if (player.noPhysics) {
            cir.setReturnValue(null);
            return;
        }
        Vec3 eyes = player.getEyePosition();
        AABB nearPlane = frustum.getNearPlaneBounds().move(eyes);
        double side = player.getBbWidth() * 0.8;
        double vertical = 0.1 * player.getScale();
        for (int i = 0; i < 8; i++) {
            Vec3 local = new Vec3(
                    ((i >> 0) % 2 - 0.5) * side,
                    ((i >> 1) % 2 - 0.5) * vertical,
                    ((i >> 2) % 2 - 0.5) * side);
            BlockPos pos = BlockPos.containing(eyes.add(frame.toWorld(local)));
            BlockState state = player.level().getBlockState(pos);
            if (state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(player.level(), pos, nearPlane)) {
                cir.setReturnValue(Pair.of(state, pos));
                return;
            }
        }
        cir.setReturnValue(null);
    }
}
