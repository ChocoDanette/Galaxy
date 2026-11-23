package io.github.chocodanette.galaxy.gravity.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import io.github.chocodanette.galaxy.gravity.FlightFramePayload;
import io.github.chocodanette.galaxy.gravity.GalaxyGravity;
import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;
import io.github.chocodanette.galaxy.gravity.SpaceFlight;

/**
 * Your own free flight in space. Each tick, looking up or down turns your whole body (the
 * frame) that way instead of tilting your head, so your character always lines up with where
 * you look, and you can keep turning past straight up into a loop. W flies where you look,
 * jump and sneak go along your own up and down.
 *
 * Runs before the normal frame update, which then leaves a free frame alone.
 */
@EventBusSubscriber(modid = GalaxyGravity.MODID, value = Dist.CLIENT)
public final class SpaceFlightClient {
    private SpaceFlightClient() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onPlayerTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) {
            return;
        }
        GravityFrame frame = player.getData(GravityFrames.FRAME);
        if (!SpaceFlight.isFreeFlying(player)) {
            if (frame.isFree()) {
                frame.setFree(false);
                ClientPacketDistributor.sendToServer(new FlightFramePayload(player.getId(), false, frame.currentRotation()));
            }
            return;
        }
        Direction axisBefore = GravityFrames.boxAxis(frame);
        float pitch = player.getXRot();
        if (Math.abs(pitch) > 1.0E-3F) {
            // Turn the frame about its own sideways axis by the pitch, then look straight ahead
            // again: the view stays exactly where it was, but now the body points there too.
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            Vector3f flat = new Vector3f(-Mth.sin(yaw), 0, Mth.cos(yaw));
            float p = pitch * Mth.DEG_TO_RAD;
            Vector3f look = new Vector3f(-Mth.sin(yaw) * Mth.cos(p), -Mth.sin(p), Mth.cos(yaw) * Mth.cos(p));
            Quaternionf turn = new Quaternionf().rotationTo(flat, look);
            frame.snapTo(frame.currentRotation().mul(turn));
            player.setXRot(0.0F);
            player.xRotO = 0.0F;
        }
        frame.setFree(true);
        GravityFrames.afterTurn(player, axisBefore);
        player.resetFallDistance();
        ClientPacketDistributor.sendToServer(new FlightFramePayload(player.getId(), true, frame.currentRotation()));
    }
}
