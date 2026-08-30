package dev.foucaultleon.flterraforged.engine.noise;

import dev.foucaultleon.flterraforged.engine.internal.Maths;

/** Small deterministic lattice value-noise implementation used by the bootstrap engine. */
public final class ValueNoise2D implements Noise2D {

    private final long seed;

    /**
     * Creates a deterministic value-noise source.
     *
     * @param seed noise seed
     */
    public ValueNoise2D(long seed) {
        this.seed = seed;
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        double tx = fade(x - x0);
        double tz = fade(z - z0);

        double a = Maths.lerp(value(x0, z0), value(x1, z0), tx);
        double b = Maths.lerp(value(x0, z1), value(x1, z1), tx);
        return Maths.lerp(a, b, tz);
    }

    private double value(int x, int z) {
        long h = seed;
        h ^= (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        h = mix64(h);
        return ((h >>> 11) * 0x1.0p-53) * 2.0D - 1.0D;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return value;
    }

    private static int fastFloor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double fade(double value) {
        return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
    }
}
