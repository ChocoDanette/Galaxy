package io.github.chocodanette.galaxy.gravity.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Mobs turn their heads toward what they look at in their own frame, and look at a tilted
 * entity's real eyes (vanilla assumes eyes sit straight above the feet along world Y).
 */
@Mixin(LookControl.class)
public abstract class LookControlMixin {
    @Shadow
    @Final
    protected Mob mob;
    @Shadow
    protected double wantedX;
    @Shadow
    protected double wantedY;
    @Shadow
    protected double wantedZ;

    @Shadow
    public abstract void setLookAt(double x, double y, double z, float yMaxRotSpeed, float xMaxRotAngle);

    @Inject(method = "setLookAt(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void galaxy$lookAtEyes(Entity target, CallbackInfo ci) {
        if (GravityFrames.tilted(target) != null) {
            Vec3 eyes = target.getEyePosition();
            setLookAt(eyes.x, eyes.y, eyes.z, mob.getHeadRotSpeed(), mob.getMaxHeadXRot());
            ci.cancel();
        }
    }

    @Inject(method = "setLookAt(Lnet/minecraft/world/entity/Entity;FF)V", at = @At("HEAD"), cancellable = true)
    private void galaxy$lookAtEyes(Entity target, float yMaxRotSpeed, float xMaxRotAngle, CallbackInfo ci) {
        if (GravityFrames.tilted(target) != null) {
            Vec3 eyes = target.getEyePosition();
            setLookAt(eyes.x, eyes.y, eyes.z, yMaxRotSpeed, xMaxRotAngle);
            ci.cancel();
        }
    }

    @Inject(method = "getYRotD", at = @At("HEAD"), cancellable = true)
    private void galaxy$yRot(CallbackInfoReturnable<Optional<Float>> cir) {
        GravityFrame frame = GravityFrames.tilted(mob);
        if (frame == null) {
            return;
        }
        Vec3 local = frame.toLocal(new Vec3(wantedX, wantedY, wantedZ).subtract(mob.position()));
        cir.setReturnValue(Math.abs(local.x) <= 1.0E-5 && Math.abs(local.z) <= 1.0E-5
                ? Optional.empty()
                : Optional.of((float) (Mth.atan2(local.z, local.x) * Mth.RAD_TO_DEG) - 90.0F));
    }

    @Inject(method = "getXRotD", at = @At("HEAD"), cancellable = true)
    private void galaxy$xRot(CallbackInfoReturnable<Optional<Float>> cir) {
        GravityFrame frame = GravityFrames.tilted(mob);
        if (frame == null) {
            return;
        }
        Vec3 local = frame.toLocal(new Vec3(wantedX, wantedY, wantedZ).subtract(mob.getEyePosition()));
        double flat = Math.sqrt(local.x * local.x + local.z * local.z);
        cir.setReturnValue(Math.abs(local.y) <= 1.0E-5 && flat <= 1.0E-5
                ? Optional.empty()
                : Optional.of((float) -(Mth.atan2(local.y, flat) * Mth.RAD_TO_DEG)));
    }
}
