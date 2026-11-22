package io.github.chocodanette.galaxy.gravity;

import java.util.List;

/**
 * The player's copy of the planets in their current dimension, as last sent by the server.
 * Holds no client-only classes, so it is safe to load on a dedicated server (where it stays empty).
 */
public final class ClientPlanets {
    private static volatile List<GravityField> fields = List.of();

    private ClientPlanets() {
    }

    public static List<GravityField> fields() {
        return fields;
    }

    static void set(List<GravityField> newFields) {
        fields = List.copyOf(newFields);
    }
}
