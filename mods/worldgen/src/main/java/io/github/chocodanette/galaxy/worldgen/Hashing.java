package io.github.chocodanette.galaxy.worldgen;

/**
 * Small, fast number mixing used by generation. The same inputs always give the same
 * output on every computer and Java version, so the server and every chunk agree on
 * where each planet is and what it's made of.
 */
final class Hashing {
    private Hashing() {
    }

    /** Scrambles a number thoroughly (the SplitMix64 finisher). */
    static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static long mix(long seed, long a, long b) {
        return mix(mix(mix(seed) ^ a) ^ b);
    }

    static long mix(long seed, long a, long b, long c) {
        return mix(mix(mix(mix(seed) ^ a) ^ b) ^ c);
    }

    /** A number from 0 (included) to 1 (excluded) for a block position. */
    static double unit(long seed, int x, int y, int z) {
        return (mix(seed, x, y, z) >>> 11) * 0x1.0p-53;
    }

    /**
     * Smooth noise from -1 to 1: neighbouring points give close values, so it makes gentle
     * hills instead of random spikes.
     */
    static double smoothNoise(long seed, double x, double y, double z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = fade(x - x0);
        double fy = fade(y - y0);
        double fz = fade(z - z0);
        double c000 = unit(seed, x0, y0, z0);
        double c100 = unit(seed, x0 + 1, y0, z0);
        double c010 = unit(seed, x0, y0 + 1, z0);
        double c110 = unit(seed, x0 + 1, y0 + 1, z0);
        double c001 = unit(seed, x0, y0, z0 + 1);
        double c101 = unit(seed, x0 + 1, y0, z0 + 1);
        double c011 = unit(seed, x0, y0 + 1, z0 + 1);
        double c111 = unit(seed, x0 + 1, y0 + 1, z0 + 1);
        double x00 = lerp(fx, c000, c100);
        double x10 = lerp(fx, c010, c110);
        double x01 = lerp(fx, c001, c101);
        double x11 = lerp(fx, c011, c111);
        double value = lerp(fz, lerp(fy, x00, x10), lerp(fy, x01, x11));
        return value * 2.0 - 1.0;
    }

    private static double fade(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
