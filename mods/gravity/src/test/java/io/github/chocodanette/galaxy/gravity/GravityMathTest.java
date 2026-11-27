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
    void cubeFacesPullStraightIn() {
        GravityField cube = new GravityField(Vec3.ZERO, 5, 25, 1, GravityField.Shape.CUBE);
        // Anywhere on the flat part of a face, down is straight into the face (no pull to its middle).
        assertVec(new Vec3(0, -1, 0), GravityMath.downDirection(List.of(cube), new Vec3(2.5, 5, -2.5)));
        assertVec(new Vec3(-1, 0, 0), GravityMath.downDirection(List.of(cube), new Vec3(5, 2, 2)));
        assertVec(new Vec3(0, 0, 1), GravityMath.downDirection(List.of(cube), new Vec3(-1, -3, -5)));
    }

    @Test
    void cubeSurfaceDistance() {
        GravityField cube = new GravityField(Vec3.ZERO, 5, 25, 1, GravityField.Shape.CUBE);
        assertEquals(0.0, cube.distanceToSurface(new Vec3(1, 5, -2)), EPS);
        assertEquals(3.0, cube.distanceToSurface(new Vec3(8, 0, 0)), EPS);
    }

    @Test
    void pullIsTheSameEverywhereOnAPlanet() {
        GravityField planet = new GravityField(Vec3.ZERO, 20, 60, 0.8);
        for (Vec3 pos : List.of(new Vec3(0, 20, 0), new Vec3(0, 22, 0), new Vec3(0, 40, 0), new Vec3(0, 59, 0), new Vec3(0, 5, 0))) {
            assertEquals(1.0, GravityMath.pullFactor(planet, pos, 0.0), EPS);
            assertEquals(0.8, GravityMath.pull(new GravityWorld(List.of(planet), 0.08, List.of()), pos, 0.0)
                    .orElseThrow().scale(), EPS);
        }
    }

    @Test
    void spacePullDragsTowardTheNearestPlanetOnlyWhenOn() {
        GravityField a = new GravityField(Vec3.ZERO, 10, 20, 1);
        GravityField b = new GravityField(new Vec3(100, 0, 0), 10, 20, 1);
        Vec3 between = new Vec3(40, 0, 0);
        assertTrue(GravityMath.pull(GravityWorld.of(List.of(a, b)), between, Double.NaN).isEmpty());

        GravityWorld space = new GravityWorld(List.of(a, b), 0.1, List.of());
        GravityMath.Pull pull = GravityMath.pull(space, between, Double.NaN).orElseThrow();
        assertVec(new Vec3(-1, 0, 0), pull.down());
        assertEquals(0.1, pull.scale(), EPS);
        assertTrue(!pull.inField());

        // A home planet pulls harder and from further away.
        GravityWorld home = new GravityWorld(List.of(a, b), 0.1, List.of(b.center()));
        GravityMath.Pull homePull = GravityMath.pull(home, new Vec3(45, 0, 0), Double.NaN).orElseThrow();
        assertVec(new Vec3(1, 0, 0), homePull.down());
        assertEquals(0.15, homePull.scale(), EPS);
    }

    @Test
    void nextToEachOfTwoOverlappingPlanetsItIsThatPlanetsPull() {
        GravityField a = new GravityField(Vec3.ZERO, 10, 30, 1);
        GravityField b = new GravityField(new Vec3(40, 0, 0), 10, 30, 1);
        GravityWorld world = GravityWorld.of(List.of(a, b));
        assertVec(new Vec3(0, -1, 0), GravityMath.pull(world, new Vec3(0, 11, 0), Double.NaN).orElseThrow().down());
        assertVec(new Vec3(1, 0, 0), GravityMath.pull(world, new Vec3(29, 1, 0), Double.NaN).orElseThrow().down());
    }

    @Test
    void thePlanetYouCameFromHoldsYouUntilPastTheMiddle() {
        // Two planets facing each other: down flips between them, a little past halfway.
        GravityField a = new GravityField(Vec3.ZERO, 5, 15, 1);
        GravityField b = new GravityField(new Vec3(0, 20, 0), 5, 15, 1);
        GravityWorld world = GravityWorld.of(List.of(a, b));
        Vec3 justPast = new Vec3(0, 10.2, 0);
        assertEquals(b, GravityMath.pull(world, justPast, Double.NaN).orElseThrow().field());
        GravityMath.Pull held = GravityMath.pull(world, justPast, Double.NaN, a.center()).orElseThrow();
        assertEquals(a, held.field());
        assertVec(new Vec3(0, -1, 0), held.down());
        assertEquals(b, GravityMath.pull(world, new Vec3(0, 11.5, 0), Double.NaN, a.center()).orElseThrow().field());
    }

    @Test
    void aFloatingPlanetPushesCloseInAndPullsFurtherOut() {
        GravityField tiny = new GravityField(Vec3.ZERO, 3, 15, 1, GravityField.Shape.SPHERE, 3.0);
        GravityWorld world = GravityWorld.of(List.of(tiny));
        GravityMath.Pull close = GravityMath.pull(world, new Vec3(0, 4, 0), Double.NaN).orElseThrow();
        assertTrue(close.floating());
        assertVec(new Vec3(0, -1, 0), close.down());
        assertTrue(close.scale() < 0, "close in it should push, scale " + close.scale());
        assertTrue(close.force().y > 0);
        assertEquals(0.0, GravityMath.pull(world, new Vec3(0, 6, 0), Double.NaN).orElseThrow().scale(), EPS);
        GravityMath.Pull far = GravityMath.pull(world, new Vec3(0, 10, 0), Double.NaN).orElseThrow();
        assertEquals(GravityMath.FLOAT_ZONE_GRAVITY, far.scale(), EPS);
    }

    @Test
    void gravityIsWeakNearAFloatingPlanetEvenOnTheBigPlanetNextToIt() {
        GravityField big = new GravityField(Vec3.ZERO, 10, 30, 1);
        GravityField tiny = new GravityField(new Vec3(20, 0, 0), 3, 8.5, 1, GravityField.Shape.SPHERE, 3.0);
        GravityWorld world = GravityWorld.of(List.of(big, tiny));
        // Inside its range, the floating planet catches you though the big one is as close.
        assertEquals(tiny, GravityMath.pull(world, new Vec3(13, 0, 0), Double.NaN).orElseThrow().field());
        GravityMath.Pull onBig = GravityMath.pull(world, new Vec3(10.5, 0, 0), Double.NaN).orElseThrow();
        assertEquals(big, onBig.field());
        assertTrue(!onBig.floating());
        assertTrue(onBig.scale() < 0.31 && onBig.scale() > 0.2, "scale " + onBig.scale());
        // Far from the tiny planet, normal gravity.
        assertEquals(1.0, GravityMath.pull(world, new Vec3(-10.5, 0, 0), Double.NaN).orElseThrow().scale(), EPS);
    }

    @Test
    void cubeZonesSplitAlongTheDiagonals() {
        GravityField cube = new GravityField(Vec3.ZERO, 5, 25, 1, GravityField.Shape.ROUNDED_CUBE);
        // Over the rounded edge, down is still one block direction: the zone you are in.
        assertVec(new Vec3(0, -1, 0), GravityMath.downDirection(List.of(cube), new Vec3(4.9, 5, 0)));
        assertVec(new Vec3(-1, 0, 0), GravityMath.downDirection(List.of(cube), new Vec3(5.1, 5, 0)));
        // Deep inside, the zones run all the way to the middle.
        assertVec(new Vec3(0, 0, 1), GravityMath.downDirection(List.of(cube), new Vec3(0.5, 1, -2)));
    }

    @Test
    void zonesKeepTheCurrentOneUntilPastTheBuffer() {
        GravityField cube = new GravityField(Vec3.ZERO, 10, 30, 1, GravityField.Shape.CUBE);
        Direction up = Direction.UP;
        // Walking from the top over the east edge: up holds until a block past the diagonal.
        for (double x = 8; x <= 13; x += 0.1) {
            Vec3 pos = new Vec3(x, 10.5, 0);
            up = cube.zoneUp(pos, up);
            if (x < 10.5 + GravityField.ZONE_BUFFER - 0.05) {
                assertEquals(Direction.UP, up, "switched too early at x=" + x);
            }
        }
        assertEquals(Direction.EAST, up);
        // Standing on the diagonal itself never flips.
        Direction held = Direction.EAST;
        for (int i = 0; i < 10; i++) {
            held = cube.zoneUp(new Vec3(11, 11, 0), held);
        }
        assertEquals(Direction.EAST, held);
    }

    @Test
    void roundPlanetsUseTheSameSixZones() {
        GravityField ball = new GravityField(Vec3.ZERO, 12, 30, 1);
        assertEquals(Direction.UP, ball.zoneUp(new Vec3(3, 12, 2)));
        assertEquals(Direction.NORTH, ball.zoneUp(new Vec3(4, -3, -11)));
    }

    @Test
    void hollowPlanetPullsInwardOutsideAndOntoTheInsideWallsInside() {
        // Half size 20: a 5 block shell, so the room inside reaches 15 blocks from the center.
        GravityField hollow = new GravityField(Vec3.ZERO, 20, 40, 1, GravityField.Shape.HOLLOW);
        assertEquals(15.0, hollow.innerRadius(), EPS);
        assertVec(new Vec3(-1, 0, 0), GravityMath.downDirection(List.of(hollow), new Vec3(25, 0, 0)));
        assertVec(new Vec3(1, 0, 0), GravityMath.downDirection(List.of(hollow), new Vec3(12, 3, 0)));
        assertVec(new Vec3(0, 0, -1), GravityMath.downDirection(List.of(hollow), new Vec3(2, 0, -10)));
        assertEquals(0.0, hollow.distanceToSurface(new Vec3(15, 0, 0)), EPS);
        assertEquals(3.0, hollow.distanceToSurface(new Vec3(12, 0, 0)), EPS);
        assertEquals(5.0, hollow.distanceToSurface(new Vec3(0, 0, 25)), EPS);
        // The middle of the shell is the border; the side you came from holds for a block.
        assertEquals(Direction.UP, hollow.zoneUp(new Vec3(0, 17, 0), Direction.UP));
        assertEquals(Direction.DOWN, hollow.zoneUp(new Vec3(0, 16, 0), Direction.UP));
    }

    @Test
    void donutWalksOnTopBottomAndBothWalls() {
        // Half width 20, tube half 6: the tube's middle line runs 14 blocks out.
        GravityField donut = new GravityField(Vec3.ZERO, 20, 40, 1, GravityField.Shape.DONUT);
        assertEquals(Direction.UP, donut.zoneUp(new Vec3(14, 7, 3)));
        assertEquals(Direction.DOWN, donut.zoneUp(new Vec3(-14, -7, 0)));
        assertEquals(Direction.EAST, donut.zoneUp(new Vec3(21, 1, 0)));
        // The inner wall, facing the hole: down points out into the ring.
        assertEquals(Direction.WEST, donut.zoneUp(new Vec3(7, 0, 2)));
        assertEquals(Direction.NORTH, donut.zoneUp(new Vec3(2, -1, -21)));
        assertEquals(0.0, donut.distanceToSurface(new Vec3(14, 6, 0)), EPS);
        assertEquals(0.0, donut.distanceToSurface(new Vec3(8, 0, 1)), EPS);
    }

    @Test
    void discPullsDownOnTopUpBelowAndInwardOnTheRim() {
        // Radius 20: 5 blocks thick on each side of its middle.
        GravityField disc = new GravityField(Vec3.ZERO, 20, 40, 1, GravityField.Shape.DISC);
        assertEquals(Direction.UP, disc.zoneUp(new Vec3(5, 6, -3)));
        assertEquals(Direction.DOWN, disc.zoneUp(new Vec3(-10, -6, 4)));
        assertEquals(Direction.EAST, disc.zoneUp(new Vec3(21, 1, 0)));
        assertEquals(Direction.NORTH, disc.zoneUp(new Vec3(2, -2, -21)));
        assertEquals(0.0, disc.distanceToSurface(new Vec3(3, 5, 0)), EPS);
    }

    @Test
    void middleIsWhereOppositeZonesMeet() {
        GravityField cube = new GravityField(Vec3.ZERO, 10, 30, 1, GravityField.Shape.CUBE);
        assertEquals(1.0, cube.distanceToMiddle(new Vec3(1, -0.5, 0)), EPS);
        GravityField disc = new GravityField(Vec3.ZERO, 20, 40, 1, GravityField.Shape.DISC);
        assertEquals(0.5, disc.distanceToMiddle(new Vec3(8, 0.5, 3)), EPS);
    }
}
