package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

class PlanetPathfinderTest {
    /** A world made of a set of solid cells; everything else is air. */
    private record Grid(Set<BlockPos> solid) implements PlanetPathfinder.Cells {
        @Override
        public boolean passable(int x, int y, int z) {
            return !solid.contains(new BlockPos(x, y, z));
        }

        @Override
        public boolean floor(int x, int y, int z) {
            return solid.contains(new BlockPos(x, y, z));
        }
    }

    /** A flat 21x21 floor whose surface faces {@code up}, with its top layer at the origin. */
    private static Set<BlockPos> floorFacing(Direction up) {
        Set<BlockPos> solid = new HashSet<>();
        Direction[] flat = PlanetPathfinder.flatDirections(up);
        Direction a = flat[0];
        Direction b = null;
        for (Direction d : flat) {
            if (d.getAxis() != a.getAxis()) {
                b = d;
                break;
            }
        }
        for (int i = -10; i <= 10; i++) {
            for (int j = -10; j <= 10; j++) {
                solid.add(BlockPos.ZERO.relative(a, i).relative(b, j));
            }
        }
        return solid;
    }

    @Test
    void walksAcrossEveryFace() {
        for (Direction up : Direction.values()) {
            Grid grid = new Grid(floorFacing(up));
            Direction[] flat = PlanetPathfinder.flatDirections(up);
            BlockPos start = BlockPos.ZERO.relative(up);
            BlockPos goal = start.relative(flat[0], 6).relative(flat[2], 4);
            PlanetPathfinder.Result result = PlanetPathfinder.find(grid, start, up, 2, Set.of(goal), 0, 500);
            assertNotNull(result, "face " + up);
            assertTrue(result.reached(), "face " + up);
            assertEquals(goal, result.cells().get(result.cells().size() - 1));
            for (BlockPos cell : result.cells()) {
                assertTrue(PlanetPathfinder.standable(grid, cell, up, 2), "standing on face " + up + " at " + cell);
            }
            // Diagonals keep it short: 6 by 4 needs about 6 moves, not 10.
            assertTrue(result.cells().size() <= 8, "path on " + up + " took " + result.cells().size());
        }
    }

    @Test
    void stepsUpAndDropsDownAlongUp() {
        Direction up = Direction.EAST;
        Set<BlockPos> solid = floorFacing(up);
        // A one-block wall along the way, sticking out of the east face.
        for (int z = -10; z <= 10; z++) {
            solid.add(new BlockPos(1, 3, z));
        }
        Grid grid = new Grid(solid);
        BlockPos start = new BlockPos(1, 0, 0);
        BlockPos goal = new BlockPos(1, 6, 0);
        PlanetPathfinder.Result result = PlanetPathfinder.find(grid, start, up, 1, Set.of(goal), 0, 500);
        assertNotNull(result);
        assertTrue(result.reached());
        assertTrue(result.cells().contains(new BlockPos(2, 3, 0)), "climbs on top of the wall");
    }

    @Test
    void goesAsCloseAsPossibleWhenBlocked() {
        Direction up = Direction.DOWN;
        Set<BlockPos> solid = floorFacing(up);
        // A tall wall that cuts the underside in two.
        for (int x = -10; x <= 10; x++) {
            for (int y = -5; y <= -1; y++) {
                solid.add(new BlockPos(x, y, 3));
            }
        }
        Grid grid = new Grid(solid);
        BlockPos start = new BlockPos(0, -1, 0);
        BlockPos goal = new BlockPos(0, -1, 8);
        PlanetPathfinder.Result result = PlanetPathfinder.find(grid, start, up, 2, Set.of(goal), 0, 2000);
        assertNotNull(result);
        assertFalse(result.reached());
        assertEquals(2, result.cells().get(result.cells().size() - 1).getZ());
    }
}
