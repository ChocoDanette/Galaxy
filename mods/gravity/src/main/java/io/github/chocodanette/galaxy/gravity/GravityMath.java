package io.github.chocodanette.galaxy.gravity;

import java.util.Collection;
import java.util.Optional;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Pure math for planet gravity, kept free of game state so it can be unit tested. */
public final class GravityMath {
    /** Below this distance from a center, the direction is undefined and we keep the fallback. */
    private static final double MIN_DISTANCE = 1.0E-6;

    private GravityMath() {
    }

    /**
     * Picks the field that governs a position: among the fields that reach it,
     * the one whose surface is closest. Empty when no field reaches the position.
     */
    public static Optional<GravityField> governingField(Collection<GravityField> fields, Vec3 pos) {
        GravityField best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GravityField field : fields) {
            if (!field.contains(pos)) {
                continue;
            }
            double distance = Math.abs(field.distanceToSurface(pos));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = field;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Unit vector pointing "down" at a position, toward the field's center.
     * At the exact center, returns the fallback direction.
     */
    public static Vec3 downDirection(GravityField field, Vec3 pos, Vec3 fallback) {
        Vec3 toCenter = field.center().subtract(pos);
        double length = toCenter.length();
        if (length < MIN_DISTANCE) {
            return fallback;
        }
        return toCenter.scale(1.0 / length);
    }

    /** Vanilla-style "down" used when no field reaches a position. */
    public static final Vec3 VANILLA_DOWN = new Vec3(0, -1, 0);

    /** Down direction for a position among many fields, falling back to vanilla gravity. */
    public static Vec3 downDirection(Collection<GravityField> fields, Vec3 pos) {
        return governingField(fields, pos)
                .map(field -> downDirection(field, pos, VANILLA_DOWN))
                .orElse(VANILLA_DOWN);
    }

    /**
     * How much closer a new face must be before gravity switches to it. Without this,
     * standing near the edge between two faces would flip gravity back and forth.
     */
    public static final double SWITCH_MARGIN = 0.15;

    /**
     * The axis direction closest to a "down" vector, staying on the current direction
     * unless another one is clearly closer (by {@link #SWITCH_MARGIN}).
     */
    public static Direction nearestDirection(Vec3 down, Direction current) {
        Direction best = Direction.DOWN;
        double bestDot = -Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            double dot = dot(down, direction);
            if (dot > bestDot) {
                bestDot = dot;
                best = direction;
            }
        }
        if (current != null && best != current && bestDot - dot(down, current) < SWITCH_MARGIN) {
            return current;
        }
        return best;
    }

    private static double dot(Vec3 v, Direction direction) {
        return v.x * direction.getStepX() + v.y * direction.getStepY() + v.z * direction.getStepZ();
    }
}
