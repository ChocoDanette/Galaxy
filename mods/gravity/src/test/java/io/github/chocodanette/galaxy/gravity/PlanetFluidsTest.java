package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

class PlanetFluidsTest {
    @Test
    void eachTurnPointsUprightDownAlongTheLiquidsDown() {
        for (Direction down : Direction.values()) {
            int[] turned = PlanetFluids.turn(down, 0, -1, 0);
            assertArrayEquals(new int[] {down.getStepX(), down.getStepY(), down.getStepZ()}, turned, down.toString());
        }
    }

    @Test
    void turnsAreRotationsNotMirrors() {
        for (Direction down : Direction.values()) {
            int[] x = PlanetFluids.turn(down, 1, 0, 0);
            int[] y = PlanetFluids.turn(down, 0, 1, 0);
            int[] z = PlanetFluids.turn(down, 0, 0, 1);
            // x cross y must be z for a rotation (a mirror flips it).
            int[] cross = {x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]};
            assertArrayEquals(z, cross, down.toString());
        }
    }

    @Test
    void sidesAreTheFourDirectionsAcrossDown() {
        for (Direction down : Direction.values()) {
            Direction[] sides = PlanetFluids.sides(down);
            assertEquals(4, sides.length);
            for (Direction side : sides) {
                assertFalse(side.getAxis() == down.getAxis());
            }
        }
    }

    @Test
    void uprightNamesOnlyCallTheRealDownDown() {
        assertEquals(Direction.DOWN, PlanetFluids.uprightName(Direction.EAST, Direction.EAST));
        assertEquals(Direction.UP, PlanetFluids.uprightName(Direction.WEST, Direction.EAST));
        // World down is only sideways for a liquid falling east.
        assertEquals(Direction.NORTH, PlanetFluids.uprightName(Direction.DOWN, Direction.EAST));
        assertEquals(Direction.SOUTH, PlanetFluids.uprightName(Direction.SOUTH, Direction.EAST));
    }

    @Test
    void turnedNeighborsLandBesideTheBlock() {
        BlockPos origin = new BlockPos(10, 64, -3);
        // Upright "above" is the liquid's up, opposite its down.
        assertEquals(origin.relative(Direction.WEST),
                PlanetFluids.turnAround(Direction.EAST, origin, origin.above()));
        assertEquals(origin.relative(Direction.SOUTH),
                PlanetFluids.turnAround(Direction.SOUTH, origin, origin.below()));
    }

    @Test
    void liquidsFallIntoEachFaceOfACubePlanet() {
        GravityField cube = new GravityField(Vec3.ZERO, 10, 40, 1, GravityField.Shape.ROUNDED_CUBE);
        GravityWorld world = GravityWorld.of(List.of(cube));
        assertEquals(Direction.DOWN, PlanetFluids.computeDown(world, new BlockPos(0, 12, 0)));
        assertEquals(Direction.UP, PlanetFluids.computeDown(world, new BlockPos(1, -13, 2)));
        assertEquals(Direction.WEST, PlanetFluids.computeDown(world, new BlockPos(12, 3, -2)));
        assertEquals(Direction.SOUTH, PlanetFluids.computeDown(world, new BlockPos(-1, 0, -13)));
        // Away from every planet, vanilla.
        assertEquals(Direction.DOWN, PlanetFluids.computeDown(world, new BlockPos(500, 0, 0)));
    }
}
