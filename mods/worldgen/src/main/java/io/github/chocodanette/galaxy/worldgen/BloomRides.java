package io.github.chocodanette.galaxy.worldgen;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import io.github.chocodanette.galaxy.gravity.GravityFrames;
import io.github.chocodanette.galaxy.worldgen.client.BloomClient;

/**
 * The server side of Launch Bloom flights. A player's game asks where a pad throws them; the
 * server finds the partner leaf from the world seed and answers with the arc. On a launch it
 * also remembers the flight for a while, so the fall at the end doesn't hurt and the
 * planets at the other end have their gravity ready.
 *
 * The player's own game moves them along the arc (players move themselves in Minecraft),
 * so both sides follow the same fixed path.
 */
public final class BloomRides {
    /** How close to a pad a player must be for a request to count. */
    private static final double PAD_REACH = 4.0;

    /** How high a Homeward Bloom throws you before you're carried off to the station. */
    static final double HOME_RISE = 40.0;

    private record Ride(BloomPath path, long startTick, boolean home) {
    }

    private static final Map<UUID, Ride> RIDES = new ConcurrentHashMap<>();

    /**
     * What happens at the top of a Homeward Bloom's throw. The Galaxy Station mod sets this to
     * "bring them to the station"; without it, the throw just ends in space.
     */
    private static volatile Consumer<ServerPlayer> homeward;

    /** Called by the Galaxy Station mod: where a Homeward Bloom sends you. */
    public static void setHomeward(Consumer<ServerPlayer> action) {
        homeward = action;
    }

    private BloomRides() {
    }

    static void register(IEventBus modEventBus) {
        modEventBus.addListener(BloomRides::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(BloomRides::onPlayerTick);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(GalaxyWorldgen.MODID).versioned("1");
        registrar.playToServer(BloomRequestPayload.TYPE, BloomRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        onRequest(player, payload);
                    }
                });
        // The lambda body only runs on a player's game, so the client class is never loaded on a server.
        registrar.playToClient(BloomPathPayload.TYPE, BloomPathPayload.STREAM_CODEC,
                (payload, context) -> BloomClient.onPath(payload));
    }

    /** The arc from a pad to its partner, or empty when the pad isn't a working linked leaf. */
    static Optional<BloomPath> pathFrom(long worldSeed, BlockPos pad) {
        return Blooms.leafAt(worldSeed, pad).flatMap(leaf -> leaf.home() ? Optional.of(homePath(leaf))
                : Blooms.target(worldSeed, leaf).map(target -> BloomPath.between(
                        leaf.standSpot(), leaf.up().getUnitVec3(), target.standSpot(), target.up().getUnitVec3())));
    }

    /** A Homeward Bloom's throw: straight up off the leaf, high above the planet. */
    static BloomPath homePath(Blooms.Leaf leaf) {
        Vec3 up = leaf.up().getUnitVec3();
        Vec3 start = leaf.standSpot();
        return BloomPath.between(start, up, start.add(up.scale(HOME_RISE)), up.reverse());
    }

    private static void onRequest(ServerPlayer player, BloomRequestPayload request) {
        if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(PlanetGravityLink.GALAXY)) {
            return;
        }
        BlockPos pad = request.pad();
        if (!PlanetBlocks.isLaunchPad(level.getBlockState(pad))
                || player.position().distanceTo(Vec3.atCenterOf(pad)) > PAD_REACH) {
            return;
        }
        Optional<BloomPath> path = pathFrom(level.getSeed(), pad);
        if (path.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("galaxy_worldgen.bloom.no_partner"));
            return;
        }
        boolean home = level.getBlockState(pad).is(PlanetBlocks.HOMEWARD_PAD.get());
        if (request.launch()) {
            if (home && homeward == null) {
                player.sendOverlayMessage(Component.translatable("galaxy_worldgen.bloom.no_station"));
                return;
            }
            if (!home) {
                // Gravity at the far end, so it's there when the player lands.
                PlanetGravityLink.addPlanetsAround(level, path.get().end(), true);
            }
            RIDES.put(player.getUUID(), new Ride(path.get(), level.getGameTime(), home));
            player.getData(GravityFrames.FRAME).setLaunched(true);
            Vec3 at = path.get().start();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, SoundSource.PLAYERS, 1.0F, 0.6F);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.0F, 0.8F);
            if (home) {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
        PacketDistributor.sendToPlayer(player, new BloomPathPayload(pad, path.get(), request.launch()));
    }

    /** During a flight nothing hurts on the way down; it ends a moment after the arc does. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Ride ride = RIDES.get(player.getUUID());
        if (ride == null) {
            return;
        }
        player.resetFallDistance();
        long age = player.level().getGameTime() - ride.startTick();
        if (!player.level().dimension().equals(PlanetGravityLink.GALAXY)) {
            RIDES.remove(player.getUUID());
        } else if (ride.home() && age >= ride.path().ticks()) {
            // The top of a Homeward Bloom's throw: off to the station.
            RIDES.remove(player.getUUID());
            Consumer<ServerPlayer> action = homeward;
            if (action != null) {
                action.accept(player);
            }
        } else if (age > ride.path().ticks() + 40) {
            RIDES.remove(player.getUUID());
        }
    }

    /** True while a player is flying along a bloom's arc (server side). */
    public static boolean isRiding(ServerPlayer player) {
        return RIDES.containsKey(player.getUUID());
    }
}
