package dev.foucaultleon.flterraforged.engine.noise.function;

/** Converts the two nearest cellular distances into a scalar edge signal. */
@FunctionalInterface
public interface EdgeFunction {

    /**
     * Computes a cellular edge value.
     *
     * @param nearest nearest feature-point distance
     * @param secondNearest second-nearest feature-point distance
     * @return edge value
     */
    double apply(double nearest, double secondNearest);

    /** Difference between the two nearest distances. */
    EdgeFunction DISTANCE_DIFFERENCE = (nearest, secondNearest) -> secondNearest - nearest;
}
