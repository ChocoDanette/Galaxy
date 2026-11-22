package io.github.chocodanette.galaxy.gravity;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Entry point for Galaxy Gravity.
 *
 * Plan for this mod, in order:
 * 1. Gravity fields and the math that picks the pull at a position. Done.
 * 2. Planets saved per dimension, synced to players, pulling entities toward their center. Done.
 * 3. Movement, hitbox and camera that turn with the local "down". Next.
 */
@Mod(GalaxyGravity.MODID)
public final class GalaxyGravity {
    public static final String MODID = "galaxy_gravity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GalaxyGravity(IEventBus modEventBus, ModContainer modContainer) {
        PlanetSync.register(modEventBus);
        GravityApplier.register();
        PlanetCommands.register();
        LOGGER.info("Galaxy Gravity loaded");
    }
}
