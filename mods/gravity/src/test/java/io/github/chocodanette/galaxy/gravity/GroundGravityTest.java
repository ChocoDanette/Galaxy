package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class GroundGravityTest {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static void assertNear(Vec3 expected, Vec3 actual, double tolerance) {
        assertTrue(expected.distanceTo(actual) < tolerance, "expected " + expected + " but got " + actual);
    }

    @Test
    void flatGroundPointsStraightUp() {
        GroundGravity.Solidity floor = (x, y, z) -> y < 0;
        Vec3 up = GroundGravity.upAt(floor, new Vec3(0.5, 0, 0.5), UP, new Vec3(0.6, 0.8, 0)).orElseThrow();
        assertNear(UP, up, 0.06);
    }

    @Test
    void flatGroundIgnoresThePlanetLean() {
        // Even when the planet's center is off to the side, standing on flat ground stays flat.
        GroundGravity.Solidity floor = (x, y, z) -> y < 0;
        Vec3 tilted = new Vec3(0.7071, 0.7071, 0);
        Vec3 up = GroundGravity.upAt(floor, new Vec3(3.3, 0, 0.5), UP, tilted).orElseThrow();
        assertTrue(up.y > 0.97, "up was " + up);
    }

    @Test
    void wallsYouWalkPastBarelyTilt() {
        GroundGravity.Solidity corridor = (x, y, z) -> y < 0 || x >= 2;
        Vec3 up = GroundGravity.upAt(corridor, new Vec3(1.5, 0, 0.5), UP, UP).orElseThrow();
        assertTrue(up.y > 0.85, "up was " + up);
    }

    @Test
    void atAnEdgeUpLeansOverIt() {
        // Ground for x < 0, a drop beyond: stepping out over the edge, up starts leaning toward +x.
        GroundGravity.Solidity ledge = (x, y, z) -> y < 0 && x < 0;
        Vec3 up = GroundGravity.upAt(ledge, new Vec3(0.4, 0, 0.5), UP, UP).orElseThrow();
        assertTrue(up.x > 0.1 && up.y > 0.5, "up was " + up);
        // Once down the side, the side face is the ground.
        Vec3 side = GroundGravity.upAt(ledge, new Vec3(0.3, -1.5, 0.5), up, UP).orElseThrow();
        assertTrue(side.x > 0.9, "up on the side was " + side);
    }

    @Test
    void standingOnASideFacePointsOutOfIt() {
        GroundGravity.Solidity wall = (x, y, z) -> x < 0;
        Vec3 east = new Vec3(1, 0, 0);
        Vec3 up = GroundGravity.upAt(wall, new Vec3(0, 3.5, 0.5), east, east).orElseThrow();
        assertNear(east, up, 0.06);
    }

    @Test
    void nothingNearbyMeansNoGround() {
        GroundGravity.Solidity space = (x, y, z) -> false;
        assertEquals(Optional.empty(), GroundGravity.upAt(space, Vec3.ZERO, UP, UP));
        GroundGravity.Solidity far = (x, y, z) -> y < -5;
        assertEquals(Optional.empty(), GroundGravity.upAt(far, Vec3.ZERO, UP, UP));
    }
}
