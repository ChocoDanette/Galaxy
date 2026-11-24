package io.github.chocodanette.galaxy.gravity;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Crawling through tight tunnels on a planet: sneak into a gap one block tall and you drop to
 * crawling (the swimming pose on land, as under a trapdoor in the overworld). You get up again
 * as soon as there's room. Works along any "up", so side tunnels and ceilings work too.
 *
 * Both the server and the player's game run this, from the same inputs, so they agree.
 */
public final class CaveMoves {
    /** How far ahead of the feet we look for a gap, in blocks. */
    private static final double LOOK_AHEAD = 0.45;

    private CaveMoves() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(CaveMoves::onPlayerTick);
    }

    private static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        GravityFrame frame = GravityFrames.of(player);
        if (frame == null || GravityFrames.worldFor(player).fields().isEmpty()) {
            return;
        }
        if (frame.isCrawling()) {
            if (player.getForcedPose() != Pose.SWIMMING || fits(player, frame, Pose.CROUCHING, Vec3.ZERO)
                    || player.isInWater() || player.isPassenger() || player.getAbilities().flying) {
                if (player.getForcedPose() == Pose.SWIMMING) {
                    player.setForcedPose(null);
                }
                frame.setCrawling(false);
            }
            return;
        }
        if (!player.isShiftKeyDown() || !player.onGround() || player.isInWater() || player.isPassenger()
                || player.getAbilities().flying || player.getForcedPose() != null) {
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        Vec3 ahead = frame.toWorld(new Vec3(-Math.sin(yaw), 0, Math.cos(yaw))).scale(LOOK_AHEAD);
        if (!fits(player, frame, Pose.CROUCHING, ahead) && fits(player, frame, Pose.SWIMMING, ahead)) {
            player.setForcedPose(Pose.SWIMMING);
            frame.setCrawling(true);
        }
    }

    /** True if the player's hitbox for that pose, stood along their "up", fits at an offset. */
    private static boolean fits(Player player, GravityFrame frame, Pose pose, Vec3 offset) {
        EntityDimensions size = player.getDimensions(pose);
        AABB box = GravityFrames.orientedBox(GravityFrames.boxAxis(frame), size.width(), size.height(),
                player.position().add(offset));
        return player.level().noCollision(player, box.deflate(1.0E-7));
    }
}
