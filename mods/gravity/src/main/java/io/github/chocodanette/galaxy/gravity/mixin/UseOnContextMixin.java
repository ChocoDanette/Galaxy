package io.github.chocodanette.galaxy.gravity.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;
import io.github.chocodanette.galaxy.gravity.TiltedPlacement;

/** "The way you face" for placed blocks (chests, furnaces, stairs) follows a tilted player's view. */
@Mixin(UseOnContext.class)
public abstract class UseOnContextMixin {
    @Shadow
    @Final
    private @Nullable Player player;

    @Inject(method = "getHorizontalDirection", at = @At("HEAD"), cancellable = true)
    private void galaxy$tiltedFacing(CallbackInfoReturnable<Direction> cir) {
        if (player == null) {
            return;
        }
        GravityFrame frame = GravityFrames.tilted(player);
        if (frame != null) {
            cir.setReturnValue(TiltedPlacement.horizontal(player.getViewVector(1.0F), GravityFrames.boxAxis(frame)));
        }
    }
}
