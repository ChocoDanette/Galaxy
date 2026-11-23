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

/**
 * The planets of one dimension, saved with the world. The server owns this list;
 * players get a copy through {@link SyncPlanetsPayload}.
 */
public final class PlanetGravityData extends SavedData {
    private static final Codec<PlanetGravityData> FULL_CODEC = RecordCodecBuilder.create(i -> i.group(
            GravityField.CODEC.listOf().fieldOf("planets").forGetter(data -> data.fields),
            Codec.BOOL.optionalFieldOf("show_centers", true).forGetter(data -> data.showCenters),
            Codec.BOOL.optionalFieldOf("show_ranges", true).forGetter(data -> data.showRanges)
    ).apply(i, PlanetGravityData::new));

    /** Reads saves from before center markers (a plain list of planets) as well. */
    public static final Codec<PlanetGravityData> CODEC = Codec.either(FULL_CODEC, GravityField.CODEC.listOf())
            .xmap(either -> either.map(data -> data, list -> new PlanetGravityData(list, true, true)), Either::left);

    public static final SavedDataType<PlanetGravityData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "planets"),
            PlanetGravityData::new, CODEC);

    private final List<GravityField> fields;
    private boolean showCenters;
    private boolean showRanges;

    public PlanetGravityData() {
        this(List.of(), true, true);
    }

    private PlanetGravityData(List<GravityField> fields, boolean showCenters, boolean showRanges) {
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

    public void clear() {
        fields.clear();
        setDirty();
    }
}
