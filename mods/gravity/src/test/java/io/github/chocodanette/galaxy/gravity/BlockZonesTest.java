package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

class BlockZonesTest {
    private static final GravityField CUBE = new GravityField(new Vec3(0.5, 100.5, 0.5), 10.5, 40, 1.0,
            GravityField.Shape.CUBE);

    @Test
    void faceBlocksHaveOneDown() {
        assertEquals(EnumSet.of(Direction.UP), CUBE.blockUps(new BlockPos(0, 110, 0)));
        assertEquals(EnumSet.of(Direction.EAST), CUBE.blockUps(new BlockPos(10, 100, 0)));
    }

    @Test
    void diagonalBlocksHaveTwoDowns() {
        // The block the top/east border runs through allows both.
        assertEquals(EnumSet.of(Direction.UP, Direction.EAST), CUBE.blockUps(new BlockPos(5, 105, 0)));
    }

    @Test
    void youKeepYourDownOnADiagonalBlock() {
        BlockPos diagonal = new BlockPos(5, 105, 0);
        assertEquals(Direction.EAST, CUBE.blockUp(diagonal, Direction.EAST));
        assertEquals(Direction.UP, CUBE.blockUp(diagonal, Direction.UP));
        // One block further onto the top face, only up is left.
        assertEquals(Direction.UP, CUBE.blockUp(new BlockPos(4, 106, 0), Direction.EAST));
    }
}
