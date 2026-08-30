package dev.foucaultleon.flterraforged.engine.terrain.provider;

import dev.foucaultleon.flterraforged.engine.terrain.Terrain;

/** Resolves a deterministic terrain landform from a normalized terrain-region selector. */
@FunctionalInterface
public interface TerrainProvider {

    /**
     * Resolves a terrain definition.
     *
     * @param selector normalized region selector in {@code [0, 1]}
     * @return terrain definition
     */
    Terrain resolve(double selector);
}
