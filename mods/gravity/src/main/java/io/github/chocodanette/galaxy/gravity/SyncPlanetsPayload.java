package io.github.chocodanette.galaxy.gravity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to player: the full list of planets in the player's current dimension. */
public record SyncPlanetsPayload(List<GravityField> fields) implements CustomPacketPayload {
    public static final Type<SyncPlanetsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "sync_planets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlanetsPayload> STREAM_CODEC = StreamCodec.composite(
            GravityField.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)), SyncPlanetsPayload::fields,
            SyncPlanetsPayload::new);

    @Override
    public Type<SyncPlanetsPayload> type() {
        return TYPE;
    }
}
