package io.github.chocodanette.galaxy.gravity.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityFrame;

import io.github.chocodanette.galaxy.gravity.GravityFrames;

/**
 * Vanilla nudges your own player sideways out of any block that overlaps a column around its
 * feet, assuming "up" is world up. On a planet's side or underside the planet itself fills
 * those columns, so the player was pushed off the surface every tick and kept falling back.
 * The nudge is skipped while tilted.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void galaxy$noSidewaysPushWhileTilted(double x, double z, CallbackInfo ci) {
        if (GravityFrames.tilted((LocalPlayer) (Object) this) != null) {
            ci.cancel();
        }
    }

    /** Flying up (jump) and down (sneak) go along your own up, not world up. */
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void galaxy$flyAlongLocalUp(LocalPlayer self, Vec3 movement) {
        GravityFrame frame = GravityFrames.tilted(self);
        if (frame == null) {
            self.setDeltaMovement(movement);
            return;
        }
        double vertical = movement.y - self.getDeltaMovement().y;
        self.setDeltaMovement(self.getDeltaMovement().add(frame.up().scale(vertical)));
    }
}
