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
    /** How much faster the turn may get each tick, in degrees (so turns start gently). */
    public static final float TURN_ACCELERATION_DEGREES = 2.0F;
    /** Near the end, each tick turns this share of what's left (so turns end gently). */
    private static final float TURN_EASE = 0.3F;
    /** ... but at least this much, in degrees, so it does finish. */
    private static final float MIN_TURN_DEGREES = 1.5F;
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

    /** How far the frame turned last tick, in radians (turns speed up gradually). */
    private float lastTurn;
    /** True while crawling through a tight tunnel (see CaveMoves). */
    private boolean crawling;

    public boolean isCrawling() {
        return crawling;
    }

    public void setCrawling(boolean crawling) {
        this.crawling = crawling;
    }

    /** Times this entity crossed a planet's middle in a row without landing (see {@link StuckSafety}). */
    private int middleCrossings;

    /** Counts one more crossing of a planet's middle and returns the new count. */
    public int addMiddleCrossing() {
        middleCrossings = Math.min(middleCrossings + 1, 100);
        return middleCrossings;
    }

    public void clearMiddleCrossings() {
        middleCrossings = 0;
    }

    /** The last block this entity stood on with a floor face (see GravityFrames.floorUp). */
    private net.minecraft.core.BlockPos lastFloor;

    public net.minecraft.core.BlockPos lastFloor() {
        return lastFloor;
    }

    public void setLastFloor(net.minecraft.core.BlockPos pos) {
        lastFloor = pos;
    }

    /** Ticks since the hitbox last swung to another axis (see {@link StuckSafety}). */
    private int ticksSinceBoxTurn = 1000;
    /** Ticks in a row the hitbox has overlapped blocks (see {@link StuckSafety}). */
    private int stuckTicks;

    /** Counts up each tick, back to 0 when the hitbox swung to another axis this tick. */
    public void noteBoxTurn(boolean turned) {
        ticksSinceBoxTurn = turned ? 0 : Math.min(ticksSinceBoxTurn + 1, 1000);
    }

    public int ticksSinceBoxTurn() {
        return ticksSinceBoxTurn;
    }

    /** Counts up while stuck in blocks, back to 0 when free. Returns the new count. */
    public int updateStuck(boolean stuck) {
        stuckTicks = stuck ? Math.min(stuckTicks + 1, 1000) : 0;
        return stuckTicks;
    }

    public int stuckTicks() {
        return stuckTicks;
    }

    /** Goes back to where the frame was before this tick's turn (no room to turn). */
    public void undoTurn() {
        rotation.set(previous);
        lastTurn = 0.0F;
    }

    /** Center of the planet that pulled this entity last, so overlapping ranges don't flicker. */
    private Vec3 holdCenter;

    public Vec3 holdCenter() {
        return holdCenter;
    }

    public void setHoldCenter(Vec3 center) {
        holdCenter = center;
    }

    /** True while the jump key is held after a jump off a floating planet (one jump per press). */
    private boolean floatJumpHeld;

    /** Records the jump key while floating; returns true once per press. */
    public boolean pressFloatJump(boolean jumping) {
        boolean fresh = jumping && !floatJumpHeld;
        floatJumpHeld = jumping;
        return fresh;
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
            lastTurn = 0.0F;
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
        // Speed up gently, slow down gently, and never go past the top speed.
        float maxStep = Math.min(MAX_TURN_DEGREES_PER_TICK * Mth.DEG_TO_RAD,
                Math.min(lastTurn + TURN_ACCELERATION_DEGREES * Mth.DEG_TO_RAD,
                        Math.max(angle * TURN_EASE, MIN_TURN_DEGREES * Mth.DEG_TO_RAD)));
        lastTurn = Math.min(angle, maxStep);
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
