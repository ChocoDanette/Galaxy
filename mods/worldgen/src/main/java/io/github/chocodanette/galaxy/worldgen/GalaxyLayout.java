package io.github.chocodanette.galaxy.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;

/**
 * The rules that place planets. Space is cut into cube cells; each cell holds at most one
 * planet (or one small gateway group), which always fits inside its cell, so planets never
 * overlap. Cells stack up and down too, so a galaxy fills a thick disc, not a flat sheet.
 * A cell's planet depends only on the world seed and the cell's position, so any chunk can be
 * built on its own, in any order, on any thread, and always gets the same result.
 */
public final class GalaxyLayout {
    /** Width of a cell, in blocks. */
    public static final int CELL_SIZE = 96;
    /** Space kept free between a planet and the edge of its cell, in blocks. */
    private static final int CELL_MARGIN = 6;

    /** Lowest and highest block of the galaxy dimension; must match dimension_type/galaxy.json. */
    public static final int MIN_Y = -1024;
    public static final int MAX_Y = 1023;
    /**
     * Cells stack up and down, so far more of them hold planets than when the galaxy was flat:
     * this keeps a galaxy about as crowded to look around in as before.
     */
    private static final double STACKED_DENSITY = 0.55;

    public static final double MIN_SPHERE_RADIUS = 6.0;
    public static final double MAX_SPHERE_RADIUS = 28.0;
    public static final double MAX_BUMPINESS = 0.15;
    /** Half size of rounded-cube planets, the usual kind. */
    public static final double MIN_ROUNDED_HALF = 6.0;
    public static final double MAX_ROUNDED_HALF = 22.0;
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
     * Empty space between neighbouring planets of a gateway chain, surface to surface. You
     * float {@link Planet#GATEWAY_HOVER} above each tiny planet, so floating heights nearly
     * touch, and gravity is weak near them, so a jump from a big planet reaches the first one.
     */
    public static final double GATEWAY_GAP = 7.0;
    /** Smallest sphere or cube with caves inside. */
    private static final double MIN_CAVE_RADIUS = 9.0;

    /**
     * The planets in a cell: none, one, or a small group (two planets joined by tiny gateway
     * planets). Everything stays inside the cell, so neighbouring cells never collide.
     */
    public static List<Planet> planetsInCell(long worldSeed, int cellX, int cellY, int cellZ) {
        double middleX = cellX * (double) CELL_SIZE + CELL_SIZE / 2.0;
        double middleY = cellY * (double) CELL_SIZE + CELL_SIZE / 2.0;
        double middleZ = cellZ * (double) CELL_SIZE + CELL_SIZE / 2.0;
        if (cellY * CELL_SIZE < MIN_Y || cellY * CELL_SIZE + CELL_SIZE - 1 > MAX_Y) {
            return List.of();
        }
        Galaxy galaxy = Galaxy.at(worldSeed, middleX);
        if (galaxy == null) {
            return List.of();
        }
        // How far out in the disc this cell is: 0 in the middle, 1 at the edge.
        double across = Math.hypot(middleX - galaxy.centerX(), middleZ) / galaxy.radius();
        double up = (middleY - Galaxy.CENTER_Y) / galaxy.thickness();
        double edge = Math.sqrt(across * across + up * up);
        if (edge > 1.0) {
            return List.of();
        }

        long seed = Hashing.mix(galaxy.seed(), cellX, cellY, cellZ);
        SplittableRandom random = new SplittableRandom(seed);
        // Crowded in the middle of the galaxy, sparse at its edge.
        double chance = galaxy.density() * STACKED_DENSITY * (1.0 - 0.6 * edge * edge);
        // The very middle always has a planet, so there's somewhere to arrive.
        boolean isArrivalCell = cellX == cellOf(galaxy.centerX()) && cellY == cellOf(Galaxy.CENTER_Y) && cellZ == 0;
        if (!isArrivalCell && random.nextDouble() >= chance) {
            return List.of();
        }
        if (isArrivalCell) {
            return List.of(singlePlanet(galaxy, random, seed, cellX, cellY, cellZ, false));
        }
        double kind = random.nextDouble();
        if (kind < galaxy.gatewayChance()) {
            return gatewayGroup(galaxy, random, cellX, cellY, cellZ);
        }
        if (kind < galaxy.gatewayChance() + galaxy.hollowChance()) {
            return List.of(hollowPlanet(galaxy, random, seed, cellX, cellY, cellZ));
        }
        return List.of(singlePlanet(galaxy, random, seed, cellX, cellY, cellZ, true));
    }

    /** Every planet in a stack of cells, from the bottom of the galaxy to its top. */
    public static List<Planet> planetsInColumn(long worldSeed, int cellX, int cellZ) {
        Galaxy galaxy = Galaxy.at(worldSeed, cellX * (double) CELL_SIZE + CELL_SIZE / 2.0);
        if (galaxy == null) {
            return List.of();
        }
        List<Planet> planets = new ArrayList<>();
        for (int cy = cellOf(Galaxy.CENTER_Y - galaxy.thickness()); cy <= cellOf(Galaxy.CENTER_Y + galaxy.thickness()); cy++) {
            planets.addAll(planetsInCell(worldSeed, cellX, cy, cellZ));
        }
        return planets;
    }

    /** The planets of every non-empty cell of the current galaxy (slow: for tests and tools). */
    static List<List<Planet>> allCells(long worldSeed) {
        Galaxy galaxy = Galaxy.current(worldSeed);
        int reach = galaxy.radius() / CELL_SIZE + 1;
        int middle = cellOf(galaxy.centerX());
        List<List<Planet>> cells = new ArrayList<>();
        for (int cx = middle - reach; cx <= middle + reach; cx++) {
            for (int cz = -reach; cz <= reach; cz++) {
                for (int cy = cellOf(Galaxy.CENTER_Y - galaxy.thickness()); cy <= cellOf(Galaxy.CENTER_Y + galaxy.thickness()); cy++) {
                    List<Planet> cell = planetsInCell(worldSeed, cx, cy, cz);
                    if (!cell.isEmpty()) {
                        cells.add(cell);
                    }
                }
            }
        }
        return cells;
    }

    /** Every planet in the cells up to {@code cells} away from the one holding this position, along each axis. */
    public static List<Planet> planetsNear(long worldSeed, Vec3 pos, int cells) {
        int cx = cellOf(pos.x);
        int cy = cellOf(pos.y);
        int cz = cellOf(pos.z);
        List<Planet> planets = new ArrayList<>();
        for (int x = cx - cells; x <= cx + cells; x++) {
            if (Galaxy.numberAt(x * (double) CELL_SIZE + CELL_SIZE / 2.0) == 0) {
                continue;
            }
            for (int y = cy - cells; y <= cy + cells; y++) {
                for (int z = cz - cells; z <= cz + cells; z++) {
                    planets.addAll(planetsInCell(worldSeed, x, y, z));
                }
            }
        }
        return planets;
    }

    /** A rounded cube (most planets) or a sharp-edged cube. */
    private static Planet singlePlanet(Galaxy galaxy, SplittableRandom random, long seed, int cellX, int cellY,
            int cellZ, boolean varied) {
        boolean cube = varied && random.nextDouble() < galaxy.cubeChance();
        PlanetTheme theme = pickTheme(galaxy, random);
        double size = biasedSize(random, galaxy.sizeBias());
        double radius;
        double bumpiness;
        if (cube) {
            radius = Math.floor(MIN_CUBE_HALF + size * (MAX_CUBE_HALF - MIN_CUBE_HALF + 1)) + 0.5;
            bumpiness = 0.0;
        } else {
            radius = MIN_ROUNDED_HALF + size * (MAX_ROUNDED_HALF - MIN_ROUNDED_HALF);
            // Hills, as a share of the half size; small planets stay smoother.
            bumpiness = (0.02 + random.nextDouble() * 0.08) * Math.min(1.0, radius / 14.0);
        }
        double extent = cube ? radius : radius * (1.0 + bumpiness);
        double strength = 0.7 + random.nextDouble() * 0.3;
        // The arrival planet has no caves, so nobody lands in a hole.
        boolean caves = varied && radius >= MIN_CAVE_RADIUS && random.nextDouble() < 0.8;
        Vec3 center = placeInCell(random, cellX, cellY, cellZ, extent, extent, extent);
        return new Planet(seed, center, radius,
                cube ? GravityField.Shape.CUBE : GravityField.Shape.ROUNDED_CUBE, theme, bumpiness, strength, caves);
    }

    /** A big round shell to explore inside, with a tunnel through its top and bottom. */
    private static Planet hollowPlanet(Galaxy galaxy, SplittableRandom random, long seed, int cellX, int cellY,
            int cellZ) {
        PlanetTheme theme = pickTheme(galaxy, random);
        double size = biasedSize(random, galaxy.sizeBias());
        double radius = MIN_HOLLOW_RADIUS + size * (MAX_SPHERE_RADIUS - MIN_HOLLOW_RADIUS);
        double strength = 0.7 + random.nextDouble() * 0.3;
        Vec3 center = placeInCell(random, cellX, cellY, cellZ, radius, radius, radius);
        return new Planet(seed, center, radius, GravityField.Shape.HOLLOW, theme, 0.0, strength, false);
    }

    /**
     * Two planets in a row with one or two tiny planets between them. The gaps are small
     * enough to jump: big planet, tiny planet, (tiny planet,) big planet.
     */
    private static List<Planet> gatewayGroup(Galaxy galaxy, SplittableRandom random, int cellX, int cellY,
            int cellZ) {
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
            // Nearly flat faces, so the gaps stay the same size all along.
            bumps[i] = big ? 0.02 : 0.0;
        }
        double cursor = 0.0;
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                cursor += radii[i - 1] + GATEWAY_GAP + radii[i];
            }
            offsets[i] = cursor;
        }
        double length = cursor;

        // In a row along x or z, so the rounded cubes face each other flat face to flat face.
        boolean alongX = random.nextBoolean();
        double sign = random.nextBoolean() ? 1.0 : -1.0;
        double dirX = alongX ? sign : 0.0;
        double dirY = 0.0;
        double dirZ = alongX ? 0.0 : sign;
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
        Vec3 middle = placeInCell(random, cellX, cellY, cellZ, halfX, halfY, halfZ);

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
            planets.add(new Planet(random.nextLong(), center, radii[i], GravityField.Shape.ROUNDED_CUBE, theme,
                    bumps[i], strength, caves));
        }
        return planets;
    }

    /**
     * A center for something that reaches this far from it along each axis, somewhere in the
     * cell with {@link #CELL_MARGIN} to spare. Centers sit in the middle of a block, so cubes
     * line up with the block grid.
     */
    private static Vec3 placeInCell(SplittableRandom random, int cellX, int cellY, int cellZ,
            double halfX, double halfY, double halfZ) {
        double roomX = Math.max(0.0, CELL_SIZE - 2 * (halfX + CELL_MARGIN));
        double roomY = Math.max(0.0, CELL_SIZE - 2 * (halfY + CELL_MARGIN));
        double roomZ = Math.max(0.0, CELL_SIZE - 2 * (halfZ + CELL_MARGIN));
        double x = cellX * (double) CELL_SIZE + halfX + CELL_MARGIN + random.nextDouble() * roomX;
        double y = cellY * (double) CELL_SIZE + halfY + CELL_MARGIN + random.nextDouble() * roomY;
        double z = cellZ * (double) CELL_SIZE + halfZ + CELL_MARGIN + random.nextDouble() * roomZ;
        return new Vec3(Math.floor(x) + 0.5, Math.floor(y) + 0.5, Math.floor(z) + 0.5);
    }

    /** Every planet whose blocks (trees included) could reach into this box (block coordinates, inclusive). */
    public static List<Planet> planetsTouching(long worldSeed, int minX, int minZ, int maxX, int maxZ) {
        int reach = (int) Math.ceil(MAX_REACH) + 1;
        List<Planet> planets = new ArrayList<>();
        for (int cx = cellOf(minX - reach); cx <= cellOf(maxX + reach); cx++) {
            for (int cz = cellOf(minZ - reach); cz <= cellOf(maxZ + reach); cz++) {
                for (Planet planet : planetsInColumn(worldSeed, cx, cz)) {
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
    public static Planet arrivalPlanet(long worldSeed) {
        Galaxy galaxy = Galaxy.current(worldSeed);
        return planetsInCell(worldSeed, cellOf(galaxy.centerX()), cellOf(Galaxy.CENTER_Y), 0).get(0);
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
