package io.github.chocodanette.galaxy.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

    private GalaxyLayout() {
    }

    public static int cellOf(double blockCoordinate) {
        return Math.floorDiv((int) Math.floor(blockCoordinate), CELL_SIZE);
    }

    /** The planet in a cell, if it has one. */
    public static Optional<Planet> planetInCell(long worldSeed, int cellX, int cellZ) {
        double middleX = cellX * (double) CELL_SIZE + CELL_SIZE / 2.0;
        double middleZ = cellZ * (double) CELL_SIZE + CELL_SIZE / 2.0;
        int number = Galaxy.numberAt(middleX);
        if (number == 0) {
            return Optional.empty();
        }
        Galaxy galaxy = Galaxy.of(worldSeed, number);
        double fromMiddle = Math.hypot(middleX - galaxy.centerX(), middleZ);
        if (fromMiddle > galaxy.radius()) {
            return Optional.empty();
        }

        long seed = Hashing.mix(galaxy.seed(), cellX, cellZ);
        SplittableRandom random = new SplittableRandom(seed);
        // Crowded in the middle of the galaxy, sparse at its edge.
        double edge = fromMiddle / galaxy.radius();
        double chance = galaxy.density() * (1.0 - 0.6 * edge * edge);
        // The very middle always has a planet, so there's somewhere to arrive.
        boolean isArrivalCell = cellX == cellOf(galaxy.centerX()) && cellZ == 0;
        if (!isArrivalCell && random.nextDouble() >= chance) {
            return Optional.empty();
        }

        boolean cube = !isArrivalCell && random.nextDouble() < galaxy.cubeChance();
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
        double strength = 0.8 + random.nextDouble() * 0.3;

        double room = CELL_SIZE - 2 * (extent + CELL_MARGIN);
        double x = cellX * (double) CELL_SIZE + extent + CELL_MARGIN + random.nextDouble() * room;
        double z = cellZ * (double) CELL_SIZE + extent + CELL_MARGIN + random.nextDouble() * room;
        double y = MIDDLE_Y + (random.nextDouble() + random.nextDouble() - 1.0) * SPREAD_Y;
        y = Math.max(MIN_Y + extent + CELL_MARGIN, Math.min(MAX_Y - extent - CELL_MARGIN, y));
        // Centers sit in the middle of a block, so cubes line up with the block grid.
        Vec3 center = new Vec3(Math.floor(x) + 0.5, Math.floor(y) + 0.5, Math.floor(z) + 0.5);

        return Optional.of(new Planet(seed, center, radius,
                cube ? GravityField.Shape.CUBE : GravityField.Shape.SPHERE, theme, bumpiness, strength));
    }

    /** Every planet whose blocks could reach into this box (block coordinates, inclusive). */
    public static List<Planet> planetsTouching(long worldSeed, int minX, int minZ, int maxX, int maxZ) {
        int reach = (int) Math.ceil(MAX_EXTENT) + 1;
        List<Planet> planets = new ArrayList<>();
        for (int cx = cellOf(minX - reach); cx <= cellOf(maxX + reach); cx++) {
            for (int cz = cellOf(minZ - reach); cz <= cellOf(maxZ + reach); cz++) {
                planetInCell(worldSeed, cx, cz).ifPresent(planet -> {
                    double extent = planet.extent();
                    Vec3 c = planet.center();
                    if (c.x + extent >= minX && c.x - extent <= maxX + 1
                            && c.z + extent >= minZ && c.z - extent <= maxZ + 1) {
                        planets.add(planet);
                    }
                });
            }
        }
        return planets;
    }

    /** The planet in the middle of a galaxy, where travellers arrive. */
    public static Planet arrivalPlanet(long worldSeed, int galaxyNumber) {
        Galaxy galaxy = Galaxy.of(worldSeed, galaxyNumber);
        return planetInCell(worldSeed, cellOf(galaxy.centerX()), 0).orElseThrow();
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
