package dev.foucaultleon.flterraforged.engine.river;

/**
 * Internal sample of a depression-filled inland water body.
 *
 * @param zone semantic basin zone
 * @param influence normalized inland-water influence in {@code [0,1]}
 * @param waterSurfaceHeight constant continuous basin water surface in world Y
 * @param minimumDepth desired minimum local water depth in blocks
 * @param shoreDistance signed approximate horizontal distance from the shoreline in blocks;
 *        positive values point into the water body and negative values point onto land
 * @param basinKey stable hydrology-grid anchor key for cross-map water-level reconciliation
 */
public record LakeHit(
        LakeZone zone,
        double influence,
        double waterSurfaceHeight,
        double minimumDepth,
        double shoreDistance,
        long basinKey) {

    /** Marker used when no stable basin anchor is available. */
    public static final long NO_BASIN_KEY = Long.MIN_VALUE;

    /** Marker used outside inland-water basins. */
    public static final LakeHit NONE = new LakeHit(
            LakeZone.NONE,
            0.0D,
            Double.NaN,
            0.0D,
            Double.NEGATIVE_INFINITY,
            NO_BASIN_KEY);

    /**
     * Creates the legacy five-value lake hit without a stable basin anchor.
     *
     * @param zone semantic basin zone
     * @param influence normalized inland-water influence
     * @param waterSurfaceHeight constant basin water surface
     * @param minimumDepth desired minimum local depth
     * @param shoreDistance signed shoreline distance
     */
    public LakeHit(
            LakeZone zone,
            double influence,
            double waterSurfaceHeight,
            double minimumDepth,
            double shoreDistance) {
        this(zone, influence, waterSurfaceHeight, minimumDepth, shoreDistance, NO_BASIN_KEY);
    }

    /**
     * Creates the legacy four-value lake hit without an explicit shoreline distance.
     *
     * @param zone semantic basin zone
     * @param influence normalized inland-water influence
     * @param waterSurfaceHeight constant basin water surface
     * @param minimumDepth desired minimum local depth
     */
    public LakeHit(
            LakeZone zone,
            double influence,
            double waterSurfaceHeight,
            double minimumDepth) {
        this(zone, influence, waterSurfaceHeight, minimumDepth, 0.0D, NO_BASIN_KEY);
    }

    /**
     * Returns whether this hit carries a stable cross-map basin anchor.
     *
     * @return {@code true} when a stable basin key is available
     */
    public boolean hasBasinKey() {
        return basinKey != NO_BASIN_KEY;
    }

    /**
     * Returns whether this sample belongs to a lake or pond zone.
     *
     * @return {@code true} for any present lake or shoreline zone
     */
    public boolean present() {
        return zone != LakeZone.NONE && Double.isFinite(waterSurfaceHeight);
    }

    /**
     * Returns whether this sample must materialize inland water.
     *
     * @return {@code true} for shallow or core lake water with positive depth
     */
    public boolean materialWater() {
        return (zone == LakeZone.SHALLOW || zone == LakeZone.CORE)
                && Double.isFinite(waterSurfaceHeight)
                && minimumDepth > 0.0D;
    }

    /**
     * Returns whether this sample is the dry shoreline transition.
     *
     * @return {@code true} for the shoreline transition zone
     */
    public boolean shore() {
        return zone == LakeZone.SHORE;
    }

    /**
     * Returns whether this sample is in the stable inner basin.
     *
     * @return {@code true} for the lake core zone
     */
    public boolean core() {
        return zone == LakeZone.CORE;
    }

    /**
     * Returns a copy using the reconciled canonical water level.
     *
     * @param waterSurfaceHeight reconciled canonical water-surface height
     * @return copied lake hit retaining all other basin metadata
     */
    public LakeHit withWaterSurfaceHeight(double waterSurfaceHeight) {
        return new LakeHit(zone, influence, waterSurfaceHeight, minimumDepth, shoreDistance, basinKey);
    }
}
