package io.github.chocodanette.galaxy.worldgen.client;

import java.util.List;

import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import io.github.chocodanette.galaxy.worldgen.GalaxyWorldgen;
import io.github.chocodanette.galaxy.worldgen.PlanetBlocks;

/** Colors planet turf and planet grass like the grass around them. */
@EventBusSubscriber(modid = GalaxyWorldgen.MODID, value = Dist.CLIENT)
public final class PlanetBlockTints {
    private PlanetBlockTints() {
    }

    @SubscribeEvent
    static void onBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(BlockTintSources.grass()), PlanetBlocks.PLANET_TURF.get(), PlanetBlocks.PLANET_GRASS.get());
    }
}
