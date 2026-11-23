package io.github.chocodanette.galaxy.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;
import io.github.chocodanette.galaxy.gravity.GravityMath;

class GalaxyLayoutTest {
    private static final long SEED = 123456789L;

    private static List<Planet> planetsOfGalaxy(long seed, int number) {
        Galaxy galaxy = Galaxy.of(seed, number);
        int reach = galaxy.radius() / GalaxyLayout.CELL_SIZE + 1;
        int middle = GalaxyLayout.cellOf(galaxy.centerX());
        List<Planet> planets = new ArrayList<>();
        for (int cx = middle - reach; cx <= middle + reach; cx++) {
            for (int cz = -reach; cz <= reach; cz++) {
                GalaxyLayout.planetInCell(seed, cx, cz).ifPresent(planets::add);
            }
        }
        return planets;
    }

    @Test
    void sameSeedGivesSamePlanets() {
        assertEquals(planetsOfGalaxy(SEED, 1), planetsOfGalaxy(SEED, 1));
    }

    @Test
    void differentSeedsAndGalaxiesDiffer() {
        assertNotEquals(planetsOfGalaxy(SEED, 1), planetsOfGalaxy(SEED + 1, 1));
        assertNotEquals(Galaxy.of(SEED, 1), Galaxy.of(SEED, 2));
    }

    @Test
    void galaxyHasManyVariedPlanets() {
        List<Planet> planets = planetsOfGalaxy(SEED, 1);
        assertTrue(planets.size() > 50, "only " + planets.size() + " planets");
        Set<GravityField.Shape> shapes = EnumSet.noneOf(GravityField.Shape.class);
        Set<PlanetTheme> themes = EnumSet.noneOf(PlanetTheme.class);
        double smallest = Double.MAX_VALUE;
        double biggest = 0;
        for (Planet p : planets) {
            shapes.add(p.shape());
            themes.add(p.theme());
            smallest = Math.min(smallest, p.radius());
            biggest = Math.max(biggest, p.radius());
        }
        assertEquals(EnumSet.allOf(GravityField.Shape.class), shapes);
        assertTrue(themes.size() >= 4, "themes: " + themes);
        assertTrue(biggest > smallest * 2, "sizes " + smallest + " to " + biggest);
    }

    @Test
    void planetsStayInsideTheirCellAndTheWorld() {
        for (int number = 1; number <= 5; number++) {
            for (Planet p : planetsOfGalaxy(SEED, number)) {
                Vec3 c = p.center();
                double e = p.extent();
                assertEquals(GalaxyLayout.cellOf(c.x - e), GalaxyLayout.cellOf(c.x + e), "x crosses cell: " + p);
                assertEquals(GalaxyLayout.cellOf(c.z - e), GalaxyLayout.cellOf(c.z + e), "z crosses cell: " + p);
                assertTrue(c.y - e >= GalaxyLayout.MIN_Y && c.y + e <= GalaxyLayout.MAX_Y, "outside height: " + p);
                assertTrue(e <= GalaxyLayout.MAX_EXTENT + 1.0e-9);
            }
        }
    }

    @Test
    void nothingBetweenGalaxies() {
        int between = GalaxyLayout.cellOf(Galaxy.SPACING * 1.5);
        assertEquals(Optional.empty(), GalaxyLayout.planetInCell(SEED, between, 0));
        assertEquals(Optional.empty(), GalaxyLayout.planetInCell(SEED, 0, 0));
    }

    @Test
    void arrivalPlanetHasGroundAndGravity() {
        for (int number = 1; number <= 20; number++) {
            Planet planet = GalaxyLayout.arrivalPlanet(SEED, number);
            Vec3 spot = planet.landingSpot();
            int x = (int) Math.floor(spot.x);
            int y = (int) Math.floor(spot.y);
            int z = (int) Math.floor(spot.z);
            assertNotNull(planet.materialAt(x, y - 1, z), "no ground under landing spot");
            assertNull(planet.materialAt(x, y, z));
            assertNull(planet.materialAt(x, y + 1, z));
            GravityField field = planet.gravityField();
            assertTrue(field.contains(spot));
            Vec3 down = GravityMath.downDirection(List.of(field), spot);
            assertTrue(down.y < -0.9, "gravity at the landing spot should point down, got " + down);
        }
    }

    @Test
    void sphereIsSolidInsideWithLayers() {
        Planet planet = new Planet(42L, new Vec3(0.5, 100.5, 0.5), 15, GravityField.Shape.SPHERE,
                PlanetTheme.VERDANT, 0.0, 1.0);
        assertEquals(Material.TOP, planet.materialAt(0, 115, 0));
        assertEquals(Material.SIDE, planet.materialAt(15, 100, 0));
        assertEquals(Material.SOIL, planet.materialAt(0, 113, 0));
        assertNull(planet.materialAt(0, 116, 0));
        assertNull(planet.materialAt(11, 111, 0));
        Material center = planet.materialAt(0, 100, 0);
        assertTrue(center == Material.CORE || center.ore() != null, "center is " + center);
        assertEquals(115, planet.topY(0, 0));
    }

    @Test
    void cubeHasFlatFaces() {
        Planet cube = new Planet(7L, new Vec3(0.5, 100.5, 0.5), 6.5, GravityField.Shape.CUBE,
                PlanetTheme.ROCKY, 0.0, 1.0);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                assertEquals(106, cube.topY(x, z));
                assertEquals(Material.TOP, cube.materialAt(x, 106, z));
                assertEquals(Material.SIDE, cube.materialAt(x, 94, z));
            }
        }
        assertNull(cube.materialAt(7, 100, 0));
    }

    @Test
    void oresAppearAndDeepOresStayDeep() {
        Planet planet = new Planet(99L, new Vec3(0.5, 100.5, 0.5), 25, GravityField.Shape.SPHERE,
                PlanetTheme.CRYSTAL, 0.0, 1.0);
        int ores = 0;
        for (int x = -25; x <= 25; x++) {
            for (int y = 75; y <= 125; y++) {
                for (int z = -25; z <= 25; z++) {
                    Material m = planet.materialAt(x, y, z);
                    if (m == null || m.ore() == null) {
                        continue;
                    }
                    ores++;
                    double depth = 25 - Math.sqrt(x * x + (y - 100) * (y - 100) + z * z);
                    assertTrue(depth / 25 >= m.ore().minDepth - 0.05, m + " too shallow at depth " + depth);
                }
            }
        }
        assertTrue(ores > 100, "only " + ores + " ore blocks");
    }

    @Test
    void chunkQueryFindsEveryPlanetThatReachesIt() {
        Planet planet = GalaxyLayout.arrivalPlanet(SEED, 3);
        int x = (int) Math.floor(planet.center().x);
        int z = (int) Math.floor(planet.center().z);
        int chunkMinX = Math.floorDiv(x, 16) * 16;
        int chunkMinZ = Math.floorDiv(z, 16) * 16;
        assertTrue(GalaxyLayout.planetsTouching(SEED, chunkMinX, chunkMinZ, chunkMinX + 15, chunkMinZ + 15).contains(planet));
    }
}
