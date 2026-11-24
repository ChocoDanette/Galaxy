package io.github.chocodanette.galaxy.gravity;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Your own settings for Galaxy Gravity (each player's game, not the server). Change them in
 * the game: Mods, Galaxy Gravity, Config.
 */
public final class GravityConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** Keep looking at the same spot when gravity turns you, instead of the view sweeping round. */
    public static final ModConfigSpec.BooleanValue STEADY_CAMERA = BUILDER
            .comment("When gravity turns you (over a planet's edge, onto a wall), keep looking at the same spot:",
                    "the world turns around your view instead of your view sweeping with your body.")
            .translation("galaxy_gravity.configuration.steady_camera")
            .define("steady_camera", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private GravityConfig() {
    }
}
