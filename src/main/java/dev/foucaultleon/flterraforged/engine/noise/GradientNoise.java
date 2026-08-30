package dev.foucaultleon.flterraforged.engine.noise;

import java.util.Objects;

/** Two-dimensional gradient noise suitable for smooth terrain signals. */
public final class GradientNoise implements Noise {

    private final long seedOffset;
    private final double frequency;
    private final Interpolation interpolation;

    /**
     * Creates a gradient-noise field.
     *
     * @param seedOffset deterministic seed offset for this field
     * @param frequency coordinate frequency multiplier
     * @param interpolation lattice interpolation curve
     */
    public GradientNoise(long seedOffset, double frequency, Interpolation interpolation) {
        if (!Double.isFinite(frequency) || frequency <= 0.0D) {
            throw new IllegalArgumentException("frequency must be finite and > 0");
        }
        this.seedOffset = seedOffset;
        this.frequency = frequency;
        this.interpolation = Objects.requireNonNull(interpolation, "interpolation");
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z, long seed) {
        double sx = x * frequency;
        double sz = z * frequency;
        int x0 = NoiseMath.floor(sx);
        int z0 = NoiseMath.floor(sz);
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        double dx = sx - x0;
        double dz = sz - z0;
        double tx = interpolation.apply(dx);
        double tz = interpolation.apply(dz);
        long actualSeed = seed ^ seedOffset;

        double n00 = dot(actualSeed, x0, z0, dx, dz);
        double n10 = dot(actualSeed, x1, z0, dx - 1.0D, dz);
        double n01 = dot(actualSeed, x0, z1, dx, dz - 1.0D);
        double n11 = dot(actualSeed, x1, z1, dx - 1.0D, dz - 1.0D);
        double north = NoiseMath.lerp(n00, n10, tx);
        double south = NoiseMath.lerp(n01, n11, tx);
        return NoiseMath.clamp(NoiseMath.lerp(north, south, tz) * 1.45D, -1.0D, 1.0D);
    }

    private static double dot(long seed, int x, int z, double dx, double dz) {
        Vector2 gradient = NoiseMath.gradient(seed, x, z);
        return gradient.x() * dx + gradient.z() * dz;
    }
}
