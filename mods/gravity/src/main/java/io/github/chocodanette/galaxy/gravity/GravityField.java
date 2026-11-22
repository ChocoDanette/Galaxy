package io.github.chocodanette.galaxy.gravity;

import net.minecraft.world.phys.Vec3;

/**
 * A spherical gravity field around one planet.
 *
 * @param center   center of the planet, in block coordinates
 * @param radius   radius of the planet's surface, in blocks
 * @param range    how far from the center the field reaches, in blocks (must be at least radius)
 * @param strength pull compared to vanilla gravity (1.0 = normal)
 */
public record GravityField(Vec3 center, double radius, double range, double strength) {
    public GravityField {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (range < radius) {
            throw new IllegalArgumentException("range must be at least radius");
        }
        if (strength < 0) {
            throw new IllegalArgumentException("strength must not be negative");
        }
    }

    /** True when the position is inside this field's reach. */
    public boolean contains(Vec3 pos) {
        return pos.distanceToSqr(center) <= range * range;
    }

    /** Distance from the position to the planet's surface (negative when inside the planet). */
    public double distanceToSurface(Vec3 pos) {
        return pos.distanceTo(center) - radius;
    }
}
