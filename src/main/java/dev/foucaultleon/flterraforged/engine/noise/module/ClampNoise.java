package dev.foucaultleon.flterraforged.engine.noise.module;

import dev.foucaultleon.flterraforged.engine.noise.Noise;
import dev.foucaultleon.flterraforged.engine.noise.NoiseMath;
import java.util.Objects;

/** Clamps another noise field to a fixed range. */
public final class ClampNoise implements Noise {

    private final Noise source;
    private final double min;
    private final double max;

    /**
     * Creates a clamp field.
     *
     * @param source source field
     * @param min minimum
     * @param max maximum
     */
    public ClampNoise(Noise source, double min, double max) {
        this.source = Objects.requireNonNull(source, "source");
        if (!Double.isFinite(min) || !Double.isFinite(max) || max < min) {
            throw new IllegalArgumentException("invalid clamp range");
        }
        this.min = min;
        this.max = max;
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z, long seed) {
        return NoiseMath.clamp(source.sample(x, z, seed), min, max);
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
