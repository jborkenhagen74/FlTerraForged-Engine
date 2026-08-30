package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;

/**
 * Engine-side definition of a terrain landform.
 *
 * <p>A terrain definition converts already-resolved continent, region and erosion signals into a
 * continuous surface height. It must not depend on Minecraft classes.</p>
 */
public interface Terrain {

    /**
     * Returns the semantic terrain identifier exposed to FlTerraForged.
     *
     * @return semantic terrain type
     */
    TerrainType type();

    /**
     * Returns the broad terrain category used by engine-side selection and blending.
     *
     * @return terrain category
     */
    TerrainCategory category();

    /**
     * Samples continuous world-space surface height.
     *
     * @param context immutable terrain context
     * @return continuous surface height in blocks
     */
    double height(TerrainContext context);

    /**
     * Samples a normalized ridge or weirdness hint associated with this terrain definition.
     *
     * @param context immutable terrain context
     * @return normalized weirdness hint in {@code [-1, 1]}
     */
    default double weirdness(TerrainContext context) {
        return 0.0D;
    }
}
