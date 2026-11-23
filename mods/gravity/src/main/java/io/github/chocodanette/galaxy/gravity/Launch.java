package io.github.chocodanette.galaxy.gravity;

import net.minecraft.world.phys.Vec3;

/**
 * The charged jump, for getting from planet to planet.
 *
 * Crouch on a planet to charge it. After a short crouch, jumping does a <b>hop</b>: enough to
 * cross the gap to a tiny gateway planet. After a long crouch, jumping does a <b>launch</b>:
 * fast enough to leave the planet's gravity and coast through space to another one.
 *
 * Either way you fly toward where you look (never flatter than {@link #MIN_ELEVATION_DEGREES}
 * above your horizon), gravity pulls you at half strength, and the air barely slows you,
 * until you land somewhere. This class holds the numbers and math; the game side is in
 * {@code LivingEntityMixin} (the jump), {@link GravityApplier} (half pull) and
 * {@link GravityFrames} (charging and landing).
 */
public final class Launch {
    /** Crouch this long (ticks, 20 per second) for a hop. */
    public static final int HOP_CHARGE_TICKS = 10;
    /** Crouch this long for a full launch. */
    public static final int LAUNCH_CHARGE_TICKS = 30;
    /** Speed of a hop, in blocks per tick. */
    public static final double HOP_SPEED = 0.9;
    /** Speed of a launch, in blocks per tick (a normal jump is about 0.42). */
    public static final double LAUNCH_SPEED = 2.0;
    /** The flattest a charged jump goes, above your own horizon. */
    public static final double MIN_ELEVATION_DEGREES = 30.0;
    /** Share of the normal pull while flying after a charged jump. */
    public static final double PULL_SHARE = 0.5;
    /** Speed kept each tick while flying after a charged jump (normal air keeps 0.91 to 0.98). */
    public static final double DRAG = 0.995;

    private Launch() {
    }

    /** Which charged jump a crouch of this many ticks gives. */
    public enum Kind {
        NONE, HOP, LAUNCH
    }

    public static Kind kindFor(int chargeTicks) {
        if (chargeTicks >= LAUNCH_CHARGE_TICKS) {
            return Kind.LAUNCH;
        }
        return chargeTicks >= HOP_CHARGE_TICKS ? Kind.HOP : Kind.NONE;
    }

    public static double speedFor(Kind kind) {
        return switch (kind) {
            case LAUNCH -> LAUNCH_SPEED;
            case HOP -> HOP_SPEED;
            case NONE -> 0.0;
        };
    }

    /**
     * The way a charged jump sends you: where you look, raised to at least
     * {@link #MIN_ELEVATION_DEGREES} above your horizon so you never dive into the ground.
     *
     * @param look unit view direction in world space
     * @param up   unit "up" of your frame in world space
     */
    public static Vec3 direction(Vec3 look, Vec3 up) {
        double elevation = look.dot(up);
        double minSin = Math.sin(Math.toRadians(MIN_ELEVATION_DEGREES));
        if (elevation >= minSin) {
            return look.normalize();
        }
        Vec3 flat = look.subtract(up.scale(elevation));
        if (flat.lengthSqr() < 1.0E-8) {
            return up;
        }
        double minCos = Math.cos(Math.toRadians(MIN_ELEVATION_DEGREES));
        return flat.normalize().scale(minCos).add(up.scale(minSin)).normalize();
    }
}
