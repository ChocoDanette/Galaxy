package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.world.phys.Vec3;

/**
 * The fixed arc a Launch Bloom throws you along: up off the leaf along its planet's "up",
 * across the gap high over both planets, and down onto the partner leaf along the other
 * planet's "up". It's a smooth curve (a cubic Bezier) whose two middle points sit above
 * each leaf, so you always leave and land straight.
 *
 * @param start   feet position on the leaf you jump from
 * @param startUp unit "up" there
 * @param end     feet position on the leaf you land on
 * @param endUp   unit "up" there
 * @param ticks   how long the flight takes (20 ticks per second)
 */
public record BloomPath(Vec3 start, Vec3 startUp, Vec3 end, Vec3 endUp, int ticks) {
    /** Flight speed along the arc, in blocks per tick. */
    static final double SPEED = 1.5;
    static final int MIN_TICKS = 30;
    static final int MAX_TICKS = 100;

    /** The arc between two leaves, with a duration fitting its length. */
    public static BloomPath between(Vec3 start, Vec3 startUp, Vec3 end, Vec3 endUp) {
        BloomPath shape = new BloomPath(start, startUp, end, endUp, 1);
        int ticks = (int) Math.round(shape.length() / SPEED);
        return new BloomPath(start, startUp, end, endUp, Math.max(MIN_TICKS, Math.min(MAX_TICKS, ticks)));
    }

    /** How high the arc rises above each leaf before bending over. */
    double lift() {
        return Math.max(6.0, start.distanceTo(end) * 0.45);
    }

    /** Point of the curve at t from 0 to 1. */
    public Vec3 at(double t) {
        double s = Math.max(0.0, Math.min(1.0, t));
        Vec3 p1 = start.add(startUp.scale(lift()));
        Vec3 p2 = end.add(endUp.scale(lift()));
        double u = 1.0 - s;
        return start.scale(u * u * u)
                .add(p1.scale(3 * u * u * s))
                .add(p2.scale(3 * u * s * s))
                .add(end.scale(s * s * s));
    }

    /** Where you are after some ticks of flight: quick off the leaf, gentle landing. */
    public Vec3 atTick(double tick) {
        double t = Math.max(0.0, Math.min(1.0, tick / ticks));
        // A bit faster than even at the start and slower at the end, so the landing is soft.
        return at(t + 0.3 * t * (1.0 - t));
    }

    /** Length of the curve, measured along it. */
    public double length() {
        double length = 0;
        Vec3 last = start;
        for (int i = 1; i <= 64; i++) {
            Vec3 next = at(i / 64.0);
            length += next.distanceTo(last);
            last = next;
        }
        return length;
    }
}
