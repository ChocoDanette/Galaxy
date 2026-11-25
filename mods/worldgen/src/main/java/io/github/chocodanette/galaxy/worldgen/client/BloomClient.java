package io.github.chocodanette.galaxy.worldgen.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import io.github.chocodanette.galaxy.gravity.GravityFrame;
import io.github.chocodanette.galaxy.gravity.GravityFrames;
import io.github.chocodanette.galaxy.worldgen.BloomPath;
import io.github.chocodanette.galaxy.worldgen.BloomPathPayload;
import io.github.chocodanette.galaxy.worldgen.BloomRequestPayload;
import io.github.chocodanette.galaxy.worldgen.GalaxyWorldgen;
import io.github.chocodanette.galaxy.worldgen.PlanetBlocks;

/**
 * Your side of Launch Blooms. Standing on a glowing pad shows a hint; crouching shows the arc
 * it throws you along as a dotted line; jumping while crouching asks the server to launch you.
 * During the flight your game moves you along the arc, tick by tick, and the camera follows.
 */
@EventBusSubscriber(modid = GalaxyWorldgen.MODID, value = Dist.CLIENT)
public final class BloomClient {
    /** Arcs the server already told us about, by pad. */
    private static final Map<BlockPos, BloomPath> KNOWN = new HashMap<>();
    /** Pads asked about, so we ask only once each. */
    private static final Set<BlockPos> ASKED = new HashSet<>();
    private static @Nullable BlockPos lastPad;
    private static @Nullable BloomPath flight;
    private static int flightAge;
    /** The world the flight started in: a Homeward Bloom's flight ends when you're moved to the station. */
    private static @Nullable Level flightLevel;

    private BloomClient() {
    }

    /** The server answered: remember the arc, and start flying if it's a launch. */
    public static void onPath(BloomPathPayload payload) {
        KNOWN.put(payload.pad(), payload.path());
        if (payload.launch()) {
            flight = payload.path();
            flightAge = 0;
            flightLevel = Minecraft.getInstance().level;
        }
    }

    /** The pad block right under the player's feet, along their "up", or null. */
    private static @Nullable BlockPos padUnder(LocalPlayer player) {
        GravityFrame frame = GravityFrames.of(player);
        Vec3 up = frame != null ? frame.up() : new Vec3(0, 1, 0);
        BlockPos below = BlockPos.containing(player.position().subtract(up.scale(0.2)));
        return PlanetBlocks.isLaunchPad(player.level().getBlockState(below)) ? below : null;
    }

    @SubscribeEvent
    static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player
                || flight != null || !player.isCrouching()) {
            return;
        }
        BlockPos pad = padUnder(player);
        if (pad != null) {
            ClientPacketDistributor.sendToServer(new BloomRequestPayload(pad, true));
        }
    }

    @SubscribeEvent
    static void onPlayerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player) {
            return;
        }
        if (flight != null) {
            fly(player);
            return;
        }
        BlockPos pad = padUnder(player);
        if (pad != null && !pad.equals(lastPad)) {
            boolean home = player.level().getBlockState(pad).is(PlanetBlocks.HOMEWARD_PAD.get());
            player.sendOverlayMessage(Component.translatable(home ? "galaxy_worldgen.bloom.home_hint" : "galaxy_worldgen.bloom.hint"));
        }
        lastPad = pad;
        if (pad == null || !player.isCrouching()) {
            return;
        }
        BloomPath path = KNOWN.get(pad);
        if (path == null) {
            if (ASKED.add(pad)) {
                ClientPacketDistributor.sendToServer(new BloomRequestPayload(pad, false));
            }
            return;
        }
        if (player.tickCount % 4 == 0) {
            showArc(player, path);
        }
    }

    /** A dotted line of light along the arc, with the dots moving forward over time. */
    private static void showArc(LocalPlayer player, BloomPath path) {
        double length = path.length();
        int dots = (int) Math.max(8, length / 2.5);
        double shift = (player.tickCount % 16) / 16.0;
        for (int i = 0; i < dots; i++) {
            Vec3 at = path.at((i + shift) / dots).add(path.startUp().scale(0.3));
            player.level().addAlwaysVisibleParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0.0, 0.0, 0.0);
        }
    }

    /** Moves the player one tick along the arc; lands them at the end. */
    private static void fly(LocalPlayer player) {
        BloomPath path = flight;
        flightAge++;
        Vec3 previous = path.atTick(flightAge - 1);
        Vec3 now = path.atTick(flightAge);
        GravityFrame frame = player.getData(GravityFrames.FRAME);
        frame.setLaunched(true);
        player.resetFallDistance();
        if (player.level() != flightLevel) {
            // Moved to another world mid-flight (a Homeward Bloom reached the station): stop here.
            flight = null;
            return;
        }
        if (player.getAbilities().flying || player.isPassenger() || flightAge > path.ticks()) {
            // Landed (a hair above the leaf so the hitbox doesn't touch it), or flight interrupted.
            if (flightAge > path.ticks()) {
                Vec3 end = path.end().add(path.endUp().scale(0.02));
                player.setPos(end.x, end.y, end.z);
                player.setDeltaMovement(Vec3.ZERO);
            }
            flight = null;
            return;
        }
        player.setPos(now.x, now.y, now.z);
        player.setDeltaMovement(now.subtract(previous));
        if (flightAge % 2 == 0) {
            player.level().addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, previous.x, previous.y, previous.z, 0.0, 0.0, 0.0);
        }
    }
}
