package io.github.chocodanette.galaxy.gravity.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;

import io.github.chocodanette.galaxy.gravity.Space;

/** In space there is no sun, moon, sunset glow or dark lower half of the sky: only stars. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Inject(method = {"renderSun", "renderMoon", "renderSunriseAndSunset", "renderDarkDisc"},
            at = @At("HEAD"), cancellable = true, require = 1)
    private void galaxy$noSunInSpace(CallbackInfo ci) {
        if (Space.isSpace(Minecraft.getInstance().level)) {
            ci.cancel();
        }
    }
}
