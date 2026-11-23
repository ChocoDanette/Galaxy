package io.github.chocodanette.galaxy.gravity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Free flight in space (creative and spectator flying in a space dimension, like the galaxy).
 * While flying there, no planet pulls you and your frame follows your view, so you can turn
 * any way you like, loops included. When you stop flying, planet gravity takes over again
 * and turns you back toward the nearest planet.
 *
 * The flying player's own game decides the frame and tells the server, which passes it on to
 * everyone who can see them.
 */
public final class SpaceFlight {
    private SpaceFlight() {
    }

    /** True when this player is free-flying: flying (creative or spectator) in space. */
    public static boolean isFreeFlying(Entity entity) {
        return entity instanceof Player player && player.getAbilities().flying && Space.isSpace(player.level());
    }

    /** True when the entity's frame is currently held by free flight. */
    public static boolean holdsFrame(Entity entity) {
        GravityFrame frame = GravityFrames.of(entity);
        return frame != null && frame.isFree();
    }

    /** On the server: a flying player reports their frame. */
    static void onFromPlayer(ServerPlayer player, FlightFramePayload payload) {
        boolean allowed = payload.free() && player.getAbilities().flying && Space.isSpace(player.level());
        GravityFrame frame = player.getData(GravityFrames.FRAME);
        if (allowed) {
            frame.snapTo(payload.rotation());
        }
        frame.setFree(allowed);
        PacketDistributor.sendToPlayersTrackingEntity(player,
                new FlightFrameUpdatePayload(player.getId(), allowed, frame.currentRotation()));
    }

    /** On a player's game: another player's frame while they free-fly. */
    static void onFromServer(Level level, FlightFrameUpdatePayload payload) {
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(payload.entityId());
        if (entity instanceof Player player && !player.isLocalPlayer()) {
            GravityFrame frame = player.getData(GravityFrames.FRAME);
            if (payload.free()) {
                frame.snapTo(payload.rotation());
            }
            frame.setFree(payload.free());
        }
    }
}
