package dev.foucaultleon.flterraforged.engine.river;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable river network and depression-filled inland-water field for one aligned hydrology region.
 *
 * <p>R45 precomputes one axis-aligned bound per refined segment. Final terrain sampling only needs
 * nearby channels, so the surface-aligned hot path rejects distant segments before invoking the
 * considerably more expensive polyline projection in {@link RiverSegment#hit(double, double)}.
 * This keeps the semantic river graph unchanged while preventing spawn generation from repeatedly
 * projecting every terrain sample against every segment in every warm hydrology map.</p>
 */
public final class Rivermap {

    private static final double MAXIMUM_LOCAL_RIVER_SEARCH = 96.0D;

    private final int regionX;
    private final int regionZ;
    private final List<RiverSegment> segments;
    private final LakeField lakes;
    private final List<IndexedSegment> indexedSegments;

    /**
     * Creates an immutable river map.
     *
     * @param regionX aligned river-region X index
     * @param regionZ aligned river-region Z index
     * @param segments directed terrain-refined channel segments whose upstream node belongs to this region
     * @param lakes depression-filled lake/pond field covering the padded hydrology region
     */
    public Rivermap(int regionX, int regionZ, List<RiverSegment> segments, LakeField lakes) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        this.lakes = Objects.requireNonNull(lakes, "lakes");
        List<IndexedSegment> bounds = new ArrayList<>(this.segments.size());
        for (RiverSegment segment : this.segments) {
            bounds.add(IndexedSegment.of(segment));
        }
        this.indexedSegments = List.copyOf(bounds);
    }

    /**
     * Returns the aligned river-region X index.
     *
     * @return river-region X index
     */
    public int regionX() {
        return regionX;
    }

    /**
     * Returns the aligned river-region Z index.
     *
     * @return river-region Z index
     */
    public int regionZ() {
        return regionZ;
    }

    /**
     * Returns the immutable directed channel segments owned by this map.
     *
     * @return immutable segment list
     */
    public List<RiverSegment> segments() {
        return segments;
    }

    /**
     * Returns the immutable padded lake field.
     *
     * @return lake field
     */
    public LakeField lakes() {
        return lakes;
    }

    /**
     * Finds the globally nearest river segment in this map.
     *
     * <p>This unbounded variant is retained for diagnostics and tests. Final terrain generation
     * uses {@link #nearestSurfaceAligned(double, double, double, double)}, whose bounded search is
     * the performance-sensitive path.</p>
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return nearest channel hit or {@link RiverHit#NONE}
     */
    public RiverHit nearest(double x, double z) {
        RiverHit nearest = RiverHit.NONE;
        for (RiverSegment segment : segments) {
            RiverHit candidate = segment.hit(x, z);
            if (candidate.distance() < nearest.distance()) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * Finds the nearest locally relevant channel whose hydraulic surface is vertically reachable.
     *
     * <p>Only channels whose refined path bounds can physically approach within 96 blocks are
     * projected. FlTerraForged's channel width and bank transitions are much smaller than this
     * guard radius, so farther channels cannot affect the sampled terrain column. A second pass is
     * restricted to the geometric winner plus {@code alternativeRange} and therefore no longer
     * repeats expensive projections across the complete map.</p>
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param terrainHeight pre-hydrology terrain height
     * @param alternativeRange maximum extra horizontal search range beyond the geometric winner
     * @return nearest surface-aligned channel hit or {@link RiverHit#NONE}
     */
    public RiverHit nearestSurfaceAligned(
            double x,
            double z,
            double terrainHeight,
            double alternativeRange) {
        RiverHit geometricNearest = nearestIndexed(x, z, MAXIMUM_LOCAL_RIVER_SEARCH);
        if (!geometricNearest.present()) {
            return RiverHit.NONE;
        }

        RiverHit nearest = geometricNearest;
        double maximumDistance = Math.min(
                MAXIMUM_LOCAL_RIVER_SEARCH,
                geometricNearest.distance() + Math.max(0.0D, alternativeRange));
        double nearestScore = geometricNearest.surfaceAlignmentScore(terrainHeight);
        for (IndexedSegment indexed : indexedSegments) {
            if (!indexed.mayReach(x, z, maximumDistance)) {
                continue;
            }
            RiverHit candidate = indexed.segment().hit(x, z);
            if (candidate.distance() > maximumDistance) {
                continue;
            }
            double candidateScore = candidate.surfaceAlignmentScore(terrainHeight);
            if (candidateScore < nearestScore) {
                nearest = candidate;
                nearestScore = candidateScore;
            }
        }
        return nearest;
    }

    /**
     * Samples inland depression water in this map.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return lake/pond hit or {@link LakeHit#NONE}
     */
    public LakeHit lake(double x, double z) {
        return lakes.sample(x, z);
    }

    private RiverHit nearestIndexed(double x, double z, double maximumDistance) {
        RiverHit nearest = RiverHit.NONE;
        for (IndexedSegment indexed : indexedSegments) {
            if (!indexed.mayReach(x, z, maximumDistance)) {
                continue;
            }
            RiverHit candidate = indexed.segment().hit(x, z);
            if (candidate.distance() <= maximumDistance
                    && candidate.distance() < nearest.distance()) {
                nearest = candidate;
            }
        }
        return nearest;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Rivermap that)) {
            return false;
        }
        return regionX == that.regionX
                && regionZ == that.regionZ
                && segments.equals(that.segments)
                && lakes.equals(that.lakes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regionX, regionZ, segments, lakes);
    }

    @Override
    public String toString() {
        return "Rivermap[regionX=" + regionX
                + ", regionZ=" + regionZ
                + ", segments=" + segments
                + ", lakes=" + lakes + ']';
    }

    private record IndexedSegment(
            RiverSegment segment,
            double minX,
            double minZ,
            double maxX,
            double maxZ) {

        static IndexedSegment of(RiverSegment segment) {
            Objects.requireNonNull(segment, "segment");
            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (RiverPathPoint point : segment.path()) {
                minX = Math.min(minX, point.x());
                minZ = Math.min(minZ, point.z());
                maxX = Math.max(maxX, point.x());
                maxZ = Math.max(maxZ, point.z());
            }
            return new IndexedSegment(segment, minX, minZ, maxX, maxZ);
        }

        boolean mayReach(double x, double z, double maximumDistance) {
            double dx = x < minX ? minX - x : x > maxX ? x - maxX : 0.0D;
            double dz = z < minZ ? minZ - z : z > maxZ ? z - maxZ : 0.0D;
            return dx * dx + dz * dz <= maximumDistance * maximumDistance;
        }
    }
}
