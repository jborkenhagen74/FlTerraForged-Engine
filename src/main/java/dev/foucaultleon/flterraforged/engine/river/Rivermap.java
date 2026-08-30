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

    /** Creates an immutable river map. */
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
