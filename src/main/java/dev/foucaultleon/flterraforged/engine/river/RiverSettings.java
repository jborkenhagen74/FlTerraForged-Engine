package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import java.util.Objects;

/**
 * Immutable settings for drainage routing, river geometry and inland-water formation.
 *
 * @param regionSize map-region size in blocks; must be divisible by {@code gridSpacing}
 * @param gridSpacing spacing between coarse drainage nodes in blocks
 * @param paddingCells number of drainage-node rings generated beyond a map region
 * @param minimumFlow accumulated drainage required for a normal visible channel
 * @param headwaterFlow minimum accumulated drainage allowed immediately upstream of a normal channel
 * @param minimumWidth minimum full channel width in blocks
 * @param maximumWidth maximum full channel width in blocks
 * @param maximumDepth maximum channel incision in blocks
 * @param widthGrowth width increase applied as flow accumulation grows
 * @param depthGrowth depth increase applied as flow accumulation grows
 * @param minimumWaterDepth minimum centerline water depth for a materialized stream
 * @param maximumWaterDepth maximum target centerline water depth for large rivers
 * @param bankFreeboard minimum vertical bank height retained above the river-water surface
 * @param meanderStrength maximum terrain-guided lateral path displacement as a fraction of grid spacing
 * @param pathSamples number of points used to refine each coarse D8 edge into a visible path
 * @param lakeMinimumDepth minimum depression-fill depth that may become inland water
 * @param lakeShoreBlend depth interval used to soften irregular lake shores
 * @param cacheSize maximum number of completed immutable rivermaps cached by a river model
 */
public record RiverSettings(
        int regionSize,
        int gridSpacing,
        int paddingCells,
        double minimumFlow,
        double headwaterFlow,
        double minimumWidth,
        double maximumWidth,
        double maximumDepth,
        double widthGrowth,
        double depthGrowth,
        double minimumWaterDepth,
        double maximumWaterDepth,
        double bankFreeboard,
        double meanderStrength,
        int pathSamples,
        double lakeMinimumDepth,
        double lakeShoreBlend,
        int cacheSize) {

    /**
     * Validates river settings.
     *
     * @param regionSize map-region size in blocks; must be divisible by {@code gridSpacing}
     * @param gridSpacing spacing between coarse drainage nodes in blocks
     * @param paddingCells number of drainage-node rings generated beyond a map region
     * @param minimumFlow accumulated drainage required for a normal visible channel
     * @param headwaterFlow minimum accumulated drainage allowed immediately upstream of a normal channel
     * @param minimumWidth minimum full channel width in blocks
     * @param maximumWidth maximum full channel width in blocks
     * @param maximumDepth maximum channel incision in blocks
     * @param widthGrowth width increase applied as flow accumulation grows
     * @param depthGrowth depth increase applied as flow accumulation grows
     * @param minimumWaterDepth minimum centerline water depth for a materialized stream
     * @param maximumWaterDepth maximum target centerline water depth for large rivers
     * @param bankFreeboard minimum vertical bank height retained above the river-water surface
     * @param meanderStrength maximum terrain-guided lateral path displacement as a fraction of grid spacing
     * @param pathSamples number of points used to refine each coarse D8 edge into a visible path
     * @param lakeMinimumDepth minimum depression-fill depth that may become inland water
     * @param lakeShoreBlend depth interval used to soften irregular lake shores
     * @param cacheSize maximum number of completed immutable rivermaps cached by a river model
     */
    public RiverSettings {
        if (regionSize <= 0 || gridSpacing <= 0 || regionSize % gridSpacing != 0) {
            throw new IllegalArgumentException("River regionSize must be positive and divisible by gridSpacing");
        }
        if (paddingCells < 4) {
            throw new IllegalArgumentException("River paddingCells must be >= 4");
        }
        if (!(minimumFlow > 0.0D) || !(headwaterFlow > 0.0D) || headwaterFlow > minimumFlow) {
            throw new IllegalArgumentException("River flow thresholds are invalid");
        }
        if (!(minimumWidth > 0.0D) || maximumWidth < minimumWidth) {
            throw new IllegalArgumentException("River width range is invalid");
        }
        if (!(maximumDepth > 0.0D)) {
            throw new IllegalArgumentException("River maximumDepth must be > 0");
        }
        if (widthGrowth < 0.0D || depthGrowth < 0.0D) {
            throw new IllegalArgumentException("River growth factors must be >= 0");
        }
        if (!(minimumWaterDepth > 0.0D) || maximumWaterDepth < minimumWaterDepth) {
            throw new IllegalArgumentException("River water-depth range is invalid");
        }
        if (!(bankFreeboard > 0.0D) || bankFreeboard >= maximumDepth) {
            throw new IllegalArgumentException("River bankFreeboard must be > 0 and below maximumDepth");
        }
        if (!Double.isFinite(meanderStrength) || meanderStrength < 0.0D || meanderStrength > 0.75D) {
            throw new IllegalArgumentException("River meanderStrength must be in [0, 0.75]");
        }
        if (pathSamples < 4 || pathSamples > 17) {
            throw new IllegalArgumentException("River pathSamples must be in [4, 17]");
        }
        if (!(lakeMinimumDepth > 0.0D) || !(lakeShoreBlend > 0.0D)) {
            throw new IllegalArgumentException("Lake depth and shore blend must be > 0");
        }
        if (cacheSize < 1) {
            throw new IllegalArgumentException("River cacheSize must be >= 1");
        }
    }

    /**
     * Derives hydrology settings from the public engine settings.
     *
     * <p>The visible path is intentionally finer than the old bootstrap network. D8 remains the
     * drainage skeleton, while the path sampler resolves each coarse edge against the local terrain
     * before it is exposed as river geometry.</p>
     *
     * @param settings engine settings
     * @return derived river settings
     */
    public static RiverSettings from(EngineSettings settings) {
        Objects.requireNonNull(settings, "settings");
        double density = Math.sqrt(settings.riverScale() / 0.00110D);
        int spacing = clampMultiple((int) Math.round(22.0D / Math.max(0.25D, density)), 16, 36, 4);
        int region = spacing * 20;
        double maximumWaterDepth = Math.max(2.25D, Math.min(4.75D, settings.riverDepth() * 0.62D));
        return new RiverSettings(
                region,
                spacing,
                16,
                5.5D,
                3.5D,
                2.0D,
                16.0D,
                settings.riverDepth(),
                1.35D,
                1.05D,
                1.35D,
                maximumWaterDepth,
                0.45D,
                0.48D,
                7,
                0.85D,
                1.35D,
                32);
    }

    private static int clampMultiple(int value, int min, int max, int multiple) {
        int clamped = Math.max(min, Math.min(max, value));
        return Math.max(min, Math.min(max, Math.round((float) clamped / multiple) * multiple));
    }
}
