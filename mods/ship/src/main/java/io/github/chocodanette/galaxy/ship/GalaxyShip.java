package io.github.chocodanette.galaxy.ship;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Entry point for Galaxy Ship: the crew's shared home.
 *
 * The ship never moves as blocks. It sits still in its own dimension of empty space
 * ({@link ShipLocation#SHIP}), and players see stars stream past the windows. Flying is
 * done at the console: the ship's position is just a galaxy number saved with the world
 * ({@link ShipData}), and landing teleports a player to that galaxy's arrival planet.
 */
@Mod(GalaxyShip.MODID)
public final class GalaxyShip {
    public static final String MODID = "galaxy_ship";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GalaxyShip(IEventBus modEventBus, ModContainer modContainer) {
        ShipBlocks.register(modEventBus);
        ShipNetwork.register(modEventBus);
        ShipBuilder.register();
        ShipCrew.register();
        ShipCommands.register();
        LOGGER.info("Galaxy Ship loaded");
    }
}
