package io.github.chocodanette.galaxy.gravity.mixin;

import java.util.function.ToDoubleFunction;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.MobPlanetAI;

/**
 * Random spots for strolling, panicking and fleeing on a planet's side (see
 * MobPlanetAI#wanderTarget). Vanilla searches for ground along world Y.
 */
@Mixin(LandRandomPos.class)
public abstract class LandRandomPosMixin {
    @Inject(method = "getPos(Lnet/minecraft/world/entity/PathfinderMob;IILjava/util/function/ToDoubleFunction;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true)
    private static void galaxy$getPos(PathfinderMob mob, int flat, int up, ToDoubleFunction<BlockPos> weight,
            CallbackInfoReturnable<@Nullable Vec3> cir) {
        MobPlanetAI.replaceRandomPos(mob, flat, up, null, cir);
    }

    @Inject(method = "getPosTowards", at = @At("HEAD"), cancellable = true)
    private static void galaxy$towards(PathfinderMob mob, int flat, int up, Vec3 towards,
            CallbackInfoReturnable<@Nullable Vec3> cir) {
        MobPlanetAI.replaceRandomPos(mob, flat, up, towards.subtract(mob.position()), cir);
    }

    @Inject(method = "getPosAway(Lnet/minecraft/world/entity/PathfinderMob;DDILnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("HEAD"), cancellable = true)
    private static void galaxy$away(PathfinderMob mob, double minFlat, double maxFlat, int up, Vec3 avoid,
            CallbackInfoReturnable<@Nullable Vec3> cir) {
        MobPlanetAI.replaceRandomPos(mob, (int) Math.ceil(maxFlat), up, mob.position().subtract(avoid), cir);
    }
}
