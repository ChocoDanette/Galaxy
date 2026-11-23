package io.github.chocodanette.galaxy.gravity.mixin.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.ClientPlanets;
import io.github.chocodanette.galaxy.gravity.GravityMath;

/** Dropped items stand, bob and spin along the planet's "up", so they lie flat on any side. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void galaxy$tiltStart(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        poseStack.pushPose();
        if (ClientPlanets.fields().isEmpty()) {
            return;
        }
        GravityMath.pull(ClientPlanets.world(), new Vec3(state.x, state.y, state.z), Double.NaN).ifPresent(pull -> {
            Vec3 up = pull.down().scale(-1);
            if (up.y < 0.999) {
                poseStack.rotate(new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                        new Vector3f((float) up.x, (float) up.y, (float) up.z)));
            }
        });
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void galaxy$tiltEnd(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        poseStack.popPose();
    }
}
