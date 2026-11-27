package io.github.chocodanette.galaxy.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;
import io.github.chocodanette.galaxy.worldgen.farming.FaceGrid;

class PlanetFarmingTest {
    /** Grass is turned toward the planet's "up"; that side should almost always be open air. */
    @Test
    void surfaceGrassFacesOutOfThePlanet() {
        for (GravityField.Shape shape : new GravityField.Shape[] {GravityField.Shape.SPHERE, GravityField.Shape.ROUNDED_CUBE}) {
            Planet planet = new Planet(7L, Vec3.ZERO, 12, shape, PlanetTheme.VERDANT, 0.0, 1.0);
            Set<Direction> faces = EnumSet.noneOf(Direction.class);
            int surface = 0;
            int open = 0;
            for (int x = -16; x <= 16; x++) {
                for (int y = -16; y <= 16; y++) {
                    for (int z = -16; z <= 16; z++) {
                        Material m = planet.materialAt(x, y, z);
                        if (m != Material.TOP && m != Material.SIDE) {
                            continue;
                        }
                        if (!hasOpenSide(planet, x, y, z)) {
                            continue; // buried: nobody sees it
                        }
                        Direction up = planet.surfaceUp(x, y, z);
                        faces.add(up);
                        surface++;
                        if (planet.materialAt(x + up.getStepX(), y + up.getStepY(), z + up.getStepZ()) == null) {
                            open++;
                        }
                    }
                }
            }
            assertEquals(EnumSet.allOf(Direction.class), faces, shape + " should have grass on all 6 sides");
            assertTrue(open == surface, shape + ": only " + open + " of " + surface + " visible grass blocks face open air");
        }
    }

    private static boolean hasOpenSide(Planet planet, int x, int y, int z) {
        for (Direction side : Direction.values()) {
            if (planet.materialAt(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ()) == null) {
                return true;
            }
        }
        return false;
    }

    @Test
    void faceGridStaysOnTheFace() {
        for (Direction up : Direction.values()) {
            Direction a = FaceGrid.first(up);
            Direction b = FaceGrid.second(up);
            assertNotEquals(up.getAxis(), a.getAxis());
            assertNotEquals(up.getAxis(), b.getAxis());
            assertNotEquals(a.getAxis(), b.getAxis());
            BlockPos p = FaceGrid.offset(BlockPos.ZERO, up, 2, -3, 1);
            assertEquals(BlockPos.ZERO.relative(a, 2).relative(b, -3).relative(up, 1), p);
        }
    }
}
