package io.github.chocodanette.galaxy.gravity;

import java.util.List;

/**
 * The player's copy of the planets in their current dimension, as last sent by the server.
 * Holds no client-only classes, so it is safe to load on a dedicated server (where it stays empty).
 */
public final class ClientPlanets {
    private static volatile GravityWorld world = GravityWorld.EMPTY;
    private static volatile boolean showRanges = true;

    private ClientPlanets() {
    }

    public static List<GravityField> fields() {
        return world.fields();
    }

    public static GravityWorld world() {
        return world;
    }

    /** Whether the server wants the blue gravity range bubbles drawn. */
    public static boolean showRanges() {
        return showRanges;
    }

    static void set(GravityWorld newWorld, boolean newShowRanges) {
        world = newWorld;
        showRanges = newShowRanges;
    }
}
