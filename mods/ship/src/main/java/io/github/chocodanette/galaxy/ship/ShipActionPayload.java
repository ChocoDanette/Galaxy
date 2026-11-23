package io.github.chocodanette.galaxy.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Player to server: a button pressed on the console screen. The server checks it all again. */
public record ShipActionPayload(int action, BlockPos console) implements CustomPacketPayload {
    public static final int LAND = 0;
    public static final int JUMP = 1;

    public static final Type<ShipActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyShip.MODID, "ship_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShipActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShipActionPayload::action,
            BlockPos.STREAM_CODEC, ShipActionPayload::console,
            ShipActionPayload::new);

    @Override
    public Type<ShipActionPayload> type() {
        return TYPE;
    }
}
