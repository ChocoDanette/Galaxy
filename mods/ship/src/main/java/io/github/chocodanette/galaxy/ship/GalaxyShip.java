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
 * into the void drops you into the real galaxy ({@link StationCrew}). Homeward Blooms in the
 * galaxy, or dying, bring you back.
 *
 * Crews can also have a station of their own ({@link StationPlots}): buy a space rock from the
 * trader on the common station ({@link StationTrade}), use it, and your station appears on its
 * own plot far from everyone else's. Crews and their roles live in {@code crew.CrewData}.
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
        StationItems.register(modEventBus);
        StationBlooms.register();
        CrewCommands.register();
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.LOCAL, StationConfig.SPEC);
        // When a galaxy collapses, its crew comes home to the station, and the model below shows the new one.
        GalaxyClock.setRecall(StationCrew::toStation);
        GalaxyCycle.onNewGalaxy(GalaxyView::refresh);
        LOGGER.info("Galaxy Station loaded");
    }
}
