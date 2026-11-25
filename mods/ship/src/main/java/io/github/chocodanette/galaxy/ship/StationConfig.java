package io.github.chocodanette.galaxy.ship;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server settings for Galaxy Station, in the file {@code galaxy_ship-local.toml} that the server
 * writes the first time it starts with this mod. Change it with the server stopped.
 */
public final class StationConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("What the trader on the common station asks for a space rock (it founds a crew's own station).")
                .push("space_rock_price");
    }

    public static final ModConfigSpec.IntValue PRICE_EMERALDS = BUILDER
            .comment("Emeralds needed.")
            .defineInRange("emeralds", 8, 0, 64 * 36);
    public static final ModConfigSpec.IntValue PRICE_COAL = BUILDER
            .comment("Coal needed (charcoal counts too).")
            .defineInRange("coal", 32, 0, 64 * 36);

    static {
        BUILDER.pop().push("player_stations");
    }

    public static final ModConfigSpec.IntValue MAX_STATIONS = BUILDER
            .comment("How many player stations this server allows in total.")
            .defineInRange("max_stations", 100, 0, 10_000);
    public static final ModConfigSpec.IntValue START_RADIUS = BUILDER
            .comment("How far from its middle a new station can be built on, sideways (6 = a 13 by 13 area).")
            .defineInRange("start_build_radius", 6, 4, 200);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private StationConfig() {
    }
}
