package dev.foucaultleon.flterraforged.engine.river;

/**
 * Internal sample of a depression-filled inland water body.
 *
 * @param influence normalized inland-water influence in {@code [0,1]}
 * @param waterSurfaceHeight continuous lake/pond water surface in world Y
 * @param minimumDepth desired minimum local water depth in blocks
 */
public record LakeHit(double influence, double waterSurfaceHeight, double minimumDepth) {

    /** Marker used outside inland-water basins. */
    public static final LakeHit NONE = new LakeHit(0.0D, Double.NaN, 0.0D);

    /**
     * Returns whether this sample lies inside a material inland-water basin.
     *
     * @return {@code true} when the sample can materialize water
     */
    public boolean present() {
        return influence > 0.0D && Double.isFinite(waterSurfaceHeight) && minimumDepth > 0.0D;
    }
}
