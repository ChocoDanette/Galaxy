package io.github.chocodanette.galaxy.core;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Entry point for Galaxy Core, the shared library every other Galaxy mod depends on.
 * Keep this mod free of gameplay: it only holds code that two or more mods need.
 */
@Mod(GalaxyCore.MODID)
public final class GalaxyCore {
    public static final String MODID = "galaxy_core";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GalaxyCore(IEventBus modEventBus, ModContainer modContainer) {
        GalaxyNetwork.register(modEventBus);
        LOGGER.info("Galaxy Core loaded");
    }
}
