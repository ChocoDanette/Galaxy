package io.github.chocodanette.galaxy.ship;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import io.github.chocodanette.galaxy.worldgen.GalaxyClock;
import io.github.chocodanette.galaxy.worldgen.GalaxyCycle;

/**
 * Entry point for Galaxy Station (mod id kept as {@code galaxy_ship} so existing installs
 * keep working): the crew's shared home.
 *
 * The station is a small, primitive platform floating in its own dimension
 * ({@link StationLocation#STATION}). Nobody can build or break there ({@link StationRules}).
 * The galaxy shows below it as a little model ({@link GalaxyView}); jumping off the station
 * into the void drops you into the real galaxy ({@link StationCrew}), and {@code /station}
 * brings you back.
 */
@Mod(GalaxyShip.MODID)
public final class GalaxyShip {
    public static final String MODID = "galaxy_ship";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GalaxyShip(IEventBus modEventBus, ModContainer modContainer) {
        StationBuilder.register();
        StationRules.register();
        StationCrew.register();
        StationCommands.register();
        // When a galaxy collapses, its crew comes home to the station, and the model below shows the new one.
        GalaxyClock.setRecall(StationCrew::toStation);
        GalaxyCycle.onNewGalaxy(GalaxyView::refresh);
        LOGGER.info("Galaxy Station loaded");
    }
}
