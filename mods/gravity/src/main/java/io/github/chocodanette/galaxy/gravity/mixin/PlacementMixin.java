package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;
import io.github.chocodanette.galaxy.gravity.TiltedPlacement;

/** Directions blocks use when placed ("nearest looking direction") follow a tilted player's real view. */
@Mixin(Direction.class)
public abstract class PlacementMixin {
    @Inject(method = "orderedByNearest", at = @At("HEAD"), cancellable = true)
    private static void galaxy$tiltedLook(Entity entity, CallbackInfoReturnable<Direction[]> cir) {
        GravityFrame frame = GravityFrames.tilted(entity);
        if (frame != null) {
            cir.setReturnValue(TiltedPlacement.byLook(entity.getViewVector(1.0F)));
        }
    }

    @Inject(method = "getFacingAxis", at = @At("HEAD"), cancellable = true)
    private static void galaxy(Entity entity, Direction.Axis axis, CallbackInfoReturnable<Direction> cir) {
        GravityFrame frame = GravityFrames.tilted(entity);
        if (frame != null) {
            double along = entity.getViewVector(1.0F).get(axis);
            cir.setReturnValue(Direction.fromAxisAndDirection(axis,
                    along >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE));
        }
    }
}
