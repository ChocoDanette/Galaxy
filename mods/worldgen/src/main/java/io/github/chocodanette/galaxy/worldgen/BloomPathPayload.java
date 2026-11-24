package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Server to player: the arc a Launch Bloom pad throws you along (players don't know the world
 * seed, so they can't work out the partner leaf themselves). With {@code launch}, start flying.
 */
public record BloomPathPayload(BlockPos pad, BloomPath path, boolean launch) implements CustomPacketPayload {
    public static final Type<BloomPathPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyWorldgen.MODID, "bloom_path"));

    private static final StreamCodec<RegistryFriendlyByteBuf, BloomPath> PATH_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, BloomPath::start,
            Vec3.STREAM_CODEC, BloomPath::startUp,
            Vec3.STREAM_CODEC, BloomPath::end,
            Vec3.STREAM_CODEC, BloomPath::endUp,
            ByteBufCodecs.VAR_INT, BloomPath::ticks,
            BloomPath::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, BloomPathPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BloomPathPayload::pad,
            PATH_CODEC, BloomPathPayload::path,
            ByteBufCodecs.BOOL, BloomPathPayload::launch,
            BloomPathPayload::new);

    @Override
    public Type<BloomPathPayload> type() {
        return TYPE;
    }
}
