package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

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
}
