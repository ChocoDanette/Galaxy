package io.github.chocodanette.galaxy.gravity.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

import io.github.chocodanette.galaxy.gravity.GravityFrames;
import io.github.chocodanette.galaxy.gravity.GravityWorld;

/**
 * The server kicks players who stay in the air too long ("Flying is not enabled"), measured
 * against world down. In a dimension with planets, floating and coasting between planets is
 * normal, so don't count it there.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
    private void galaxy$floatingInSpace(Entity entity, CallbackInfoReturnable<Integer> cir) {
        GravityWorld world = GravityFrames.worldFor(entity);
        if (!world.fields().isEmpty() || world.spacePull() > 0) {
            cir.setReturnValue(Integer.MAX_VALUE);
        }
    }
}
