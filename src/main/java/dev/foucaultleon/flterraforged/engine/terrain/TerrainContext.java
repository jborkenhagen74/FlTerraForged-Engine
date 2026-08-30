package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.continent.ContinentSample;
import dev.foucaultleon.flterraforged.engine.terrain.region.TerrainRegionSample;
import java.util.Objects;

/**
 * Immutable input shared by terrain definitions while shaping one horizontal world position.
 *
 * @param world engine world bounds and sea-level context
 * @param x world X coordinate
 * @param z world Z coordinate
 * @param continent continent sample for the position
 * @param region terrain-region sample for the position
 */
public record TerrainContext(
        EngineContext world,
        double x,
        double z,
        ContinentSample continent,
        TerrainRegionSample region) {

    /**
     * Validates a terrain context.
     *
     * @param world engine world bounds and sea-level context
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param continent continent sample for the position
     * @param region terrain-region sample for the position
     */
    public TerrainContext {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(continent, "continent");
        Objects.requireNonNull(region, "region");
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("terrain context numeric values must be finite");
        }
    }

    /**
     * Returns the continent signal in the conventional {@code [-1, 1]} range.
     *
     * @return continentalness signal
     */
    public double continentalness() {
        return continent.continentalness();
    }
}
