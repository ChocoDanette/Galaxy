package io.github.chocodanette.galaxy.gravity.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

import io.github.chocodanette.galaxy.gravity.MobPlanetAI;
import io.github.chocodanette.galaxy.gravity.PlanetNavigator;

/**
 * Vanilla moves a walking mob's target down or up along world Y to find ground. On a planet's
 * side that would drag the target off the face, so tilted mobs skip it.
 */
@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationMixin {
    /** Chasing someone on a planet's side: aim for the cell their feet are in. */
    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), cancellable = true)
    private void galaxy$createPathToEntity(net.minecraft.world.entity.Entity target, int reachRange,
            CallbackInfoReturnable<@Nullable Path> cir) {
        PathNavigation self = (PathNavigation) (Object) this;
        if (MobPlanetAI.walkingUp(((PathNavigationAccess) self).galaxy$mob()) != null) {
            cir.setReturnValue(((PlanetNavigator) self).galaxy$planetPath(
                    ImmutableSet.of(io.github.chocodanette.galaxy.gravity.GravityFrames.feetBlock(target)), reachRange));
        }
    }

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), cancellable = true)
    private void galaxy$createPath(BlockPos pos, int reachRange, CallbackInfoReturnable<@Nullable Path> cir) {
        PathNavigation self = (PathNavigation) (Object) this;
        if (MobPlanetAI.walkingUp(((PathNavigationAccess) self).galaxy$mob()) != null) {
            cir.setReturnValue(((PlanetNavigator) self).galaxy$planetPath(ImmutableSet.of(pos), reachRange));
        }
    }
}
