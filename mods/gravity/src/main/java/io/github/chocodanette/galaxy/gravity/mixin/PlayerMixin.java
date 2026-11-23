package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Vanilla picks standing, crouching or crawling by checking whether an upright box fits.
 * On a planet's side the upright box pokes into the planet, so check the tilted box instead.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "canPlayerFitWithinBlocksAndEntitiesWhen", at = @At("HEAD"), cancellable = true)
    private void galaxy$fitTiltedBox(Pose pose, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        GravityFrame frame = GravityFrames.tilted(self);
        if (frame == null) {
            return;
        }
        Direction axis = GravityFrames.boxAxis(frame);
        if (axis == Direction.UP) {
            return;
        }
        EntityDimensions size = self.getDimensions(pose);
        cir.setReturnValue(self.level().noCollision(self,
                GravityFrames.orientedBox(axis, size.width(), size.height(), self.position()).deflate(1.0E-7)));
    }

    /** Speed along your own up when flying started this tick, see {@link #galaxy$flyingDamping}. */
    @Unique
    private double galaxy$flyUpSpeed;

    @Inject(method = "travel", at = @At("HEAD"))
    private void galaxy$rememberFlyUpSpeed(Vec3 input, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        GravityFrame frame = GravityFrames.tilted(self);
        galaxy$flyUpSpeed = frame == null ? 0.0 : frame.toLocal(self.getDeltaMovement()).y;
    }

    /**
     * Vanilla flight slows your up and down speed along world y. When tilted, slow it along
     * your own up instead, and keep the rest of the movement (vanilla would throw away the
     * world-y part of flying forward, so you couldn't fly where you look).
     */
    @Redirect(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;with(Lnet/minecraft/core/Direction$Axis;D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 galaxy$flyingDamping(Vec3 movement, Direction.Axis axis, double value) {
        Player self = (Player) (Object) this;
        GravityFrame frame = GravityFrames.tilted(self);
        if (frame == null || axis != Direction.Axis.Y) {
            return movement.with(axis, value);
        }
        Vec3 local = frame.toLocal(movement);
        return frame.toWorld(new Vec3(local.x, galaxy$flyUpSpeed * 0.6, local.z));
    }
}
