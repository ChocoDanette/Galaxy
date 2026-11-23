package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class GravityMathTest {
    private static final double EPS = 1.0E-9;

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS);
        assertEquals(expected.y, actual.y, EPS);
        assertEquals(expected.z, actual.z, EPS);
    }

    @Test
    void pullsTowardCenterOnEverySide() {
        GravityField planet = new GravityField(Vec3.ZERO, 20, 60, 1);
        assertVec(new Vec3(0, -1, 0), GravityMath.downDirection(List.of(planet), new Vec3(0, 25, 0)));
        assertVec(new Vec3(0, 1, 0), GravityMath.downDirection(List.of(planet), new Vec3(0, -25, 0)));
        assertVec(new Vec3(-1, 0, 0), GravityMath.downDirection(List.of(planet), new Vec3(25, 0, 0)));
    }

    @Test
    void fallsBackToVanillaOutsideEveryField() {
        GravityField planet = new GravityField(Vec3.ZERO, 20, 60, 1);
        assertVec(GravityMath.VANILLA_DOWN, GravityMath.downDirection(List.of(planet), new Vec3(0, 100, 0)));
        assertVec(GravityMath.VANILLA_DOWN, GravityMath.downDirection(List.of(), Vec3.ZERO));
    }

    @Test
    void nearestSurfaceWinsWhereFieldsOverlap() {
        GravityField big = new GravityField(new Vec3(0, 0, 0), 40, 100, 1);
        GravityField small = new GravityField(new Vec3(80, 0, 0), 10, 40, 1);
        // 12 blocks from the small planet's surface, 18 from the big one's.
        Vec3 pos = new Vec3(58, 0, 0);
        assertTrue(GravityMath.governingField(List.of(big, small), pos).orElseThrow() == small);
        assertVec(new Vec3(1, 0, 0), GravityMath.downDirection(List.of(big, small), pos));
    }

    @Test
    void centerUsesFallback() {
        GravityField planet = new GravityField(Vec3.ZERO, 20, 60, 1);
        assertVec(GravityMath.VANILLA_DOWN, GravityMath.downDirection(List.of(planet), Vec3.ZERO));
    }

    @Test
    void rejectsInvalidFields() {
        assertThrows(IllegalArgumentException.class, () -> new GravityField(Vec3.ZERO, 0, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new GravityField(Vec3.ZERO, 20, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new GravityField(Vec3.ZERO, 20, 30, -1));
    }

    @Test
    void cubeFacesPullStraightIn() {
        GravityField cube = new GravityField(Vec3.ZERO, 5, 25, 1, GravityField.Shape.CUBE);
        // Anywhere on the flat part of a face, down is straight into the face (no pull to its middle).
        assertVec(new Vec3(0, -1, 0), GravityMath.downDirection(List.of(cube), new Vec3(2.5, 5, -2.5)));
        assertVec(new Vec3(-1, 0, 0), GravityMath.downDirection(List.of(cube), new Vec3(5, 2, 2)));
        assertVec(new Vec3(0, 0, 1), GravityMath.downDirection(List.of(cube), new Vec3(-1, -3, -5)));
    }

    @Test
    void cubeEdgesBendGradually() {
        GravityField cube = new GravityField(Vec3.ZERO, 5, 25, 1, GravityField.Shape.CUBE);
        Vec3 corner = GravityMath.downDirection(List.of(cube), new Vec3(5, 5, 0));
        double s = Math.sqrt(0.5);
        assertVec(new Vec3(-s, -s, 0), corner);
        Vec3 nearEdge = GravityMath.downDirection(List.of(cube), new Vec3(4, 5, 0));
        assertTrue(nearEdge.y < -s && nearEdge.x < 0, "tilts partway near the edge");
    }

    @Test
    void cubeSurfaceDistance() {
        GravityField cube = new GravityField(Vec3.ZERO, 5, 25, 1, GravityField.Shape.CUBE);
        assertEquals(0.0, cube.distanceToSurface(new Vec3(1, 5, -2)), EPS);
        assertEquals(3.0, cube.distanceToSurface(new Vec3(8, 0, 0)), EPS);
    }

    @Test
    void pullIsFullAtTheSurfaceAndFadesTowardTheEdge() {
        GravityField planet = new GravityField(Vec3.ZERO, 20, 60, 1);
        assertEquals(1.0, GravityMath.pullFactor(planet, new Vec3(0, 20, 0), Double.NaN), EPS);
        assertEquals(0.25, GravityMath.pullFactor(planet, new Vec3(0, 40, 0), Double.NaN), EPS);
        assertEquals(GravityMath.MIN_PULL_FACTOR, GravityMath.pullFactor(planet, new Vec3(0, 60, 0), Double.NaN), EPS);
        // Inside the planet the pull stays full.
        assertEquals(1.0, GravityMath.pullFactor(planet, new Vec3(0, 5, 0), Double.NaN), EPS);
    }

    @Test
    void pullWeakensAboveTheTakeoffSpotOnly() {
        GravityField planet = new GravityField(Vec3.ZERO, 20, 1000, 1);
        double onGround = GravityMath.pullFactor(planet, new Vec3(0, 20, 0), 0.0);
        double twoUp = GravityMath.pullFactor(planet, new Vec3(0, 22, 0), 0.0);
        double belowTakeoff = GravityMath.pullFactor(planet, new Vec3(0, 22, 0), 5.0);
        assertEquals(1.0, onGround, 1.0E-6);
        assertTrue(twoUp < 0.3, "two blocks up should be much lighter, was " + twoUp);
        assertTrue(belowTakeoff > 0.99, "below the take-off spot pull is full, was " + belowTakeoff);
    }
}
