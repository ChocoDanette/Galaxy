package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
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
    void snapsToNearestFace() {
        assertEquals(Direction.DOWN, GravityMath.nearestDirection(new Vec3(0.1, -0.9, 0), Direction.DOWN));
        assertEquals(Direction.EAST, GravityMath.nearestDirection(new Vec3(0.95, 0.1, 0), Direction.DOWN));
        assertEquals(Direction.UP, GravityMath.nearestDirection(new Vec3(0, 1, 0), Direction.DOWN));
    }

    @Test
    void keepsCurrentFaceNearAnEdge() {
        // Just past 45 degrees between down and east: not enough to switch away from down.
        Vec3 nearEdge = new Vec3(0.72, -0.69, 0).normalize();
        assertEquals(Direction.DOWN, GravityMath.nearestDirection(nearEdge, Direction.DOWN));
        assertEquals(Direction.EAST, GravityMath.nearestDirection(nearEdge, Direction.EAST));
        // Clearly closer to east: switch.
        assertEquals(Direction.EAST, GravityMath.nearestDirection(new Vec3(0.9, -0.4, 0).normalize(), Direction.DOWN));
    }
}
