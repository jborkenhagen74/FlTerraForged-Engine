package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.internal.Maths;

/**
 * Immutable directed river segment joining two drainage nodes.
 *
 * @param startX upstream X coordinate
 * @param startZ upstream Z coordinate
 * @param endX downstream X coordinate
 * @param endZ downstream Z coordinate
 * @param startHeight upstream eroded terrain height
 * @param endHeight downstream eroded terrain height
 * @param flow accumulated drainage weight at the upstream node
 * @param width full channel width in blocks
 * @param depth maximum centerline incision depth in blocks
 */
public record RiverSegment(
        int startX,
        int startZ,
        int endX,
        int endZ,
        double startHeight,
        double endHeight,
        double flow,
        double width,
        double depth) {

    /** Vertical inset of the water surface below the pre-incision channel banks. */
    private static final double WATER_SURFACE_INSET = 0.35D;

    /**
     * Returns the shortest horizontal distance from a world point to this segment.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return horizontal distance in blocks
     */
    public double distanceTo(double x, double z) {
        return projection(x, z).distance();
    }

    /**
     * Samples the segment at the closest projected point.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return closest-point hydrology hit
     */
    public RiverHit hit(double x, double z) {
        Projection projection = projection(x, z);
        double normalized = Maths.clamp(projection.distance() / Math.max(0.5D, width * 0.5D), 0.0D, 1.0D);
        double channel = 1.0D - Maths.smooth(normalized);
        double bedDepth = depth * channel;
        double surfaceHeight = Maths.lerp(startHeight, endHeight, projection.alpha());
        double waterSurfaceHeight = surfaceHeight - WATER_SURFACE_INSET;
        return new RiverHit(
                projection.distance(),
                width,
                bedDepth,
                surfaceHeight,
                waterSurfaceHeight,
                flow);
    }

    private Projection projection(double x, double z) {
        double dx = endX - startX;
        double dz = endZ - startZ;
        double lengthSquared = dx * dx + dz * dz;
        double alpha;
        if (lengthSquared <= 1.0E-12D) {
            alpha = 0.0D;
        } else {
            alpha = Maths.clamp(((x - startX) * dx + (z - startZ) * dz) / lengthSquared, 0.0D, 1.0D);
        }
        double closestX = startX + dx * alpha;
        double closestZ = startZ + dz * alpha;
        return new Projection(alpha, Math.hypot(x - closestX, z - closestZ));
    }

    private record Projection(double alpha, double distance) {
    }
}
