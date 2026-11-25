package io.github.chocodanette.galaxy.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Galaxies don't last. Each one lives for a random 1 to 6 hours of play (the clock only runs
 * while someone is online), then collapses: everyone inside is pulled back home, and a new,
 * different galaxy takes its place. {@link GalaxyClock} runs the clock, the warnings and the
 * collapse; this is what's saved with the world (in the overworld's data, one for the whole
 * server).
 *
 * Every galaxy gets the next number, which picks everything in it, and is built in a fresh
 * slot (a strip of the galaxy dimension far from the others). Slots of old galaxies are
 * "dirty" until their files are deleted, which happens safely the next time the server starts
 * ({@link GalaxyWipe}); a dirty slot is never reused before that.
 *
 * The number and slot are also kept in plain static fields, because world generation runs on
 * other threads and needs them without touching saved data. Only the server sets them.
 */
public final class GalaxyCycle extends SavedData {
    /** Bump when the planet layout changes, so existing worlds start a new galaxy built the new way. */
    public static final int LAYOUT_VERSION = 2;
    public static final long TICKS_PER_HOUR = 20L * 60 * 60;
    public static final long MIN_LIFE = TICKS_PER_HOUR;
    public static final long MAX_LIFE = 6 * TICKS_PER_HOUR;

    public static final Codec<GalaxyCycle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("number", 1).forGetter(data -> data.number),
            Codec.INT.optionalFieldOf("slot", 1).forGetter(data -> data.slot),
            Codec.LONG.optionalFieldOf("ticks_left", -1L).forGetter(data -> data.ticksLeft),
            Codec.LONG.optionalFieldOf("life", 0L).forGetter(data -> data.life),
            Codec.INT.optionalFieldOf("layout", 0).forGetter(data -> data.layout),
            Codec.INT.listOf().optionalFieldOf("dirty_slots", List.of()).forGetter(data -> data.dirtySlots)
    ).apply(i, GalaxyCycle::new));

    public static final SavedDataType<GalaxyCycle> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(GalaxyWorldgen.MODID, "galaxy_cycle"),
            GalaxyCycle::new, CODEC);

    private static volatile int currentNumber = 1;
    private static volatile int currentSlot = 1;
    private static final List<Consumer<MinecraftServer>> NEW_GALAXY_LISTENERS = new ArrayList<>();

    private int number;
    private int slot;
    private long ticksLeft;
    private long life;
    private int layout;
    private final List<Integer> dirtySlots;

    public GalaxyCycle() {
        this(1, 1, -1L, 0L, 0, List.of());
    }

    private GalaxyCycle(int number, int slot, long ticksLeft, long life, int layout, List<Integer> dirtySlots) {
        this.number = Math.max(1, number);
        this.slot = Math.clamp(slot, 1, Galaxy.MAX_SLOT);
        this.ticksLeft = ticksLeft;
        this.life = life;
        this.layout = layout;
        this.dirtySlots = new ArrayList<>(dirtySlots);
    }

    public static GalaxyCycle get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** The number of the galaxy that's here now. */
    public static int number() {
        return currentNumber;
    }

    /** The slot the galaxy that's here now is built in. */
    public static int slot() {
        return currentSlot;
    }

    /** Runs whenever a new galaxy replaces the old one (the station redraws its model, for one). */
    public static void onNewGalaxy(Consumer<MinecraftServer> listener) {
        NEW_GALAXY_LISTENERS.add(listener);
    }

    static void fireNewGalaxy(MinecraftServer server) {
        for (Consumer<MinecraftServer> listener : NEW_GALAXY_LISTENERS) {
            listener.accept(server);
        }
    }

    /** Copies the saved number and slot to where world generation reads them. */
    void publish() {
        currentNumber = number;
        currentSlot = slot;
    }

    /** Back to galaxy 1, for the next world opened in this game (single player). */
    static void forget() {
        currentNumber = 1;
        currentSlot = 1;
    }

    /** For tests: pretend this galaxy is the current one. */
    static void setCurrentForTests(int number, int slot) {
        currentNumber = number;
        currentSlot = slot;
    }

    /** Ticks of play left before this galaxy collapses (-1 = clock not started yet). */
    public long ticksLeft() {
        return ticksLeft;
    }

    /** How long this galaxy lives in all, in ticks. */
    public long life() {
        return life;
    }

    void setTicksLeft(long ticks) {
        ticksLeft = Math.max(0, ticks);
        life = Math.max(life, ticksLeft);
        setDirty();
    }

    /** Counts one tick down; saved now and then (the world saves dirty data on its own schedule). */
    void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft % 100 == 0) {
                setDirty();
            }
        }
    }

    /** Gives the galaxy a fresh random life of 1 to 6 hours. */
    void startClock(RandomSource random) {
        life = MIN_LIFE + (long) (random.nextDouble() * (MAX_LIFE - MIN_LIFE));
        ticksLeft = life;
        setDirty();
    }

    boolean layoutOutdated() {
        return layout < LAYOUT_VERSION;
    }

    void markLayoutCurrent() {
        layout = LAYOUT_VERSION;
        setDirty();
    }

    /** Moves on to a new galaxy in a clean slot, and marks the old slot for deletion. */
    void advance(RandomSource random) {
        if (!dirtySlots.contains(slot)) {
            dirtySlots.add(slot);
        }
        number++;
        int next = slot;
        for (int i = 0; i < Galaxy.MAX_SLOT; i++) {
            next = next % Galaxy.MAX_SLOT + 1;
            if (!dirtySlots.contains(next)) {
                break;
            }
        }
        slot = next;
        layout = LAYOUT_VERSION;
        startClock(random);
        publish();
    }

    /** Slots of old galaxies whose files are still on disk. */
    List<Integer> dirtySlots() {
        return List.copyOf(dirtySlots);
    }

    void markClean(int cleanSlot) {
        if (dirtySlots.remove(Integer.valueOf(cleanSlot))) {
            setDirty();
        }
    }
}
