package dev.foucaultleon.flterraforged.engine.river;

/**
 * Internal nearest-channel result retaining information not exposed by the stable engine API.
 *
 * @param distance horizontal distance to the centerline in blocks
 * @param width full channel width in blocks
 * @param depth local incision depth in blocks
 * @param surfaceHeight eroded pre-incision height interpolated along the segment
 * @param flow accumulated drainage weight represented by the segment
 */
public record RiverHit(
        double distance,
        double width,
        double depth,
        double surfaceHeight,
        double flow) {

    /** Shared marker used when no segment is present in the searched maps. */
    public static final RiverHit NONE = new RiverHit(Double.POSITIVE_INFINITY, 0.0D, 0.0D, Double.NaN, 0.0D);

    /**
     * Returns whether this result references an actual river segment.
     *
     * @return {@code true} when a finite centerline distance is available
     */
    public boolean present() {
        return Double.isFinite(distance);
    }
}
