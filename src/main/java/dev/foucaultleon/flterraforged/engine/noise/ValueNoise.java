package dev.foucaultleon.flterraforged.engine.noise;

import java.util.Objects;

/** Seed-aware lattice value-noise implementation. */
public final class ValueNoise implements Noise {

    private final long seedOffset;
    private final double frequency;
    private final Interpolation interpolation;

    /**
     * Creates a value-noise field.
     *
     * @param seedOffset deterministic seed offset for this field
     * @param frequency coordinate frequency multiplier
     * @param interpolation lattice interpolation curve
     */
    public ValueNoise(long seedOffset, double frequency, Interpolation interpolation) {
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
        double tx = interpolation.apply(sx - x0);
        double tz = interpolation.apply(sz - z0);
        long actualSeed = seed ^ seedOffset;
        double north = NoiseMath.lerp(
                NoiseMath.value(actualSeed, x0, z0),
                NoiseMath.value(actualSeed, x1, z0),
                tx);
        double south = NoiseMath.lerp(
                NoiseMath.value(actualSeed, x0, z1),
                NoiseMath.value(actualSeed, x1, z1),
                tx);
        return NoiseMath.lerp(north, south, tz);
    }
}
