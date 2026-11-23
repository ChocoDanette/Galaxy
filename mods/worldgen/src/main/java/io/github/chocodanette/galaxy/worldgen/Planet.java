package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;

/**
 * One generated planet: where it is, its size and shape, what it's made of and how strongly
 * it pulls. Everything about it comes from its seed, so it is the same every time it's built.
 *
 * @param seed      decides the hills and ore spots of this planet
 * @param center    center of the planet, at the middle of a block
 * @param radius    for a sphere, its average radius; for a cube, half its side length (blocks)
 * @param shape     sphere or cube
 * @param theme     what it's made of
 * @param bumpiness how hilly a sphere is, as a share of its radius (0 = perfectly round)
 * @param strength  gravity compared to normal (1.0 = normal)
 */
public record Planet(long seed, Vec3 center, double radius, GravityField.Shape shape, PlanetTheme theme,
                     double bumpiness, double strength) {
    /** How deep the soil layer under the surface goes, in blocks. */
    static final double SOIL_DEPTH = 4.0;
    /** How bumpy the hills are: smaller is broader hills. */
    private static final double HILL_FREQUENCY = 2.4;
    /** Below this "up" amount, a sphere's surface uses the side block (see {@link PlanetTheme}). */
    private static final double TOP_THRESHOLD = 0.45;

    /** Farthest a block of this planet can be from its center along any axis. */
    public double extent() {
        return shape == GravityField.Shape.CUBE ? radius : radius * (1.0 + bumpiness);
    }

    /** How far the gravity reaches from the center: a bit more for bigger planets. */
    public double gravityRange() {
        return extent() + 10.0 + radius * 0.5;
    }

    /** The gravity field the Gravity mod uses to pull players and mobs toward this planet. */
    public GravityField gravityField() {
        return new GravityField(center, radius, gravityRange(), strength, shape);
    }

    /**
     * What the block at this position is made of, or null for empty space.
     * Safe to call from several threads at once.
     */
    public Material materialAt(int x, int y, int z) {
        double dx = x + 0.5 - center.x;
        double dy = y + 0.5 - center.y;
        double dz = z + 0.5 - center.z;
        double depth;
        boolean onTop;
        if (shape == GravityField.Shape.CUBE) {
            double ax = Math.abs(dx);
            double ay = Math.abs(dy);
            double az = Math.abs(dz);
            double far = Math.max(ax, Math.max(ay, az));
            depth = radius - far;
            onTop = dy > 0 && ay >= ax && ay >= az;
        } else {
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > extent()) {
                return null;
            }
            depth = surfaceRadius(dx, dy, dz, distance) - distance;
            onTop = distance > 0 && dy / distance > TOP_THRESHOLD;
        }
        if (depth < 0) {
            return null;
        }
        if (depth < 1.0) {
            return onTop ? Material.TOP : Material.SIDE;
        }
        if (depth < SOIL_DEPTH) {
            return Material.SOIL;
        }

        // 0 just under the soil, 1 at the very center.
        double inner = Math.min(1.0, depth / radius);
        Material rock = inner > 0.7 ? Material.CORE : inner > 0.4 ? Material.DEEP : Material.STONE;
        Material.Ore ore = oreAt(x, y, z, inner);
        return ore == null ? rock : Material.ore(ore, rock != Material.STONE);
    }

    /** Share of open surface spots that grow a plant, on planets that have plants. */
    static final double PLANT_CHANCE = 0.15;
    /** Share of those plants that are starblooms instead of grass. */
    static final double BLOOM_SHARE = 0.12;

    /** A plant growing at a spot: which one, and which way it grows. */
    public record PlantSpot(boolean bloom, Direction up) {
    }

    /**
     * The plant growing in this empty spot, or null. Plants stand on the surface and grow
     * away from the center, on every side of the planet.
     */
    public PlantSpot plantAt(int x, int y, int z) {
        if (!theme.hasPlants()) {
            return null;
        }
        double dx = x + 0.5 - center.x;
        double dy = y + 0.5 - center.y;
        double dz = z + 0.5 - center.z;
        Direction up = Direction.getApproximateNearest(dx, dy, dz);
        Material below = materialAt(x - up.getStepX(), y - up.getStepY(), z - up.getStepZ());
        if (below != Material.TOP && below != Material.SIDE) {
            return null;
        }
        if (materialAt(x, y, z) != null) {
            return null;
        }
        double roll = Hashing.unit(seed ^ 0x6A09E667F3BCC909L, x, y, z);
        if (roll >= PLANT_CHANCE) {
            return null;
        }
        return new PlantSpot(roll < PLANT_CHANCE * BLOOM_SHARE, up);
    }

    /** Distance from the center to the surface in this direction, with gentle hills. */
    private double surfaceRadius(double dx, double dy, double dz, double distance) {
        if (bumpiness <= 0 || distance < 1.0e-6) {
            return radius;
        }
        double scale = HILL_FREQUENCY / distance;
        double hills = Hashing.smoothNoise(seed, dx * scale, dy * scale, dz * scale);
        return radius * (1.0 + bumpiness * hills);
    }

    /**
     * Ores come in small clusters: the planet is split into 2x2x2 groups of blocks, a few
     * groups hold an ore, and most blocks of such a group get it.
     */
    private Material.Ore oreAt(int x, int y, int z, double inner) {
        int gx = x >> 1;
        int gy = y >> 1;
        int gz = z >> 1;
        if (Hashing.unit(seed, gx, gy, gz) >= theme.oreChance()) {
            return null;
        }
        if (Hashing.unit(seed ^ 0x5DEECE66DL, x, y, z) >= 0.7) {
            return null;
        }
        int total = 0;
        for (Material.Ore ore : Material.Ore.values()) {
            if (inner >= ore.minDepth) {
                total += theme.oreWeight(ore);
            }
        }
        if (total == 0) {
            return null;
        }
        int pick = (int) (Hashing.unit(seed ^ 0x2545F4914F6CDD1DL, gx, gy, gz) * total);
        for (Material.Ore ore : Material.Ore.values()) {
            if (inner >= ore.minDepth) {
                pick -= theme.oreWeight(ore);
                if (pick < 0) {
                    return ore;
                }
            }
        }
        return null;
    }

    /**
     * The y of the highest block of this planet in a column, or {@link Integer#MIN_VALUE}
     * when the column misses the planet.
     */
    public int topY(int x, int z) {
        int highest = (int) Math.floor(center.y + extent());
        int lowest = (int) Math.floor(center.y - extent());
        for (int y = highest; y >= lowest; y--) {
            if (materialAt(x, y, z) != null) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** The block just above the top of the planet, where a player can stand. */
    public Vec3 landingSpot() {
        int x = (int) Math.floor(center.x);
        int z = (int) Math.floor(center.z);
        return new Vec3(x + 0.5, topY(x, z) + 1, z + 0.5);
    }
}
