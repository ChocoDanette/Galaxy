package io.github.chocodanette.galaxy.gravity;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The local "up" of one entity, as a rotation from world space to the entity's own frame.
 *
 * Yaw and pitch stay in the entity's own frame, so mouse look keeps working unchanged:
 * this rotation is applied on top for movement, aiming and the camera. It turns toward the
 * planet's "up" a little each tick, so the view tilts gradually as you walk around a planet.
 */
public final class GravityFrame {
    /** Fastest the frame may turn, in degrees per tick (20 ticks per second). */
    public static final float MAX_TURN_DEGREES_PER_TICK = 9.0F;
    /** Below this angle from world up, the entity counts as upright and vanilla code runs untouched. */
    private static final float TILT_THRESHOLD_RADIANS = 0.002F;

    private final Quaternionf rotation = new Quaternionf();
    private final Quaternionf previous = new Quaternionf();
    /**
     * Distance to the planet's surface of the spot the entity last stood on, so gravity knows
     * how high above it the entity has risen. NaN until it has touched the ground.
     */
    private double takeoffDistance = Double.NaN;
    /**
     * True during free flight in space: the frame follows the player's view instead of turning
     * toward a planet.
     */
    private boolean free;
    /** The way gravity pulls this entity, as picked by the last frame update (null = unknown). */
    private Vec3 gravityDown;
    /** Ticks since the entity last stood on something. */
    private int ticksOffGround = 1000;

    /** Block direction "up" points along in step gravity (null until first used). */
    private net.minecraft.core.Direction stepUp;

    public net.minecraft.core.Direction stepUp() {
        return stepUp;
    }

    public void setStepUp(net.minecraft.core.Direction stepUp) {
        this.stepUp = stepUp;
    }

    /** Center of the planet that pulled this entity last, so overlapping ranges don't flicker. */
    private Vec3 holdCenter;

    public Vec3 holdCenter() {
        return holdCenter;
    }

    public void setHoldCenter(Vec3 center) {
        holdCenter = center;
    }

    /** Ticks spent crouching on a planet, for the charged jump (see {@link Launch}). */
    private int chargeTicks;
    /** True from a charged jump until landing: half pull, little air drag. */
    private boolean launched;

    /** Counts crouch ticks while charging, back to 0 otherwise. Returns the new count. */
    public int updateCharge(boolean charging) {
        chargeTicks = charging ? Math.min(chargeTicks + 1, 1000) : 0;
        return chargeTicks;
    }

    public int chargeTicks() {
        return chargeTicks;
    }

    public boolean isLaunched() {
        return launched;
    }

    public void setLaunched(boolean launched) {
        this.launched = launched;
    }

    public Vec3 gravityDown() {
        return gravityDown;
    }

    public void setGravityDown(Vec3 down) {
        gravityDown = down;
    }

    /** Counts up while in the air, back to 0 on landing. Returns the new count. */
    public int updateGround(boolean onGround) {
        ticksOffGround = onGround ? 0 : Math.min(ticksOffGround + 1, 1000);
        return ticksOffGround;
    }

    public boolean isFree() {
        return free;
    }

    public void setFree(boolean free) {
        this.free = free;
    }

    /** A copy of the current rotation. */
    public Quaternionf currentRotation() {
        return new Quaternionf(rotation);
    }

    /** Jumps straight to a rotation, with no turning in between (used by free flight). */
    public void snapTo(Quaternionf target) {
        rotation.set(target).normalize();
        previous.set(rotation);
    }

    /** Turns toward a target "up" (unit vector), at most {@link #MAX_TURN_DEGREES_PER_TICK}. */
    public void tick(Vec3 targetUp) {
        tick(targetUp, null);
    }

    /** Past this angle the shortest turn has no clear side, so we pick one (see below). */
    private static final float FLIP_RADIANS = 170.0F * Mth.DEG_TO_RAD;

    /**
     * Same, for a "up" that may flip right over (between two planets facing each other).
     * Turning by 180 degrees could go any way round, so it rolls around {@code facing} (the
     * way the entity faces): you keep looking where you were going while the world turns
     * over, instead of a random tumble.
     */
    public void tick(Vec3 targetUp, Vec3 facing) {
        previous.set(rotation);
        Vector3f currentUp = rotation.transform(new Vector3f(0, 1, 0));
        Vector3f target = new Vector3f((float) targetUp.x, (float) targetUp.y, (float) targetUp.z).normalize();
        float angle = currentUp.angle(target);
        if (angle < 1.0E-4F) {
            return;
        }
        Quaternionf turn = new Quaternionf().rotationTo(currentUp, target);
        if (angle > FLIP_RADIANS && facing != null) {
            Vector3f axis = new Vector3f((float) facing.x, (float) facing.y, (float) facing.z);
            axis.sub(new Vector3f(currentUp).mul(axis.dot(currentUp)));
            if (axis.lengthSquared() > 1.0E-6F) {
                turn = new Quaternionf().rotationAxis(angle, axis.normalize());
            }
        }
        float maxStep = MAX_TURN_DEGREES_PER_TICK * Mth.DEG_TO_RAD;
        if (angle > maxStep) {
            turn = new Quaternionf().slerp(turn, maxStep / angle);
        }
        rotation.premul(turn).normalize();
    }

    /**
     * Once the frame is back upright, it can still carry a turn around "up" picked up on the
     * way (walking over a few edges, or round a corner). While tilted that turn is part of
     * the view, but upright the game ignores the frame, so the view would jump sideways by
     * that much. This removes the turn from the frame and returns it as a change of yaw, in
     * degrees, so the caller can add it to the entity's yaw and the view stays where it was.
     * Returns 0 when there is nothing to remove.
     */
    public float settleTwist() {
        if (isTilted()) {
            return 0.0F;
        }
        Vector3f forward = rotation.transform(new Vector3f(0, 0, 1));
        float twist = (float) Math.atan2(forward.x, forward.z);
        if (Math.abs(twist) < 1.0E-5F) {
            return 0.0F;
        }
        Quaternionf undo = new Quaternionf().rotationY(-twist);
        rotation.mul(undo).normalize();
        previous.mul(undo).normalize();
        return -twist * Mth.RAD_TO_DEG;
    }

    public double takeoffDistance() {
        return takeoffDistance;
    }

    public void setTakeoffDistance(double distance) {
        takeoffDistance = distance;
    }

    /** True when the entity is tilted away from world up, so our movement rules apply. */
    public boolean isTilted() {
        return upVector().y < Math.cos(TILT_THRESHOLD_RADIANS);
    }

    public Vector3f upVector() {
        return rotation.transform(new Vector3f(0, 1, 0));
    }

    public Vec3 up() {
        Vector3f up = upVector();
        return new Vec3(up.x, up.y, up.z);
    }

    /** Rotation between the previous and current tick, for smooth rendering. */
    public Quaternionf rotation(float partialTick) {
        return new Quaternionf(previous).slerp(rotation, partialTick);
    }

    public Vec3 up(float partialTick) {
        Vector3f up = rotation(partialTick).transform(new Vector3f(0, 1, 0));
        return new Vec3(up.x, up.y, up.z);
    }

    /** Local frame to world space. */
    public Vec3 toWorld(Vec3 local) {
        Vector3f v = rotation.transform(new Vector3f((float) local.x, (float) local.y, (float) local.z));
        return new Vec3(v.x, v.y, v.z);
    }

    /** World space to local frame. */
    public Vec3 toLocal(Vec3 world) {
        Vector3f v = rotation.transformInverse(new Vector3f((float) world.x, (float) world.y, (float) world.z));
        return new Vec3(v.x, v.y, v.z);
    }
}
