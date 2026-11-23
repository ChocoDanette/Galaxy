package io.github.chocodanette.galaxy.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to player: what the console shows. With {@code openScreen} the console screen
 * opens; without it, an already open screen just refreshes (for example when a crewmate
 * adds fuel or jumps).
 */
public record ShipStatePayload(boolean openScreen, BlockPos console, int galaxy, int fuel, int maxFuel, int jumpCost)
        implements CustomPacketPayload {
    public static final Type<ShipStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GalaxyShip.MODID, "ship_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShipStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShipStatePayload::openScreen,
            BlockPos.STREAM_CODEC, ShipStatePayload::console,
            ByteBufCodecs.VAR_INT, ShipStatePayload::galaxy,
            ByteBufCodecs.VAR_INT, ShipStatePayload::fuel,
            ByteBufCodecs.VAR_INT, ShipStatePayload::maxFuel,
            ByteBufCodecs.VAR_INT, ShipStatePayload::jumpCost,
            ShipStatePayload::new);

    @Override
    public Type<ShipStatePayload> type() {
        return TYPE;
    }
}
