package io.github.chocodanette.galaxy.gravity.mixin.client;

import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;

/** Tilts the camera with the player's gravity frame, smoothly between ticks. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    @Final
    private static Vector3fc FORWARDS;
    @Shadow
    @Final
    private static Vector3fc UP;
    @Shadow
    @Final
    private static Vector3fc LEFT;

    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private @Nullable Entity entity;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Unique
    private float galaxy$partialTick;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void galaxy$rememberPartialTick(float partialTicks, CallbackInfo ci) {
        galaxy$partialTick = partialTicks;
    }

    /** Yaw and pitch are in the player's own frame; turn the result into world space. */
    @Inject(method = "setRotation(FFF)V", at = @At("TAIL"))
    private void galaxy$tiltRotation(float yRot, float xRot, float roll, CallbackInfo ci) {
        GravityFrame frame = entity == null ? null : GravityFrames.tilted(entity);
        if (frame == null) {
            return;
        }
        rotation.premul(frame.rotation(galaxy$partialTick));
        FORWARDS.rotate(rotation, forwards);
        UP.rotate(rotation, up);
        LEFT.rotate(rotation, left);
    }

    /** Puts the camera at the eyes, along the local "up" instead of world up. */
    @Inject(method = "alignWithEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", shift = At.Shift.AFTER))
    private void galaxy$tiltPosition(float partialTicks, CallbackInfo ci) {
        GravityFrame frame = entity == null ? null : GravityFrames.tilted(entity);
        if (frame == null) {
            return;
        }
        Vec3 feet = new Vec3(
                Mth.lerp(partialTicks, entity.xo, entity.getX()),
                Mth.lerp(partialTicks, entity.yo, entity.getY()),
                Mth.lerp(partialTicks, entity.zo, entity.getZ()));
        float eyes = Mth.lerp(partialTicks, eyeHeightOld, eyeHeight);
        setPosition(feet.add(frame.up(partialTicks).scale(eyes)));
    }
}
