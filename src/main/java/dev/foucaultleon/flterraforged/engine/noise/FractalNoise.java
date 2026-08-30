package dev.foucaultleon.flterraforged.engine.noise;

import java.util.Objects;

/** Multi-octave fractal wrapper for a seed-aware noise field. */
public final class FractalNoise implements Noise {

    private final Noise source;
    private final int octaves;
    private final double gain;
    private final double lacunarity;
    private final double normalization;

    /**
     * Creates a fractal field.
     *
     * @param source base field
     * @param octaves octave count, at least one
     * @param gain amplitude multiplier between octaves
     * @param lacunarity frequency multiplier between octaves
     */
    public FractalNoise(Noise source, int octaves, double gain, double lacunarity) {
        this.source = Objects.requireNonNull(source, "source");
        if (octaves < 1) {
            throw new IllegalArgumentException("octaves must be >= 1");
        }
        if (!(gain > 0.0D && gain <= 1.0D)) {
            throw new IllegalArgumentException("gain must be in (0, 1]");
        }
        if (lacunarity <= 1.0D || !Double.isFinite(lacunarity)) {
            throw new IllegalArgumentException("lacunarity must be finite and > 1");
        }
        this.octaves = octaves;
        this.gain = gain;
        this.lacunarity = lacunarity;
        double amplitude = 1.0D;
        double sum = 0.0D;
        for (int octave = 0; octave < octaves; octave++) {
            sum += amplitude;
            amplitude *= gain;
        }
        this.normalization = 1.0D / sum;
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z, long seed) {
        double frequency = 1.0D;
        double amplitude = 1.0D;
        double result = 0.0D;
        for (int octave = 0; octave < octaves; octave++) {
            long octaveSeed = seed + 0x9E3779B97F4A7C15L * octave;
            result += source.sample(x * frequency, z * frequency, octaveSeed) * amplitude;
            frequency *= lacunarity;
            amplitude *= gain;
        }
        return result * normalization;
    }

    /** {@inheritDoc} */
    @Override
    public double minValue() {
        return source.minValue();
    }

    /** {@inheritDoc} */
    @Override
    public double maxValue() {
        return source.maxValue();
    }
}
