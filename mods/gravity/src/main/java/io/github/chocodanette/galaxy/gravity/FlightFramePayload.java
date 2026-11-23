package io.github.chocodanette.galaxy.gravity;

import org.joml.Quaternionf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A player's free-flight frame. Sent by the flying player to the server each tick, and by the
 * server to the players who can see them, so everyone agrees on which way they're turned.
 *
 * @param entityId the flying player (ignored on the way to the server)
 * @param free     true while free-flying; false once flight ends and planet gravity takes over
 * @param rotation the frame's rotation from world space
 */
public record FlightFramePayload(int entityId, boolean free, Quaternionf rotation) implements CustomPacketPayload {
    public static final Type<FlightFramePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "flight_frame"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlightFramePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FlightFramePayload::entityId,
            ByteBufCodecs.BOOL, FlightFramePayload::free,
            ByteBufCodecs.QUATERNIONF.map(Quaternionf::new, q -> q), FlightFramePayload::rotation,
            FlightFramePayload::new);

    @Override
    public Type<FlightFramePayload> type() {
        return TYPE;
    }
}
