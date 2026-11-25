package io.github.chocodanette.galaxy.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;

/** Ember and Void planets, which replace the Nether and the End. */
class ShrinesTest {
    private static final long SEED = 123456789L;

    private static List<Planet> planetsOfGalaxy(long seed, int number) {
        Galaxy galaxy = Galaxy.of(seed, number);
        int reach = galaxy.radius() / GalaxyLayout.CELL_SIZE + 1;
        int middle = GalaxyLayout.cellOf(galaxy.centerX());
        List<Planet> planets = new ArrayList<>();
        for (int cx = middle - reach; cx <= middle + reach; cx++) {
            for (int cz = -reach; cz <= reach; cz++) {
                planets.addAll(GalaxyLayout.planetsInCell(seed, cx, cz));
            }
        }
        return planets;
    }

    @Test
    void voidIsNeverACommonPick() {
        assertFalse(PlanetTheme.COMMON.contains(PlanetTheme.VOID));
        for (int number = 1; number <= 20; number++) {
            assertFalse(Galaxy.of(SEED, number).favorites().contains(PlanetTheme.VOID));
        }
    }

    @Test
    void everyGalaxyHasAFewFarVoidPlanetsAndEmberShrines() {
        int galaxiesWithEmber = 0;
        for (int number = 1; number <= 5; number++) {
            Galaxy galaxy = Galaxy.of(SEED, number);
            int voids = 0;
            int ember = 0;
            for (Planet p : planetsOfGalaxy(SEED, number)) {
                if (p.theme() == PlanetTheme.VOID) {
                    voids++;
                    double dx = p.center().x - galaxy.centerX();
                    double dz = p.center().z;
                    assertTrue(Math.sqrt(dx * dx + dz * dz) > galaxy.radius() * GalaxyLayout.VOID_MIN_DISTANCE - 96,
                            "void planet too close to the middle: " + p);
                    assertNotEquals(GravityField.Shape.HOLLOW, p.shape());
                }
                if (Shrines.of(p) == Shrines.Kind.EMBER) {
                    ember++;
                }
            }
            assertTrue(voids >= 2 && voids <= 40, "galaxy " + number + " has " + voids + " void planets");
            if (ember > 0) {
                galaxiesWithEmber++;
            }
        }
        assertTrue(galaxiesWithEmber >= 3, "only " + galaxiesWithEmber + " galaxies have an ember shrine");
    }

    @Test
    void arrivalPlanetNeverHasAShrine() {
        for (int number = 1; number <= 50; number++) {
            Planet arrival = GalaxyLayout.arrivalPlanet(SEED, number);
            assertEquals(null, Shrines.of(arrival), "galaxy " + number);
            assertNotEquals(PlanetTheme.VOID, arrival.theme());
        }
    }

    @Test
    void emberPlanetsHoldNetherOresAndDebrisOnlyDeep() {
        Planet planet = new Planet(7L, new Vec3(0.5, 160.5, 0.5), 20, GravityField.Shape.ROUNDED_CUBE,
                PlanetTheme.EMBER, 0.0, 1.0, false);
        int quartz = 0;
        int debris = 0;
        for (int x = -20; x <= 20; x++) {
            for (int y = 140; y <= 180; y++) {
                for (int z = -20; z <= 20; z++) {
                    Material m = planet.materialAt(x, y, z);
                    if (m == Material.QUARTZ) {
                        quartz++;
                    }
                    if (m == Material.DEBRIS) {
                        debris++;
                        double far = Math.max(Math.abs(x + 0.5 - 0.5), Math.max(Math.abs(y + 0.5 - 160.5),
                                Math.abs(z + 0.5 - 0.5)));
                        assertTrue(far < 20 * 0.4 + 1, "debris near the surface at " + x + " " + y + " " + z);
                    }
                }
            }
        }
        assertTrue(quartz > 50, "quartz: " + quartz);
        assertTrue(debris > 0, "no ancient debris");
    }

    @Test
    void otherThemesGetNoNetherOres() {
        for (PlanetTheme theme : PlanetTheme.values()) {
            if (theme == PlanetTheme.EMBER) {
                continue;
            }
            for (Material.Ore ore : List.of(Material.Ore.QUARTZ, Material.Ore.NETHER_GOLD, Material.Ore.GLOWSTONE,
                    Material.Ore.DEBRIS)) {
                assertEquals(0, theme.oreWeight(ore), theme + " " + ore);
            }
        }
    }

    /** Prints where to find shrines, for testing on a server with this seed. */
    @Test
    void printShrines() {
        for (Planet p : planetsOfGalaxy(SEED, 1)) {
            Shrines.Kind kind = Shrines.of(p);
            if (kind != null) {
                System.out.println("SHRINE " + kind + " at " + Shrines.platformCenter(p).toShortString());
            }
        }
    }
}
