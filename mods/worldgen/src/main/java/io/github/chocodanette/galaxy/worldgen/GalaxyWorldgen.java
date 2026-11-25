package io.github.chocodanette.galaxy.worldgen;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entry point for Galaxy Generation: the galaxy dimension and the planets in it.
 *
 * The dimension itself is described in data/galaxy_worldgen (its sky, height and the
 * generator to use). The Java side places the planets ({@link GalaxyLayout}), builds their
 * blocks ({@link PlanetChunkGenerator}) and hands each one to the Gravity mod
 * ({@link PlanetGravityLink}).
 */
@Mod(GalaxyWorldgen.MODID)
public final class GalaxyWorldgen {
    public static final String MODID = "galaxy_worldgen";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MODID);

    static {
        CHUNK_GENERATORS.register("planets", () -> PlanetChunkGenerator.CODEC);
    }

    public GalaxyWorldgen(IEventBus modEventBus, ModContainer modContainer) {
        CHUNK_GENERATORS.register(modEventBus);
        PlanetBlocks.register(modEventBus);
        BloomRides.register(modEventBus);
        PlanetGravityLink.register();
        GalaxyCommands.register();
        GalaxyClock.register();
        LOGGER.info("Galaxy Generation loaded");
    }
}
