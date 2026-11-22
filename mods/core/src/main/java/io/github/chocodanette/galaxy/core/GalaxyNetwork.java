package io.github.chocodanette.galaxy.core;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Single place where Galaxy mods register their network messages.
 *
 * The server is the authority for gravity, generation and ship state. Mods send
 * that state to players with messages registered here, never the other way round
 * (except player input).
 */
public final class GalaxyNetwork {
    /** Bump this when any message format changes, so mismatched clients are refused cleanly. */
    public static final String PROTOCOL_VERSION = "1";

    private GalaxyNetwork() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(GalaxyNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(GalaxyCore.MODID).versioned(PROTOCOL_VERSION);
        // Core has no messages of its own yet. Other mods register theirs with
        // event.registrar(theirModId) in their own listener.
    }
}
