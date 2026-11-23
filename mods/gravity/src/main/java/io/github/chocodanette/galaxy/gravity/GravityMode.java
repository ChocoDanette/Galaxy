package io.github.chocodanette.galaxy.gravity;

import com.mojang.serialization.Codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import io.netty.buffer.ByteBuf;

/**
 * How players and mobs decide which way is down on a planet. Kept switchable per dimension
 * (/galaxy planet mode ...) so the options can be compared in game.
 */
public enum GravityMode implements StringRepresentable {
    /**
     * Down points into the ground around your feet (the average of the nearby block faces), so
     * flat ground stays still and edges turn you smoothly. Far from any ground, it falls back
     * to the planet's pull toward its center.
     */
    GROUND("ground"),
    /** Down always points toward the planet's center (or into a cube's face). */
    CENTER("center");

    public static final Codec<GravityMode> CODEC = StringRepresentable.fromEnum(GravityMode::values);
    public static final StreamCodec<ByteBuf, GravityMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(id -> values()[id], GravityMode::ordinal);

    private final String name;

    GravityMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
