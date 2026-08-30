package dev.foucaultleon.flterraforged.engine.noise;

/** Stateless two-dimensional noise source with a nominal [-1, 1] output. */
@FunctionalInterface
public interface Noise2D {
    double sample(double x, double z);
}
