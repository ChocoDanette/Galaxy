package io.github.chocodanette.galaxy.gravity.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.ImmutableSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.MobPlanetAI;
import io.github.chocodanette.galaxy.gravity.PlanetNavigator;
import io.github.chocodanette.galaxy.gravity.PlanetPathfinder;

/**
 * Walking mobs on a planet's side or underside find and follow paths along their own "up"
 * (see {@link PlanetPathfinder}). Upright mobs, and mobs that fly or swim, keep vanilla
 * navigation.
 */
@Mixin(PathNavigation.class)
public abstract class PathNavigationMixin implements PlanetNavigator {
    @Shadow
    @Final
    protected Mob mob;
    @Shadow
    protected @Nullable Path path;
    @Shadow
    protected double speedModifier;
    @Shadow
    protected int tick;
    @Shadow
    protected boolean hasDelayedRecomputation;
    @Shadow
    private @Nullable BlockPos targetPos;
    @Shadow
    private int reachRange;

    /** The axis the current path was planned along; a new face means a new path. */
    @Unique
    private @Nullable Direction galaxy$pathUp;

    @Shadow
    protected abstract boolean canUpdatePath();

    @Shadow
    public abstract boolean isDone();

    @Shadow
    public abstract void recomputePath();

    @Shadow
    protected abstract void doStuckDetection(Vec3 mobPos);

    @Shadow
    private void resetStuckTimeout() {
        throw new AssertionError();
    }

    /** The face this navigation's mob walks on, or null for vanilla behavior. */
    @Unique
    private @Nullable Direction galaxy$walkingUp() {
        if (!((Object) this instanceof GroundPathNavigation)) {
            return null;
        }
        return MobPlanetAI.walkingUp(mob);
    }

    @Override
    public @Nullable Path galaxy$planetPath(Set<BlockPos> targets, int reach) {
        Direction up = MobPlanetAI.walkingUp(mob);
        if (up == null || targets.isEmpty() || !canUpdatePath()) {
            return null;
        }
        if (path != null && !path.isDone() && targets.contains(targetPos) && up == galaxy$pathUp) {
            return path;
        }
        PlanetPathfinder.Result result = MobPlanetAI.findPath(mob, up, targets, reach);
        if (result == null) {
            return null;
        }
        List<Node> nodes = new ArrayList<>(result.cells().size());
        for (BlockPos cell : result.cells()) {
            nodes.add(new Node(cell.getX(), cell.getY(), cell.getZ()));
        }
        Path found = new Path(nodes, result.target(), result.reached());
        targetPos = result.target();
        reachRange = reach;
        galaxy$pathUp = up;
        resetStuckTimeout();
        return found;
    }

    @Inject(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
            at = @At("HEAD"), cancellable = true)
    private void galaxy$createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reach,
            float maxPathLength, CallbackInfoReturnable<@Nullable Path> cir) {
        if (galaxy$walkingUp() != null) {
            cir.setReturnValue(galaxy$planetPath(targets, reach));
        }
    }

    /** Follows the path node by node, measuring along the face instead of world X and Z. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void galaxy$tick(CallbackInfo ci) {
        Direction up = galaxy$walkingUp();
        if (up == null) {
            return;
        }
        ci.cancel();
        tick++;
        if (hasDelayedRecomputation) {
            recomputePath();
        }
        if (isDone()) {
            return;
        }
        if (galaxy$pathUp != up && targetPos != null) {
            // Walked over an edge onto another face: plan again from here.
            BlockPos target = targetPos;
            path = null;
            path = galaxy$planetPath(ImmutableSet.of(target), reachRange);
            if (isDone()) {
                return;
            }
        }
        if (canUpdatePath()) {
            Vec3 u = up.getUnitVec3();
            double reach = mob.getBbWidth() > 0.75F ? mob.getBbWidth() / 2.0F : 0.75F - mob.getBbWidth() / 2.0F;
            while (!path.isDone()) {
                Vec3 offset = MobPlanetAI.standingPoint(path.getNextNodePos(), up).subtract(mob.position());
                double along = offset.dot(u);
                Vec3 flat = offset.subtract(u.scale(along));
                if (flat.length() > reach || Math.abs(along) >= 1.0) {
                    break;
                }
                path.advance();
            }
            doStuckDetection(mob.position());
        }
        if (!isDone()) {
            Vec3 goal = MobPlanetAI.standingPoint(path.getNextNodePos(), up);
            mob.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speedModifier);
        }
    }
}
