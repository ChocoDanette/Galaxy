package io.github.chocodanette.galaxy.gravity.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.particle.EndRodParticle;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SuspendedTownParticle;

import io.github.chocodanette.galaxy.gravity.client.ParticleFrame;

/** Flames, end rod sparks and similar drifting particles move in their planet frame too. */
@Mixin({FlameParticle.class, EndRodParticle.class, SuspendedTownParticle.class})
public abstract class FreeParticleMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void galaxy$tiltedFreeMove(double xa, double ya, double za, CallbackInfo ci) {
        ParticleFrame self = (ParticleFrame) this;
        if (self.galaxy$frame() != null) {
            self.galaxy$moveFree(xa, ya, za);
            ci.cancel();
        }
    }
}
