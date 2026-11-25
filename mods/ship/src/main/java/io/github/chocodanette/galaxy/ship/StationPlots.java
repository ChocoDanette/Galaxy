package io.github.chocodanette.galaxy.ship;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.ship.crew.Crew;
import io.github.chocodanette.galaxy.ship.crew.CrewData;
import io.github.chocodanette.galaxy.ship.crew.CrewRole;
import io.github.chocodanette.galaxy.worldgen.PlanetBlocks;

/**
 * Where crews' own stations are, and how a new one is set up.
 *
 * Every player station lives in the same station dimension as the common station, on its own
 * plot. Plot 1 is 2000 blocks east of the common station, plot 2 is 4000 blocks east, and so
 * on, so stations never see or touch each other. The station dimension isn't part of any
 * galaxy, so when a galaxy ends and a new one takes its place, stations stay exactly as they are.
 */
public final class StationPlots {
    /** Blocks between two plots' middles. */
    public static final int SPACING = 2000;
    /** How far below the floor a crew may build (well above where a fall sends you to the galaxy). */
    public static final int BUILD_BELOW = 12;
    /** How far above the floor a crew may build. */
    public static final int BUILD_ABOVE = 24;
    /** Radius of the rock island a new station starts on (a little over 9 by 9 blocks). */
    private static final double ISLAND = 4.5;

    private StationPlots() {
    }

    /** The middle of a plot's floor (you stand at this height), like the common station's origin. */
    public static BlockPos center(int plot) {
        return new BlockPos(plot * SPACING, StationLocation.ORIGIN.getY(), 0);
    }

    /** Where crew members appear on their station: just south of the middle, facing south. */
    public static Vec3 spawn(int plot) {
        BlockPos c = center(plot);
        return new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 1.5);
    }

    /** Which plot this spot is on, or {@link Crew#NO_PLOT} for the common station and the empty space. */
    public static int plotAt(BlockPos pos) {
        int plot = Math.round(pos.getX() / (float) SPACING);
        if (plot < 1 || Math.abs(pos.getX() - plot * SPACING) > SPACING / 2 || Math.abs(pos.getZ()) > SPACING / 2) {
            return Crew.NO_PLOT;
        }
        return plot;
    }

    /** True when this spot is inside the crew's building area. */
    public static boolean inBuildArea(Crew crew, BlockPos pos) {
        if (!crew.hasStation()) {
            return false;
        }
        BlockPos c = center(crew.plot);
        int dy = pos.getY() - c.getY();
        return Math.abs(pos.getX() - c.getX()) <= crew.buildRadius && Math.abs(pos.getZ() - c.getZ()) <= crew.buildRadius
                && dy >= -BUILD_BELOW && dy <= BUILD_ABOVE;
    }

    /** The crew that owns the station at this spot, if any. */
    public static Optional<Crew> ownerAt(MinecraftServer server, BlockPos pos) {
        return CrewData.get(server).crewOnPlot(plotAt(pos));
    }

    /** The player's crew, if it has a station. */
    public static Optional<Crew> stationOf(ServerPlayer player) {
        return CrewData.get(player.level().getServer()).crewOf(player.getUUID()).filter(Crew::hasStation);
    }

    /**
     * Uses a space rock: founds the player's crew's station (starting a crew of one if they
     * have none) and takes them there. Returns false, with a message, when it can't.
     */
    public static boolean found(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel station = StationLocation.level(server);
        if (station == null) {
            return false;
        }
        CrewData data = CrewData.get(server);
        UUID id = player.getUUID();
        Optional<Crew> current = data.crewOf(id);
        if (current.isPresent() && current.get().hasStation()) {
            player.sendSystemMessage(Component.translatable("galaxy_ship.rock.already_have"), false);
            return false;
        }
        if (current.isPresent() && current.get().role(id).orElse(CrewRole.GUEST) != CrewRole.CAPTAIN) {
            player.sendSystemMessage(Component.translatable("galaxy_ship.rock.captain_only"), false);
            return false;
        }
        if (data.stationCount() >= StationConfig.MAX_STATIONS.getAsInt()) {
            player.sendSystemMessage(Component.translatable("galaxy_ship.rock.server_full"), false);
            return false;
        }
        String name = player.getGameProfile().name();
        Crew crew = current.orElseGet(() -> data.create(id, name, Component.translatable(
                "galaxy_ship.crew.default_name", name).getString()));
        int plot = data.claimPlot(crew, StationConfig.START_RADIUS.getAsInt());
        build(station, plot);
        GalaxyShip.LOGGER.info("{} founded station {} for crew '{}'", name, plot, crew.name);
        StationCrew.toStation(player);
        StationCrew.makeStationHome(player);
        player.sendSystemMessage(Component.translatable("galaxy_ship.rock.founded", crew.name), false);
        return true;
    }

    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState ROCK = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /**
     * A new station: the space rock you bought, as a small island with a plank floor, a chest,
     * a lantern, and a little Launch Bloom that throws you back to the common station.
     */
    public static void build(ServerLevel level, int plot) {
        BlockPos o = center(plot);
        int r = (int) Math.ceil(ISLAND) + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > ISLAND) {
                    continue;
                }
                set(level, o.offset(x, -1, z), d > ISLAND - 1 ? ROCK : PLANKS);
                int depth = (int) Math.round((ISLAND - d) * 0.9) + 1;
                for (int y = 2; y <= depth; y++) {
                    set(level, o.offset(x, -y, z), (x * 7 + z * 13 + y * 5) % 4 == 0 ? MOSSY : ROCK);
                }
            }
        }
        // The way back: a small bloom at the north edge, its glowing pad set in the floor.
        placeBloom(level, o.offset(0, -1, -3), Direction.NORTH);
        set(level, o.offset(2, 0, -1), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        set(level, o.offset(-2, 0, 1), Blocks.LANTERN.defaultBlockState());
    }

    /**
     * A small Launch Bloom: the pad sits in the floor at {@code pad}, with leaves on both sides,
     * and the stem rises behind it ({@code back}) to a flower head with a glowing core.
     */
    static void placeBloom(ServerLevel level, BlockPos pad, Direction back) {
        Direction side = back.getClockWise();
        set(level, pad, PlanetBlocks.BLOOM_PAD.get().defaultBlockState());
        set(level, pad.relative(side), PlanetBlocks.BLOOM_LEAF.get().defaultBlockState());
        set(level, pad.relative(side.getOpposite()), PlanetBlocks.BLOOM_LEAF.get().defaultBlockState());
        BlockPos stem = pad.relative(back).above();
        for (int y = 0; y < 3; y++) {
            set(level, stem.above(y), PlanetBlocks.BLOOM_STEM.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        }
        BlockPos head = stem.above(3);
        set(level, head, PlanetBlocks.BLOOM_CORE.get().defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            set(level, head.relative(d), PlanetBlocks.BLOOM_PETAL.get().defaultBlockState());
        }
    }

    /** Empties a crew's whole building area (used when an operator removes a station). */
    public static void clear(ServerLevel level, Crew crew) {
        BlockPos c = center(crew.plot);
        int r = Math.max(crew.buildRadius, (int) ISLAND + 2);
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -BUILD_BELOW - 4; y <= BUILD_ABOVE; y++) {
                    BlockPos pos = c.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        // Containers drop nothing: the station is gone, and so is what was in it.
                        level.removeBlockEntity(pos);
                        level.setBlock(pos, AIR, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
