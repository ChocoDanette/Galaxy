package io.github.chocodanette.galaxy.gravity;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The planets of one dimension, saved with the world. The server owns this list;
 * players get a copy through {@link SyncPlanetsPayload}.
 */
public final class PlanetGravityData extends SavedData {
    public static final Codec<PlanetGravityData> CODEC = GravityField.CODEC.listOf()
            .xmap(PlanetGravityData::new, data -> data.fields);

    public static final SavedDataType<PlanetGravityData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GalaxyGravity.MODID, "planets"),
            PlanetGravityData::new, CODEC);

    private final List<GravityField> fields;

    public PlanetGravityData() {
        this(List.of());
    }

    private PlanetGravityData(List<GravityField> fields) {
        this.fields = new ArrayList<>(fields);
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

    public void clear() {
        fields.clear();
        setDirty();
    }
}
