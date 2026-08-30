package dev.foucaultleon.flterraforged.engine.terrain.region;

/**
 * Deterministic terrain-region partition result.
 *
 * @param id stable owning-region selector in {@code [0, 1]}
 * @param neighborId stable neighboring-region selector in {@code [0, 1]}
 * @param edge normalized distance from the closest region boundary in {@code [0, 1]}
 * @param cellX owning terrain-region cell X
 * @param cellZ owning terrain-region cell Z
 */
public record TerrainRegionSample(double id, double neighborId, double edge, int cellX, int cellZ) {

    /**
     * Validates a terrain-region sample.
     *
     * @param id stable owning-region selector in {@code [0, 1]}
     * @param neighborId stable neighboring-region selector in {@code [0, 1]}
     * @param edge normalized distance from the closest region boundary in {@code [0, 1]}
     * @param cellX owning terrain-region cell X
     * @param cellZ owning terrain-region cell Z
     */
    public TerrainRegionSample {
        if (!unit(id) || !unit(neighborId) || !unit(edge)) {
            throw new IllegalArgumentException("terrain region signals must be finite and in [0, 1]");
        }
    }

    private static boolean unit(double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
    }
}
