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
 */
public record LakeHit(
        LakeZone zone,
        double influence,
        double waterSurfaceHeight,
        double minimumDepth,
        double shoreDistance) {

    /** Marker used outside inland-water basins. */
    public static final LakeHit NONE = new LakeHit(
            LakeZone.NONE, 0.0D, Double.NaN, 0.0D, Double.NEGATIVE_INFINITY);

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
        this(zone, influence, waterSurfaceHeight, minimumDepth, 0.0D);
    }

    /**
     * Returns whether this sample belongs to a lake or pond zone.
     *
     * @return {@code true} for shore, shallow and core basin samples
     */
    public boolean present() {
        return zone != LakeZone.NONE && Double.isFinite(waterSurfaceHeight);
    }

    /**
     * Returns whether this sample must materialize inland water.
     *
     * @return {@code true} for shallow and core water zones
     */
    public boolean materialWater() {
        return (zone == LakeZone.SHALLOW || zone == LakeZone.CORE)
                && Double.isFinite(waterSurfaceHeight)
                && minimumDepth > 0.0D;
    }

    /**
     * Returns whether this sample is the dry shoreline transition.
     *
     * @return {@code true} in the lake/pond shore zone
     */
    public boolean shore() {
        return zone == LakeZone.SHORE;
    }

    /**
     * Returns whether this sample is in the stable inner basin.
     *
     * @return {@code true} in the lake/pond core
     */
    public boolean core() {
        return zone == LakeZone.CORE;
    }
}
