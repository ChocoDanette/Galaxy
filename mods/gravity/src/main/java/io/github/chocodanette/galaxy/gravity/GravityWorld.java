package io.github.chocodanette.galaxy.gravity;

import java.util.List;

import net.minecraft.world.phys.Vec3;

/**
 * The gravity rules of one dimension: its planets, plus an optional weak pull in the empty
 * space between them.
 *
 * @param fields    every planet's gravity field
 * @param spacePull outside every field, things drift toward the nearest planet with this
 *                  share of its pull (0 = off, so vanilla gravity applies there)
 * @param anchors   centers of planets that pull harder from space (each galaxy's home planet)
 */
public record GravityWorld(List<GravityField> fields, double spacePull, List<Vec3> anchors) {
    public static final GravityWorld EMPTY = new GravityWorld(List.of(), 0.0, List.of());

    public GravityWorld {
        fields = List.copyOf(fields);
        anchors = List.copyOf(anchors);
    }

    /** Just planets, no pull between them. */
    public static GravityWorld of(List<GravityField> fields) {
        return new GravityWorld(fields, 0.0, List.of());
    }

    public boolean isAnchor(GravityField field) {
        return anchors.contains(field.center());
    }
}
