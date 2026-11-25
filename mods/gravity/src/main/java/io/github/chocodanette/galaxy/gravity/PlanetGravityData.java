package io.github.chocodanette.galaxy.gravity;

import java.util.ArrayList;
import java.util.List;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;

/**
 * The planets of one dimension, saved with the world. The server owns this list;
 * players get a copy through {@link SyncPlanetsPayload}.
 */
public final class PlanetGravityData extends SavedData {
    private static final Codec<PlanetGravityData> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
            GravityField.CODEC.listOf().fieldOf("planets").forGetter(data -> data.fields),
            Codec.BOOL.optionalFieldOf("show_centers", true).forGetter(data -> data.showCenters),
            Codec.BOOL.optionalFieldOf("show_ranges", true).forGetter(data -> data.showRanges),
            Codec.DOUBLE.optionalFieldOf("space_pull", 0.0).forGetter(data -> data.spacePull),
            Vec3.CODEC.listOf().optionalFieldOf("anchors", List.of()).forGetter(data -> data.anchors),
            GravityMode.CODEC.optionalFieldOf("gravity_mode_v2", GravityMode.STEPS).forGetter(data -> data.mode)
    ).apply(i, PlanetGravityData::new));

    /** Reads saves from before center markers (a plain list of planets) as well. */
    public static final Codec<PlanetGravityData> CODEC = Codec.either(FULL_CODEC, GravityField.CODEC.listOf())
            .xmap(either -> either.map(data -> data, list -> new PlanetGravityData(list, true, true, 0.0, List.of(), GravityMode.STEPS)), Either::left);

    public static final SavedDataType<PlanetGravityData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "planets"),
            PlanetGravityData::new, CODEC);

    private final List<GravityField> fields;
    private boolean showCenters;
    private boolean showRanges;
    private double spacePull;
    private final List<Vec3> anchors;
    private GravityMode mode;

    public PlanetGravityData() {
        this(List.of(), true, true, 0.0, List.of(), GravityMode.STEPS);
    }

    private PlanetGravityData(List<GravityField> fields, boolean showCenters, boolean showRanges,
            double spacePull, List<Vec3> anchors, GravityMode mode) {
        this.mode = mode;
        this.spacePull = spacePull;
        this.anchors = new ArrayList<>(anchors);
        this.fields = new ArrayList<>(fields);
        this.showCenters = showCenters;
        this.showRanges = showRanges;
    }

    public static PlanetGravityData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<GravityField> fields() {
        return List.copyOf(fields);
    }

    public void add(GravityField field) {
        fields.add(field);
        setDirty();
    }

    /** Removes the field at a 0-based index. Returns false when the index is out of range. */
    public boolean remove(int index) {
        if (index < 0 || index >= fields.size()) {
            return false;
        }
        fields.remove(index);
        setDirty();
        return true;
    }

    /** Whether a glowing marker shows each planet's gravity center. */
    public boolean showCenters() {
        return showCenters;
    }

    public void setShowCenters(boolean show) {
        showCenters = show;
        setDirty();
    }

    /** Whether players see a light blue bubble showing how far each planet's gravity reaches. */
    public boolean showRanges() {
        return showRanges;
    }

    public void setShowRanges(boolean show) {
        showRanges = show;
        setDirty();
    }

    /** This dimension's planets and space pull, ready for the gravity math. */
    public GravityWorld world() {
        return new GravityWorld(fields, spacePull, anchors, mode);
    }

    /**
     * Turns on a weak pull toward the nearest planet in the empty space between planets, as a
     * share of normal gravity (0 turns it off and leaves vanilla gravity there).
     */
    public void setSpacePull(double pull) {
        if (pull != spacePull) {
            spacePull = pull;
            setDirty();
        }
    }

    public GravityMode mode() {
        return mode;
    }

    public void setMode(GravityMode mode) {
        this.mode = mode;
        setDirty();
    }

    public double spacePull() {
        return spacePull;
    }

    /** Marks a planet center as a home planet that pulls harder from space. Returns true if new. */
    public boolean addAnchor(Vec3 center) {
        if (anchors.contains(center)) {
            return false;
        }
        anchors.add(center);
        setDirty();
        return true;
    }

    public void clear() {
        fields.clear();
        setDirty();
    }

    /** Forgets every planet and home planet, for a dimension whose planets are all gone. */
    public void reset() {
        fields.clear();
        anchors.clear();
        setDirty();
    }
}
