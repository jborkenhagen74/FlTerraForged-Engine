package dev.foucaultleon.flterraforged.engine.noise.domain;

import dev.foucaultleon.flterraforged.engine.noise.Vector2;

/** Coordinate transformation applied before sampling another noise field. */
@FunctionalInterface
public interface Domain {

    /**
     * Transforms a coordinate.
     *
     * @param x input X
     * @param z input Z
     * @param seed seed
     * @return transformed coordinate
     */
    Vector2 transform(double x, double z, long seed);
}
