package io.github.chocodanette.galaxy.worldgen;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Builds the galaxy dimension: empty space, plus the blocks of every planet that
 * {@link GalaxyLayout} places. Each chunk only asks which planets reach into it, so chunks
 * can be built in any order and always join up.
 */
public final class PlanetChunkGenerator extends ChunkGenerator {
    public static final MapCodec<PlanetChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("biome").forGetter(generator -> generator.biome)
    ).apply(i, i.stable(PlanetChunkGenerator::new)));

    private final Holder<Biome> biome;

    public PlanetChunkGenerator(Holder<Biome> biome) {
        super(new FixedBiomeSource(biome));
        this.biome = biome;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
            StructureManager structureManager, BiomeManager biomeManager,
            @Nullable WorldGenRegion carverBiomeRegion, Set<Holder<Biome>> possibleBiomes) {
        ChunkPos pos = chunk.getPos();
        int minX = pos.getMinBlockX();
        int minZ = pos.getMinBlockZ();
        int maxX = pos.getMaxBlockX();
        int maxZ = pos.getMaxBlockZ();
        int bottom = Math.max(chunk.getMinY(), GalaxyLayout.MIN_Y);
        int top = Math.min(chunk.getMaxY(), GalaxyLayout.MAX_Y);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Planet planet : GalaxyLayout.planetsTouching(randomState.seed(), minX, minZ, maxX, maxZ)) {
            Vec3 c = planet.center();
            double extent = planet.extent();
            int x0 = Math.max(minX, (int) Math.floor(c.x - extent));
            int x1 = Math.min(maxX, (int) Math.floor(c.x + extent));
            int z0 = Math.max(minZ, (int) Math.floor(c.z - extent));
            int z1 = Math.min(maxZ, (int) Math.floor(c.z + extent));
            int y0 = Math.max(bottom, (int) Math.floor(c.y - extent));
            int y1 = Math.min(top, (int) Math.floor(c.y + extent));
            PlanetTheme theme = planet.theme();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        Material material = planet.materialAt(x, y, z);
                        if (material != null) {
                            chunk.setBlockState(cursor.set(x, y, z), theme.block(material).defaultBlockState());
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        // Nothing yet: planets get their surface details in a later version.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
    }

    @Override
    public int getGenDepth() {
        return GalaxyLayout.MAX_Y - GalaxyLayout.MIN_Y + 1;
    }

    @Override
    public int getSeaLevel() {
        return GalaxyLayout.MIN_Y;
    }

    @Override
    public int getMinY() {
        return GalaxyLayout.MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int highest = heightAccessor.getMinY();
        for (Planet planet : GalaxyLayout.planetsTouching(randomState.seed(), x, z, x, z)) {
            int top = planet.topY(x, z);
            if (top != Integer.MIN_VALUE) {
                highest = Math.max(highest, top + 1);
            }
        }
        return highest;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int minY = heightAccessor.getMinY();
        BlockState[] column = new BlockState[heightAccessor.getHeight()];
        Arrays.fill(column, Blocks.AIR.defaultBlockState());
        List<Planet> planets = GalaxyLayout.planetsTouching(randomState.seed(), x, z, x, z);
        for (Planet planet : planets) {
            for (int i = 0; i < column.length; i++) {
                Material material = planet.materialAt(x, minY + i, z);
                if (material != null) {
                    column[i] = planet.theme().block(material).defaultBlockState();
                }
            }
        }
        return new NoiseColumn(minY, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos, SamplerContext samplerContext) {
        int number = Galaxy.numberAt(feetPos.getX());
        result.add(number == 0 ? "Galaxy: none (empty space)" : "Galaxy: " + number);
    }
}
