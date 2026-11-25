package io.github.chocodanette.galaxy.ship;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import io.github.chocodanette.galaxy.ship.crew.Crew;
import io.github.chocodanette.galaxy.worldgen.PlanetBlocks;

/**
 * The Launch Blooms on the stations. They work like the ones in the galaxy (stand on the
 * glowing pad, crouch to see where it throws you, jump while crouching to go), but instead of
 * an arc to another leaf they throw you straight up into space and you come down on another
 * station:
 * <ul>
 * <li>the bloom on the common station takes you to your crew's own station;</li>
 * <li>the bloom on your own station takes you to the common station.</li>
 * </ul>
 * Everything happens on the server: it sees the jump, throws the player up, and moves them.
 */
public final class StationBlooms {
    /** How long you fly up before arriving, in ticks (20 ticks = 1 second). */
    private static final int FLIGHT_TICKS = 14;
    /** How hard the bloom throws you up. */
    private static final double THROW = 1.8;

    private record Launch(boolean toCommon, long startTick) {
    }

    private static final Map<UUID, Launch> LAUNCHES = new ConcurrentHashMap<>();

    private StationBlooms() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StationBlooms::onJump);
        NeoForge.EVENT_BUS.addListener(StationBlooms::onPlayerTick);
    }

    /** The bloom pad under the player's feet on a station, or null. */
    private static BlockPos padUnder(ServerPlayer player) {
        if (!StationLocation.isStation(player.level())) {
            return null;
        }
        BlockPos below = BlockPos.containing(player.position().subtract(0, 0.2, 0));
        return player.level().getBlockState(below).is(PlanetBlocks.BLOOM_PAD.get()) ? below : null;
    }

    /** The server sees every player jump (from their movement), so it can launch them. */
    private static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isCrouching()
                || LAUNCHES.containsKey(player.getUUID())) {
            return;
        }
        BlockPos pad = padUnder(player);
        if (pad == null) {
            return;
        }
        boolean toCommon = StationPlots.plotAt(pad) != Crew.NO_PLOT;
        if (!toCommon && StationPlots.stationOf(player).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("galaxy_ship.bloom.no_station"));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        player.setDeltaMovement(0.0, THROW, 0.0);
        player.needsSync = true;
        LAUNCHES.put(player.getUUID(), new Launch(toCommon, level.getGameTime()));
        Vec3 at = player.position();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Launch launch = LAUNCHES.get(player.getUUID());
        if (launch != null) {
            fly(player, launch);
            return;
        }
        // Crouching on a pad: a dotted line of light shows the throw, straight up.
        if (player.isCrouching() && player.tickCount % 4 == 0 && padUnder(player) != null) {
            ServerLevel level = (ServerLevel) player.level();
            double shift = (player.tickCount % 16) / 16.0;
            for (int i = 0; i < 8; i++) {
                double y = player.getY() + 0.5 + (i + shift) * 1.5;
                level.sendParticles(player, ParticleTypes.END_ROD, true, true,
                        player.getX(), y, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void fly(ServerPlayer player, Launch launch) {
        player.resetFallDistance();
        ServerLevel level = (ServerLevel) player.level();
        long age = level.getGameTime() - launch.startTick();
        if (!StationLocation.isStation(level) || age > FLIGHT_TICKS + 40) {
            LAUNCHES.remove(player.getUUID());
            return;
        }
        if (age % 2 == 0) {
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, player.getX(), player.getY(), player.getZ(),
                    2, 0.2, 0.2, 0.2, 0.0);
        }
        if (age >= FLIGHT_TICKS) {
            LAUNCHES.remove(player.getUUID());
            if (launch.toCommon()) {
                StationCrew.toCommonStation(player);
                player.sendOverlayMessage(Component.translatable("galaxy_ship.bloom.to_common"));
            } else {
                StationCrew.toStation(player);
                player.sendOverlayMessage(Component.translatable("galaxy_ship.bloom.to_own"));
            }
        }
    }
}
