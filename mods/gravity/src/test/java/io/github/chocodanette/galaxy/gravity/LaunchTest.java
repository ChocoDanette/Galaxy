package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class LaunchTest {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    @Test
    void chargeLengthPicksTheJump() {
        assertEquals(Launch.Kind.NONE, Launch.kindFor(Launch.HOP_CHARGE_TICKS - 1));
        assertEquals(Launch.Kind.HOP, Launch.kindFor(Launch.HOP_CHARGE_TICKS));
        assertEquals(Launch.Kind.LAUNCH, Launch.kindFor(Launch.LAUNCH_CHARGE_TICKS));
    }

    @Test
    void flyWhereYouLookButNeverIntoTheGround() {
        Vec3 steep = new Vec3(0, 1, 1).normalize();
        assertEquals(0.0, Launch.direction(steep, UP).distanceTo(steep), 1.0E-9);

        double minSin = Math.sin(Math.toRadians(Launch.MIN_ELEVATION_DEGREES));
        Vec3 level = Launch.direction(new Vec3(1, 0, 0), UP);
        assertEquals(minSin, level.y, 1.0E-9);
        assertTrue(level.x > 0.8);
        Vec3 down = Launch.direction(new Vec3(1, -1, 0).normalize(), UP);
        assertEquals(minSin, down.y, 1.0E-9);
        assertEquals(0.0, Launch.direction(new Vec3(0, -1, 0), UP).distanceTo(UP), 1.0E-9);

        // Works the same in a tilted frame.
        Vec3 east = new Vec3(1, 0, 0);
        Vec3 sideways = Launch.direction(new Vec3(0, 0, 1), east);
        assertEquals(minSin, sideways.dot(east), 1.0E-9);
    }

    /** Flies like after a charged jump: half pull, low drag. Returns the farthest distance from the planet's surface. */
    private static double fly(GravityField field, Vec3 start, Vec3 velocity, int ticks) {
        Vec3 pos = start;
        double farthest = 0;
        for (int i = 0; i < ticks; i++) {
            if (field.contains(pos)) {
                Vec3 down = GravityMath.downDirection(field, pos, GravityMath.VANILLA_DOWN);
                velocity = velocity.add(down.scale(0.08 * field.strength() * Launch.PULL_SHARE));
            }
            velocity = velocity.scale(Launch.DRAG);
            pos = pos.add(velocity);
            farthest = Math.max(farthest, field.distanceToSurface(pos));
            if (!field.contains(pos)) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return farthest;
    }

    @Test
    void aLaunchLeavesEvenTheBiggestPlanet() {
        Vec3 center = new Vec3(0.5, 100.5, 0.5);
        double half = 22;
        // The galaxy's biggest planets: range = extent + 10 + half/2 (bumpy surface up to 10% higher).
        GravityField big = new GravityField(center, half, half * 1.1 + 10 + half / 2, 1.0, GravityField.Shape.ROUNDED_CUBE);
        GravityField hollow = new GravityField(center, 28, 28 * 1.1 + 10 + 14, 1.0, GravityField.Shape.HOLLOW);
        for (GravityField field : List.of(big, hollow)) {
            Vec3 start = center.add(0, field.radius() + 1, 0.3);
            assertEquals(Double.POSITIVE_INFINITY, fly(field, start, UP.scale(Launch.LAUNCH_SPEED), 400),
                    "launch fell back to " + field.shape());
        }
    }

    @Test
    void aHopCrossesAGatewayGapButStaysNearTheGround() {
        Vec3 center = new Vec3(0.5, 100.5, 0.5);
        GravityField tiny = new GravityField(center, 3.5, 3.5 + 10 + 1.75, 1.0, GravityField.Shape.SPHERE);
        Vec3 start = center.add(0, tiny.radius() + 0.1, 0);
        double peak = fly(tiny, start, UP.scale(Launch.HOP_SPEED), 400);
        // Gateway planets are 4.5 blocks apart, and the next one takes over halfway.
        assertTrue(peak > 4.5, "hop only rises " + peak);
        assertTrue(peak < tiny.range() - tiny.radius(), "a hop shouldn't throw you into space: " + peak);
    }
}
