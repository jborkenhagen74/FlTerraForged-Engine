package dev.foucaultleon.flterraforged.engine.terrain.region;

/**
 * Immutable multi-region terrain blend around one world position.
 *
 * <p>The owning terrain is represented by {@link #primary()}. The compact influence arrays contain
 * every nearby Voronoi region whose contribution is non-zero at this position. Keeping all active
 * neighbors avoids secondary-neighbor switching seams at triple and higher-order junctions.</p>
 */
public final class TerrainRegionBlendSample {

    private final TerrainRegionSample primary;
    private final double[] ids;
    private final double[] weights;
    private final int size;

    TerrainRegionBlendSample(
            TerrainRegionSample primary,
            double[] ids,
            double[] weights,
            int size) {
        if (primary == null) {
            throw new NullPointerException("primary");
        }
        if (ids == null || weights == null) {
            throw new NullPointerException("terrain blend arrays");
        }
        if (ids.length != weights.length || size < 1 || size > ids.length) {
            throw new IllegalArgumentException("terrain blend arrays and size are inconsistent");
        }
        this.primary = primary;
        this.ids = ids;
        this.weights = weights;
        this.size = size;
    }

    /**
     * Returns the ordinary owner/nearest-neighbor region sample.
     *
     * @return primary region sample
     */
    public TerrainRegionSample primary() {
        return primary;
    }

    /**
     * Returns the number of active terrain influences.
     *
     * @return active influence count
     */
    public int size() {
        return size;
    }

    /**
     * Returns a terrain selector by active influence index.
     *
     * @param index influence index
     * @return selector in {@code [0, 1]}
     */
    public double id(int index) {
        checkIndex(index);
        return ids[index];
    }

    /**
     * Returns a normalized terrain weight by active influence index.
     *
     * @param index influence index
     * @return weight in {@code (0, 1]}
     */
    public double weight(int index) {
        checkIndex(index);
        return weights[index];
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index);
        }
    }
}
