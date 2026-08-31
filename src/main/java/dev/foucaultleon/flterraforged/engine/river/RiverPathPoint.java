package dev.foucaultleon.flterraforged.engine.river;

/**
 * One immutable world-space point on a terrain-refined visible river path.
 *
 * @param x world X coordinate
 * @param z world Z coordinate
 * @param terrainHeight pre-river eroded terrain height at the refined centerline
 * @param waterSurfaceHeight bank-contained hydrologic water surface at this point
 */
public record RiverPathPoint(
        double x,
        double z,
        double terrainHeight,
        double waterSurfaceHeight) {
}
