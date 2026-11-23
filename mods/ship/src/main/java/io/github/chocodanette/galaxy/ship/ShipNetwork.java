package io.github.chocodanette.galaxy.ship;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import io.github.chocodanette.galaxy.ship.client.ShipClient;

/** The console's two messages: the ship's state to players, and button presses to the server. */
public final class ShipNetwork {
    /** How close (in blocks) a player must be to the console for a button press to count. */
    private static final double CONSOLE_REACH = 8.0;

    private ShipNetwork() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(ShipNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(GalaxyShip.MODID).versioned("1");
        // The lambda body only runs on a player's game, so the client class is never loaded on a server.
        registrar.playToClient(ShipStatePayload.TYPE, ShipStatePayload.STREAM_CODEC,
                (payload, context) -> ShipClient.onState(payload));
        registrar.playToServer(ShipActionPayload.TYPE, ShipActionPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        onAction(player, payload);
                    }
                });
    }

    private static void onAction(ServerPlayer player, ShipActionPayload payload) {
        ServerLevel level = player.level();
        if (!level.getBlockState(payload.console()).is(ShipBlocks.CONSOLE.get())
                || player.position().distanceTo(Vec3.atCenterOf(payload.console())) > CONSOLE_REACH) {
            return;
        }
        switch (payload.action()) {
            case ShipActionPayload.LAND -> ShipCrew.land(player);
            case ShipActionPayload.JUMP -> ShipCrew.jump(player);
            default -> {
            }
        }
    }

    /** Refreshes the console screen of everyone on board. */
    public static void sendStateToShip(MinecraftServer server, boolean openScreen) {
        ServerLevel ship = ShipLocation.level(server);
        if (ship != null) {
            PacketDistributor.sendToPlayersInDimension(ship,
                    ShipData.get(server).state(openScreen, ShipLocation.CONSOLE));
        }
    }
}
