package io.github.chocodanette.galaxy.gravity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Keeps every player's copy of the planets in step with the server. */
public final class PlanetSync {
    private PlanetSync() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(PlanetSync::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(PlanetSync::onLogin);
        NeoForge.EVENT_BUS.addListener(PlanetSync::onChangeDimension);
        NeoForge.EVENT_BUS.addListener(PlanetSync::onRespawn);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(GalaxyGravity.MODID).versioned("1").playToClient(
                SyncPlanetsPayload.TYPE,
                SyncPlanetsPayload.STREAM_CODEC,
                (payload, context) -> ClientPlanets.set(payload.fields()));
    }

    /** Sends the planets of a dimension to everyone in it. Call after any change. */
    public static void sendToDimension(ServerLevel level) {
        PacketDistributor.sendToPlayersInDimension(level, new SyncPlanetsPayload(PlanetGravityData.get(level).fields()));
    }

    private static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncPlanetsPayload(PlanetGravityData.get(player.level()).fields()));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player);
        }
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendTo(player);
        }
    }
}
