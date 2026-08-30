package dev.foucaultleon.flterraforged.engine.noise;

/** Low-level deterministic math and hashing utilities for noise implementations. */
public final class NoiseMath {

    private static final long X_PRIME = 0x9E3779B185EBCA87L;
    private static final long Z_PRIME = 0xC2B2AE3D27D4EB4FL;
    private static final long SEED_PRIME = 0x165667B19E3779F9L;

    private NoiseMath() {
    }

    /**
     * Clamps a value to a closed interval.
     *
     * @param value value to clamp
     * @param min lower bound
     * @param max upper bound
     * @return clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Linearly interpolates between two values.
     *
     * @param from start value
     * @param to end value
     * @param alpha interpolation factor
     * @return interpolated value
     */
    public static double lerp(double from, double to, double alpha) {
        return from + alpha * (to - from);
    }

    /**
     * Maps a value from one range to another.
     *
     * @param value source value
     * @param sourceMin source minimum
     * @param sourceMax source maximum
     * @param targetMin target minimum
     * @param targetMax target maximum
     * @return mapped value
     */
    public static double map(double value, double sourceMin, double sourceMax, double targetMin, double targetMax) {
        if (sourceMax == sourceMin) {
            return targetMin;
        }
        double alpha = (value - sourceMin) / (sourceMax - sourceMin);
        return lerp(targetMin, targetMax, alpha);
    }

    /**
     * Returns the mathematical floor as an integer.
     *
     * @param value input value
     * @return floor of the value
     */
    public static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    /**
     * Produces a stable 64-bit hash for a seed and lattice coordinate.
     *
     * @param seed seed
     * @param x lattice X
     * @param z lattice Z
     * @return mixed hash
     */
    public static long hash(long seed, int x, int z) {
        long value = seed * SEED_PRIME;
        value ^= (long) x * X_PRIME;
        value ^= (long) z * Z_PRIME;
        return mix64(value);
    }

    /**
     * Produces a deterministic scalar in {@code [-1, 1]} for a lattice coordinate.
     *
     * @param seed seed
     * @param x lattice X
     * @param z lattice Z
     * @return deterministic scalar
     */
    public static double value(long seed, int x, int z) {
        long hash = hash(seed, x, z);
        double normalized = (hash >>> 11) * 0x1.0p-53;
        return normalized * 2.0D - 1.0D;
    }

    /**
     * Returns a unit gradient direction selected from sixteen evenly spaced directions.
     *
     * @param seed seed
     * @param x lattice X
     * @param z lattice Z
     * @return unit gradient
     */
    public static Vector2 gradient(long seed, int x, int z) {
        int index = (int) (hash(seed, x, z) & 15L);
        double angle = index * (Math.PI * 2.0D / 16.0D);
        return new Vector2(Math.cos(angle), Math.sin(angle));
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }
}
