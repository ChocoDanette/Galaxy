package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

class TiltedPlacementTest {
    @Test
    void nearestLookComesFirst() {
        assertEquals(Direction.EAST, TiltedPlacement.byLook(new Vec3(0.9, 0.3, 0.1))[0]);
        assertEquals(Direction.DOWN, TiltedPlacement.byLook(new Vec3(0.1, -0.9, 0.2))[0]);
    }

    @Test
    void facingSkipsYourOwnUpAxis() {
        // Standing on a planet's east side, looking mostly "up" (east) and a bit north:
        // east is your up, so the block faces north.
        assertEquals(Direction.NORTH, TiltedPlacement.horizontal(new Vec3(0.9, 0.0, -0.3), Direction.EAST));
        assertEquals(Direction.EAST, TiltedPlacement.horizontal(new Vec3(0.9, 0.0, -0.3), Direction.UP));
    }
}
