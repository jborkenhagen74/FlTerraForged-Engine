package dev.foucaultleon.flterraforged.engine.continent;

import java.util.Objects;

/**
 * Mutable caller-owned workspace used while resolving one advanced continent Voronoi cell.
 *
 * <p>This is deliberately different from the general engine
 * {@link dev.foucaultleon.flterraforged.engine.cell.Cell Cell}. A {@code ContinentCell} only holds
 * geometric intermediate state needed to identify a continent owner and its boundary. The general
 * engine cell is the cross-stage carrier that later receives the finished continent signals along
 * with terrain, erosion, rivers and climate data.</p>
 *
 * <p>Instances contain no shared global state and may be reused by a caller via {@link #reset()}.
 * They must not be shared concurrently between sampling calls.</p>
 */
public final class ContinentCell {

    private double sampleX;
    private double sampleZ;
    private ContinentPoint owner;
    private ContinentPoint nearestNeighbor;
    private double ownerDistanceSquared;
    private double borderDistanceSquared;
    private double neighborAverageX;
    private double neighborAverageZ;
    private int neighborCount;
    private boolean skipped;

    /** Creates an empty reusable continent workspace. */
    public ContinentCell() {
        reset();
    }

    /**
     * Clears this workspace.
     *
     * @return this cell
     */
    public ContinentCell reset() {
        sampleX = 0.0D;
        sampleZ = 0.0D;
        owner = null;
        nearestNeighbor = null;
        ownerDistanceSquared = Double.POSITIVE_INFINITY;
        borderDistanceSquared = Double.POSITIVE_INFINITY;
        neighborAverageX = 0.0D;
        neighborAverageZ = 0.0D;
        neighborCount = 0;
        skipped = false;
        return this;
    }

    /**
     * Returns the warped sample X coordinate.
     *
     * @return warped sample X coordinate in normalized continent-cell space
     */
    public double sampleX() {
        return sampleX;
    }

    /**
     * Returns the warped sample Z coordinate.
     *
     * @return warped sample Z coordinate in normalized continent-cell space
     */
    public double sampleZ() {
        return sampleZ;
    }

    /**
     * Returns the owning jittered point.
     *
     * @return nearest jittered point that owns this sample
     */
    public ContinentPoint owner() {
        return owner;
    }

    /**
     * Returns the neighbor forming the nearest boundary.
     *
     * @return neighboring point whose bisector is nearest to this sample
     */
    public ContinentPoint nearestNeighbor() {
        return nearestNeighbor;
    }

    /**
     * Returns the squared owner distance.
     *
     * @return squared distance from the sample to its owning jittered point
     */
    public double ownerDistanceSquared() {
        return ownerDistanceSquared;
    }

    /**
     * Returns the squared distance to the nearest Voronoi boundary.
     *
     * @return squared perpendicular distance to the nearest Voronoi boundary
     */
    public double borderDistanceSquared() {
        return borderDistanceSquared;
    }

    /**
     * Returns the mean neighbor X coordinate.
     *
     * @return average X coordinate of considered neighboring jittered points
     */
    public double neighborAverageX() {
        return neighborCount == 0 ? (owner == null ? 0.0D : owner.x()) : neighborAverageX / neighborCount;
    }

    /**
     * Returns the mean neighbor Z coordinate.
     *
     * @return average Z coordinate of considered neighboring jittered points
     */
    public double neighborAverageZ() {
        return neighborCount == 0 ? (owner == null ? 0.0D : owner.z()) : neighborAverageZ / neighborCount;
    }

    /**
     * Returns the number of considered neighbors.
     *
     * @return number of neighboring points considered around the owner
     */
    public int neighborCount() {
        return neighborCount;
    }

    /**
     * Reports whether the cell is skipped.
     *
     * @return whether this tectonic cell was suppressed by continent-skipping rules
     */
    public boolean skipped() {
        return skipped;
    }

    void setSample(double x, double z) {
        sampleX = x;
        sampleZ = z;
    }

    void considerOwner(ContinentPoint point, double distanceSquared) {
        Objects.requireNonNull(point, "point");
        if (distanceSquared < ownerDistanceSquared) {
            owner = point;
            ownerDistanceSquared = distanceSquared;
        }
    }

    void considerNeighbor(ContinentPoint point, double boundaryDistanceSquared) {
        Objects.requireNonNull(point, "point");
        neighborAverageX += point.x();
        neighborAverageZ += point.z();
        neighborCount++;
        if (boundaryDistanceSquared < borderDistanceSquared) {
            nearestNeighbor = point;
            borderDistanceSquared = boundaryDistanceSquared;
        }
    }

    void setSkipped(boolean value) {
        skipped = value;
    }
}
