package io.github.chocodanette.galaxy.gravity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to player: the full list of planets in the player's current dimension, and whether to draw their ranges. */
public record SyncPlanetsPayload(List<GravityField> fields, boolean showRanges) implements CustomPacketPayload {
    public static final Type<SyncPlanetsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "sync_planets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlanetsPayload> STREAM_CODEC = StreamCodec.composite(
            GravityField.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)), SyncPlanetsPayload::fields,
            ByteBufCodecs.BOOL, SyncPlanetsPayload::showRanges,
            SyncPlanetsPayload::new);

    /** The current planets and settings of a dimension. */
    public static SyncPlanetsPayload of(PlanetGravityData data) {
        return new SyncPlanetsPayload(data.fields(), data.showRanges());
    }

    @Override
    public Type<SyncPlanetsPayload> type() {
        return TYPE;
    }
}
