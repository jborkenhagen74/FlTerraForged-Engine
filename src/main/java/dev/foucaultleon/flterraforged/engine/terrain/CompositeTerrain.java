package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import java.util.Arrays;
import java.util.Objects;

/** Blended view of multiple terrain definitions near terrain-region boundaries. */
public final class CompositeTerrain implements Terrain {

    private final Terrain primary;
    private final Terrain secondary;
    private final Terrain[] components;
    private final double[] weights;

    /**
     * Creates the traditional two-terrain blend.
     *
     * @param primary owning terrain definition
     * @param secondary neighboring terrain definition
     * @param primaryWeight primary weight in {@code [0.5, 1]}
     */
    public CompositeTerrain(Terrain primary, Terrain secondary, double primaryWeight) {
        this(
                Objects.requireNonNull(primary, "primary"),
                new Terrain[] {primary, Objects.requireNonNull(secondary, "secondary")},
                new double[] {validatedPrimaryWeight(primaryWeight), 1.0D - primaryWeight});
    }

    /**
     * Creates a normalized multi-terrain blend.
     *
     * @param primary owning terrain definition used for semantic classification
     * @param components active terrain definitions
     * @param weights normalized component weights
     */
    CompositeTerrain(Terrain primary, Terrain[] components, double[] weights) {
        this.primary = Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(weights, "weights");
        if (components.length < 2 || components.length != weights.length) {
            throw new IllegalArgumentException("composite terrain needs matching arrays with at least two entries");
        }
        this.components = components.clone();
        this.weights = weights.clone();
        double sum = 0.0D;
        Terrain firstSecondary = null;
        for (int index = 0; index < this.components.length; index++) {
            Terrain terrain = Objects.requireNonNull(this.components[index], "component");
            double weight = this.weights[index];
            if (!Double.isFinite(weight) || weight < 0.0D || weight > 1.0D) {
                throw new IllegalArgumentException("component weights must be finite and in [0, 1]");
            }
            sum += weight;
            if (firstSecondary == null && terrain != primary) {
                firstSecondary = terrain;
            }
        }
        if (Math.abs(sum - 1.0D) > 1.0E-9D) {
            throw new IllegalArgumentException("component weights must sum to 1");
        }
        this.secondary = firstSecondary == null ? this.components[1] : firstSecondary;
    }

    /** {@inheritDoc} */
    @Override
    public TerrainType type() {
        return primary.type();
    }

    /** {@inheritDoc} */
    @Override
    public TerrainCategory category() {
        return primary.category();
    }

    /** {@inheritDoc} */
    @Override
    public double height(TerrainContext context) {
        double value = 0.0D;
        for (int index = 0; index < components.length; index++) {
            value += components[index].height(context) * weights[index];
        }
        return value;
    }

    /** {@inheritDoc} */
    @Override
    public double weirdness(TerrainContext context) {
        double value = 0.0D;
        for (int index = 0; index < components.length; index++) {
            value += components[index].weirdness(context) * weights[index];
        }
        return value;
    }

    /**
     * Returns the owning terrain definition.
     *
     * @return primary terrain
     */
    public Terrain primary() {
        return primary;
    }

    /**
     * Returns the first neighboring terrain definition.
     *
     * @return secondary terrain
     */
    public Terrain secondary() {
        return secondary;
    }

    /**
     * Returns the total contribution of the owning terrain definition.
     *
     * @return primary weight
     */
    public double primaryWeight() {
        double weight = 0.0D;
        for (int index = 0; index < components.length; index++) {
            if (components[index] == primary) {
                weight += weights[index];
            }
        }
        return weight;
    }

    /**
     * Returns the number of distinct active terrain definitions.
     *
     * @return component count
     */
    public int componentCount() {
        return components.length;
    }

    @Override
    public String toString() {
        return "CompositeTerrain{" + Arrays.toString(components) + "}";
    }

    private static double validatedPrimaryWeight(double primaryWeight) {
        if (!Double.isFinite(primaryWeight) || primaryWeight < 0.5D || primaryWeight > 1.0D) {
            throw new IllegalArgumentException("primaryWeight must be finite and in [0.5, 1]");
        }
        return primaryWeight;
    }
}
