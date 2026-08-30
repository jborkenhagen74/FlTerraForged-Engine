package dev.foucaultleon.flterraforged.engine.noise.module;

import dev.foucaultleon.flterraforged.engine.noise.Noise;
import dev.foucaultleon.flterraforged.engine.noise.NoiseMath;
import java.util.Objects;

/** Maps a source field from its nominal range to another range. */
public final class MapNoise implements Noise {

    private final Noise source;
    private final double min;
    private final double max;

    /**
     * Creates a range mapping field.
     *
     * @param source source field
     * @param min target minimum
     * @param max target maximum
     */
    public MapNoise(Noise source, double min, double max) {
        this.source = Objects.requireNonNull(source, "source");
        if (!Double.isFinite(min) || !Double.isFinite(max) || max < min) {
            throw new IllegalArgumentException("invalid map range");
        }
        this.min = min;
        this.max = max;
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z, long seed) {
        return NoiseMath.map(source.sample(x, z, seed), source.minValue(), source.maxValue(), min, max);
    }

    /** {@inheritDoc} */
    @Override
    public double minValue() {
        return min;
    }

    /** {@inheritDoc} */
    @Override
    public double maxValue() {
        return max;
    }
}
