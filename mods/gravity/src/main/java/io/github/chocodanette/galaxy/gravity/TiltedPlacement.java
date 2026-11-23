package io.github.chocodanette.galaxy.gravity;

import java.util.Arrays;
import java.util.Comparator;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Which way a tilted player "faces" when placing blocks. Vanilla works this out from yaw and
 * pitch, which are measured in the player's own tilted frame, so on a planet's side a chest
 * or piston would face a random-looking way. Here we use the real view direction instead.
 *
 * Blocks still only have world directions: stairs, slabs and doors can't lean sideways, so on
 * a planet's side they're placed as close to "facing you" as the block allows.
 */
public final class TiltedPlacement {
    private TiltedPlacement() {
    }

    /** All six directions, nearest to where the player looks first. */
    public static Direction[] byLook(Vec3 view) {
        Direction[] all = Direction.values().clone();
        Arrays.sort(all, Comparator.comparingDouble((Direction d) -> -view.dot(d.getUnitVec3())));
        return all;
    }

    /**
     * The direction to use as "the way you face" for blocks that turn to face you (chests,
     * furnaces, stairs): the one nearest your view among those across your own "up".
     * When standing on a planet's side that can be up or down in the world, which only some
     * blocks accept, so we keep to the four world-horizontal ones when we can.
     */
    public static Direction horizontal(Vec3 view, Direction localUpAxis) {
        Direction best = null;
        double bestDot = -Double.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d.getAxis() == localUpAxis.getAxis()) {
                continue;
            }
            double dot = view.dot(d.getUnitVec3());
            if (dot > bestDot) {
                bestDot = dot;
                best = d;
            }
        }
        return best;
    }
}
