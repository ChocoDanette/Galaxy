package io.github.chocodanette.galaxy.worldgen;

import java.util.List;
import java.util.SplittableRandom;

/**
 * One galaxy: a round patch of the galaxy dimension filled with planets. Each galaxy has its
 * own personality, picked from the world seed and its number: size, how crowded it is, which
 * kinds of planets are common, how big they tend to be and how many are cubes.
 *
 * @param number      1, 2, 3...
 * @param seed        decides everything inside this galaxy
 * @param centerX     x of the galaxy's middle (z is always 0)
 * @param radius      how far planets reach from the middle, in blocks
 * @param density     chance that a spot holds a planet near the middle (fewer toward the edge)
 * @param favorites   kinds of planets that are common here; the others are rare
 * @param sizeBias    from -1 (mostly small planets) to 1 (mostly big ones)
 * @param cubeChance  share of planets that are cubes
 * @param hollowChance  share of spots that hold a hollow planet
 * @param gatewayChance share of spots that hold two planets joined by tiny gateway planets
 */
public record Galaxy(int number, long seed, int centerX, int radius, double density,
                     List<PlanetTheme> favorites, double sizeBias, double cubeChance,
                     double hollowChance, double gatewayChance) {
    /** Distance between the middles of two neighbouring galaxies, so they never touch. */
    public static final int SPACING = 100_000;
    /** Highest galaxy number, to stay inside Minecraft's world border. */
    public static final int MAX_NUMBER = 250;

    public static Galaxy of(long worldSeed, int number) {
        long seed = Hashing.mix(worldSeed, 0x6A1A7, number);
        SplittableRandom random = new SplittableRandom(seed);
        int radius = 640 + random.nextInt(641);
        double density = 0.55 + random.nextDouble() * 0.35;
        PlanetTheme[] themes = PlanetTheme.COMMON.toArray(new PlanetTheme[0]);
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
        return new Galaxy(number, seed, number * SPACING, radius, density, List.of(favorites), sizeBias, cubeChance,
                hollowChance, gatewayChance);
    }

    /** The galaxy whose area covers this x, or 0 when x is between galaxies' numbers. */
    public static int numberAt(double x) {
        long number = Math.round(x / SPACING);
        return number >= 1 && number <= MAX_NUMBER ? (int) number : 0;
    }

    /** How common a kind of planet is in this galaxy, compared to the others. */
    int themeWeight(PlanetTheme theme) {
        if (theme.isRare()) {
            return 0;
        }
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
