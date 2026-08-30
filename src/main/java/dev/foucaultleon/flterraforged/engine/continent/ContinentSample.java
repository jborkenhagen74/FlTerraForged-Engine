package dev.foucaultleon.flterraforged.engine.continent;

import java.util.Objects;

/**
 * Immutable result of continent partitioning for one world coordinate.
 *
 * @param id stable normalized identifier in {@code [0, 1]}
 * @param edge normalized inward distance from the continent boundary in {@code [0, 1]}
 * @param center stable world-space center of the owning continent cell
 */
public record ContinentSample(double id, double edge, ContinentCenter center) {

    /**
     * Validates the sample.
     *
     * @param id stable normalized identifier
     * @param edge normalized inward edge distance
     * @param center continent center
     */
    public ContinentSample {
        if (!Double.isFinite(id) || id < 0.0D || id > 1.0D) {
            throw new IllegalArgumentException("id must be finite and in [0, 1]");
        }
        if (!Double.isFinite(edge) || edge < 0.0D || edge > 1.0D) {
            throw new IllegalArgumentException("edge must be finite and in [0, 1]");
        }
        Objects.requireNonNull(center, "center");
    }

    /**
     * Maps the inward edge signal to the conventional continentalness range.
     *
     * @return value in {@code [-1, 1]}
     */
    public double continentalness() {
        return edge * 2.0D - 1.0D;
    }
}
