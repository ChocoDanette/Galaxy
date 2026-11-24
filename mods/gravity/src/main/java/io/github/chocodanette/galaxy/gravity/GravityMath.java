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
     * Share of the planet's full pull (0 to 1) at a position. For now gravity is the same
     * everywhere inside a planet's range, so walking and jumping feel like the overworld (with
     * the planet's own strength). A later "high jump" action will lower it to take off.
     *
     * @param takeoffDistance the {@link GravityField#distanceToSurface} of the spot the entity
     *                        last stood on, or NaN when unknown (kept for the high jump)
     */
    public static double pullFactor(GravityField field, Vec3 pos, double takeoffDistance) {
        return 1.0;
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
        return pull(world, pos, takeoffDistance, null);
    }

    /** How sharply a closer planet wins over a farther one where ranges overlap. */
    static final double BLEND_POWER = 4.0;
    /** Extra weight the planet you're already on keeps, so you don't flip back and forth. */
    static final double HOLD_WEIGHT = 2.0;
    /** A range's pull fades in over this many blocks from its edge, where ranges overlap. */
    static final double EDGE_FADE = 4.0;

    /**
     * Same, remembering which planet the entity was on. Where two ranges overlap, the pulls
     * blend: the closer planet counts much more (by distance to its surface), and a range's
     * pull fades in from its edge, so "down" swings round smoothly from one planet to the other
     * instead of snapping. Right between two planets facing each other the pulls cancel and
     * down flips, but the planet you came from keeps a little extra weight
     * ({@link #HOLD_WEIGHT}) so it only flips once you're past the middle.
     *
     * @param holdCenter center of the planet the entity was pulled by last, or null
     */
    public static Optional<Pull> pull(GravityWorld world, Vec3 pos, double takeoffDistance, Vec3 holdCenter) {
        Vec3 sum = Vec3.ZERO;
        double totalWeight = 0;
        double strength = 0;
        GravityField dominant = null;
        double dominantWeight = -1;
        for (GravityField field : world.fields()) {
            if (!field.contains(pos)) {
                continue;
            }
            double weight = blendWeight(field, pos, holdCenter);
            Vec3 down = downDirection(field, pos, VANILLA_DOWN);
            sum = sum.add(down.scale(weight));
            totalWeight += weight;
            strength += weight * field.strength() * pullFactor(field, pos, takeoffDistance);
            if (weight > dominantWeight) {
                dominantWeight = weight;
                dominant = field;
            }
        }
        if (dominant != null) {
            Vec3 down = sum.length() > 1.0E-6 * totalWeight ? sum.normalize()
                    : downDirection(dominant, pos, VANILLA_DOWN);
            return Optional.of(new Pull(down, strength / totalWeight, dominant, true));
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

    /** How much one planet counts in a blend (see {@link #pull(GravityWorld, Vec3, double, Vec3)}). */
    static double blendWeight(GravityField field, Vec3 pos, Vec3 holdCenter) {
        double distance = Math.max(0.0, Math.abs(field.distanceToSurface(pos)));
        double weight = 1.0 / Math.pow(distance + 1.0, BLEND_POWER);
        double edge = (field.range() - pos.distanceTo(field.center())) / EDGE_FADE;
        if (edge < 1.0) {
            double t = Math.max(0.0, edge);
            // Never quite zero, so a lone range still pulls right up to its edge.
            weight *= Math.max(1.0E-4, t * t * (3 - 2 * t));
        }
        if (holdCenter != null && holdCenter.equals(field.center())) {
            weight *= HOLD_WEIGHT;
        }
        return weight;
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

    /** How far past the diagonal (in degrees) "up" must get before step gravity switches face. */
    public static final double STEP_SWITCH_MARGIN_DEGREES = 8.0;

    /**
     * Step gravity: the block direction "up" points along, for a planet's true up. Keeps the
     * current direction until the true up is clearly closer to another one (past the
     * diagonal by {@link #STEP_SWITCH_MARGIN_DEGREES}), so it doesn't flip back and forth.
     *
     * @param trueUp  unit "up" from the planet's pull
     * @param current the direction used so far, or null
     */
    public static net.minecraft.core.Direction stepUp(Vec3 trueUp, net.minecraft.core.Direction current) {
        net.minecraft.core.Direction nearest = net.minecraft.core.Direction.getApproximateNearest(trueUp.x, trueUp.y, trueUp.z);
        if (current == null || current == nearest) {
            return nearest;
        }
        double keep = Math.cos(Math.toRadians(45.0 + STEP_SWITCH_MARGIN_DEGREES));
        return trueUp.dot(current.getUnitVec3()) >= keep ? current : nearest;
    }
}
