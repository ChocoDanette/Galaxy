package io.github.chocodanette.galaxy.worldgen;

import java.util.List;
import java.util.SplittableRandom;

/**
 * One galaxy: a flat disc of planets in the galaxy dimension, like a real galaxy seen from
 * the side. Each galaxy has its own personality, picked from the world seed and its number:
 * size, how crowded it is, which kinds of planets are common, how big they tend to be and how
 * many are cubes.
 *
 * Galaxies don't last: {@link GalaxyCycle} replaces the current one with a new one every few
 * hours. Only the current galaxy has planets; every new one gets a higher number (so it's
 * different) and is built in a fresh "slot", a strip of the dimension far from the others.
 *
 * @param number      1, 2, 3... grows forever, one per galaxy that ever existed
 * @param slot        which strip of the dimension it's built in, 1 to {@link #MAX_SLOT}
 * @param seed        decides everything inside this galaxy
 * @param centerX     x of the galaxy's middle (y and z are always 0)
 * @param radius      how far planets reach from the middle, across the disc, in blocks
 * @param thickness   how far planets reach above and below the middle, in blocks
 * @param density     chance that a spot holds a planet near the middle (fewer toward the edge)
 * @param favorites   kinds of planets that are common here; the others are rare
 * @param sizeBias    from -1 (mostly small planets) to 1 (mostly big ones)
 * @param cubeChance  share of planets that are cubes
 * @param hollowChance  share of spots that hold a hollow planet
 * @param gatewayChance share of spots that hold two planets joined by tiny gateway planets
 */
public record Galaxy(int number, int slot, long seed, int centerX, int radius, int thickness, double density,
                     List<PlanetTheme> favorites, double sizeBias, double cubeChance,
                     double hollowChance, double gatewayChance) {
    /** Distance between the middles of two neighbouring slots, so galaxies never touch. */
    public static final int SPACING = 100_000;
    /** Highest slot, to stay inside Minecraft's world border. */
    public static final int MAX_SLOT = 250;
    /** Height of the middle of every galaxy. */
    public static final int CENTER_Y = 0;

    public static Galaxy of(long worldSeed, int number, int slot) {
        long seed = Hashing.mix(worldSeed, 0x6A1A7, number);
        SplittableRandom random = new SplittableRandom(seed);
        int radius = 640 + random.nextInt(641);
        double density = 0.55 + random.nextDouble() * 0.35;
        PlanetTheme[] themes = PlanetTheme.values();
        int favoriteCount = 2 + random.nextInt(3);
        PlanetTheme[] favorites = new PlanetTheme[favoriteCount];
        for (int i = 0; i < favoriteCount; i++) {
            PlanetTheme pick;
            do {
                pick = themes[random.nextInt(themes.length)];
            } while (contains(favorites, pick));
            favorites[i] = pick;
        }
        double sizeBias = random.nextDouble() * 2.0 - 1.0;
        double cubeChance = 0.05 + random.nextDouble() * 0.30;
        double hollowChance = 0.03 + random.nextDouble() * 0.09;
        double gatewayChance = 0.06 + random.nextDouble() * 0.14;
        // A disc: a quarter to a third as thick as it is wide, and always inside the dimension.
        int thickness = (int) Math.min(GalaxyLayout.MAX_Y - CENTER_Y - 48,
                radius * (0.22 + random.nextDouble() * 0.12));
        return new Galaxy(number, slot, seed, slot * SPACING, radius, thickness, density, List.of(favorites), sizeBias,
                cubeChance, hollowChance, gatewayChance);
    }

    /** The slot whose strip covers this x, or 0 outside every slot. */
    public static int slotAt(double x) {
        long slot = Math.round(x / SPACING);
        return slot >= 1 && slot <= MAX_SLOT ? (int) slot : 0;
    }

    /** The number of the current galaxy if this x is in its strip, or 0 (old galaxies and deep space). */
    public static int numberAt(double x) {
        int slot = slotAt(x);
        return slot != 0 && slot == GalaxyCycle.slot() ? GalaxyCycle.number() : 0;
    }

    /** The current galaxy if this x is in its strip, or null. */
    public static Galaxy at(long worldSeed, double x) {
        int number = numberAt(x);
        return number == 0 ? null : of(worldSeed, number, GalaxyCycle.slot());
    }

    /** The galaxy that's here now. */
    public static Galaxy current(long worldSeed) {
        return of(worldSeed, GalaxyCycle.number(), GalaxyCycle.slot());
    }

    /** How common a kind of planet is in this galaxy, compared to the others. */
    int themeWeight(PlanetTheme theme) {
        return favorites.contains(theme) ? 6 : 1;
    }

    private static boolean contains(PlanetTheme[] array, PlanetTheme theme) {
        for (PlanetTheme t : array) {
            if (t == theme) {
                return true;
            }
        }
        return false;
    }
}
