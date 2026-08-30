package dev.foucaultleon.flterraforged.engine.noise;

/** Immutable octave/fractal wrapper around another noise source. */
public final class FractalNoise2D implements Noise2D {

    private final Noise2D source;
    private final int octaves;
    private final double persistence;
    private final double lacunarity;
    private final double normalization;

    public FractalNoise2D(Noise2D source, int octaves, double persistence, double lacunarity) {
        if (octaves < 1) {
            throw new IllegalArgumentException("octaves must be >= 1");
        }
        if (!(persistence > 0.0D && persistence <= 1.0D)) {
            throw new IllegalArgumentException("persistence must be in (0, 1]");
        }
        if (lacunarity <= 1.0D) {
            throw new IllegalArgumentException("lacunarity must be > 1");
        }
        this.source = source;
        this.octaves = octaves;
        this.persistence = persistence;
        this.lacunarity = lacunarity;
        double amplitude = 1.0D;
        double total = 0.0D;
        for (int i = 0; i < octaves; i++) {
            total += amplitude;
            amplitude *= persistence;
        }
        this.normalization = 1.0D / total;
    }

    @Override
    public double sample(double x, double z) {
        double result = 0.0D;
        double frequency = 1.0D;
        double amplitude = 1.0D;
        for (int octave = 0; octave < octaves; octave++) {
            result += source.sample(x * frequency, z * frequency) * amplitude;
            frequency *= lacunarity;
            amplitude *= persistence;
        }
        return result * normalization;
    }
}
