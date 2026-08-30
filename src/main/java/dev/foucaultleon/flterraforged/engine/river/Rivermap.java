package dev.foucaultleon.flterraforged.engine.river;

import java.util.List;
import java.util.Objects;

/**
 * Immutable river network for one aligned hydrology region.
 *
 * @param regionX aligned river-region X index
 * @param regionZ aligned river-region Z index
 * @param segments directed channel segments whose upstream node belongs to this region
 */
public record Rivermap(int regionX, int regionZ, List<RiverSegment> segments) {

    /**
     * Creates an immutable river map.
     *
     * @param regionX river-region X index
     * @param regionZ river-region Z index
     * @param segments river segments
     */
    public Rivermap {
        Objects.requireNonNull(segments, "segments");
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
}
