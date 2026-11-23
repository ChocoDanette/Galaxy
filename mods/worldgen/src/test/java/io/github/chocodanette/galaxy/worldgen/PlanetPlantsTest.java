package io.github.chocodanette.galaxy.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;

class PlanetPlantsTest {
    @Test
    void plantsGrowOnEverySideAndOnlyOnTheSurface() {
        Planet planet = new Planet(42L, Vec3.ZERO, 12, GravityField.Shape.SPHERE, PlanetTheme.VERDANT, 0.0, 1.0);
        Set<Direction> sides = EnumSet.noneOf(Direction.class);
        int plants = 0;
        for (int x = -14; x <= 14; x++) {
            for (int y = -14; y <= 14; y++) {
                for (int z = -14; z <= 14; z++) {
                    Planet.PlantSpot spot = planet.plantAt(x, y, z);
                    if (spot == null) {
                        continue;
                    }
                    plants++;
                    sides.add(spot.up());
                    assertNull(planet.materialAt(x, y, z));
                    Material below = planet.materialAt(
                            x - spot.up().getStepX(), y - spot.up().getStepY(), z - spot.up().getStepZ());
                    assertTrue(below == Material.TOP || below == Material.SIDE);
                }
            }
        }
        assertTrue(plants > 50, "expected plenty of plants, got " + plants);
        assertEquals(EnumSet.allOf(Direction.class), sides);
    }

    @Test
    void barrenPlanetsHaveNoPlants() {
        Planet planet = new Planet(42L, Vec3.ZERO, 12, GravityField.Shape.SPHERE, PlanetTheme.ROCKY, 0.0, 1.0);
        for (int y = -14; y <= 14; y++) {
            assertNull(planet.plantAt(0, y, 0));
        }
    }
}
