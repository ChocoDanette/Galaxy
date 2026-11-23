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

    /** Turns toward a target "up" (unit vector), at most {@link #MAX_TURN_DEGREES_PER_TICK}. */
    public void tick(Vec3 targetUp) {
        previous.set(rotation);
        Vector3f currentUp = rotation.transform(new Vector3f(0, 1, 0));
        Vector3f target = new Vector3f((float) targetUp.x, (float) targetUp.y, (float) targetUp.z).normalize();
        float angle = currentUp.angle(target);
        if (angle < 1.0E-4F) {
            return;
        }
        Quaternionf turn = new Quaternionf().rotationTo(currentUp, target);
        float maxStep = MAX_TURN_DEGREES_PER_TICK * Mth.DEG_TO_RAD;
        if (angle > maxStep) {
            turn = new Quaternionf().slerp(turn, maxStep / angle);
        }
        rotation.premul(turn).normalize();
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
