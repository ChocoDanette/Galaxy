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

    /**
     * How fast the pull weakens as you rise above the spot you left the ground from.
     * 1.5 means half the pull at 2/3 of a block up, a quarter at 2 blocks. A normal jump goes
     * about 2 to 3 blocks high on a galaxy planet instead of 1.
     */
    public static final double LIFT_FALLOFF_PER_BLOCK = 1.5;

    /**
     * The weakest the pull gets anywhere inside a field, as a share of full strength. Keeps a
     * slow drift back to the planet right at the edge, so you don't hang there forever.
     */
    public static final double MIN_PULL_FACTOR = 0.03;

    /**
     * Share of the planet's full pull (0 to 1) at a position.
     *
     * Two things weaken it. Near the edge of the field the pull fades out smoothly, reaching
     * almost nothing at the edge. And the higher you are above the spot you left the ground
     * from, the weaker the pull, so jumps go high and float. Standing on the ground (or
     * falling below your take-off spot) you get the full edge-faded pull, so walking feels
     * normal.
     *
     * @param takeoffDistance the {@link GravityField#distanceToSurface} of the spot the entity
     *                        last stood on, or NaN when unknown
     */
    public static double pullFactor(GravityField field, Vec3 pos, double takeoffDistance) {
        double distance = field.distanceToSurface(pos);
        double band = field.range() - field.radius();
        double edge = 1.0;
        if (band > MIN_DISTANCE) {
            double t = clamp01(distance / band);
            edge = (1.0 - t) * (1.0 - t);
        }
        double lift = 1.0;
        if (!Double.isNaN(takeoffDistance)) {
            double height = Math.max(0.0, distance - takeoffDistance);
            lift = 1.0 / (1.0 + LIFT_FALLOFF_PER_BLOCK * height);
        }
        return Math.max(MIN_PULL_FACTOR, edge * lift);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** How much harder a home planet pulls from space than other planets. */
    public static final double ANCHOR_BOOST = 1.5;

    /**
     * The pull on something at a position.
     *
     * @param down    unit vector the pull points along
     * @param scale   share of normal gravity (planet strength and fading included)
     * @param field   the planet doing the pulling
     * @param inField true inside the planet's range, false for the weak pull from space
     */
    public record Pull(Vec3 down, double scale, GravityField field, boolean inField) {
    }

    /**
     * The pull at a position: the governing planet inside a range, otherwise the weak pull
     * toward the nearest planet when the dimension has one. Empty means vanilla gravity.
     *
     * @param takeoffDistance see {@link #pullFactor}; NaN for things that don't jump
     */
    public static Optional<Pull> pull(GravityWorld world, Vec3 pos, double takeoffDistance) {
        Optional<GravityField> governing = governingField(world.fields(), pos);
        if (governing.isPresent()) {
            GravityField field = governing.get();
            return Optional.of(new Pull(downDirection(field, pos, VANILLA_DOWN),
                    field.strength() * pullFactor(field, pos, takeoffDistance), field, true));
        }
        if (world.spacePull() <= 0) {
            return Optional.empty();
        }
        GravityField best = null;
        double bestScore = Double.MAX_VALUE;
        for (GravityField field : world.fields()) {
            double score = field.distanceToSurface(pos) / (world.isAnchor(field) ? ANCHOR_BOOST : 1.0);
            if (score < bestScore) {
                bestScore = score;
                best = field;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        double boost = world.isAnchor(best) ? ANCHOR_BOOST : 1.0;
        return Optional.of(new Pull(downDirection(best, pos, VANILLA_DOWN),
                world.spacePull() * best.strength() * boost, best, false));
    }

    /** Down direction under a dimension's gravity rules, falling back to vanilla gravity. */
    public static Vec3 downDirection(GravityWorld world, Vec3 pos) {
        return pull(world, pos, Double.NaN).map(Pull::down).orElse(VANILLA_DOWN);
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
