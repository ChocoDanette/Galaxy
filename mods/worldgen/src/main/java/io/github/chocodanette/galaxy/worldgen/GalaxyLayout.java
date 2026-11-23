package io.github.chocodanette.galaxy.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;

/**
 * The rules that place planets. Space is cut into square cells seen from above; each cell
 * holds at most one planet, which always fits inside its cell, so planets never overlap.
 * A cell's planet depends only on the world seed and the cell's position, so any chunk can be
 * built on its own, in any order, on any thread, and always gets the same result.
 */
public final class GalaxyLayout {
    /** Width of a cell, in blocks. */
    public static final int CELL_SIZE = 96;
    /** Space kept free between a planet and the edge of its cell, in blocks. */
    private static final int CELL_MARGIN = 6;

    /** Lowest and highest block of the galaxy dimension; must match dimension_type/galaxy.json. */
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 383;
    /** Height planets gather around, and how far they spread up and down from it. */
    private static final int MIDDLE_Y = 160;
    private static final int SPREAD_Y = 90;

    public static final double MIN_SPHERE_RADIUS = 6.0;
    public static final double MAX_SPHERE_RADIUS = 28.0;
    public static final double MAX_BUMPINESS = 0.15;
    public static final double MIN_CUBE_HALF = 5.0;
    public static final double MAX_CUBE_HALF = 13.0;
    /** Largest distance from a planet's center to any of its blocks. */
    public static final double MAX_EXTENT = MAX_SPHERE_RADIUS * (1.0 + MAX_BUMPINESS);
    /** Largest distance from a planet's center to anything of it, trees included. */
    public static final double MAX_REACH = MAX_EXTENT + Trees.MAX_HEIGHT;

    private GalaxyLayout() {
    }

    public static int cellOf(double blockCoordinate) {
        return Math.floorDiv((int) Math.floor(blockCoordinate), CELL_SIZE);
    }

    /** Smallest hollow planet: it needs room inside to walk around. */
    public static final double MIN_HOLLOW_RADIUS = 18.0;
    /** Size range of the two big planets of a gateway pair. */
    private static final double MIN_PAIR_RADIUS = 9.0;
    private static final double MAX_PAIR_RADIUS = 13.0;
    /** Size range of the tiny gateway planets between them. */
    public static final double MIN_GATEWAY_RADIUS = 2.5;
    public static final double MAX_GATEWAY_RADIUS = 3.5;
    /**
     * Empty space between neighbouring planets of a gateway chain, surface to surface. A jump
     * goes about 2.5 blocks up, and past halfway the next planet's gravity takes over, so a
     * good jump carries you across.
     */
    public static final double GATEWAY_GAP = 4.5;
    /** Smallest sphere or cube with caves inside. */
    private static final double MIN_CAVE_RADIUS = 9.0;

    /**
     * The planets in a cell: none, one, or a small group (two planets joined by tiny gateway
     * planets). Everything stays inside the cell, so neighbouring cells never collide.
     */
    public static List<Planet> planetsInCell(long worldSeed, int cellX, int cellZ) {
        double middleX = cellX * (double) CELL_SIZE + CELL_SIZE / 2.0;
        double middleZ = cellZ * (double) CELL_SIZE + CELL_SIZE / 2.0;
        int number = Galaxy.numberAt(middleX);
        if (number == 0) {
            return List.of();
        }
        Galaxy galaxy = Galaxy.of(worldSeed, number);
        double fromMiddle = Math.hypot(middleX - galaxy.centerX(), middleZ);
        if (fromMiddle > galaxy.radius()) {
            return List.of();
        }

        long seed = Hashing.mix(galaxy.seed(), cellX, cellZ);
        SplittableRandom random = new SplittableRandom(seed);
        // Crowded in the middle of the galaxy, sparse at its edge.
        double edge = fromMiddle / galaxy.radius();
        double chance = galaxy.density() * (1.0 - 0.6 * edge * edge);
        // The very middle always has a planet, so there's somewhere to arrive.
        boolean isArrivalCell = cellX == cellOf(galaxy.centerX()) && cellZ == 0;
        if (!isArrivalCell && random.nextDouble() >= chance) {
            return List.of();
        }
        if (isArrivalCell) {
            return List.of(singlePlanet(galaxy, random, seed, cellX, cellZ, false));
        }
        double kind = random.nextDouble();
        if (kind < galaxy.gatewayChance()) {
            return gatewayGroup(galaxy, random, cellX, cellZ);
        }
        if (kind < galaxy.gatewayChance() + galaxy.hollowChance()) {
            return List.of(hollowPlanet(galaxy, random, seed, cellX, cellZ));
        }
        return List.of(singlePlanet(galaxy, random, seed, cellX, cellZ, true));
    }

    /** A plain sphere or cube. */
    private static Planet singlePlanet(Galaxy galaxy, SplittableRandom random, long seed, int cellX, int cellZ,
            boolean varied) {
        boolean cube = varied && random.nextDouble() < galaxy.cubeChance();
        PlanetTheme theme = pickTheme(galaxy, random);
        double size = biasedSize(random, galaxy.sizeBias());
        double radius;
        double bumpiness;
        if (cube) {
            radius = Math.floor(MIN_CUBE_HALF + size * (MAX_CUBE_HALF - MIN_CUBE_HALF + 1)) + 0.5;
            bumpiness = 0.0;
        } else {
            radius = MIN_SPHERE_RADIUS + size * (MAX_SPHERE_RADIUS - MIN_SPHERE_RADIUS);
            // Small planets stay rounder so they're still easy to walk around.
            bumpiness = (0.03 + random.nextDouble() * (MAX_BUMPINESS - 0.03)) * Math.min(1.0, radius / 14.0);
        }
        double extent = cube ? radius : radius * (1.0 + bumpiness);
        double strength = 0.7 + random.nextDouble() * 0.3;
        // The arrival planet has no caves, so nobody lands in a hole.
        boolean caves = varied && radius >= MIN_CAVE_RADIUS && random.nextDouble() < 0.8;
        Vec3 center = placeInCell(random, cellX, cellZ, extent, extent, extent);
        return new Planet(seed, center, radius,
                cube ? GravityField.Shape.CUBE : GravityField.Shape.SPHERE, theme, bumpiness, strength, caves);
    }

    /** A big round shell to explore inside, with a tunnel through its top and bottom. */
    private static Planet hollowPlanet(Galaxy galaxy, SplittableRandom random, long seed, int cellX, int cellZ) {
        PlanetTheme theme = pickTheme(galaxy, random);
        double size = biasedSize(random, galaxy.sizeBias());
        double radius = MIN_HOLLOW_RADIUS + size * (MAX_SPHERE_RADIUS - MIN_HOLLOW_RADIUS);
        double strength = 0.7 + random.nextDouble() * 0.3;
        Vec3 center = placeInCell(random, cellX, cellZ, radius, radius, radius);
        return new Planet(seed, center, radius, GravityField.Shape.HOLLOW, theme, 0.0, strength, false);
    }

    /**
     * Two planets in a row with one or two tiny planets between them. The gaps are small
     * enough to jump: big planet, tiny planet, (tiny planet,) big planet.
     */
    private static List<Planet> gatewayGroup(Galaxy galaxy, SplittableRandom random, int cellX, int cellZ) {
        int tinyCount = random.nextDouble() < 0.4 ? 2 : 1;
        int count = tinyCount + 2;
        double[] radii = new double[count];
        double[] bumps = new double[count];
        double[] offsets = new double[count];
        for (int i = 0; i < count; i++) {
            boolean big = i == 0 || i == count - 1;
            radii[i] = big
                    ? MIN_PAIR_RADIUS + random.nextDouble() * (MAX_PAIR_RADIUS - MIN_PAIR_RADIUS)
                    : MIN_GATEWAY_RADIUS + random.nextDouble() * (MAX_GATEWAY_RADIUS - MIN_GATEWAY_RADIUS);
            // Nearly round, so the gaps stay the same size all along.
            bumps[i] = big ? 0.03 : 0.0;
        }
        double cursor = 0.0;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                cursor += radii[i - 1] + GATEWAY_GAP + radii[i];
            }
            offsets[i] = cursor;
        }
        double length = cursor;

        double angle = random.nextDouble() * Math.PI * 2.0;
        double tilt = (random.nextDouble() * 2.0 - 1.0) * 0.35;
        double dirX = Math.cos(tilt) * Math.cos(angle);
        double dirY = Math.sin(tilt);
        double dirZ = Math.cos(tilt) * Math.sin(angle);
        double halfX = 0.0;
        double halfY = 0.0;
        double halfZ = 0.0;
        for (int i = 0; i < count; i++) {
            double along = offsets[i] - length / 2.0;
            double extent = radii[i] * (1.0 + bumps[i]) + 1.0;
            halfX = Math.max(halfX, Math.abs(dirX * along) + extent);
            halfY = Math.max(halfY, Math.abs(dirY * along) + extent);
            halfZ = Math.max(halfZ, Math.abs(dirZ * along) + extent);
        }
        Vec3 middle = placeInCell(random, cellX, cellZ, halfX, halfY, halfZ);

        PlanetTheme first = pickTheme(galaxy, random);
        PlanetTheme last = pickTheme(galaxy, random);
        List<Planet> planets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double along = offsets[i] - length / 2.0;
            Vec3 center = new Vec3(
                    Math.floor(middle.x + dirX * along) + 0.5,
                    Math.floor(middle.y + dirY * along) + 0.5,
                    Math.floor(middle.z + dirZ * along) + 0.5);
            boolean big = i == 0 || i == count - 1;
            // Tiny planets take after the planet they're closest to.
            PlanetTheme theme = i < count / 2.0 ? first : last;
            double strength = 0.7 + random.nextDouble() * 0.3;
            boolean caves = big && radii[i] >= MIN_CAVE_RADIUS && random.nextDouble() < 0.7;
            planets.add(new Planet(random.nextLong(), center, radii[i], GravityField.Shape.SPHERE, theme,
                    bumps[i], strength, caves));
        }
        return planets;
    }

    /**
     * A center for something that reaches this far from it along each axis, somewhere in the
     * cell with {@link #CELL_MARGIN} to spare. Centers sit in the middle of a block, so cubes
     * line up with the block grid.
     */
    private static Vec3 placeInCell(SplittableRandom random, int cellX, int cellZ,
            double halfX, double halfY, double halfZ) {
        double roomX = Math.max(0.0, CELL_SIZE - 2 * (halfX + CELL_MARGIN));
        double roomZ = Math.max(0.0, CELL_SIZE - 2 * (halfZ + CELL_MARGIN));
        double x = cellX * (double) CELL_SIZE + halfX + CELL_MARGIN + random.nextDouble() * roomX;
        double z = cellZ * (double) CELL_SIZE + halfZ + CELL_MARGIN + random.nextDouble() * roomZ;
        double y = MIDDLE_Y + (random.nextDouble() + random.nextDouble() - 1.0) * SPREAD_Y;
        y = Math.max(MIN_Y + halfY + CELL_MARGIN, Math.min(MAX_Y - halfY - CELL_MARGIN, y));
        return new Vec3(Math.floor(x) + 0.5, Math.floor(y) + 0.5, Math.floor(z) + 0.5);
    }

    /** Every planet whose blocks (trees included) could reach into this box (block coordinates, inclusive). */
    public static List<Planet> planetsTouching(long worldSeed, int minX, int minZ, int maxX, int maxZ) {
        int reach = (int) Math.ceil(MAX_REACH) + 1;
        List<Planet> planets = new ArrayList<>();
        for (int cx = cellOf(minX - reach); cx <= cellOf(maxX + reach); cx++) {
            for (int cz = cellOf(minZ - reach); cz <= cellOf(maxZ + reach); cz++) {
                for (Planet planet : planetsInCell(worldSeed, cx, cz)) {
                    double r = planet.reach();
                    Vec3 c = planet.center();
                    if (c.x + r >= minX && c.x - r <= maxX + 1
                            && c.z + r >= minZ && c.z - r <= maxZ + 1) {
                        planets.add(planet);
                    }
                }
            }
        }
        return planets;
    }

    /** The planet in the middle of a galaxy, where travellers arrive. */
    public static Planet arrivalPlanet(long worldSeed, int galaxyNumber) {
        Galaxy galaxy = Galaxy.of(worldSeed, galaxyNumber);
        return planetsInCell(worldSeed, cellOf(galaxy.centerX()), 0).get(0);
    }

    /** A size from 0 (smallest) to 1 (biggest): small planets are most common. */
    private static double biasedSize(SplittableRandom random, double bias) {
        double roll = random.nextDouble();
        // bias -1 squares the roll (more small planets), +1 square-roots it (more big ones).
        double power = Math.pow(2.0, -bias) * 1.6;
        return Math.pow(roll, power);
    }

    private static PlanetTheme pickTheme(Galaxy galaxy, SplittableRandom random) {
        int total = 0;
        for (PlanetTheme theme : PlanetTheme.values()) {
            total += galaxy.themeWeight(theme);
        }
        int pick = random.nextInt(total);
        for (PlanetTheme theme : PlanetTheme.values()) {
            pick -= galaxy.themeWeight(theme);
            if (pick < 0) {
                return theme;
            }
        }
        return PlanetTheme.VERDANT;
    }
}
