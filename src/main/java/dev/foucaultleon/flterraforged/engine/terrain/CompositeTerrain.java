package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.Objects;

/**
 * Blended view of two terrain definitions near a terrain-region boundary.
 */
public final class CompositeTerrain implements Terrain {

    private final Terrain primary;
    private final Terrain secondary;
    private final double primaryWeight;

    /**
     * Creates a blended terrain definition.
     *
     * @param primary owning terrain definition
     * @param secondary neighboring terrain definition
     * @param primaryWeight primary weight in {@code [0.5, 1]}
     */
    public CompositeTerrain(Terrain primary, Terrain secondary, double primaryWeight) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.secondary = Objects.requireNonNull(secondary, "secondary");
        if (!Double.isFinite(primaryWeight) || primaryWeight < 0.5D || primaryWeight > 1.0D) {
            throw new IllegalArgumentException("primaryWeight must be finite and in [0.5, 1]");
        }
        this.primaryWeight = primaryWeight;
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
        return Maths.lerp(secondary.height(context), primary.height(context), primaryWeight);
    }

    /** {@inheritDoc} */
    @Override
    public double weirdness(TerrainContext context) {
        return Maths.lerp(secondary.weirdness(context), primary.weirdness(context), primaryWeight);
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
     * Returns the neighboring terrain definition.
     *
     * @return secondary terrain
     */
    public Terrain secondary() {
        return secondary;
    }

    /**
     * Returns the owning terrain contribution.
     *
     * @return primary weight
     */
    public double primaryWeight() {
        return primaryWeight;
    }
}
