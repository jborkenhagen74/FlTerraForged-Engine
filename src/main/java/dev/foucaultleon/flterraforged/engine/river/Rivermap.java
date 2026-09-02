package dev.foucaultleon.flterraforged.engine.river;

import java.util.List;
import java.util.Objects;

/**
 * Immutable river network and depression-filled inland-water field for one aligned hydrology region.
 *
 * @param regionX aligned river-region X index
 * @param regionZ aligned river-region Z index
 * @param segments directed terrain-refined channel segments whose upstream node belongs to this region
 * @param lakes depression-filled lake/pond field covering the padded hydrology region
 */
public record Rivermap(int regionX, int regionZ, List<RiverSegment> segments, LakeField lakes) {

    /**
     * Creates an immutable river map.
     *
     * @param regionX aligned river-region X index
     * @param regionZ aligned river-region Z index
     * @param segments directed terrain-refined channel segments whose upstream node belongs to this region
     * @param lakes depression-filled lake/pond field covering the padded hydrology region
     */
    public Rivermap {
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(lakes, "lakes");
        segments = List.copyOf(segments);
    }

    /**
     * Finds the nearest river segment in this map.
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
     * Finds the nearest channel whose hydraulic surface is vertically reachable from the terrain.
     *
     * <p>This prevents a deeply buried, unrelated segment from winning by a fraction of a block at
     * a projected crossing while a slightly farther surface channel owns the visible bank.</p>
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
        RiverHit geometricNearest = nearest(x, z);
        RiverHit nearest = geometricNearest;
        double maximumDistance = geometricNearest.distance() + alternativeRange;
        double nearestScore = geometricNearest.surfaceAlignmentScore(terrainHeight);
        for (RiverSegment segment : segments) {
            RiverHit candidate = segment.hit(x, z);
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
}
