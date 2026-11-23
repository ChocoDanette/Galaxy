package io.github.chocodanette.galaxy.ship;

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

import io.github.chocodanette.galaxy.worldgen.Galaxy;
import io.github.chocodanette.galaxy.worldgen.GalaxyLayout;
import io.github.chocodanette.galaxy.worldgen.Planet;
import io.github.chocodanette.galaxy.worldgen.PlanetGravityLink;

/**
 * Moving the crew around: landing on a planet, beaming back up, jumping the ship to the next
 * galaxy, and making the ship everyone's home (new players start on board, and players who
 * die without a bed wake up on board).
 */
public final class ShipCrew {
    /** Tag given to a player the first time they're brought aboard. */
    private static final String BOARDED_TAG = "galaxy_ship_boarded";

    private ShipCrew() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ShipCrew::onLogin);
        NeoForge.EVENT_BUS.addListener(ShipCrew::onRespawn);
    }

    /** Teleports a player onto the ship, next to the console. */
    public static void beamAboard(ServerPlayer player) {
        ServerLevel ship = ShipLocation.level(player.level().getServer());
        if (ship == null) {
            return;
        }
        Vec3 spot = ShipLocation.SPAWN;
        player.teleportTo(ship, spot.x, spot.y, spot.z, Set.of(), ShipLocation.SPAWN_YAW, 0.0F, true);
        ship.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /** Sends one player down to the arrival planet of the galaxy the ship is parked at. */
    static void land(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel galaxyLevel = server.getLevel(PlanetGravityLink.GALAXY);
        if (galaxyLevel == null) {
            player.sendSystemMessage(Component.translatable("galaxy_ship.land.no_galaxy"), false);
            return;
        }
        int number = ShipData.get(server).galaxy();
        Planet planet = GalaxyLayout.arrivalPlanet(galaxyLevel.getSeed(), number);
        Vec3 spot = planet.landingSpot();
        // Gravity first, so it's already there when the player arrives.
        PlanetGravityLink.addPlanetsAround(galaxyLevel, spot, true);
        BlockPos padPos = BlockPos.containing(spot);
        if (!galaxyLevel.getBlockState(padPos).is(ShipBlocks.RETURN_PAD.get())) {
            galaxyLevel.setBlockAndUpdate(padPos, ShipBlocks.RETURN_PAD.get().defaultBlockState());
        }
        player.teleportTo(galaxyLevel, spot.x, spot.y, spot.z, Set.of(), player.getYRot(), 0.0F, true);
        galaxyLevel.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.9F);
        player.sendSystemMessage(Component.translatable("galaxy_ship.land.arrived",
                number, planet.theme().displayName()), false);
    }

    /** Flies the whole ship to the next galaxy, if there's enough fuel. */
    static void jump(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ShipData data = ShipData.get(server);
        if (data.galaxy() >= Galaxy.MAX_NUMBER) {
            player.sendSystemMessage(Component.translatable("galaxy_ship.jump.last"), true);
            return;
        }
        if (data.fuel() < ShipData.JUMP_COST) {
            player.sendSystemMessage(Component.translatable("galaxy_ship.jump.no_fuel",
                    ShipData.JUMP_COST, data.fuel()), true);
            return;
        }
        data.setFuel(data.fuel() - ShipData.JUMP_COST);
        data.setGalaxy(data.galaxy() + 1);
        ServerLevel ship = ShipLocation.level(server);
        if (ship != null) {
            for (ServerPlayer crew : ship.players()) {
                crew.sendSystemMessage(Component.translatable("galaxy_ship.jump.done",
                        player.getDisplayName(), data.galaxy()), false);
            }
            BlockPos o = ShipLocation.ORIGIN;
            // Volume 2 carries about 32 blocks: the whole ship hears it once.
            ship.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 0.7F);
        }
        ShipNetwork.sendStateToShip(server, false);
    }

    /** Makes a player respawn on the ship, unless they set a bed somewhere. */
    private static void makeShipHome(ServerPlayer player) {
        BlockPos spawn = BlockPos.containing(ShipLocation.SPAWN);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(ShipLocation.SHIP, spawn, ShipLocation.SPAWN_YAW, 0.0F), true), false);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.entityTags().contains(BOARDED_TAG)) {
            player.addTag(BOARDED_TAG);
            makeShipHome(player);
            beamAboard(player);
            player.sendSystemMessage(Component.translatable("galaxy_ship.welcome"), false);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // No bed left (it was broken, or never set): the ship takes them in.
        if (player.getRespawnConfig() == null) {
            makeShipHome(player);
            beamAboard(player);
        }
    }
}
