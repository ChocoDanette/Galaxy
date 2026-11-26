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
 * 3. Our own round gravity: each entity's frame tilts gradually toward its planet, and
 *    movement, jumping, aim, eyes, hitbox and camera follow it (see GravityFrame and mixin/). Done.
 * 4. Drawing other players and mobs tilted. Next.
 */
@Mod(GalaxyGravity.MODID)
public final class GalaxyGravity {
    public static final String MODID = "galaxy_gravity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GalaxyGravity(IEventBus modEventBus, ModContainer modContainer) {
        PlanetSync.register(modEventBus);
        GravityFrames.register(modEventBus);
        GravityApplier.register();
        PlanetCommands.register();
        CaveMoves.register();
        StuckSafety.register();
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, GravityConfig.SPEC);
        LOGGER.info("Galaxy Gravity loaded");
    }
}
