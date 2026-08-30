package dev.foucaultleon.flterraforged.engine.noise.module;

import dev.foucaultleon.flterraforged.engine.noise.Noise;

/** Constant scalar noise field. */
public final class ConstantNoise implements Noise {

    private final double value;

    /**
     * Creates a constant field.
     *
     * @param value constant value
     */
    public ConstantNoise(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        this.value = value;
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z, long seed) {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public double minValue() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public double maxValue() {
        return value;
    }
}
