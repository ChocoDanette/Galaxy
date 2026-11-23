package io.github.chocodanette.galaxy.worldgen;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Tree shapes for planets. A tree is drawn in its own little frame: "h" is the height above
 * the ground block, "a" and "b" go sideways. {@link #place} turns that into world blocks
 * along the planet's "up" at the spot, so a tree on the side of a planet grows sideways.
 */
public final class Trees {
    /** Tallest a tree gets, counted from the ground block, plus its widest crown. */
    static final double MAX_HEIGHT = 11.0;
    /** Smallest planet that grows trees: tiny ones curve away too fast under a crown. */
    static final double MIN_PLANET_RADIUS = 6.0;
    /** Closest two trees may stand, in blocks. */
    static final int MIN_SPACING = 5;

    private Trees() {
    }

    /** What a tree block is: the trunk, its leaves (or cap), the second leaf kind, or a light. */
    public enum Part {
        TRUNK, LEAVES, LEAVES_ALT, LIGHT
    }

    /** One block of a tree shape, in the tree's own frame. */
    record Piece(int h, int a, int b, Part part) {
    }

    /** The kinds of trees, and the blocks each is made of. */
    public enum Style {
        OAK(() -> Blocks.OAK_LOG, () -> Blocks.OAK_LEAVES, () -> Blocks.OAK_LEAVES),
        BIRCH(() -> Blocks.BIRCH_LOG, () -> Blocks.BIRCH_LEAVES, () -> Blocks.BIRCH_LEAVES),
        SPRUCE(() -> Blocks.SPRUCE_LOG, () -> Blocks.SPRUCE_LEAVES, () -> Blocks.SPRUCE_LEAVES),
        AZALEA(() -> Blocks.OAK_LOG, () -> Blocks.AZALEA_LEAVES, () -> Blocks.FLOWERING_AZALEA_LEAVES),
        RED_MUSHROOM(() -> Blocks.MUSHROOM_STEM, () -> Blocks.RED_MUSHROOM_BLOCK, () -> Blocks.RED_MUSHROOM_BLOCK),
        BROWN_MUSHROOM(() -> Blocks.MUSHROOM_STEM, () -> Blocks.BROWN_MUSHROOM_BLOCK, () -> Blocks.BROWN_MUSHROOM_BLOCK),
        CRIMSON(() -> Blocks.CRIMSON_STEM, () -> Blocks.NETHER_WART_BLOCK, () -> Blocks.NETHER_WART_BLOCK);

        private final Supplier<Block> trunk;
        private final Supplier<Block> leaves;
        private final Supplier<Block> leavesAlt;

        Style(Supplier<Block> trunk, Supplier<Block> leaves, Supplier<Block> leavesAlt) {
            this.trunk = trunk;
            this.leaves = leaves;
            this.leavesAlt = leavesAlt;
        }

        public Block block(Part part) {
            return switch (part) {
                case TRUNK -> trunk.get();
                case LEAVES -> leaves.get();
                case LEAVES_ALT -> leavesAlt.get();
                case LIGHT -> Blocks.SHROOMLIGHT;
            };
        }
    }

    /** Fills {@code out} with the blocks of one tree of this style. */
    static void shape(Style style, SplittableRandom random, List<Piece> out) {
        switch (style) {
            case OAK, AZALEA -> roundTree(random, 4 + random.nextInt(3), style == Style.AZALEA, out);
            case BIRCH -> roundTree(random, 5 + random.nextInt(3), false, out);
            case SPRUCE -> spruce(random, 6 + random.nextInt(3), out);
            case RED_MUSHROOM -> redMushroom(4 + random.nextInt(3), out);
            case BROWN_MUSHROOM -> brownMushroom(4 + random.nextInt(3), out);
            case CRIMSON -> crimson(random, 3 + random.nextInt(3), out);
        }
    }

    private static void trunk(int height, List<Piece> out) {
        for (int h = 1; h <= height; h++) {
            out.add(new Piece(h, 0, 0, Part.TRUNK));
        }
    }

    /** An oak-like tree: a trunk with a rounded crown near the top. */
    private static void roundTree(SplittableRandom random, int height, boolean flowers, List<Piece> out) {
        for (int h = height - 2; h <= height + 1; h++) {
            int size = h >= height ? 1 : 2;
            for (int a = -size; a <= size; a++) {
                for (int b = -size; b <= size; b++) {
                    boolean corner = Math.abs(a) == size && Math.abs(b) == size;
                    if (corner && (h == height + 1 || random.nextInt(2) == 0)) {
                        continue;
                    }
                    if (a == 0 && b == 0 && h <= height) {
                        continue;
                    }
                    Part part = flowers && random.nextInt(4) == 0 ? Part.LEAVES_ALT : Part.LEAVES;
                    out.add(new Piece(h, a, b, part));
                }
            }
        }
        trunk(height, out);
    }

    /** A pointed tree: rings of leaves that shrink toward the top. */
    private static void spruce(SplittableRandom random, int height, List<Piece> out) {
        out.add(new Piece(height + 1, 0, 0, Part.LEAVES));
        int bottom = 2 + random.nextInt(2);
        for (int h = bottom; h <= height; h++) {
            int fromTop = height - h;
            int size = fromTop % 2 == 0 ? Math.min(2, fromTop / 2 + 1) : Math.min(2, fromTop / 2);
            for (int a = -size; a <= size; a++) {
                for (int b = -size; b <= size; b++) {
                    if ((a != 0 || b != 0) && Math.abs(a) + Math.abs(b) <= size + 1
                            && !(Math.abs(a) == size && Math.abs(b) == size && size > 0)) {
                        out.add(new Piece(h, a, b, Part.LEAVES));
                    }
                }
            }
        }
        trunk(height, out);
    }

    /** A big red mushroom: a stem and a domed cap. */
    private static void redMushroom(int height, List<Piece> out) {
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                out.add(new Piece(height + 1, a, b, Part.LEAVES));
            }
        }
        for (int h = height - 2; h <= height; h++) {
            for (int a = -2; a <= 2; a++) {
                for (int b = -2; b <= 2; b++) {
                    boolean rim = Math.abs(a) == 2 || Math.abs(b) == 2;
                    boolean corner = Math.abs(a) == 2 && Math.abs(b) == 2;
                    if (rim && !corner) {
                        out.add(new Piece(h, a, b, Part.LEAVES));
                    }
                }
            }
        }
        trunk(height, out);
    }

    /** A big brown mushroom: a stem and a wide flat cap. */
    private static void brownMushroom(int height, List<Piece> out) {
        for (int a = -3; a <= 3; a++) {
            for (int b = -3; b <= 3; b++) {
                if (Math.abs(a) == 3 && Math.abs(b) == 3) {
                    continue;
                }
                out.add(new Piece(height + 1, a, b, Part.LEAVES));
            }
        }
        trunk(height, out);
    }

    /** A crimson fungus tree with a few glowing bits in its cap, so ember planets have light. */
    private static void crimson(SplittableRandom random, int height, List<Piece> out) {
        for (int h = height - 1; h <= height + 1; h++) {
            int size = h == height + 1 ? 1 : 2;
            for (int a = -size; a <= size; a++) {
                for (int b = -size; b <= size; b++) {
                    if (Math.abs(a) + Math.abs(b) > size + 1 || (a == 0 && b == 0 && h <= height)) {
                        continue;
                    }
                    Part part = random.nextInt(7) == 0 ? Part.LIGHT : Part.LEAVES;
                    out.add(new Piece(h, a, b, part));
                }
            }
        }
        trunk(height, out);
    }

    /** World position of a tree piece for a tree standing on {@code ground} and growing toward {@code up}. */
    static int[] place(int[] ground, Direction up, Piece piece) {
        Direction.Axis axis = up.getAxis();
        // The two sideways directions: the axes other than "up".
        Direction.Axis sideA = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis sideB = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        int[] at = {ground[0], ground[1], ground[2]};
        add(at, axis, up.getAxisDirection().getStep() * piece.h());
        add(at, sideA, piece.a());
        add(at, sideB, piece.b());
        return at;
    }

    private static void add(int[] at, Direction.Axis axis, int amount) {
        switch (axis) {
            case X -> at[0] += amount;
            case Y -> at[1] += amount;
            case Z -> at[2] += amount;
        }
    }
}
