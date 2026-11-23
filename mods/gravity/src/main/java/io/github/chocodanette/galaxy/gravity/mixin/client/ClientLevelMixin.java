package io.github.chocodanette.galaxy.gravity.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.CardinalLighting;

import io.github.chocodanette.galaxy.gravity.Space;

/**
 * Vanilla shades block faces by world direction: tops bright, bottoms dark. On a planet there
 * is no up, so in space every face gets the same shade and planets look the same all around.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Unique
    private static final CardinalLighting GALAXY$EVEN = new CardinalLighting(0.9F, 0.9F, 0.9F, 0.9F, 0.9F, 0.9F);

    @Inject(method = "cardinalLighting", at = @At("HEAD"), cancellable = true)
    private void galaxy$evenLightInSpace(CallbackInfoReturnable<CardinalLighting> cir) {
        if (Space.isSpace((ClientLevel) (Object) this)) {
            cir.setReturnValue(GALAXY$EVEN);
        }
    }
}
