package io.github.chocodanette.galaxy.gravity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

import io.netty.buffer.ByteBuf;

/**
 * The gravity field around one planet.
 *
 * @param center   center of the planet, in block coordinates
 * @param radius   for a sphere or hollow planet, its (outer) radius; for a cube, half its
 *                 side length, in blocks
 * @param range    how far from the center the field reaches, in blocks (must be at least radius)
 * @param strength pull compared to vanilla gravity (1.0 = normal)
 * @param shape    the planet's shape, which decides which way "down" points on its surface
 * @param hover    0 for a normal planet. Above 0, a floating planet: it pulls you in from
 *                 afar but pushes you away when you get closer than this to its surface, so
 *                 you float around it at that height and can never land (gateway planets)
 */
public record GravityField(Vec3 center, double radius, double range, double strength, Shape shape, double hover) {
    /**
     * How far in from a cube's edges its gravity starts to bend over the edge, in blocks.
     * On the rest of a face, down points straight into the face so it walks like flat ground.
     */
    public static final double CUBE_EDGE_ROUNDING = 2.0;

    /** How a field is written into the world save. Older saves without a shape load as spheres. */
    public static final Codec<GravityField> CODEC = RecordCodecBuilder.create(i -> i.group(
            Vec3.CODEC.fieldOf("center").forGetter(GravityField::center),
            Codec.DOUBLE.fieldOf("radius").forGetter(GravityField::radius),
            Codec.DOUBLE.fieldOf("range").forGetter(GravityField::range),
            Codec.DOUBLE.fieldOf("strength").forGetter(GravityField::strength),
            Shape.CODEC.optionalFieldOf("shape", Shape.SPHERE).forGetter(GravityField::shape),
            Codec.DOUBLE.optionalFieldOf("hover", 0.0).forGetter(GravityField::hover)
    ).apply(i, GravityField::new));

    /** How a field is sent from the server to players. */
    public static final StreamCodec<ByteBuf, GravityField> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, GravityField::center,
            ByteBufCodecs.DOUBLE, GravityField::radius,
            ByteBufCodecs.DOUBLE, GravityField::range,
            ByteBufCodecs.DOUBLE, GravityField::strength,
            Shape.STREAM_CODEC, GravityField::shape,
            ByteBufCodecs.DOUBLE, GravityField::hover,
            GravityField::new);

    public GravityField {
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (range < radius) {
            throw new IllegalArgumentException("range must be at least radius");
        }
        if (strength < 0) {
            throw new IllegalArgumentException("strength must not be negative");
        }
        if (shape == null) {
            throw new IllegalArgumentException("shape must be set");
        }
        if (hover < 0) {
            throw new IllegalArgumentException("hover must not be negative");
        }
    }

    /** A normal planet you can land on. */
    public GravityField(Vec3 center, double radius, double range, double strength, Shape shape) {
        this(center, radius, range, strength, shape, 0.0);
    }

    /** True for a floating planet (see {@link #hover}). */
    public boolean floats() {
        return hover > 0;
    }

    /** How many blocks it takes the push to turn into a pull around the floating height. */
    public static final double HOVER_SPRING = 2.0;

    /**
     * Share of the pull at a position, from -1 to 1. Always 1 on a normal planet. On a floating
     * planet it is negative (pushes out) below the floating height, 0 right at it and positive
     * above, changing smoothly like a spring so you settle at that height.
     */
    public double pullShare(Vec3 pos) {
        if (!floats()) {
            return 1.0;
        }
        double off = (distanceToSurface(pos) - hover) / HOVER_SPRING;
        return Math.max(-1.0, Math.min(1.0, off));
    }

    /** A round planet. */
    public GravityField(Vec3 center, double radius, double range, double strength) {
        this(center, radius, range, strength, Shape.SPHERE);
    }

    /** True when the position is inside this field's reach. */
    public boolean contains(Vec3 pos) {
        return pos.distanceToSqr(center) <= range * range;
    }

    // ------------------------------------------------------------------ zone gravity
    //
    // Every planet is split into zones, and each zone has one "down" along a block direction.
    // The planet's core decides it for every block: a block inside one zone has that zone's
    // down, and a block the border between zones runs through has both downs. Players, mobs,
    // items, sand and water all follow the block they are in. Inside a zone the planet plays
    // like normal Minecraft: body, hitbox, blocks and mobs all line up.

    /**
     * How far past a zone border you must go before down switches back the way you came, in
     * blocks, so standing right on a border never flickers.
     */
    public static final double ZONE_BUFFER = 1.0;

    /** The zone's "up" at a position, with no memory of the zone you were in. */
    public Direction zoneUp(Vec3 pos) {
        return zoneUp(pos, null);
    }

    /**
     * The zone's "up" at a position. Each zone scores how far the position is into it; the
     * best one wins, but the zone you are already in ({@code current}) keeps winning until
     * another is ahead by more than {@link #ZONE_BUFFER}.
     *
     * <ul>
     * <li>Cubes, rounded cubes and round planets: six zones, split along the diagonals from
     * the center, so each face (or each sixth of a ball) has one down, into the planet.</li>
     * <li>Hollow planets (hollow cubes): six outer zones like a cube, and six inner zones
     * where down points out onto the inside walls. The middle of the shell is the border.</li>
     * <li>Donuts: a square ring around world y. Each side of its tube (top, bottom, outer
     * wall, inner wall) is a zone with down into the tube.</li>
     * <li>Discs: a flat round slab across world y. The top pulls down, the bottom pulls up,
     * and the rim wall pulls inward (along the nearest of four directions).</li>
     * </ul>
     */
    public Direction zoneUp(Vec3 pos, Direction current) {
        double[] score = zoneScores(pos, current);
        // Up wins ties (right at the middle), so you start the right way up.
        Direction best = Direction.UP;
        double bestScore = score[Direction.UP.ordinal()];
        for (Direction dir : Direction.values()) {
            if (score[dir.ordinal()] > bestScore) {
                bestScore = score[dir.ordinal()];
                best = dir;
            }
        }
        if (current != null && current != best && score[current.ordinal()] >= bestScore - ZONE_BUFFER) {
            return current;
        }
        return best;
    }

    /** How far a position is into each zone (indexed by {@link Direction#ordinal()}). */
    private double[] zoneScores(Vec3 pos, Direction current) {
        double dx = pos.x - center.x;
        double dy = pos.y - center.y;
        double dz = pos.z - center.z;
        double[] score = new double[6];
        java.util.Arrays.fill(score, Double.NEGATIVE_INFINITY);
        switch (shape) {
            case HOLLOW -> {
                double cheb = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                double middle = radius - hollowShellThickness(radius) / 2.0;
                // Outside the middle of the shell: like a cube. Inside: the same zones, flipped.
                double side = cheb - middle;
                double sign = side >= 0 ? 1.0 : -1.0;
                if (current != null && Math.abs(side) < ZONE_BUFFER) {
                    // Near the middle of the shell, stay on the side you were on.
                    sign = facesOut(current, dx, dy, dz) ? 1.0 : -1.0;
                }
                boxScores(score, dx, dy, dz, sign);
            }
            case DONUT -> {
                // Seen across the tube, its four sides split along the tube's diagonals: the
                // top and bottom score how far up or down you are, the outer and inner walls
                // how far out or in from the tube's middle line.
                double middle = radius - donutTube(radius);
                score[Direction.UP.ordinal()] = dy;
                score[Direction.DOWN.ordinal()] = -dy;
                double ax = Math.abs(dx);
                double az = Math.abs(dz);
                double u = Math.max(ax, az) - middle;
                // The walls belong to the side of the square ring you're on (x or z); the
                // other side's walls only come close near the ring's corners.
                Direction xOut = dx >= 0 ? Direction.EAST : Direction.WEST;
                Direction zOut = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
                Direction mine = ax >= az ? xOut : zOut;
                Direction other = ax >= az ? zOut : xOut;
                double corner = Math.abs(ax - az);
                score[mine.ordinal()] = u;
                score[mine.getOpposite().ordinal()] = -u;
                score[other.ordinal()] = u - corner;
                score[other.getOpposite().ordinal()] = -u - corner;
            }
            case DISC -> {
                // Top pulls down, bottom pulls up, and past the rim the rim wall takes over:
                // the border is where you are as far out past the rim as above the top.
                double flat = Math.sqrt(dx * dx + dz * dz);
                double rimScore = flat - radius + discHalfThickness(radius);
                score[Direction.UP.ordinal()] = dy;
                score[Direction.DOWN.ordinal()] = -dy;
                double ax = Math.abs(dx);
                double az = Math.abs(dz);
                Direction xOut = dx >= 0 ? Direction.EAST : Direction.WEST;
                Direction zOut = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
                // The rim faces along the nearest of four directions.
                score[xOut.ordinal()] = rimScore - Math.max(0.0, az - ax);
                score[zOut.ordinal()] = rimScore - Math.max(0.0, ax - az);
            }
            default -> boxScores(score, dx, dy, dz, 1.0);
        }
        return score;
    }

    /** Cube zones: each face scores how far out the position is along it ({@code sign} -1 flips them). */
    private static void boxScores(double[] score, double dx, double dy, double dz, double sign) {
        score[Direction.EAST.ordinal()] = dx * sign;
        score[Direction.WEST.ordinal()] = -dx * sign;
        score[Direction.UP.ordinal()] = dy * sign;
        score[Direction.DOWN.ordinal()] = -dy * sign;
        score[Direction.SOUTH.ordinal()] = dz * sign;
        score[Direction.NORTH.ordinal()] = -dz * sign;
    }

    /** True if "up" along this direction points away from the center at this offset. */
    private static boolean facesOut(Direction up, double dx, double dy, double dz) {
        return up.getStepX() * dx + up.getStepY() * dy + up.getStepZ() * dz >= 0;
    }

    /**
     * Near a border, how deep into the next zone a block's floor faces reach, as a share of
     * how deep it is into its own zone. 0.6 is a wedge of about 30 degrees each side of the
     * border, so it gets wider the deeper you dig (like the drawing's yellow blocks).
     */
    public static final double FLOOR_WEDGE = 0.6;
    /** ... and at least this close to the border (blocks), so the edge blocks of a face count. */
    public static final double FLOOR_SLACK = 0.75;

    /**
     * The faces of a block that count as floor, given as the "up" an entity standing on that
     * face would have. A block inside a zone has one (its zone's), a block near the border
     * between two zones has both, and near a corner of a cube, three. Only floor faces hold
     * an entity: walk past the edge of any other face and you fall.
     */
    public java.util.EnumSet<Direction> blockUps(net.minecraft.core.BlockPos pos) {
        double[] score = zoneScores(Vec3.atCenterOf(pos), null);
        double best = Double.NEGATIVE_INFINITY;
        for (double value : score) {
            best = Math.max(best, value);
        }
        double needed = Math.min(best * FLOOR_WEDGE, best - FLOOR_SLACK);
        java.util.EnumSet<Direction> ups = java.util.EnumSet.noneOf(Direction.class);
        for (Direction dir : Direction.values()) {
            if (score[dir.ordinal()] > Double.NEGATIVE_INFINITY && score[dir.ordinal()] >= needed) {
                ups.add(dir);
            }
        }
        if (ups.isEmpty()) {
            ups.add(zoneUp(Vec3.atCenterOf(pos)));
        }
        return ups;
    }

    /**
     * The "up" for something in this block that had {@code current} before: it keeps
     * {@code current} while the block allows it, and otherwise takes the zone of the block's
     * middle.
     */
    public Direction blockUp(net.minecraft.core.BlockPos pos, Direction current) {
        if (current != null && blockUps(pos).contains(current)) {
            return current;
        }
        return zoneUp(Vec3.atCenterOf(pos));
    }

    /** Unit vector "down" for the zone at a position (no memory of the previous zone). */
    public Vec3 zoneDown(Vec3 pos) {
        return zoneUp(pos).getOpposite().getUnitVec3();
    }

    /** Distance from the position to the planet's surface (negative when inside the planet). */
    public double distanceToSurface(Vec3 pos) {
        double dx = pos.x - center.x;
        double dy = pos.y - center.y;
        double dz = pos.z - center.z;
        return switch (shape) {
            case SPHERE -> pos.distanceTo(center) - radius;
            case CUBE -> cheb(dx, dy, dz) - radius;
            case ROUNDED_CUBE -> {
                double core = Math.max(0.0, radius - roundedBevel(radius));
                double ex = dx - clamp(dx, -core, core);
                double ey = dy - clamp(dy, -core, core);
                double ez = dz - clamp(dz, -core, core);
                yield Math.sqrt(ex * ex + ey * ey + ez * ez) - (radius - core);
            }
            case HOLLOW -> {
                double cheb = cheb(dx, dy, dz);
                yield Math.max(cheb - radius, (radius - hollowShellThickness(radius)) - cheb);
            }
            case DONUT -> {
                double tube = donutTube(radius);
                double u = Math.max(Math.abs(dx), Math.abs(dz)) - (radius - tube);
                yield Math.max(Math.abs(u), Math.abs(dy)) - tube;
            }
            case DISC -> Math.max(Math.sqrt(dx * dx + dz * dz) - radius, Math.abs(dy) - discHalfThickness(radius));
        };
    }

    /**
     * How far into the planet's middle a position is, where zones on opposite sides meet, in
     * blocks: 0 right at the middle. Used for the push that carries you out of the middle.
     */
    public double distanceToMiddle(Vec3 pos) {
        double dx = pos.x - center.x;
        double dy = pos.y - center.y;
        double dz = pos.z - center.z;
        return switch (shape) {
            case HOLLOW -> Math.abs(cheb(dx, dy, dz) - (radius - hollowShellThickness(radius) / 2.0));
            case DONUT -> {
                double u = Math.max(Math.abs(dx), Math.abs(dz)) - (radius - donutTube(radius));
                yield Math.max(Math.abs(u), Math.abs(dy));
            }
            case DISC -> Math.sqrt(dx * dx + dz * dz) <= radius ? Math.abs(dy) : Double.MAX_VALUE;
            default -> cheb(dx, dy, dz);
        };
    }

    private static double cheb(double dx, double dy, double dz) {
        return Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
    }

    /** How thick the shell of a hollow planet of this half size is, in blocks. */
    public static double hollowShellThickness(double radius) {
        return Math.max(5.0, Math.min(7.0, Math.round(radius * 0.25)));
    }

    /** Half the side of the square doors through the middle of each face of a hollow planet. */
    public static double hollowDoorHalf(double radius) {
        return 1.5;
    }

    /** For a hollow planet, half the side of the open space inside its shell. */
    public double innerRadius() {
        return shape == Shape.HOLLOW ? radius - hollowShellThickness(radius) : 0.0;
    }

    /** Half the thickness of a donut's square tube; {@code radius} is half the whole ring's width. */
    public static double donutTube(double radius) {
        return Math.max(3.0, Math.min(8.0, Math.round(radius * 0.3)));
    }

    /** Half the thickness of a disc planet; {@code radius} is the disc's radius. */
    public static double discHalfThickness(double radius) {
        return Math.max(3.0, Math.min(7.0, Math.round(radius * 0.25)));
    }

    /**
     * How round the edges of a rounded cube of this half size are: the radius of the curve
     * that joins two faces, in blocks. Gravity only turns while you walk over that curve.
     */
    public static double roundedBevel(double halfSize) {
        return Math.max(1.5, Math.min(9.0, Math.round(halfSize * 0.4)));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Shape implements StringRepresentable {
        SPHERE("sphere"),
        CUBE("cube"),
        /** A hollow cube you can walk on outside and inside, with a door through each face. */
        HOLLOW("hollow"),
        /**
         * A cube with wide rounded edges. On each face down points straight into the face, so
         * blocks and gravity line up like in the overworld; it only turns on the rounded edges.
         */
        ROUNDED_CUBE("rounded_cube"),
        /** A square ring around world y, with a hole through the middle. */
        DONUT("donut"),
        /** A flat round slab across world y: walk on its top, its bottom and its rim. */
        DISC("disc");

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);
        public static final StreamCodec<ByteBuf, Shape> STREAM_CODEC =
                ByteBufCodecs.idMapper(id -> values()[id], Shape::ordinal);

        private final String name;

        Shape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
