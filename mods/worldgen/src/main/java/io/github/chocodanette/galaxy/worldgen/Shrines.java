package io.github.chocodanette.galaxy.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.loot.LootTable;

import io.github.chocodanette.galaxy.gravity.GravityField;

/**
 * What used to be in the Nether and the End, now found on planets.
 * <ul>
 * <li>An <b>Ember shrine</b> stands on top of many Ember planets: a nether brick platform with a
 * blaze spawner (blaze rods), nether wart growing in soul sand, and a chest of Nether loot
 * (quartz, glowstone, ghast tears, and with luck a wither skeleton skull or a netherite
 * upgrade template). Ember planets also hold quartz, nether gold and glowstone in their rock,
 * and ancient debris in their core.</li>
 * <li>A <b>Void shrine</b> stands on top of every Void planet: a purpur platform guarded by
 * shulkers, with a chest of End loot (ender pearls, chorus, dragon's breath, and sometimes an
 * elytra). Chorus plants grow on the top of Void planets.</li>
 * </ul>
 * Everything is built on the planet's top face, where planet "down" is the normal down, so
 * spawners, nether wart and chorus behave as in vanilla.
 */
public final class Shrines {
    private Shrines() {
    }

    public enum Kind {
        EMBER, VOID
    }

    /** Smallest planet (half size or radius) with room for a shrine on top. */
    static final double MIN_RADIUS = 8.0;
    /** Share of big enough Ember planets that get a shrine. */
    static final double EMBER_SHRINE_CHANCE = 0.6;
    /** Half width of a shrine's platform: 5 by 5 blocks. */
    static final int HALF = 2;
    /** Air kept above the platform. */
    private static final int HEADROOM = 4;
    /** Share of open spots on top of a Void planet where a chorus plant grows. */
    static final double CHORUS_CHANCE = 0.012;

    public static final ResourceKey<LootTable> EMBER_LOOT = loot("chests/ember_shrine");
    public static final ResourceKey<LootTable> VOID_LOOT = loot("chests/void_shrine");

    private static ResourceKey<LootTable> loot(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(GalaxyWorldgen.MODID, path));
    }

    /** The kind of shrine on this planet, or null for none. Depends only on the planet. */
    public static Kind of(Planet planet) {
        if (planet.shape() == GravityField.Shape.HOLLOW || planet.radius() < MIN_RADIUS || isArrival(planet)) {
            return null;
        }
        if (planet.theme() == PlanetTheme.VOID) {
            return Kind.VOID;
        }
        if (planet.theme() == PlanetTheme.EMBER
                && Hashing.unit(planet.seed() ^ 0x5A1E5A1E5L, 0, 0, 0) < EMBER_SHRINE_CHANCE) {
            return Kind.EMBER;
        }
        return null;
    }

    /** The middle planet of a galaxy, where everyone lands, stays peaceful. */
    private static boolean isArrival(Planet planet) {
        int number = Galaxy.numberAt(planet.center().x);
        return number != 0
                && GalaxyLayout.cellOf(planet.center().x) == GalaxyLayout.cellOf((double) number * Galaxy.SPACING)
                && GalaxyLayout.cellOf(planet.center().z) == 0;
    }

    /** The middle of the shrine's platform: the ground block on top of the planet. */
    static BlockPos platformCenter(Planet planet) {
        int x = (int) Math.floor(planet.center().x);
        int z = (int) Math.floor(planet.center().z);
        return new BlockPos(x, planet.topY(x, z), z);
    }

    /**
     * Builds the shrines and chorus groves that belong to this chunk. A shrine is built by
     * the chunk holding its middle (it may reach a couple of blocks into the next chunk,
     * which world generation allows).
     */
    static void decorate(WorldGenLevel level, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        int maxX = pos.getMaxBlockX();
        int maxZ = pos.getMaxBlockZ();
        long seed = level.getSeed();
        for (Planet planet : GalaxyLayout.planetsTouching(seed, minX, minZ, maxX, maxZ)) {
            if (planet.theme() == PlanetTheme.VOID) {
                growChorus(level, planet, minX, minZ, maxX, maxZ);
            }
            Kind kind = of(planet);
            if (kind == null) {
                continue;
            }
            BlockPos center = platformCenter(planet);
            if (center.getX() < minX || center.getX() > maxX || center.getZ() < minZ || center.getZ() > maxZ) {
                continue;
            }
            RandomSource random = RandomSource.create(planet.seed() ^ 0x5B1E5L);
            if (kind == Kind.EMBER) {
                buildEmber(level, center, random);
            } else {
                buildVoid(level, center, random);
            }
        }
    }

    private static void platform(WorldGenLevel level, BlockPos center, Block floor) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                set(level, center.offset(dx, 0, dz), floor.defaultBlockState());
                // Fill dips under the platform, so it never floats over a hollow in the hills.
                for (int dy = 1; dy <= 3; dy++) {
                    BlockPos below = center.offset(dx, -dy, dz);
                    if (level.getBlockState(below).canBeReplaced()) {
                        set(level, below, floor.defaultBlockState());
                    }
                }
                for (int dy = 1; dy <= HEADROOM; dy++) {
                    set(level, center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void buildEmber(WorldGenLevel level, BlockPos center, RandomSource random) {
        platform(level, center, Blocks.NETHER_BRICKS);
        for (int sx : new int[] {-HALF, HALF}) {
            for (int sz : new int[] {-HALF, HALF}) {
                set(level, center.offset(sx, 1, sz), Blocks.NETHER_BRICK_FENCE.defaultBlockState());
                set(level, center.offset(sx, 2, sz), Blocks.NETHER_BRICK_FENCE.defaultBlockState());
                set(level, center.offset(sx, 3, sz), Blocks.NETHER_BRICK_SLAB.defaultBlockState());
            }
        }
        // A small nether wart patch, ripe, so it can be picked and replanted.
        for (int dx : new int[] {-1, 1}) {
            BlockPos soil = center.offset(dx, 0, 1);
            set(level, soil, Blocks.SOUL_SAND.defaultBlockState());
            set(level, soil.above(), Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3));
        }
        BlockPos spawner = center.above();
        set(level, spawner, Blocks.SPAWNER.defaultBlockState());
        if (level.getBlockEntity(spawner) instanceof SpawnerBlockEntity spawnerEntity) {
            spawnerEntity.setEntityId(EntityTypes.BLAZE, random);
        }
        chest(level, center.offset(0, 1, -1), Direction.SOUTH, EMBER_LOOT, random);
    }

    private static void buildVoid(WorldGenLevel level, BlockPos center, RandomSource random) {
        platform(level, center, Blocks.PURPUR_BLOCK);
        for (int sx : new int[] {-HALF, HALF}) {
            for (int sz : new int[] {-HALF, HALF}) {
                set(level, center.offset(sx, 1, sz), Blocks.PURPUR_PILLAR.defaultBlockState());
                set(level, center.offset(sx, 2, sz), Blocks.PURPUR_PILLAR.defaultBlockState());
                set(level, center.offset(sx, 3, sz),
                        Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
            }
        }
        chest(level, center.above(), Direction.SOUTH, VOID_LOOT, random);
        // Shulkers guard the chest from two sides.
        for (int dx : new int[] {-HALF, HALF}) {
            Shulker shulker = EntityTypes.SHULKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (shulker != null) {
                BlockPos at = center.offset(dx, 1, 0);
                shulker.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
                shulker.setPersistenceRequired();
                level.addFreshEntity(shulker);
            }
        }
    }

    private static void chest(WorldGenLevel level, BlockPos pos, Direction facing, ResourceKey<LootTable> loot,
            RandomSource random) {
        set(level, pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
        RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
    }

    /** Chorus plants on the top face of a Void planet, grown the way the End grows them. */
    private static void growChorus(WorldGenLevel level, Planet planet, int minX, int minZ, int maxX, int maxZ) {
        BlockPos shrine = of(planet) == Kind.VOID ? platformCenter(planet) : null;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (Hashing.unit(planet.seed() ^ 0xC40205L, x, 0, z) >= CHORUS_CHANCE) {
                    continue;
                }
                if (shrine != null && Math.abs(x - shrine.getX()) <= HALF + 3 && Math.abs(z - shrine.getZ()) <= HALF + 3) {
                    continue;
                }
                int top = planet.topY(x, z);
                if (top == Integer.MIN_VALUE || planet.materialAt(x, top, z) != Material.TOP
                        || planet.upAt(x, top + 1, z) != Direction.UP) {
                    continue;
                }
                BlockPos spot = new BlockPos(x, top + 1, z);
                if (!level.getBlockState(spot).isAir() || !level.getBlockState(spot.below()).is(Blocks.END_STONE)) {
                    continue;
                }
                RandomSource random = RandomSource.create(planet.seed() ^ ((long) x * 31L + z));
                ChorusFlowerBlock.generatePlant(level, spot, random, 8);
            }
        }
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
