package io.github.chocodanette.galaxy.ship;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The ship's state, saved with the world. There is one ship per world, shared by everyone,
 * so this lives in the overworld's save data. Only the server changes it.
 */
public final class ShipData extends SavedData {
    /** Fuel the tank holds at most. */
    public static final int MAX_FUEL = 64;
    /** Fuel one jump to the next galaxy burns. */
    public static final int JUMP_COST = 8;
    /** A new ship starts with enough for two jumps. */
    public static final int STARTING_FUEL = 16;

    public static final Codec<ShipData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("built", false).forGetter(data -> data.built),
            Codec.INT.optionalFieldOf("galaxy", 1).forGetter(data -> data.galaxy),
            Codec.INT.optionalFieldOf("fuel", STARTING_FUEL).forGetter(data -> data.fuel)
    ).apply(i, ShipData::new));

    public static final SavedDataType<ShipData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GalaxyShip.MODID, "ship"),
            ShipData::new, CODEC);

    private boolean built;
    private int galaxy;
    private int fuel;

    public ShipData() {
        this(false, 1, STARTING_FUEL);
    }

    private ShipData(boolean built, int galaxy, int fuel) {
        this.built = built;
        this.galaxy = galaxy;
        this.fuel = Mth.clamp(fuel, 0, MAX_FUEL);
    }

    public static ShipData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Whether the ship's blocks have been placed in this world yet. */
    public boolean built() {
        return built;
    }

    public void setBuilt() {
        built = true;
        setDirty();
    }

    /** The galaxy the ship is parked at. */
    public int galaxy() {
        return galaxy;
    }

    public void setGalaxy(int galaxy) {
        this.galaxy = galaxy;
        setDirty();
    }

    public int fuel() {
        return fuel;
    }

    public void setFuel(int fuel) {
        this.fuel = Mth.clamp(fuel, 0, MAX_FUEL);
        setDirty();
    }

    public ShipStatePayload state(boolean openScreen, net.minecraft.core.BlockPos console) {
        return new ShipStatePayload(openScreen, console, galaxy, fuel, MAX_FUEL, JUMP_COST);
    }
}
