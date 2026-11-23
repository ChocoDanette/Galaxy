package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Air and ground movement, friction and jumping in the entity's own frame. Vanilla applies
 * friction to world X and Z and gravity to world Y, which only works when "down" is world down.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    private Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 input, float friction) {
        throw new AssertionError();
    }

    @Shadow
    private static float computeModifiedFriction(float friction, float modifier) {
        throw new AssertionError();
    }

    @Shadow
    public abstract double getAttributeValue(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute);

    @Shadow
    public abstract boolean shouldDiscardFriction();

    @Shadow
    protected abstract double getEffectiveGravity();

    @Shadow
    protected abstract float getJumpPower();

    @Unique
    private LivingEntity galaxy$self() {
        return (LivingEntity) (Object) this;
    }

    @Inject(method = "travelInAir", at = @At("HEAD"), cancellable = true)
    private void galaxy$travelInAir(Vec3 input, CallbackInfo ci) {
        LivingEntity self = galaxy$self();
        GravityFrame frame = GravityFrames.tilted(self);
        if (frame == null) {
            return;
        }
        ci.cancel();

        // The block under the feet, along the local "down".
        BlockPos below = BlockPos.containing(self.position().subtract(frame.up().scale(0.5)));
        float blockFriction = self.onGround()
                ? computeModifiedFriction(
                        self.level().getBlockState(below).getFriction(self.level(), below, self),
                        (float) getAttributeValue(Attributes.FRICTION_MODIFIER))
                : 1.0F;

        // World-space movement after walking input and collisions. Planet gravity is added
        // separately by GravityApplier; vanilla gravity only applies when no planet cancels it.
        Vec3 movement = handleRelativeFrictionAndCalculateMovement(input, blockFriction)
                .subtract(0, getEffectiveGravity(), 0);
        if (shouldDiscardFriction()) {
            self.setDeltaMovement(movement);
            return;
        }

        float airDragModifier = (float) getAttributeValue(Attributes.AIR_DRAG_MODIFIER);
        float airDrag = computeModifiedFriction(0.91F, airDragModifier);
        float friction = blockFriction * airDrag;
        float verticalFriction = computeModifiedFriction(0.98F, airDragModifier);
        Vec3 local = frame.toLocal(movement);
        self.setDeltaMovement(frame.toWorld(new Vec3(local.x * friction, local.y * verticalFriction, local.z * friction)));
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void galaxy$jump(CallbackInfo ci) {
        LivingEntity self = galaxy$self();
        GravityFrame frame = GravityFrames.tilted(self);
        if (frame == null) {
            return;
        }
        ci.cancel();
        float jumpPower = getJumpPower();
        if (jumpPower <= 1.0E-5F) {
            return;
        }
        Vec3 local = frame.toLocal(self.getDeltaMovement());
        local = new Vec3(local.x, Math.max(jumpPower, local.y), local.z);
        if (self.isSprinting()) {
            float angle = self.getYRot() * Mth.DEG_TO_RAD;
            local = local.add(-Mth.sin(angle) * 0.2, 0.0, Mth.cos(angle) * 0.2);
        }
        self.setDeltaMovement(frame.toWorld(local));
        self.needsSync = true;
        net.neoforged.neoforge.common.CommonHooks.onLivingJump(self);
    }
}
