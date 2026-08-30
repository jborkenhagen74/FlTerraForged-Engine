package dev.foucaultleon.flterraforged.engine.noise;

/**
 * Immutable two-dimensional vector used by domain and gradient-noise calculations.
 *
 * @param x X component
 * @param z Z component
 */
public record Vector2(double x, double z) {

    /** Zero vector. */
    public static final Vector2 ZERO = new Vector2(0.0D, 0.0D);

    /**
     * Adds another vector.
     *
     * @param other vector to add
     * @return summed vector
     */
    public Vector2 add(Vector2 other) {
        return new Vector2(x + other.x, z + other.z);
    }

    /**
     * Scales this vector.
     *
     * @param factor scale factor
     * @return scaled vector
     */
    public Vector2 multiply(double factor) {
        return new Vector2(x * factor, z * factor);
    }
}
