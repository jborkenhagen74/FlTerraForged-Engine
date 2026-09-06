package dev.foucaultleon.flterraforged.engine.chunk;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;

/**
 * Minimal internal bridge from complete chunk generation to the lower-level final terrain cache.
 *
 * <p>The interface deliberately exposes only point sampling. It prevents the chunk package from
 * depending on the package-private cache implementation while avoiding boxed coordinates in the
 * 256-sample chunk hot path.</p>
 */
@FunctionalInterface
public interface TerrainPointSampler {

    /**
     * Returns the canonical final terrain sample at a world position.
     *
     * @param x world X
     * @param z world Z
     * @return final terrain sample
     */
    TerrainSample sample(int x, int z);
}
