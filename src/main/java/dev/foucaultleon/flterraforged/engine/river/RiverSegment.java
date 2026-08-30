package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.List;
import java.util.Objects;

/**
 * Immutable directed river segment joining two drainage nodes through a refined visible path.
 *
 * @param startX upstream coarse-node X coordinate
 * @param startZ upstream coarse-node Z coordinate
 * @param endX downstream coarse-node X coordinate
 * @param endZ downstream coarse-node Z coordinate
 * @param startHeight upstream eroded terrain height
 * @param endHeight downstream eroded terrain height
 * @param startWaterHeight upstream hydrologic water surface
 * @param endWaterHeight downstream hydrologic water surface
 * @param flow accumulated drainage weight at the upstream node
 * @param width full channel width in blocks
 * @param depth maximum centerline incision depth in blocks
 * @param path terrain-refined visible centerline including both endpoints
 */
public record RiverSegment(
        int startX,
        int startZ,
        int endX,
        int endZ,
        double startHeight,
        double endHeight,
        double startWaterHeight,
        double endWaterHeight,
        double flow,
        double width,
        double depth,
        List<RiverPathPoint> path) {

    /**
     * Copies and validates the visible path.
     *
     * @param startX upstream coarse-node X coordinate
     * @param startZ upstream coarse-node Z coordinate
     * @param endX downstream coarse-node X coordinate
     * @param endZ downstream coarse-node Z coordinate
     * @param startHeight upstream eroded terrain height
     * @param endHeight downstream eroded terrain height
     * @param startWaterHeight upstream hydrologic water surface
     * @param endWaterHeight downstream hydrologic water surface
     * @param flow accumulated drainage weight at the upstream node
     * @param width full channel width in blocks
     * @param depth maximum centerline incision depth in blocks
     * @param path terrain-refined visible centerline including both endpoints
     */
    public RiverSegment {
        Objects.requireNonNull(path, "path");
        path = List.copyOf(path);
        if (path.size() < 2) {
            throw new IllegalArgumentException("River path must contain at least two points");
        }
        if (endWaterHeight > startWaterHeight + 1.0E-7D) {
            throw new IllegalArgumentException("River water surface must not rise downstream");
        }
    }

    /**
     * Returns the shortest horizontal distance from a world point to the refined path.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return horizontal distance in blocks
     */
    public double distanceTo(double x, double z) {
        return projection(x, z).distance();
    }

    /**
     * Samples the segment at the closest projected point on the refined path.
     *
     * <p>The channel has a gently flattened wet core before it rises toward the banks. This avoids
     * one-block-thin wet centerlines and guarantees the configured hydrologic water surface has a
     * stable channel to occupy.</p>
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return closest-point hydrology hit
     */
    public RiverHit hit(double x, double z) {
        Projection projection = projection(x, z);
        double halfWidth = Math.max(0.5D, width * 0.5D);
        double normalized = Maths.clamp(projection.distance() / halfWidth, 0.0D, 1.0D);
        double bankAlpha = Maths.clamp((normalized - 0.28D) / 0.72D, 0.0D, 1.0D);
        double channel = 1.0D - Maths.smooth(bankAlpha);
        double bedDepth = depth * channel;
        double surfaceHeight = Maths.lerp(startHeight, endHeight, projection.alpha());
        double waterSurfaceHeight = Maths.lerp(startWaterHeight, endWaterHeight, projection.alpha());
        return new RiverHit(
                projection.distance(),
                width,
                bedDepth,
                surfaceHeight,
                waterSurfaceHeight,
                flow,
                false);
    }

    private Projection projection(double x, double z) {
        Projection nearest = new Projection(0.0D, Double.POSITIVE_INFINITY);
        int segments = path.size() - 1;
        for (int index = 0; index < segments; index++) {
            RiverPathPoint start = path.get(index);
            RiverPathPoint end = path.get(index + 1);
            double dx = end.x() - start.x();
            double dz = end.z() - start.z();
            double lengthSquared = dx * dx + dz * dz;
            double localAlpha;
            if (lengthSquared <= 1.0E-12D) {
                localAlpha = 0.0D;
            } else {
                localAlpha = Maths.clamp(
                        ((x - start.x()) * dx + (z - start.z()) * dz) / lengthSquared,
                        0.0D,
                        1.0D);
            }
            double closestX = start.x() + dx * localAlpha;
            double closestZ = start.z() + dz * localAlpha;
            double distance = Math.hypot(x - closestX, z - closestZ);
            if (distance < nearest.distance()) {
                double globalAlpha = (index + localAlpha) / segments;
                nearest = new Projection(globalAlpha, distance);
            }
        }
        return nearest;
    }

    private record Projection(double alpha, double distance) {
    }
}
