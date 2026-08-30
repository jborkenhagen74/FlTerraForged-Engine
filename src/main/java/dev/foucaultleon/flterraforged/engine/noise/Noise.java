package dev.foucaultleon.flterraforged.engine.noise;

/**
 * Seed-aware two-dimensional scalar field used by the terrain engine.
 *
 * <p>The contract is deliberately independent from Minecraft and serialization APIs. Implementations
 * are expected to be immutable and thread-safe unless explicitly documented otherwise.</p>
 */
@FunctionalInterface
public interface Noise {

    /**
     * Samples the field.
     *
     * @param x X coordinate in noise space
     * @param z Z coordinate in noise space
     * @param seed world or feature seed
     * @return sampled value
     */
    double sample(double x, double z, long seed);

    /**
     * Returns the nominal minimum produced by this field.
     *
     * @return nominal minimum
     */
    default double minValue() {
        return -1.0D;
    }

    /**
     * Returns the nominal maximum produced by this field.
     *
     * @return nominal maximum
     */
    default double maxValue() {
        return 1.0D;
    }
}
