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
 * 1. Gravity fields and the math that picks the pull at a position (this commit).
 * 2. Server-side list of fields per dimension, saved with the world and synced to players.
 * 3. Player and mob movement along the local "down", rotated hitbox and camera.
 */
@Mod(GalaxyGravity.MODID)
public final class GalaxyGravity {
    public static final String MODID = "galaxy_gravity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GalaxyGravity(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Galaxy Gravity loaded");
    }
}
