package io.github.chocodanette.galaxy.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Builds the ship's blocks once per world, the first time the server starts with this mod.
 *
 * The ship is drawn from simple rules rather than a saved structure, so it's easy to tweak:
 * a hull 25 blocks long, pointing north, narrowing to a glass cockpit at the nose, with
 * windows along the sides, small wings and glowing engines at the back. Inside: the console
 * at the front, lights in the ceiling, and a few storage and crafting blocks at the back.
 */
public final class ShipBuilder {
    private static final int NOSE = -12;
    private static final int TAIL = 12;

    private static final BlockState HULL = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
    private static final BlockState TRIM = Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState();
    private static final BlockState FLOOR = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState WINDOW = Blocks.GLASS.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState ENGINE = Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private ShipBuilder() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(ShipBuilder::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ShipData data = ShipData.get(server);
        ServerLevel ship = ShipLocation.level(server);
        if (ship == null) {
            GalaxyShip.LOGGER.error("The ship dimension isn't loaded; the ship can't be built");
            return;
        }
        if (!data.built()) {
            build(ship);
            data.setBuilt();
            GalaxyShip.LOGGER.info("Built the crew's ship");
        }
    }

    /** Half the hull's inside-plus-wall width at a row (z), narrowing toward the nose. */
    private static int halfWidth(int z) {
        return switch (z - NOSE) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            default -> 5;
        };
    }

    /** Height of the hull's roof above the floor at a row (z): lower over the cockpit. */
    private static int roof(int z) {
        return z - NOSE < 3 ? 4 : 5;
    }

    private static boolean inHull(int x, int y, int z) {
        return z >= NOSE && z <= TAIL && Math.abs(x) <= halfWidth(z) && y >= -1 && y <= roof(z);
    }

    /** Places (or puts back) the whole ship. Anything players built nearby inside its box is replaced. */
    public static void build(ServerLevel level) {
        BlockPos o = ShipLocation.ORIGIN;
        // Hull: solid where a block of the hull's volume touches the outside, hollow elsewhere.
        for (int z = NOSE; z <= TAIL; z++) {
            for (int x = -5; x <= 5; x++) {
                for (int y = -1; y <= 5; y++) {
                    if (!inHull(x, y, z)) {
                        continue;
                    }
                    boolean shell = !inHull(x + 1, y, z) || !inHull(x - 1, y, z) || !inHull(x, y + 1, z)
                            || !inHull(x, y - 1, z) || !inHull(x, y, z + 1) || !inHull(x, y, z - 1);
                    set(level, o.offset(x, y, z), shell ? shellBlock(x, y, z) : AIR);
                }
            }
        }
        // A darker floor inside, over the belly.
        for (int z = NOSE + 1; z < TAIL; z++) {
            for (int x = -halfWidth(z) + 1; x <= halfWidth(z) - 1; x++) {
                set(level, o.offset(x, -1, z), FLOOR);
            }
        }
        buildWings(level, o);
        buildEngines(level, o);
        furnish(level, o);
    }

    private static BlockState shellBlock(int x, int y, int z) {
        boolean cockpit = z - NOSE < 4;
        boolean side = Math.abs(x) == halfWidth(z) && y >= 1 && y <= 2;
        if (y == -1) {
            return Math.abs(x) == halfWidth(z) ? TRIM : HULL;
        }
        if (cockpit && y >= 0 && y < roof(z)) {
            // Wide glass at the nose: the best view of space.
            return WINDOW;
        }
        if (cockpit && y == roof(z) && Math.abs(x) < halfWidth(z)) {
            return WINDOW;
        }
        if (side && Math.floorMod(z, 3) != 0 && z < TAIL - 1) {
            return WINDOW;
        }
        if (y == roof(z) && Math.abs(x) == 3 && Math.floorMod(z, 4) == 0 && !cockpit) {
            return LIGHT;
        }
        if (y == roof(z) && x == 0 && !cockpit) {
            return TRIM;
        }
        return Math.abs(x) == halfWidth(z) && y == 0 ? TRIM : HULL;
    }

    private static void buildWings(ServerLevel level, BlockPos o) {
        for (int z = 0; z <= 9; z++) {
            int reach = Math.min(4, 1 + z / 2);
            for (int i = 1; i <= reach; i++) {
                BlockState wing = i == reach ? TRIM : HULL;
                set(level, o.offset(5 + i, 0, z), wing);
                set(level, o.offset(-5 - i, 0, z), wing);
            }
        }
    }

    private static void buildEngines(ServerLevel level, BlockPos o) {
        for (int x : new int[] {-3, -2, 2, 3}) {
            for (int y = 1; y <= 2; y++) {
                set(level, o.offset(x, y, TAIL), LIGHT);
                set(level, o.offset(x, y, TAIL + 1), ENGINE);
            }
        }
    }

    private static void furnish(ServerLevel level, BlockPos o) {
        set(level, ShipLocation.CONSOLE, ShipBlocks.CONSOLE.get().defaultBlockState());
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST);
        set(level, o.offset(-4, 0, 9), chest);
        set(level, o.offset(-4, 0, 10), chest);
        set(level, o.offset(4, 0, 9), Blocks.CRAFTING_TABLE.defaultBlockState());
        set(level, o.offset(4, 0, 10), Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.WEST));
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
