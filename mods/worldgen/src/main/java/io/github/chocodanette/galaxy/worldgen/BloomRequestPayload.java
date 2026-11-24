package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Player to server: "where does the Launch Bloom pad I'm standing on throw me?" and, with
 * {@code launch}, "throw me now".
 */
public record BloomRequestPayload(BlockPos pad, boolean launch) implements CustomPacketPayload {
    public static final Type<BloomRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyWorldgen.MODID, "bloom_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BloomRequestPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BloomRequestPayload::pad,
            ByteBufCodecs.BOOL, BloomRequestPayload::launch,
            BloomRequestPayload::new);

    @Override
    public Type<BloomRequestPayload> type() {
        return TYPE;
    }
}
