package io.github.chocodanette.galaxy.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.gravity.GravityField;

/**
 * Launch Blooms: giant flowers that throw you to another planet.
 *
 * A bloom has a stem, a flower head and {@link #LEAVES} big leaves you can stand on. Some
 * leaves are <b>linked</b> to a leaf of a bloom on a nearby planet: their middle block is a
 * glowing launch pad, and jumping on it (while crouching) throws you along a fixed arc that
 * lands on the partner pad (see {@link BloomPath}).
 *
 * Which planets are linked is worked out from the world seed alone, so the server, every chunk
 * and every test agree, and every link works both ways:
 * <ul>
 * <li>two big-enough planets whose surfaces are {@link #MIN_GAP} to {@link #MAX_GAP} blocks
 * apart are candidates, and a hash of the pair decides whether they want a link;</li>
 * <li>each planet keeps its {@link #LEAVES} wanted partners with the lowest hash;</li>
 * <li>a link is real when both planets keep each other.</li>
 * </ul>
 * A planet with at least one real link grows one bloom, on the side facing its first partner.
 *
 * <b>Homeward Blooms</b> are a rarer, blue kind: about one big planet in
 * {@code 1 / HOMEWARD_CHANCE} grows one instead of a normal bloom (never the arrival planet).
 * One of its leaves throws you straight up, out of the galaxy and back to the station.
 */
public final class Blooms {
    /** Leaves per bloom, so the most links a planet can have. */
    public static final int LEAVES = 4;
    /** Closest two linked planets' surfaces may be (closer, the charged hop is enough). */
    static final double MIN_GAP = 12.0;
    /** Farthest two linked planets' surfaces may be. */
    static final double MAX_GAP = 100.0;
    /** Share of candidate pairs that want a link. */
    static final double LINK_CHANCE = 0.5;
    /** Smallest planet that grows a bloom. */
    static final double MIN_RADIUS = 8.0;
    /** How far the bloom stands from the middle of its face, so it isn't on the landing spot. */
    private static final double OFFSET = 5.0;
    /** Share of big planets that grow a Homeward Bloom (about 1 in 15). */
    static final double HOMEWARD_CHANCE = 1.0 / 15.0;
    /** The leaf of a Homeward Bloom that throws you home (the lowest, easiest to reach). */
    static final int HOME_LEAF = 0;

    private Blooms() {
    }

    /** What a bloom block is. */
    public enum Part {
        STEM, LEAF, PAD, PETAL, CORE,
        /** The Homeward Bloom's own colors. */
        HOME_PAD, HOME_PETAL, HOME_CORE
    }

    /** One block of a bloom. */
    public record Block(BlockPos pos, Part part, Direction up) {
    }

    /**
     * One leaf of a bloom. {@code pad} is the middle block of the leaf: you stand on top of it.
     * For a linked leaf, {@code partner} is the other planet and {@code partnerLeaf} the index
     * of the leaf it lands on; for a plain leaf, partner is null. {@code home} is true for the
     * leaf of a Homeward Bloom that throws you back to the station.
     */
    public record Leaf(BlockPos pad, Direction up, Planet partner, int partnerLeaf, boolean home) {
        public boolean linked() {
            return partner != null;
        }

        /** True when jumping on this leaf throws you somewhere (another planet, or home). */
        public boolean launches() {
            return linked() || home;
        }

        /** Where your feet are when standing in the middle of this leaf. */
        public Vec3 standSpot() {
            return Vec3.atCenterOf(pad).add(up.getUnitVec3().scale(0.5));
        }
    }

    /** A planet's bloom: its blocks and its leaves (always {@link #LEAVES} of them). */
    public record Bloom(Planet planet, Direction up, List<Block> blocks, List<Leaf> leaves, boolean homeward) {
    }

    // ---------------------------------------------------------------- links

    static boolean canGrowBloom(Planet planet) {
        return planet.radius() >= MIN_RADIUS && planet.radius() > GalaxyLayout.MAX_GATEWAY_RADIUS;
    }

    /**
     * True when this planet grows a Homeward Bloom: a big planet that isn't its galaxy's
     * arrival planet and wins a roll of {@link #HOMEWARD_CHANCE}. It grows no normal bloom.
     */
    public static boolean isHomeward(long worldSeed, Planet planet) {
        if (!canGrowBloom(planet)) {
            return false;
        }
        double roll = (Hashing.mix(planet.seed(), 0x2545F4914F6CDD1DL, 0x6A09E667F3BCC909L) >>> 11) * 0x1.0p-53;
        if (roll >= HOMEWARD_CHANCE) {
            return false;
        }
        int number = Galaxy.numberAt(planet.center().x);
        return number == 0 || GalaxyLayout.arrivalPlanet(worldSeed, number).seed() != planet.seed();
    }

    /** True when this planet can be linked to others by normal Launch Blooms. */
    static boolean canLink(long worldSeed, Planet planet) {
        return canGrowBloom(planet) && !isHomeward(worldSeed, planet);
    }

    static double gap(Planet a, Planet b) {
        return a.center().distanceTo(b.center()) - a.extent() - b.extent();
    }

    /** Same number whichever way round the pair is given. */
    static double pairRoll(Planet a, Planet b) {
        long lo = Math.min(a.seed(), b.seed());
        long hi = Math.max(a.seed(), b.seed());
        return (Hashing.mix(lo, hi, 0x5BE0CD19137E2179L) >>> 11) * 0x1.0p-53;
    }

    /** Planets this one would like to link with, best (lowest roll) first, at most {@link #LEAVES}. */
    static List<Planet> wanted(long worldSeed, Planet planet) {
        if (!canLink(worldSeed, planet)) {
            return List.of();
        }
        int reach = (int) Math.ceil((MAX_GAP + 2 * GalaxyLayout.MAX_EXTENT) / GalaxyLayout.CELL_SIZE) + 1;
        int cx = GalaxyLayout.cellOf(planet.center().x);
        int cz = GalaxyLayout.cellOf(planet.center().z);
        List<Planet> found = new ArrayList<>();
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                for (Planet other : GalaxyLayout.planetsInCell(worldSeed, x, z)) {
                    if (other.equals(planet) || !canLink(worldSeed, other)) {
                        continue;
                    }
                    double gap = gap(planet, other);
                    if (gap >= MIN_GAP && gap <= MAX_GAP && pairRoll(planet, other) < LINK_CHANCE) {
                        found.add(other);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(other -> pairRoll(planet, other)));
        return found.size() > LEAVES ? List.copyOf(found.subList(0, LEAVES)) : found;
    }

    /** The planets this one is really linked with (both want it), best first. */
    public static List<Planet> links(long worldSeed, Planet planet) {
        List<Planet> links = new ArrayList<>();
        for (Planet other : wanted(worldSeed, planet)) {
            if (wanted(worldSeed, other).contains(planet)) {
                links.add(other);
            }
        }
        return links;
    }

    // ---------------------------------------------------------------- blooms

    private static final Map<Planet, Optional<Bloom>> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 512;

    /** The planet's bloom, if it has any link. Worked out once and kept. */
    public static Optional<Bloom> bloom(long worldSeed, Planet planet) {
        Optional<Bloom> cached = CACHE.get(planet);
        if (cached != null) {
            return cached;
        }
        Optional<Bloom> bloom;
        if (isHomeward(worldSeed, planet)) {
            bloom = growHomeward(planet);
        } else {
            List<Planet> links = links(worldSeed, planet);
            bloom = links.isEmpty() ? Optional.empty() : grow(worldSeed, planet, links);
        }
        if (CACHE.size() > CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(planet, bloom);
        return bloom;
    }

    /** The leaf whose pad is at this position, on any planet near it. */
    public static Optional<Leaf> leafAt(long worldSeed, BlockPos pad) {
        for (Planet planet : GalaxyLayout.planetsTouching(worldSeed, pad.getX(), pad.getZ(), pad.getX(), pad.getZ())) {
            Optional<Bloom> bloom = bloom(worldSeed, planet);
            if (bloom.isPresent()) {
                for (Leaf leaf : bloom.get().leaves()) {
                    if (leaf.pad().equals(pad)) {
                        return Optional.of(leaf);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** The leaf a linked leaf throws you to. */
    public static Optional<Leaf> target(long worldSeed, Leaf from) {
        // A home leaf throws you out of the galaxy, not to another leaf.
        if (!from.linked()) {
            return Optional.empty();
        }
        return bloom(worldSeed, from.partner()).map(bloom -> bloom.leaves().get(from.partnerLeaf()));
    }

    /** Leaf heights (above the ground) and sideways directions, in the bloom's own frame. */
    private static final int[][] LEAF_SPOTS = {{3, 3, 0}, {4, 0, 3}, {5, -3, 0}, {6, 0, -3}};
    private static final int STEM_HEIGHT = 8;
    /** How far a leaf's middle is from the stem. */
    static final int PAD_DISTANCE = 4;

    /** Where a planet's bloom stands: its "up" and the ground block under its stem. */
    private record Site(Direction up, int[] ground) {
    }

    private static Optional<Site> site(Planet planet, Vec3 toFirst) {
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        sides.sort(Comparator.comparingDouble(d -> -d.getUnitVec3().dot(toFirst)));
        double offset = Math.min(OFFSET, planet.radius() * 0.35);
        for (Direction up : sides) {
            if (planet.shape() == GravityField.Shape.HOLLOW && up.getAxis() == Direction.Axis.Y) {
                continue; // the tunnel is there
            }
            // A few spots near the middle of the face, but not right on it (that's the landing spot).
            for (Direction side : Direction.values()) {
                if (side.getAxis() == up.getAxis()) {
                    continue;
                }
                int[] ground = findGround(planet, up, side, offset);
                if (ground != null && fits(planet, up, ground)) {
                    return Optional.of(new Site(up, ground));
                }
            }
        }
        return Optional.empty();
    }

    /** True when no part of a bloom here would be inside the planet, and each pad has room above. */
    private static boolean fits(Planet planet, Direction up, int[] ground) {
        List<Block> blocks = new ArrayList<>();
        shape(blocks, null, ground, up, List.of(), false);
        for (Block block : blocks) {
            int n = block.part() == Part.PAD || isPadSpot(block, ground, up) ? 4 : 0;
            for (int h = 0; h <= n; h++) {
                BlockPos pos = block.pos().relative(up, h);
                if (planet.materialAt(pos.getX(), pos.getY(), pos.getZ()) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isPadSpot(Block block, int[] ground, Direction up) {
        for (int[] spot : LEAF_SPOTS) {
            int[] pad = Trees.place(ground, up, new Trees.Piece(spot[0], PAD_DISTANCE * Integer.signum(spot[1]),
                    PAD_DISTANCE * Integer.signum(spot[2]), Trees.Part.LEAVES));
            if (block.pos().equals(new BlockPos(pad[0], pad[1], pad[2]))) {
                return true;
            }
        }
        return false;
    }

    /** Whether this planet really grows a bloom (it has a link and somewhere to stand it). */
    private static boolean grows(long worldSeed, Planet planet) {
        List<Planet> links = links(worldSeed, planet);
        return !links.isEmpty() && site(planet, toward(planet, links.get(0))).isPresent();
    }

    private static Vec3 toward(Planet planet, Planet other) {
        return other.center().subtract(planet.center());
    }

    private static Optional<Bloom> grow(long worldSeed, Planet planet, List<Planet> links) {
        return site(planet, toward(planet, links.get(0)))
                .map(site -> build(worldSeed, planet, links, site.up(), site.ground()));
    }

    /** A Homeward Bloom, on a side of the planet picked from its seed. */
    private static Optional<Bloom> growHomeward(Planet planet) {
        Direction facing = Direction.values()[(int) Math.floorMod(Hashing.mix(planet.seed(), 0x3C6EF372FE94F82BL, 17L), 6L)];
        return site(planet, facing.getUnitVec3()).map(site -> {
            List<Block> blocks = new ArrayList<>();
            List<Leaf> leaves = new ArrayList<>();
            shape(blocks, leaves, site.ground(), site.up(), List.of(), true);
            for (int i = 0; i < leaves.size(); i++) {
                Leaf leaf = leaves.get(i);
                leaves.set(i, new Leaf(leaf.pad(), site.up(), null, -1, i == HOME_LEAF));
            }
            return new Bloom(planet, site.up(), List.copyOf(blocks), List.copyOf(leaves), true);
        });
    }

    /** The top block of the planet's face along "up", in the column shifted sideways. Null when there's no good ground. */
    private static int[] findGround(Planet planet, Direction up, Direction side, double offset) {
        Vec3 column = planet.center().add(side.getUnitVec3().scale(offset));
        Vec3 step = up.getUnitVec3();
        for (double d = planet.extent() + 2; d >= planet.radius() * 0.5; d -= 1.0) {
            BlockPos pos = BlockPos.containing(column.add(step.scale(d)));
            Material material = planet.materialAt(pos.getX(), pos.getY(), pos.getZ());
            if (material == null) {
                continue;
            }
            if (material != Material.TOP && material != Material.SIDE && material != Material.SOIL) {
                return null;
            }
            return new int[] {pos.getX(), pos.getY(), pos.getZ()};
        }
        return null;
    }

    private static Bloom build(long worldSeed, Planet planet, List<Planet> links, Direction up, int[] ground) {
        // Leaf i goes to link i, if the planet at the other end really has a bloom to land on.
        List<Planet> partners = new ArrayList<>();
        for (int i = 0; i < LEAVES; i++) {
            Planet partner = i < links.size() ? links.get(i) : null;
            partners.add(partner != null && grows(worldSeed, partner) ? partner : null);
        }
        List<Block> blocks = new ArrayList<>();
        List<Leaf> leaves = new ArrayList<>();
        shape(blocks, leaves, ground, up, partners, false);
        for (int i = 0; i < leaves.size(); i++) {
            Leaf leaf = leaves.get(i);
            Planet partner = partners.get(i);
            int partnerLeaf = partner == null ? -1 : links(worldSeed, partner).indexOf(planet);
            leaves.set(i, new Leaf(leaf.pad(), up, partner, partnerLeaf, false));
        }
        return new Bloom(planet, up, List.copyOf(blocks), List.copyOf(leaves), false);
    }

    /**
     * The bloom's blocks (and, when {@code leaves} isn't null, its leaves without partners yet).
     * A leaf's middle is a pad when {@code partners} has a planet for it, or, on a Homeward
     * Bloom ({@code homeward}), when it's the {@link #HOME_LEAF}.
     */
    private static void shape(List<Block> blocks, List<Leaf> leaves, int[] ground, Direction up, List<Planet> partners,
            boolean homeward) {
        Part pad = homeward ? Part.HOME_PAD : Part.PAD;
        Part petal = homeward ? Part.HOME_PETAL : Part.PETAL;
        Part core = homeward ? Part.HOME_CORE : Part.CORE;
        for (int i = 0; i < LEAVES; i++) {
            int h = LEAF_SPOTS[i][0];
            int da = Integer.signum(LEAF_SPOTS[i][1]);
            int db = Integer.signum(LEAF_SPOTS[i][2]);
            boolean linked = homeward ? i == HOME_LEAF : i < partners.size() && partners.get(i) != null;
            // The leaf's stalk from the stem, then a rounded 3x3 pad far enough out that
            // nothing of the flower is above it (you fly straight up off it).
            add(blocks, ground, up, h, da, db, Part.LEAF);
            add(blocks, ground, up, h, 2 * da, 2 * db, Part.LEAF);
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a != 0 && b != 0 && (a * da + b * db) <= 0) {
                        continue; // trim the two corners nearest the stem
                    }
                    boolean middle = a == 0 && b == 0;
                    add(blocks, ground, up, h, PAD_DISTANCE * da + a, PAD_DISTANCE * db + b,
                            middle && linked ? pad : Part.LEAF);
                }
            }
            if (leaves != null) {
                int[] at = Trees.place(ground, up, new Trees.Piece(h, PAD_DISTANCE * da, PAD_DISTANCE * db, Trees.Part.LEAVES));
                leaves.add(new Leaf(new BlockPos(at[0], at[1], at[2]), up, null, -1, false));
            }
        }
        // The flower head: a glowing middle with a ring of petals.
        for (int a = -2; a <= 2; a++) {
            for (int b = -2; b <= 2; b++) {
                if (Math.abs(a) == 2 && Math.abs(b) == 2) {
                    continue;
                }
                boolean middle = Math.abs(a) + Math.abs(b) <= 1;
                add(blocks, ground, up, STEM_HEIGHT + 1, a, b, middle ? core : petal);
            }
        }
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (Math.abs(a) + Math.abs(b) == 1) {
                    add(blocks, ground, up, STEM_HEIGHT + 2, a * 2, b * 2, petal);
                }
            }
        }
        // The stem last, so it wins where a leaf's stalk meets it.
        for (int h = 1; h <= STEM_HEIGHT; h++) {
            add(blocks, ground, up, h, 0, 0, Part.STEM);
        }
    }

    private static void add(List<Block> blocks, int[] ground, Direction up, int h, int a, int b, Part part) {
        int[] at = Trees.place(ground, up, new Trees.Piece(h, a, b, Trees.Part.LEAVES));
        blocks.add(new Block(new BlockPos(at[0], at[1], at[2]), part, up));
    }
}
