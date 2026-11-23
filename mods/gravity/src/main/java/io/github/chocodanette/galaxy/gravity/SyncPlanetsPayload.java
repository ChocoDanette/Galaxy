package io.github.chocodanette.galaxy.gravity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Server to player: the full list of planets in the player's current dimension, and whether to draw their ranges. */
public record SyncPlanetsPayload(List<GravityField> fields, double spacePull, List<Vec3> anchors, GravityMode mode, boolean showRanges) implements CustomPacketPayload {
    public static final Type<SyncPlanetsPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "sync_planets"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlanetsPayload> STREAM_CODEC = StreamCodec.composite(
            GravityField.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)), SyncPlanetsPayload::fields,
            ByteBufCodecs.DOUBLE, SyncPlanetsPayload::spacePull,
            Vec3.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncPlanetsPayload::anchors,
            GravityMode.STREAM_CODEC, SyncPlanetsPayload::mode,
            ByteBufCodecs.BOOL, SyncPlanetsPayload::showRanges,
            SyncPlanetsPayload::new);

    /** The current planets and settings of a dimension. */
    public static SyncPlanetsPayload of(PlanetGravityData data) {
        GravityWorld world = data.world();
        return new SyncPlanetsPayload(world.fields(), world.spacePull(), world.anchors(), world.mode(), data.showRanges());
    }

    @Override
    public Type<SyncPlanetsPayload> type() {
        return TYPE;
    }
}
