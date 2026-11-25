package io.github.chocodanette.galaxy.gravity.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.MobPlanetAI;

/**
 * Random spots for strolling, panicking and fleeing on a planet's side (see
 * MobPlanetAI#wanderTarget). Vanilla searches for ground along world Y.
 */
@Mixin(DefaultRandomPos.class)
public abstract class DefaultRandomPosMixin {
    @Inject(method = "getPos", at = @At("HEAD"), cancellable = true)
    private static void galaxy$getPos(PathfinderMob mob, int flat, int up, CallbackInfoReturnable<@Nullable Vec3> cir) {
        MobPlanetAI.replaceRandomPos(mob, flat, up, null, cir);
    }

    @Inject(method = "getPosTowards", at = @At("HEAD"), cancellable = true)
    private static void galaxy$towards(PathfinderMob mob, int flat, int up, Vec3 towards, double maxRadians,
            CallbackInfoReturnable<@Nullable Vec3> cir) {
        MobPlanetAI.replaceRandomPos(mob, flat, up, towards.subtract(mob.position()), cir);
    }

    @Inject(method = "getPosAway", at = @At("HEAD"), cancellable = true)
    private static void galaxy$away(PathfinderMob mob, int flat, int up, Vec3 avoid,
            CallbackInfoReturnable<@Nullable Vec3> cir) {
        MobPlanetAI.replaceRandomPos(mob, flat, up, mob.position().subtract(avoid), cir);
    }
}

