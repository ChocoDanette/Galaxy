package io.github.chocodanette.galaxy.gravity;

import org.joml.Quaternionf;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to player: another player's free-flight frame, so everyone sees which way they are turned.
 *
 * @param entityId the flying player
 * @param free     true while free-flying; false once flight ends and planet gravity takes over
 * @param rotation the frame's rotation from world space
 */
public record FlightFrameUpdatePayload(int entityId, boolean free, Quaternionf rotation) implements CustomPacketPayload {
    public static final Type<FlightFrameUpdatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "flight_frame_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlightFrameUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FlightFrameUpdatePayload::entityId,
            ByteBufCodecs.BOOL, FlightFrameUpdatePayload::free,
            ByteBufCodecs.QUATERNIONF.map(Quaternionf::new, q -> q), FlightFrameUpdatePayload::rotation,
            FlightFrameUpdatePayload::new);

    @Override
    public Type<FlightFrameUpdatePayload> type() {
        return TYPE;
    }
}
