package io.github.chocodanette.galaxy.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The station's state, saved with the world in the overworld's data (one station per
 * world, shared by everyone). Only the server changes it.
 */
public final class StationData extends SavedData {
    /** Bump when the station's look changes, so existing worlds get the new one. */
    public static final int LAYOUT_VERSION = 1;
    /**
     * Bump when things are added to the existing station (without rebuilding it, so chests
     * keep what's in them).
     */
    public static final int EXTRAS_VERSION = 1;

    public static final Codec<StationData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("built_version", 0).forGetter(data -> data.builtVersion),
            Codec.INT.optionalFieldOf("galaxy", 1).forGetter(data -> data.galaxy),
            Codec.INT.optionalFieldOf("view_galaxy", 0).forGetter(data -> data.viewGalaxy),
            Codec.INT.optionalFieldOf("extras_version", 0).forGetter(data -> data.extrasVersion)
    ).apply(i, StationData::new));

    public static final SavedDataType<StationData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GalaxyShip.MODID, "station"),
            StationData::new, CODEC);

    private int builtVersion;
    private int galaxy;
    private int viewGalaxy;
    private int extrasVersion;

    public StationData() {
        this(0, 1, 0, 0);
    }

    private StationData(int builtVersion, int galaxy, int viewGalaxy, int extrasVersion) {
        this.builtVersion = builtVersion;
        this.galaxy = galaxy;
        this.viewGalaxy = viewGalaxy;
        this.extrasVersion = extrasVersion;
    }

    /** Whether the latest additions (the trader, the bloom to your own station) are in place. */
    public boolean extrasUpToDate() {
        return extrasVersion >= EXTRAS_VERSION;
    }

    public void setExtrasBuilt() {
        extrasVersion = EXTRAS_VERSION;
        setDirty();
    }

    public static StationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Whether the current station layout has been placed in this world. */
    public boolean upToDate() {
        return builtVersion >= LAYOUT_VERSION;
    }

    public void setBuilt() {
        builtVersion = LAYOUT_VERSION;
        setDirty();
    }

    /** The galaxy below the station: the one you dive into. */
    public int galaxy() {
        return galaxy;
    }

    public void setGalaxy(int galaxy) {
        this.galaxy = galaxy;
        setDirty();
    }

    /** The galaxy the model below the station currently shows (0 = none yet). */
    public int viewGalaxy() {
        return viewGalaxy;
    }

    public void setViewGalaxy(int viewGalaxy) {
        this.viewGalaxy = viewGalaxy;
        setDirty();
    }
}
