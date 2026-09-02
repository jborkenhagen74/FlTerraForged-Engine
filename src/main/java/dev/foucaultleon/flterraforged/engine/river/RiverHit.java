package dev.foucaultleon.flterraforged.engine.river;

/**
 * Internal nearest-watercourse result retaining information not exposed by the stable engine API.
 *
 * @param distance horizontal distance to the centerline in blocks
 * @param width full channel/body width hint in blocks
 * @param depth local terrain incision depth in blocks
 * @param surfaceHeight eroded pre-incision height at the sampled path position
 * @param waterSurfaceHeight continuous world-space Y coordinate of the local water surface
 * @param flow accumulated drainage weight represented by the watercourse
 * @param lake whether this hit represents a pond/lake instead of a linear channel
 */
public record RiverHit(
        double distance,
        double width,
        double depth,
        double surfaceHeight,
        double waterSurfaceHeight,
        double flow,
        boolean lake) {

    private static final double FREE_VERTICAL_ALIGNMENT = 2.0D;
    private static final double VERTICAL_ALIGNMENT_WEIGHT = 2.0D;

    /** Shared marker used when no hydrology feature is present. */
    public static final RiverHit NONE = new RiverHit(
            Double.POSITIVE_INFINITY,
            0.0D,
            0.0D,
            Double.NaN,
            Double.NaN,
            0.0D,
            false);

    /**
     * Returns whether this result references an actual hydrology feature.
     *
     * @return {@code true} when a finite centerline/body distance is available
     */
    public boolean present() {
        return Double.isFinite(distance);
    }

    /**
     * Returns a terrain-aware selection score for overlapping projected channels.
     *
     * <p>Horizontal distance remains the primary signal near an ordinary channel. A candidate
     * whose water surface is implausibly far below or above local terrain receives a penalty so it
     * cannot displace a similarly close surface channel at a two-dimensional crossing.</p>
     *
     * @param terrainHeight local pre-hydrology terrain height
     * @return lower-is-better surface-alignment score
     */
    public double surfaceAlignmentScore(double terrainHeight) {
        if (!present() || !Double.isFinite(terrainHeight) || !Double.isFinite(waterSurfaceHeight)) {
            return distance;
        }
        double verticalMismatch = Math.max(
                0.0D,
                Math.abs(terrainHeight - waterSurfaceHeight) - FREE_VERTICAL_ALIGNMENT);
        return distance + verticalMismatch * VERTICAL_ALIGNMENT_WEIGHT;
    }
}
