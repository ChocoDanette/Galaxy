package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * A mob walking to a spot turns and jumps in its own frame. Vanilla works out the turn from
 * world X and Z and the jump from world Y, which on a planet's side points the wrong way.
 */
@Mixin(MoveControl.class)
public abstract class MoveControlMixin {
    /** Height a tilted mob climbs without jumping (see LivingEntityMixin and EntityMixin). */
    private static final double GALAXY$STEP = 1.0;

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
    protected double speedModifier;
    @Shadow
    protected MoveControl.Operation operation;

    @Shadow
    protected abstract float rotlerp(float a, float b, float max);

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void galaxy$tick(CallbackInfo ci) {
        if (operation != MoveControl.Operation.MOVE_TO) {
            return;
        }
        GravityFrame frame = GravityFrames.tilted(mob);
        if (frame == null) {
            return;
        }
        ci.cancel();
        operation = MoveControl.Operation.WAIT;
        Vec3 local = frame.toLocal(new Vec3(wantedX - mob.getX(), wantedY - mob.getY(), wantedZ - mob.getZ()));
        if (local.lengthSqr() < 2.5000003E-7F) {
            mob.setZza(0.0F);
            return;
        }
        double flatSqr = local.x * local.x + local.z * local.z;
        if (flatSqr > 1.0E-6) {
            float yaw = (float) (Mth.atan2(local.z, local.x) * Mth.RAD_TO_DEG) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 90.0F));
        }
        mob.setSpeed((float) (speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        if (local.y > GALAXY$STEP && flatSqr < Math.max(1.0F, mob.getBbWidth())) {
            mob.getJumpControl().jump();
            operation = MoveControl.Operation.JUMPING;
        }
    }
}
