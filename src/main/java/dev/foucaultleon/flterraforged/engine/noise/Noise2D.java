package dev.foucaultleon.flterraforged.engine.noise;

/** Stateless two-dimensional noise source with a nominal {@code [-1, 1]} output. */
@FunctionalInterface
public interface Noise2D {

    /**
     * Samples the noise field.
     *
     * @param x X coordinate in noise space
     * @param z Z coordinate in noise space
     * @return nominal noise value in {@code [-1, 1]}
     */
    double sample(double x, double z);
}
