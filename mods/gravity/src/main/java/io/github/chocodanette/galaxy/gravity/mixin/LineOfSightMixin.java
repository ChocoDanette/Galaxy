package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Seeing another entity, measured between real eyes. Vanilla puts the eyes straight above
 * the feet along world Y, which on a planet's underside is inside the rock, so mobs there
 * could never see (or target) you, and on some sides the sight line just grazed the ground.
 */
@Mixin(LivingEntity.class)
public abstract class LineOfSightMixin {
    @Inject(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z",
            at = @At("HEAD"), cancellable = true)
    private void galaxy$lineOfSight(Entity target, ClipContext.Block blocks, ClipContext.Fluid fluids, double eyeY,
            CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        boolean selfTilted = GravityFrames.tilted(self) != null;
        boolean targetTilted = GravityFrames.tilted(target) != null;
        if (!selfTilted && !targetTilted || target.level() != self.level()) {
            return;
        }
        Vec3 from = self.getEyePosition();
        // An upright target keeps the height the caller asked for (usually its eyes).
        Vec3 to = targetTilted
                ? target.getEyePosition()
                : new Vec3(target.getX(), eyeY, target.getZ());
        if (to.distanceTo(from) > 128.0) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(self.level().clip(new ClipContext(from, to, blocks, fluids, self)).getType()
                == HitResult.Type.MISS);
    }
}
