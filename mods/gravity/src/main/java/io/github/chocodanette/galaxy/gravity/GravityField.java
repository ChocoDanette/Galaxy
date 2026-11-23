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
 * @param radius   for a sphere, its radius; for a cube, half its side length, in blocks
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
     * the face, and only near an edge does the pull turn over the edge.
     */
    public Vec3 pullPoint(Vec3 pos) {
        if (shape == Shape.SPHERE) {
            return center;
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
        return pos.distanceTo(pullPoint(pos)) - (radius - coreHalfSize());
    }

    private double coreHalfSize() {
        return Math.max(0.0, radius - CUBE_EDGE_ROUNDING);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Shape implements StringRepresentable {
        SPHERE("sphere"),
        CUBE("cube");

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
