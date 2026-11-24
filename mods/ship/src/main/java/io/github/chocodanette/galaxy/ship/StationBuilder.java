package io.github.chocodanette.galaxy.ship;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Builds the station once per world, the first time the server starts with this mod (and again
 * when {@link StationData#LAYOUT_VERSION} goes up).
 *
 * The first station is primitive: a small round deck of oak planks on a lump of cobblestone,
 * with a fence around it, a little hut for storage and crafting, a few lanterns, and a plank
 * walkway sticking out over the edge with no fence, to jump off into the galaxy below.
 * It's drawn from simple rules rather than a saved structure, so it's easy to tweak.
 */
public final class StationBuilder {
    /** Radius of the deck, in blocks. */
    private static final double DECK = 6.5;
    /** Past this radius the deck is the cobblestone rim with the fence on it. */
    private static final double RIM = 5.5;
    /** Length of the walkway past the deck (to the south). */
    private static final int WALKWAY = 4;

    private static final BlockState PLANKS = Blocks.OAK_PLANKS.defaultBlockState();
    private static final BlockState ROCK = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState MOSSY = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    private static final BlockState FENCE = Blocks.OAK_FENCE.defaultBlockState();
    private static final BlockState POST = Blocks.SPRUCE_LOG.defaultBlockState();
    private static final BlockState WALL = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState PANE = Blocks.GLASS_PANE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private StationBuilder() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(StationBuilder::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        StationData data = StationData.get(server);
        ServerLevel station = StationLocation.level(server);
        if (station == null) {
            GalaxyShip.LOGGER.error("The station dimension isn't loaded; the station can't be built");
            return;
        }
        if (!data.upToDate()) {
            build(station);
            data.setBuilt();
            GalaxyShip.LOGGER.info("Built the crew's station");
        }
        GalaxyView.refresh(server);
    }

    /** Places (or puts back) the whole station. */
    public static void build(ServerLevel level) {
        BlockPos o = StationLocation.ORIGIN;
        List<BlockPos> connecting = new ArrayList<>();
        int r = (int) Math.ceil(DECK) + 1;
        // Clear the space above the deck, then lay the deck and the rock under it.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r + WALKWAY; z++) {
                for (int y = 0; y <= 5; y++) {
                    set(level, o.offset(x, y, z), AIR);
                }
                double d = Math.sqrt(x * x + z * z);
                if (d > DECK) {
                    continue;
                }
                set(level, o.offset(x, -1, z), d > RIM ? ROCK : PLANKS);
                // The rock underneath narrows to a point, like a floating island.
                int depth = (int) Math.round((DECK - d) * 0.8) + 1;
                for (int y = 2; y <= depth; y++) {
                    set(level, o.offset(x, -y, z), (x * 7 + z * 13 + y * 5) % 4 == 0 ? MOSSY : ROCK);
                }
                if (d > RIM && !(x == 0 && z > 0)) {
                    set(level, o.offset(x, 0, z), FENCE);
                    connecting.add(o.offset(x, 0, z));
                }
            }
        }
        // The walkway: planks out over the edge with nothing to stop you.
        for (int z = (int) RIM; z <= (int) DECK + WALKWAY; z++) {
            set(level, o.offset(0, -1, z), PLANKS);
        }
        // Lanterns on four fence posts.
        for (BlockPos post : List.of(o.offset(6, 0, 0), o.offset(-6, 0, 0), o.offset(4, 0, 4), o.offset(-4, 0, 4))) {
            set(level, post, FENCE);
            set(level, post.above(), Blocks.LANTERN.defaultBlockState());
        }
        buildHut(level, o, connecting);
        // Fences and glass panes join up with their neighbours once everything is placed.
        for (BlockPos pos : connecting) {
            set(level, pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos));
        }
    }

    /** A small spruce hut at the back of the deck, door facing the walkway. */
    private static void buildHut(ServerLevel level, BlockPos o, List<BlockPos> connecting) {
        int minX = -2, maxX = 2, minZ = -5, maxZ = -2;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edgeX = x == minX || x == maxX;
                boolean edgeZ = z == minZ || z == maxZ;
                for (int y = 0; y <= 2; y++) {
                    BlockPos pos = o.offset(x, y, z);
                    if (edgeX && edgeZ) {
                        set(level, pos, POST.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                    } else if (edgeX || edgeZ) {
                        boolean door = z == maxZ && x == 0 && y <= 1;
                        boolean window = y == 1 && (edgeX ? z == -4 || z == -3 : x == -1 || x == 1) && !(z == maxZ && x == 0);
                        if (door) {
                            set(level, pos, AIR);
                        } else if (window) {
                            set(level, pos, PANE);
                            connecting.add(pos);
                        } else {
                            set(level, pos, WALL);
                        }
                    }
                }
                set(level, o.offset(x, 3, z), PLANKS);
            }
        }
        set(level, o.offset(-1, 0, -4), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        set(level, o.offset(1, 0, -4), Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, o.offset(1, 0, -3), Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST));
        set(level, o.offset(-1, 0, -3), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        set(level, o.offset(0, 2, -4), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
