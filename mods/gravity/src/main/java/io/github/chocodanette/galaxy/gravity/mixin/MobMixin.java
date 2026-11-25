package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Melee reach on a planet's side: vanilla grows the attack box along world X and Z only, so a
 * mob on a side face couldn't hit someone in front of it along world Y.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "getAttackBoundingBox", at = @At("RETURN"), cancellable = true)
    private void galaxy$attackBox(double reach, CallbackInfoReturnable<AABB> cir) {
        Mob self = (Mob) (Object) this;
        if (GravityFrames.tilted(self) != null && self.getVehicle() == null) {
            cir.setReturnValue(GravityFrames.inflateAlongFrame(self, self.getBoundingBox(), reach, 0.0));
        }
    }
}
