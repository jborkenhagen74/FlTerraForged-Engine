package dev.foucaultleon.flterraforged.engine.noise;

import java.util.Objects;

/** Adapter binding a seed-aware {@link Noise} to the legacy seed-bound {@link Noise2D} contract. */
public final class SeededNoise2D implements Noise2D {

    private final Noise source;
    private final long seed;

    /**
     * Creates a seed-bound adapter.
     *
     * @param source source noise
     * @param seed seed bound to the adapter
     */
    public SeededNoise2D(Noise source, long seed) {
        this.source = Objects.requireNonNull(source, "source");
        this.seed = seed;
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z) {
        return source.sample(x, z, seed);
    }
}
