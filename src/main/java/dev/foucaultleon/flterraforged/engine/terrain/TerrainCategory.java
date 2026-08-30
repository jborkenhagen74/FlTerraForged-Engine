package dev.foucaultleon.flterraforged.engine.terrain;

/**
 * Broad engine-side terrain families used to organize and blend configured terrain shapes.
 *
 * <p>The categories are intentionally independent from Minecraft biomes and surface materials.</p>
 */
public enum TerrainCategory {
    /** Terrain below or immediately around sea level. */
    OCEAN,
    /** Low-relief land intended for broad flat areas. */
    FLAT,
    /** Rolling terrain with moderate relief. */
    HILLS,
    /** High-relief ridge-dominated terrain. */
    MOUNTAINS,
    /** Elevated terrain with a comparatively flat upper surface. */
    PLATEAU,
    /** Depressed or channel-like terrain between surrounding landforms. */
    VALLEY
}
