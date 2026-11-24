package io.github.chocodanette.galaxy.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;
import io.github.chocodanette.galaxy.gravity.GravityMath;

/**
 * One generated planet: where it is, its size and shape, what it's made of and how strongly
 * it pulls. Everything about it comes from its seed, so it is the same every time it's built.
 *
 * @param seed      decides the hills, caves, trees and ore spots of this planet
 * @param center    center of the planet, at the middle of a block
 * @param radius    for a sphere or hollow planet, its (average) radius; for a cube, half its
 *                  side length (blocks)
 * @param shape     sphere, cube or hollow
 * @param theme     what it's made of
 * @param bumpiness how hilly a sphere is, as a share of its radius (0 = perfectly round)
 * @param strength  gravity compared to normal (1.0 = normal)
 * @param caves     whether tunnels wind through the inside
 */
public record Planet(long seed, Vec3 center, double radius, GravityField.Shape shape, PlanetTheme theme,
                     double bumpiness, double strength, boolean caves) {
    /** How deep the soil layer under the surface goes, in blocks. */
    static final double SOIL_DEPTH = 4.0;
    /** Soil depth in the thin shell of a hollow planet, so there's still some rock. */
    private static final double HOLLOW_SOIL_DEPTH = 2.0;
    /** How bumpy the hills are: smaller is broader hills. */
    private static final double HILL_FREQUENCY = 2.4;
    /** Below this "up" amount, a sphere's surface uses the side block (see {@link PlanetTheme}). */
    private static final double TOP_THRESHOLD = 0.45;

    /** A planet without caves. */
    public Planet(long seed, Vec3 center, double radius, GravityField.Shape shape, PlanetTheme theme,
            double bumpiness, double strength) {
        this(seed, center, radius, shape, theme, bumpiness, strength, false);
    }

    /** Farthest a block of this planet's ground can be from its center along any axis. */
    public double extent() {
        return shape == GravityField.Shape.SPHERE || shape == GravityField.Shape.ROUNDED_CUBE
                ? radius * (1.0 + bumpiness)
                : radius;
    }

    /** Farthest anything of this planet reaches, trees included. */
    public double reach() {
        // Trees and Launch Blooms both stay within this height above the ground.
        return extent() + Trees.MAX_HEIGHT;
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
     * What the block at this position is made of, or null for empty space (or a cave).
     * Safe to call from several threads at once.
     */
    public Material materialAt(int x, int y, int z) {
        return materialAt(x, y, z, true);
    }

    private Material materialAt(int x, int y, int z, boolean carve) {
        double dx = x + 0.5 - center.x;
        double dy = y + 0.5 - center.y;
        double dz = z + 0.5 - center.z;
        double depth;
        boolean onTop;
        double inner;
        double soilDepth = SOIL_DEPTH;
        if (shape == GravityField.Shape.CUBE) {
            double ax = Math.abs(dx);
            double ay = Math.abs(dy);
            double az = Math.abs(dz);
            double far = Math.max(ax, Math.max(ay, az));
            depth = radius - far;
            onTop = dy > 0 && ay >= ax && ay >= az;
            inner = Math.min(1.0, depth / radius);
        } else if (shape == GravityField.Shape.ROUNDED_CUBE) {
            // Distance from a smaller, sharp cube inside: the faces are flat and the edges round.
            double bevel = GravityField.roundedBevel(radius);
            double core = radius - bevel;
            double qx = clamp(dx, core);
            double qy = clamp(dy, core);
            double qz = clamp(dz, core);
            double ex = dx - qx;
            double ey = dy - qy;
            double ez = dz - qz;
            double out = Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (out > bevel + bumpiness * radius + 1.0) {
                return null;
            }
            // Hills rise straight out of each face, following the point under them on the inner cube.
            double surface = bevel;
            if (bumpiness > 0) {
                double f = HILL_FREQUENCY / Math.max(radius, 1.0);
                surface += bumpiness * radius * Hashing.smoothNoise(seed, qx * f, qy * f, qz * f);
            }
            if (out > 1.0e-9) {
                depth = surface - out;
                onTop = ey / out > TOP_THRESHOLD;
            } else {
                double far = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                depth = surface + core - far;
                onTop = false;
            }
            inner = Math.min(1.0, depth / radius);
        } else if (shape == GravityField.Shape.HOLLOW) {
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > radius) {
                return null;
            }
            double cavity = innerRadius();
            if (distance < CORE_SUN_RADIUS) {
                return Material.GLOW;
            }
            double outerDepth = radius - distance;
            double innerDepth = distance - cavity;
            double wallDepth = Math.sqrt(dx * dx + dz * dz) - GravityField.hollowTunnelRadius(radius);
            depth = Math.min(outerDepth, Math.min(innerDepth, wallDepth));
            if (depth < 0) {
                return null;
            }
            if (depth < 1.0 && innerDepth < 1.0 && depth == innerDepth) {
                // The inner ground: lit here and there by glowing patches, since no light gets in.
                if (Hashing.unit(seed ^ 0x3C6EF372FE94F82BL, x, y, z) < HOLLOW_GLOW_CHANCE) {
                    return Material.GLOW;
                }
                return theme.topFalls() ? Material.SIDE : Material.TOP;
            }
            onTop = depth == outerDepth && dy / distance > TOP_THRESHOLD;
            inner = Math.min(1.0, depth / (GravityField.hollowShellThickness(radius) / 2.0));
            soilDepth = HOLLOW_SOIL_DEPTH;
        } else {
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > extent()) {
                return null;
            }
            depth = surfaceRadius(dx, dy, dz, distance) - distance;
            onTop = distance > 0 && dy / distance > TOP_THRESHOLD;
            inner = Math.min(1.0, depth / radius);
        }
        if (depth < 0) {
            return null;
        }
        if (carve && caves && isCave(x, y, z, depth, inner)) {
            return null;
        }
        if (depth < 1.0) {
            return onTop ? Material.TOP : Material.SIDE;
        }
        if (depth < soilDepth) {
            return Material.SOIL;
        }

        // 0 just under the soil, 1 at the very center.
        Material rock = inner > 0.7 ? Material.CORE : inner > 0.4 ? Material.DEEP : Material.STONE;
        Material.Ore ore = oreAt(x, y, z, inner);
        return ore == null ? rock : Material.ore(ore, rock != Material.STONE);
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    /** For a hollow planet, the radius of the open space inside. */
    public double innerRadius() {
        return gravityField().innerRadius();
    }

    // ---------------------------------------------------------------- hollow planets

    /** Size of the little glowing "sun" floating in the middle of a hollow planet. */
    static final double CORE_SUN_RADIUS = 1.8;
    /** Share of a hollow planet's inner ground that glows. */
    static final double HOLLOW_GLOW_CHANCE = 0.025;

    // ---------------------------------------------------------------- caves

    /** Size of the cave pattern: bigger means longer, more spread out tunnels. */
    private static final double CAVE_SCALE = 11.0;
    /** How wide tunnels are; tuned so a tunnel is about 2 to 4 blocks across. */
    private static final double CAVE_WIDTH = 0.24;

    /**
     * Caves are long winding tunnels: a spot is open where two smooth noise patterns are both
     * close to zero, which happens along lines. A third, broader pattern decides which parts
     * of the planet have tunnels at all, and where a tunnel may break through the surface.
     */
    private boolean isCave(int x, int y, int z, double depth, double inner) {
        if (inner > 0.8) {
            return false;
        }
        // Sand and gravel would pour into a tunnel right under them, so keep a roof there.
        if (theme.topFalls() && depth < SOIL_DEPTH + 1.0) {
            return false;
        }
        double f = 1.0 / CAVE_SCALE;
        double zone = Hashing.smoothNoise(seed ^ 0x1F83D9ABFB41BD6BL, x * f * 0.6, y * f * 0.6, z * f * 0.6);
        if (zone < -0.25) {
            return false;
        }
        if (depth < 3.0 && zone < 0.25) {
            return false;
        }
        double a = Hashing.smoothNoise(seed ^ 0x5BE0CD19137E2179L, x * f, y * f, z * f);
        if (Math.abs(a) >= CAVE_WIDTH) {
            return false;
        }
        double b = Hashing.smoothNoise(seed ^ 0x9B05688C2B3E6C1FL, x * f, y * f, z * f);
        return a * a + b * b < CAVE_WIDTH * CAVE_WIDTH;
    }

    /** Share of cave spots next to rock that get glow lichen. */
    static final double LICHEN_CHANCE = 0.09;

    /**
     * For a spot inside a cave, the side of it that glow lichen clings to, or null for no
     * lichen. Lichen can hang on any side, so it fits planets where "down" changes.
     */
    public Direction lichenAt(int x, int y, int z) {
        if (!caves || materialAt(x, y, z) != null || materialAt(x, y, z, false) == null) {
            return null;
        }
        double roll = Hashing.unit(seed ^ 0x510E527FADE682D1L, x, y, z);
        if (roll >= LICHEN_CHANCE) {
            return null;
        }
        Direction[] sides = Direction.values();
        int start = (int) (roll / LICHEN_CHANCE * sides.length);
        for (int i = 0; i < sides.length; i++) {
            Direction side = sides[(start + i) % sides.length];
            if (materialAt(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ()) != null) {
                return side;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- plants

    /** Share of open surface spots that grow a plant, on planets that have plants. */
    static final double PLANT_CHANCE = 0.15;
    /** Share of those plants that are starblooms instead of grass. */
    static final double BLOOM_SHARE = 0.12;

    /** A plant growing at a spot: which one, and which way it grows. */
    public record PlantSpot(boolean bloom, Direction up) {
    }

    /**
     * The plant growing in this empty spot, or null. Plants stand on the surface and grow
     * away from it, on every side of the planet (and on the inside of hollow ones).
     */
    public PlantSpot plantAt(int x, int y, int z) {
        if (!theme.hasPlants()) {
            return null;
        }
        Direction up = upAt(x, y, z);
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

    /** The block side that points most "up" at this spot, following the planet's gravity. */
    Direction upAt(int x, int y, int z) {
        double dx = x + 0.5 - center.x;
        double dy = y + 0.5 - center.y;
        double dz = z + 0.5 - center.z;
        if (shape == GravityField.Shape.SPHERE) {
            return Direction.getApproximateNearest(dx, dy, dz);
        }
        Vec3 pos = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        Vec3 down = GravityMath.downDirection(gravityField(), pos, GravityMath.VANILLA_DOWN);
        return Direction.getApproximateNearest(-down.x, -down.y, -down.z);
    }

    // ---------------------------------------------------------------- trees

    /** One block of a tree. */
    public record TreeBlock(int x, int y, int z, Trees.Part part, Trees.Style style, Direction up) {
    }

    /** Trees are worked out once per planet and kept, since every chunk of it asks. */
    private static final Map<Planet, List<TreeBlock>> TREE_CACHE = new ConcurrentHashMap<>();
    private static final int TREE_CACHE_LIMIT = 512;

    boolean hasTrees() {
        return !theme.treeStyles().isEmpty() && radius >= Trees.MIN_PLANET_RADIUS;
    }

    /**
     * Every tree block of this planet, leaves first and trunks last, so a trunk always wins
     * where two trees touch. Trees grow out of the ground along the planet's "up".
     */
    public List<TreeBlock> trees() {
        if (!hasTrees()) {
            return List.of();
        }
        List<TreeBlock> cached = TREE_CACHE.get(this);
        if (cached != null) {
            return cached;
        }
        List<TreeBlock> grown = growTrees();
        if (TREE_CACHE.size() > TREE_CACHE_LIMIT) {
            TREE_CACHE.clear();
        }
        TREE_CACHE.put(this, grown);
        return grown;
    }

    private List<TreeBlock> growTrees() {
        List<Trees.Style> styles = theme.treeStyles();
        boolean hollow = shape == GravityField.Shape.HOLLOW;
        double outerArea = shape == GravityField.Shape.CUBE || shape == GravityField.Shape.ROUNDED_CUBE
                ? 24.0 * radius * radius
                : 4.0 * Math.PI * radius * radius;
        double innerArea = hollow ? 4.0 * Math.PI * innerRadius() * innerRadius() : 0.0;
        int attempts = (int) ((outerArea + innerArea) * theme.treeDensity());
        SplittableRandom random = new SplittableRandom(seed ^ 0x243F6A8885A308D3L);
        List<int[]> roots = new ArrayList<>();
        List<TreeBlock> leaves = new ArrayList<>();
        List<TreeBlock> trunks = new ArrayList<>();
        List<Trees.Piece> pieces = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            // A random direction from the center, evenly spread over the sphere.
            double uy = random.nextDouble() * 2.0 - 1.0;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double flat = Math.sqrt(1.0 - uy * uy);
            double ux = flat * Math.cos(angle);
            double uz = flat * Math.sin(angle);
            boolean inside = hollow && random.nextDouble() * (outerArea + innerArea) < innerArea;
            Trees.Style style = styles.get(random.nextInt(styles.size()));
            long treeSeed = random.nextLong();

            int[] root = findGround(ux, uy, uz, inside);
            if (root == null) {
                continue;
            }
            Material ground = materialAt(root[0], root[1], root[2]);
            if (ground != Material.TOP && ground != Material.SIDE) {
                continue;
            }
            Direction up = inside
                    ? Direction.getApproximateNearest(-ux, -uy, -uz)
                    : Direction.getApproximateNearest(ux, uy, uz);
            int bx = root[0] + up.getStepX();
            int by = root[1] + up.getStepY();
            int bz = root[2] + up.getStepZ();
            if (materialAt(bx, by, bz) != null || upAt(bx, by, bz) != up || tooClose(roots, root)) {
                continue;
            }

            pieces.clear();
            Trees.shape(style, new SplittableRandom(treeSeed), pieces);
            if (!trunkIsClear(root, up, pieces)) {
                continue;
            }
            roots.add(root);
            for (Trees.Piece piece : pieces) {
                int[] at = Trees.place(root, up, piece);
                if (materialAt(at[0], at[1], at[2]) != null) {
                    continue;
                }
                TreeBlock block = new TreeBlock(at[0], at[1], at[2], piece.part(), style, up);
                (piece.part() == Trees.Part.TRUNK ? trunks : leaves).add(block);
            }
        }
        leaves.addAll(trunks);
        return List.copyOf(leaves);
    }

    /** The first ground block met when coming in from space (or out from the middle). */
    private int[] findGround(double ux, double uy, double uz, boolean fromInside) {
        int far = (int) Math.ceil(extent()) + 1;
        if (fromInside) {
            for (int k = 0; k <= far; k++) {
                int[] at = along(ux, uy, uz, k);
                Material m = materialAt(at[0], at[1], at[2]);
                if (m != null && m != Material.GLOW) {
                    return at;
                }
            }
            return null;
        }
        for (int k = far; k >= 0; k--) {
            int[] at = along(ux, uy, uz, k);
            if (materialAt(at[0], at[1], at[2]) != null) {
                return at;
            }
        }
        return null;
    }

    private int[] along(double ux, double uy, double uz, double distance) {
        return new int[] {
                (int) Math.floor(center.x + ux * distance),
                (int) Math.floor(center.y + uy * distance),
                (int) Math.floor(center.z + uz * distance)};
    }

    private static boolean tooClose(List<int[]> roots, int[] root) {
        for (int[] other : roots) {
            int dx = other[0] - root[0];
            int dy = other[1] - root[1];
            int dz = other[2] - root[2];
            if (dx * dx + dy * dy + dz * dz < Trees.MIN_SPACING * Trees.MIN_SPACING) {
                return true;
            }
        }
        return false;
    }

    private boolean trunkIsClear(int[] root, Direction up, List<Trees.Piece> pieces) {
        for (Trees.Piece piece : pieces) {
            if (piece.part() == Trees.Part.TRUNK) {
                int[] at = Trees.place(root, up, piece);
                if (materialAt(at[0], at[1], at[2]) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- ground shape

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

    /**
     * The block just above the top of the planet, where a player can stand. Hollow planets
     * have their tunnel at the top, so you land a little to the side of it.
     */
    public Vec3 landingSpot() {
        int x = (int) Math.floor(center.x);
        int z = (int) Math.floor(center.z);
        if (shape == GravityField.Shape.HOLLOW) {
            x += (int) Math.ceil(GravityField.hollowTunnelRadius(radius) + 2.0);
        }
        return new Vec3(x + 0.5, topY(x, z) + 1, z + 0.5);
    }
}
