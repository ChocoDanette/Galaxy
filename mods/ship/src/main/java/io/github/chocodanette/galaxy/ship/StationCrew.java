package io.github.chocodanette.galaxy.ship;

import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import io.github.chocodanette.galaxy.gravity.GravityFrames;
import io.github.chocodanette.galaxy.ship.crew.Crew;
import io.github.chocodanette.galaxy.worldgen.BloomRides;
import io.github.chocodanette.galaxy.worldgen.GalaxyCycle;
import io.github.chocodanette.galaxy.worldgen.GalaxyLayout;
import io.github.chocodanette.galaxy.worldgen.Planet;
import io.github.chocodanette.galaxy.worldgen.PlanetGravityLink;

/**
 * Moving the crew around: diving from a station into the galaxy, coming back on a Homeward
 * Bloom (or {@code /station} for operators), and making the station everyone's home (new
 * players start on the common station; players who die without a bed wake up on their crew's
 * station, or the common one). Coming home always means your crew's own station if it has one.
 */
public final class StationCrew {
    /** Tag given to a player the first time they're brought to the station. */
    private static final String ARRIVED_TAG = "galaxy_station_arrived";
    /** How high above the arrival planet's surface a dive drops you. */
    private static final double DROP_HEIGHT = 12.0;

    private StationCrew() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StationCrew::onLogin);
        NeoForge.EVENT_BUS.addListener(StationCrew::onRespawn);
        NeoForge.EVENT_BUS.addListener(StationCrew::onPlayerTick);
        // The top of a Homeward Bloom's throw lands you on the station.
        BloomRides.setHomeward(StationCrew::homeFromBloom);
    }

    private static void homeFromBloom(ServerPlayer player) {
        toStation(player);
        player.sendOverlayMessage(Component.translatable("galaxy_ship.homeward"));
    }

    /**
     * Brings a player home: onto their crew's own station if it has one, otherwise onto the
     * common station's deck. Everything that sends players back up uses this.
     */
    public static void toStation(ServerPlayer player) {
        Optional<Crew> own = StationPlots.stationOf(player);
        if (own.isPresent()) {
            teleport(player, StationPlots.spawn(own.get().plot), StationLocation.SPAWN_YAW);
        } else {
            toCommonStation(player);
        }
    }

    /** Teleports a player onto the common station's deck, where everyone starts. */
    public static void toCommonStation(ServerPlayer player) {
        teleport(player, StationLocation.SPAWN, StationLocation.SPAWN_YAW);
    }

    private static void teleport(ServerPlayer player, Vec3 spot, float yaw) {
        ServerLevel station = StationLocation.level(player.level().getServer());
        if (station == null) {
            return;
        }
        player.teleportTo(station, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        station.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /** Drops a player into the galaxy below the station, a little above its arrival planet. */
    static void dive(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel galaxyLevel = server.getLevel(PlanetGravityLink.GALAXY);
        if (galaxyLevel == null) {
            player.sendSystemMessage(Component.translatable("galaxy_ship.no_galaxy"), false);
            toStation(player);
            return;
        }
        int number = GalaxyCycle.number();
        Planet planet = GalaxyLayout.arrivalPlanet(galaxyLevel.getSeed());
        Vec3 ground = planet.landingSpot();
        Vec3 spot = ground.add(0, DROP_HEIGHT, 0);
        // Gravity first, so it's already there when the player arrives.
        PlanetGravityLink.addPlanetsAround(galaxyLevel, ground, true);
        player.teleportTo(galaxyLevel, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), player.getXRot(), true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        // Counts as a charged jump's flight until landing: no fall damage on the way down.
        player.getData(GravityFrames.FRAME).setLaunched(true);
        galaxyLevel.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.9F);
        player.sendSystemMessage(Component.translatable("galaxy_ship.entered", number, planet.theme().displayName()), false);
    }

    /** Falling off the station into the void takes you to the galaxy. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && StationLocation.isStation(player.level())
                && player.getY() < StationLocation.ORIGIN.getY() - StationLocation.DIVE_DEPTH
                && !player.isSpectator()) {
            dive(player);
        }
    }

    /**
     * Makes a player respawn on their station (their crew's own, or else the common one),
     * unless they set a bed somewhere.
     */
    public static void makeStationHome(ServerPlayer player) {
        Optional<Crew> own = StationPlots.stationOf(player);
        BlockPos spawn = BlockPos.containing(own.map(c -> StationPlots.spawn(c.plot)).orElse(StationLocation.SPAWN));
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(StationLocation.STATION, spawn, StationLocation.SPAWN_YAW, 0.0F), true), false);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.entityTags().contains(ARRIVED_TAG)) {
            player.addTag(ARRIVED_TAG);
            makeStationHome(player);
            toCommonStation(player);
            player.sendSystemMessage(Component.translatable("galaxy_ship.welcome"), false);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // No bed left (it was broken, or never set): the station takes them in.
        if (player.getRespawnConfig() == null
                || player.getRespawnConfig().respawnData().dimension().equals(OLD_SHIP)) {
            makeStationHome(player);
            toStation(player);
        } else if (StationLocation.isStation(player.level()) && !StationRules.belongsAt(player, player.blockPosition())) {
            // Their bed is on a station whose crew they've left (or were kicked from).
            makeStationHome(player);
            toStation(player);
        }
    }

    /** The old ship dimension, gone since the station replaced it. */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> OLD_SHIP =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(GalaxyShip.MODID, "ship"));
}
