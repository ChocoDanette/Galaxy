package io.github.chocodanette.galaxy.gravity;

import java.util.Collection;
import java.util.Optional;

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
     * Unit vector pointing "down" at a position: toward the center of a sphere, straight into
     * the face of a cube (bending over its edges). Toward the center when deep inside a cube,
     * and the fallback direction at the exact center.
     */
    public static Vec3 downDirection(GravityField field, Vec3 pos, Vec3 fallback) {
        Vec3 down = field.pullPoint(pos).subtract(pos);
        double length = down.length();
        if (length < MIN_DISTANCE) {
            down = field.center().subtract(pos);
            length = down.length();
            if (length < MIN_DISTANCE) {
                return fallback;
            }
        }
        return down.scale(1.0 / length);
    }

    /** Vanilla-style "down" used when no field reaches a position. */
    public static final Vec3 VANILLA_DOWN = new Vec3(0, -1, 0);

    /** Down direction for a position among many fields, falling back to vanilla gravity. */
    public static Vec3 downDirection(Collection<GravityField> fields, Vec3 pos) {
        return governingField(fields, pos)
                .map(field -> downDirection(field, pos, VANILLA_DOWN))
                .orElse(VANILLA_DOWN);
    }
}
