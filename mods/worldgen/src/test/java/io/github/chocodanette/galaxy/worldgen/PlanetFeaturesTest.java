package io.github.chocodanette.galaxy.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;
import io.github.chocodanette.galaxy.gravity.GravityMath;
import io.github.chocodanette.galaxy.gravity.GravityWorld;

class PlanetFeaturesTest {
    private static final long SEED = 123456789L;
    private static final Vec3 CENTER = new Vec3(0.5, 100.5, 0.5);

    private static double openShare(Planet planet, double within) {
        int open = 0;
        int total = 0;
        int r = (int) Math.ceil(within);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > within * within) {
                        continue;
                    }
                    total++;
                    if (planet.materialAt(x, 100 + y, z) == null) {
                        open++;
                    }
                }
            }
        }
        return open / (double) total;
    }

    @Test
    void cavesWindThroughSomePlanets() {
        Planet solid = new Planet(5L, CENTER, 22, GravityField.Shape.SPHERE, PlanetTheme.VERDANT, 0.05, 1.0, false);
        Planet caves = new Planet(5L, CENTER, 22, GravityField.Shape.SPHERE, PlanetTheme.VERDANT, 0.05, 1.0, true);
        assertEquals(0.0, openShare(solid, 20));
        double share = openShare(caves, 20);
        assertTrue(share > 0.03 && share < 0.15, "caves take " + share + " of the inside");
    }

    @Test
    void caveLichenClingsToRock() {
        Planet planet = new Planet(5L, CENTER, 22, GravityField.Shape.SPHERE, PlanetTheme.VERDANT, 0.05, 1.0, true);
        int lichen = 0;
        for (int x = -22; x <= 22; x++) {
            for (int y = 78; y <= 122; y++) {
                for (int z = -22; z <= 22; z++) {
                    Direction side = planet.lichenAt(x, y, z);
                    if (side == null) {
                        continue;
                    }
                    lichen++;
                    assertNull(planet.materialAt(x, y, z));
                    assertNotNull(planet.materialAt(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ()));
                }
            }
        }
        assertTrue(lichen > 20, "only " + lichen + " lichen");
    }

    @Test
    void sandPlanetsKeepARoofOverTheirCaves() {
        Planet dune = new Planet(5L, CENTER, 22, GravityField.Shape.SPHERE, PlanetTheme.DUNE, 0.0, 1.0, true);
        for (int x = -22; x <= 22; x++) {
            for (int z = -22; z <= 22; z++) {
                int top = dune.topY(x, z);
                if (top == Integer.MIN_VALUE || dune.materialAt(x, top, z) != Material.TOP) {
                    continue;
                }
                assertNotNull(dune.materialAt(x, top - 1, z), "sand over a hole at " + x + " " + z);
            }
        }
    }

    @Test
    void treesStandOnTheGroundAndGrowAwayFromIt() {
        Planet planet = new Planet(5L, CENTER, 18, GravityField.Shape.SPHERE, PlanetTheme.VERDANT, 0.05, 1.0, true);
        List<Planet.TreeBlock> trees = planet.trees();
        int bases = 0;
        for (Planet.TreeBlock block : trees) {
            assertNull(planet.materialAt(block.x(), block.y(), block.z()), "tree inside the ground");
            Vec3 c = planet.center();
            double far = Math.max(Math.abs(block.x() + 0.5 - c.x),
                    Math.max(Math.abs(block.y() + 0.5 - c.y), Math.abs(block.z() + 0.5 - c.z)));
            assertTrue(far <= planet.reach(), "tree reaches past the planet's reach");
            if (block.part() == Trees.Part.TRUNK) {
                Direction up = block.up();
                Material below = planet.materialAt(block.x() - up.getStepX(), block.y() - up.getStepY(),
                        block.z() - up.getStepZ());
                if (below == Material.TOP || below == Material.SIDE) {
                    bases++;
                }
            }
        }
        assertTrue(bases >= 15, "only " + bases + " trees");
        assertTrue(new HashSetOfUps(trees).size() >= 5, "trees should grow on many sides");
        assertEquals(trees, planet.trees(), "trees must be the same every time");
    }

    @Test
    void barePlanetsHaveNoTrees() {
        Planet planet = new Planet(5L, CENTER, 18, GravityField.Shape.SPHERE, PlanetTheme.ROCKY, 0.05, 1.0, true);
        assertEquals(List.of(), planet.trees());
    }

    @Test
    void hollowPlanetHasAWalkableInsideAndATunnel() {
        Planet hollow = new Planet(6L, CENTER, 22, GravityField.Shape.HOLLOW, PlanetTheme.VERDANT, 0.0, 1.0, false);
        double inner = hollow.innerRadius();
        // Open inside, solid shell, open tunnel through the top and the bottom.
        assertNull(hollow.materialAt(10, 100, 0));
        assertNotNull(hollow.materialAt((int) Math.ceil(inner) + 1, 100, 0));
        assertNull(hollow.materialAt(0, 100 + 20, 0));
        assertNull(hollow.materialAt(0, 100 - 20, 0));
        assertEquals(Material.GLOW, hollow.materialAt(0, 100, 0), "the little sun in the middle");

        // The inner ground is grassy, and gravity there points out into it.
        int floor = (int) Math.floor(inner);
        Material ground = hollow.materialAt(floor, 100, 0);
        assertTrue(ground == Material.TOP || ground == Material.GLOW, "inner ground is " + ground);
        Vec3 down = GravityMath.downDirection(List.of(hollow.gravityField()), new Vec3(floor - 1.5, 100.5, 0.5));
        assertTrue(down.x > 0.95, "inside, down should point out to the ground: " + down);
        assertEquals(Direction.WEST, hollow.upAt(floor - 1, 100, 0));
    }

    @Test
    void hollowPlanetsGrowTreesInside() {
        Planet hollow = new Planet(6L, CENTER, 22, GravityField.Shape.HOLLOW, PlanetTheme.VERDANT, 0.0, 1.0, false);
        double inner = hollow.innerRadius();
        long insideTrunks = hollow.trees().stream()
                .filter(b -> b.part() == Trees.Part.TRUNK)
                .filter(b -> Math.sqrt(sq(b.x() + 0.5 - CENTER.x) + sq(b.y() + 0.5 - CENTER.y)
                        + sq(b.z() + 0.5 - CENTER.z)) < inner)
                .count();
        assertTrue(insideTrunks > 10, "only " + insideTrunks + " trunk blocks inside");
    }

    private static List<List<Planet>> gatewayGroups() {
        List<List<Planet>> groups = new ArrayList<>();
        for (int number = 1; number <= 4; number++) {
            GalaxyCycle.setCurrentForTests(number, number);
            for (List<Planet> planets : GalaxyLayout.allCells(SEED)) {
                if (planets.size() > 1) {
                    groups.add(planets);
                }
            }
        }
        return groups;
    }

    @Test
    void gatewayPlanetsFloatInARowBetweenTwoPlanets() {
        List<List<Planet>> groups = gatewayGroups();
        assertTrue(groups.size() > 10, "only " + groups.size() + " gateway groups");
        for (List<Planet> group : groups) {
            assertTrue(group.size() == 3 || group.size() == 4);
            assertTrue(group.get(0).radius() > GalaxyLayout.MAX_GATEWAY_RADIUS);
            assertTrue(group.get(group.size() - 1).radius() > GalaxyLayout.MAX_GATEWAY_RADIUS);
            for (int i = 1; i < group.size() - 1; i++) {
                assertTrue(group.get(i).radius() <= GalaxyLayout.MAX_GATEWAY_RADIUS);
                assertTrue(group.get(i).gravityField().floats());
            }
            assertTrue(!group.get(0).gravityField().floats());
            List<GravityField> fields = group.stream().map(Planet::gravityField).toList();
            for (int i = 0; i + 1 < group.size(); i++) {
                Planet a = group.get(i);
                Planet b = group.get(i + 1);
                double gap = a.center().distanceTo(b.center()) - a.radius() - b.radius();
                assertTrue(gap > 5.5 && gap < 9.0, "gap of " + gap);
                // Standing on the big planet, its own gravity holds you (weakly). At the
                // floating height of the tiny planet, it holds you there.
                GravityWorld world = GravityWorld.of(fields);
                Vec3 dir = b.center().subtract(a.center()).normalize();
                if (!a.isGateway()) {
                    Vec3 onA = a.center().add(dir.scale(a.radius() + 0.5));
                    assertEquals(a.gravityField(), GravityMath.pull(world, onA, Double.NaN).orElseThrow().field());
                }
                if (b.isGateway()) {
                    Vec3 floating = b.center().subtract(dir.scale(b.radius() + Planet.GATEWAY_HOVER));
                    GravityMath.Pull pull = GravityMath.pull(world, floating, Double.NaN).orElseThrow();
                    assertEquals(b.gravityField(), pull.field());
                    assertTrue(Math.abs(pull.scale()) < 0.05, "scale at floating height " + pull.scale());
                }
            }
            // No two planets of a group touch.
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    Planet a = group.get(i);
                    Planet b = group.get(j);
                    assertTrue(a.center().distanceTo(b.center()) > a.extent() + b.extent() + 2.0);
                }
            }
        }
    }

    @Test
    void galaxiesHaveHollowPlanets() {
        long hollow = 0;
        for (int number = 1; number <= 3; number++) {
            GalaxyCycle.setCurrentForTests(number, number);
            for (List<Planet> cell : GalaxyLayout.allCells(SEED)) {
                hollow += cell.stream()
                        .filter(p -> p.shape() == GravityField.Shape.HOLLOW)
                        .peek(p -> assertTrue(p.radius() >= GalaxyLayout.MIN_HOLLOW_RADIUS))
                        .count();
            }
        }
        assertTrue(hollow > 5, "only " + hollow + " hollow planets");
    }

    private static double sq(double v) {
        return v * v;
    }

    /** The different "up" sides trees grow toward. */
    private static final class HashSetOfUps extends java.util.HashSet<Direction> {
        HashSetOfUps(List<Planet.TreeBlock> trees) {
            for (Planet.TreeBlock block : trees) {
                add(block.up());
            }
        }
    }

    // ---------------------------------------------------------------- seas

    private static Planet seaPlanet(PlanetTheme theme) {
        return new Planet(SEED, CENTER, 16, GravityField.Shape.ROUNDED_CUBE, theme, 0.22, 1.0, true);
    }

    @Test
    void waterPlanetHasSeaIslandsAndSeabed() {
        Planet planet = seaPlanet(PlanetTheme.OCEAN);
        assertTrue(planet.hasSea());
        int sea = 0;
        int seabed = 0;
        int dryGround = 0;
        int r = (int) Math.ceil(planet.extent()) + 1;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Material m = planet.materialAt(x, 100 + y, z);
                    boolean liquid = planet.liquidAt(x, 100 + y, z);
                    if (liquid) {
                        sea++;
                        assertNull(m, "sea inside the ground at " + x + " " + y + " " + z);
                    }
                    if (m == Material.SEABED || m == Material.SEABED_PATCH) {
                        seabed++;
                    }
                    if (m == Material.TOP || m == Material.SIDE) {
                        dryGround++;
                    }
                }
            }
        }
        assertTrue(sea > 500, "sea blocks: " + sea);
        assertTrue(seabed > 200, "seabed blocks: " + seabed);
        assertTrue(dryGround > 50, "island ground blocks: " + dryGround);
    }

    @Test
    void caveTunnelsNeverFillWithTheSea() {
        Planet planet = seaPlanet(PlanetTheme.LAVA);
        int r = (int) Math.ceil(planet.extent());
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (planet.liquidAt(x, 100 + y, z)) {
                        // Open sea is outside the uncarved ground too.
                        assertTrue(planet.seaGap(x + 0.5 - CENTER.x, y + 100.5 - CENTER.y, z + 0.5 - CENTER.z) > 0);
                    }
                }
            }
        }
    }

    @Test
    void onlyOutsideSurfacesHoldSeas() {
        assertFalse(new Planet(SEED, CENTER, 16, GravityField.Shape.HOLLOW, PlanetTheme.OCEAN, 0.0, 1.0).hasSea());
        assertFalse(new Planet(SEED, CENTER, 3, GravityField.Shape.ROUNDED_CUBE, PlanetTheme.OCEAN, 0.0, 1.0).hasSea());
        assertFalse(seaPlanet(PlanetTheme.VERDANT).hasSea());
    }

    @Test
    void lavaPlanetLandingIsOnAnIsland() {
        Planet planet = seaPlanet(PlanetTheme.LAVA);
        Vec3 spot = planet.landingSpot();
        int x = (int) Math.floor(spot.x);
        int y = (int) Math.floor(spot.y);
        int z = (int) Math.floor(spot.z);
        assertFalse(planet.liquidAt(x, y, z), "landed in lava");
        assertNotNull(planet.materialAt(x, y - 1, z));
    }
}
