package io.github.chocodanette.galaxy.gravity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
 */
public record GravityField(Vec3 center, double radius, double range, double strength, Shape shape) {
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
            Shape.CODEC.optionalFieldOf("shape", Shape.SPHERE).forGetter(GravityField::shape)
    ).apply(i, GravityField::new));

    /** How a field is sent from the server to players. */
    public static final StreamCodec<ByteBuf, GravityField> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, GravityField::center,
            ByteBufCodecs.DOUBLE, GravityField::radius,
            ByteBufCodecs.DOUBLE, GravityField::range,
            ByteBufCodecs.DOUBLE, GravityField::strength,
            Shape.STREAM_CODEC, GravityField::shape,
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
    }

    /** A round planet. */
    public GravityField(Vec3 center, double radius, double range, double strength) {
        this(center, radius, range, strength, Shape.SPHERE);
    }

    /** True when the position is inside this field's reach. */
    public boolean contains(Vec3 pos) {
        return pos.distanceToSqr(center) <= range * range;
    }

    /**
     * The point that gravity pulls toward: the center for a sphere. For a cube it is the
     * closest point of a smaller cube inside it, so on a face you're pulled straight into
     * the face, and only near an edge does the pull turn over the edge. A hollow planet works
     * the same way with a thinner copy of its shell (see {@link #hollowPullPoint}).
     */
    public Vec3 pullPoint(Vec3 pos) {
        if (shape == Shape.SPHERE) {
            return center;
        }
        if (shape == Shape.HOLLOW) {
            return hollowPullPoint(pos);
        }
        double core = coreHalfSize();
        return new Vec3(
                clamp(pos.x, center.x - core, center.x + core),
                clamp(pos.y, center.y - core, center.y + core),
                clamp(pos.z, center.z - core, center.z + core));
    }

    /** Distance from the position to the planet's surface (negative when inside the planet). */
    public double distanceToSurface(Vec3 pos) {
        if (shape == Shape.SPHERE) {
            return pos.distanceTo(center) - radius;
        }
        if (shape == Shape.HOLLOW) {
            return pos.distanceTo(pullPoint(pos)) - CUBE_EDGE_ROUNDING;
        }
        return pos.distanceTo(pullPoint(pos)) - (radius - coreHalfSize());
    }

    /** How thick the shell of a hollow planet of this radius is, in blocks. */
    public static double hollowShellThickness(double radius) {
        return Math.max(5.0, Math.min(7.0, Math.round(radius * 0.25)));
    }

    /** How wide the tunnels through a hollow planet's poles are (from the middle to the wall). */
    public static double hollowTunnelRadius(double radius) {
        return Math.max(3.0, Math.min(4.0, Math.round(radius * 0.15)));
    }

    /** For a hollow planet, the radius of the open space inside its shell. */
    public double innerRadius() {
        return shape == Shape.HOLLOW ? radius - hollowShellThickness(radius) : 0.0;
    }

    /**
     * A hollow planet is a shell with a big open space inside and a tunnel through its top and
     * bottom (along world y). Its gravity pulls toward the closest point of a thinner copy of
     * that shell, the same trick cubes use for their edges. Outside, that points to the
     * center. Inside, it points out to the inner ground, so you walk on the inside. In a
     * tunnel it points to the tunnel wall, and at the tunnel's ends it bends smoothly around
     * the rim, so you can walk from the outside, through the tunnel, onto the inside.
     *
     * The math happens on a flat slice through the poles: s is the height along the tunnel
     * and w the distance from the tunnel's middle line. The thin shell on that slice is a
     * ring between two circles with a strip cut out near the line.
     */
    private Vec3 hollowPullPoint(Vec3 pos) {
        double rounding = CUBE_EDGE_ROUNDING;
        double outer = radius - rounding;
        double inner = innerRadius() + rounding;
        double wall = hollowTunnelRadius(radius) + rounding;
        double rx = pos.x - center.x;
        double ry = pos.y - center.y;
        double rz = pos.z - center.z;
        double s = ry;
        double w = Math.sqrt(rx * rx + rz * rz);
        double ux = 1.0;
        double uz = 0.0;
        if (w > 1.0E-6) {
            ux = rx / w;
            uz = rz / w;
        }
        double norm = Math.sqrt(s * s + w * w);
        if (w >= wall && norm >= inner && norm <= outer) {
            return pos;
        }

        double bestS = 0.0;
        double bestW = 0.0;
        double best = Double.MAX_VALUE;
        // Straight out to the outer circle, or in to the inner circle, when that lands on the shell.
        for (double ring : new double[] {outer, inner}) {
            double cs;
            double cw;
            if (norm > 1.0E-6) {
                cs = s / norm * ring;
                cw = w / norm * ring;
            } else {
                cs = 0.0;
                cw = ring;
            }
            if (cw >= wall) {
                double d = sq(cs - s) + sq(cw - w);
                if (d < best) {
                    best = d;
                    bestS = cs;
                    bestW = cw;
                }
            }
        }
        // Across to the tunnel wall, stopping at the rim where it meets either circle.
        double low = Math.sqrt(Math.max(0.0, inner * inner - wall * wall));
        double high = Math.sqrt(Math.max(0.0, outer * outer - wall * wall));
        double side = s >= 0 ? 1.0 : -1.0;
        double cs = side * clamp(Math.abs(s), low, high);
        double d = sq(cs - s) + sq(wall - w);
        if (d < best) {
            bestS = cs;
            bestW = wall;
        }
        return new Vec3(center.x + ux * bestW, center.y + bestS, center.z + uz * bestW);
    }

    private static double sq(double value) {
        return value * value;
    }

    private double coreHalfSize() {
        return Math.max(0.0, radius - CUBE_EDGE_ROUNDING);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Shape implements StringRepresentable {
        SPHERE("sphere"),
        CUBE("cube"),
        /** A round shell you can walk on outside and inside, with a tunnel through its poles. */
        HOLLOW("hollow");

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
