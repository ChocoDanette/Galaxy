package io.github.chocodanette.galaxy.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import io.github.chocodanette.galaxy.worldgen.GalaxyLayout;
import io.github.chocodanette.galaxy.worldgen.Material;
import io.github.chocodanette.galaxy.worldgen.Planet;
import io.github.chocodanette.galaxy.worldgen.PlanetGravityLink;

/**
 * The galaxy you see below the station: a small model of the real planets around the arrival
 * planet, built from blocks far under the deck. Each model planet is a shrunk copy of the real
 * one (same shape, same grass and stone), placed where the real one is, at a smaller scale.
 * The arrival planet is right under the station, where you fall when you jump.
 */
public final class GalaxyView {
    /** Real distances between planets are divided by this. */
    static final double SPACING = 5.0;
    /** Real planet sizes are divided by this. */
    static final double SIZE = 3.0;
    /** How many cells around the arrival planet are shown, in each direction. */
    static final int CELLS = 3;
    /** Box the model fits in, around {@link StationLocation#VIEW_CENTER}: cleared before drawing. */
    static final int HALF_WIDTH = 70;
    static final int HALF_HEIGHT = 30;

    private GalaxyView() {
    }

    /** One model planet: where its center goes (relative to the view center) and its real planet. */
    record Model(Vec3 offset, Planet planet) {
    }

    /** The planets to show for a galaxy, with where each goes. */
    static List<Model> models(long seed, int galaxy) {
        Planet arrival = GalaxyLayout.arrivalPlanet(seed, galaxy);
        int cx = GalaxyLayout.cellOf(arrival.center().x);
        int cz = GalaxyLayout.cellOf(arrival.center().z);
        List<Model> models = new ArrayList<>();
        for (int x = cx - CELLS; x <= cx + CELLS; x++) {
            for (int z = cz - CELLS; z <= cz + CELLS; z++) {
                for (Planet planet : GalaxyLayout.planetsInCell(seed, x, z)) {
                    Vec3 offset = planet.center().subtract(arrival.center()).scale(1.0 / SPACING);
                    double r = planet.extent() / SIZE;
                    if (Math.abs(offset.x) + r < HALF_WIDTH && Math.abs(offset.z) + r < HALF_WIDTH
                            && Math.abs(offset.y) + r < HALF_HEIGHT) {
                        models.add(new Model(offset, planet));
                    }
                }
            }
        }
        return models;
    }

    /** Rebuilds the model when the galaxy below the station has changed. */
    static void refresh(MinecraftServer server) {
        StationData data = StationData.get(server);
        if (data.viewGalaxy() != data.galaxy()) {
            rebuild(server);
        }
    }

    /** Clears the old model and draws the galaxy the station is over now. */
    static void rebuild(MinecraftServer server) {
        ServerLevel station = StationLocation.level(server);
        ServerLevel galaxy = server.getLevel(PlanetGravityLink.GALAXY);
        if (station == null || galaxy == null) {
            return;
        }
        StationData data = StationData.get(server);
        BlockPos c = StationLocation.VIEW_CENTER;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
            for (int z = -HALF_WIDTH; z <= HALF_WIDTH; z++) {
                for (int y = -HALF_HEIGHT; y <= HALF_HEIGHT; y++) {
                    BlockPos pos = c.offset(x, y, z);
                    if (!station.getBlockState(pos).isAir()) {
                        station.setBlock(pos, air, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        int count = 0;
        for (Model model : models(galaxy.getSeed(), data.galaxy())) {
            draw(station, c, model);
            count++;
        }
        data.setViewGalaxy(data.galaxy());
        GalaxyShip.LOGGER.info("Drew {} planets of galaxy {} below the station", count, data.galaxy());
    }

    /** Draws one shrunk planet: solid wherever the real planet is solid at the matching spot. */
    private static void draw(ServerLevel level, BlockPos c, Model model) {
        Planet planet = model.planet();
        Vec3 center = Vec3.atCenterOf(c).add(model.offset());
        int r = (int) Math.ceil(planet.extent() / SIZE) + 1;
        BlockPos base = BlockPos.containing(center);
        BlockState top = solid(planet.theme().block(Material.TOP).defaultBlockState(), planet);
        BlockState inside = solid(planet.theme().block(Material.STONE).defaultBlockState(), planet);
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = base.offset(x, y, z);
                    if (!solidAt(planet, center, pos)) {
                        continue;
                    }
                    boolean outside = !solidAt(planet, center, pos.above()) || !solidAt(planet, center, pos.below())
                            || !solidAt(planet, center, pos.north()) || !solidAt(planet, center, pos.south())
                            || !solidAt(planet, center, pos.east()) || !solidAt(planet, center, pos.west());
                    level.setBlock(pos, outside ? top : inside, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Whether the real planet is solid at the spot matching this model block. */
    private static boolean solidAt(Planet planet, Vec3 modelCenter, BlockPos pos) {
        Vec3 real = planet.center().add(Vec3.atCenterOf(pos).subtract(modelCenter).scale(SIZE));
        return planet.materialAt((int) Math.floor(real.x), (int) Math.floor(real.y), (int) Math.floor(real.z)) != null;
    }

    /** Sand and gravel would fall in the station's void: swap them for something that stays put. */
    private static BlockState solid(BlockState state, Planet planet) {
        if (!(state.getBlock() instanceof FallingBlock)) {
            return state;
        }
        BlockState deep = planet.theme().block(Material.DEEP).defaultBlockState();
        return deep.getBlock() instanceof FallingBlock ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : deep;
    }
}
