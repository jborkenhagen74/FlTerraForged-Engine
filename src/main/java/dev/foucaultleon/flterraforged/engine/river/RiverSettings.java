package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import java.util.Objects;

/**
 * Immutable settings for coarse drainage analysis and river incision.
 *
 * @param regionSize map-region size in blocks; must be divisible by {@code gridSpacing}
 * @param gridSpacing spacing between drainage nodes in blocks
 * @param paddingCells number of drainage-node rings generated beyond a map region
 * @param minimumFlow minimum accumulated drainage-node count required to create a river segment
 * @param minimumWidth minimum full river width in blocks
 * @param maximumWidth maximum full river width in blocks
 * @param maximumDepth maximum river-bed incision in blocks
 * @param widthGrowth width increase applied as flow accumulation grows
 * @param depthGrowth depth increase applied as flow accumulation grows
 * @param cacheSize maximum number of completed immutable rivermaps cached by a river model
 */
public record RiverSettings(
        int regionSize,
        int gridSpacing,
        int paddingCells,
        double minimumFlow,
        double minimumWidth,
        double maximumWidth,
        double maximumDepth,
        double widthGrowth,
        double depthGrowth,
        int cacheSize) {

    /**
     * Creates validated river settings.
     *
     * @param regionSize map-region size in blocks
     * @param gridSpacing drainage-grid spacing in blocks
     * @param paddingCells drainage padding in node cells
     * @param minimumFlow minimum accumulated flow
     * @param minimumWidth minimum full river width
     * @param maximumWidth maximum full river width
     * @param maximumDepth maximum incision depth
     * @param widthGrowth flow-to-width growth factor
     * @param depthGrowth flow-to-depth growth factor
     * @param cacheSize completed-map cache size
     */
    public RiverSettings {
        if (regionSize <= 0 || gridSpacing <= 0 || regionSize % gridSpacing != 0) {
            throw new IllegalArgumentException("River regionSize must be positive and divisible by gridSpacing");
        }
        if (paddingCells < 2) {
            throw new IllegalArgumentException("River paddingCells must be >= 2");
        }
        if (!(minimumFlow > 0.0D)) {
            throw new IllegalArgumentException("River minimumFlow must be > 0");
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
        if (cacheSize < 1) {
            throw new IllegalArgumentException("River cacheSize must be >= 1");
        }
    }

    /**
     * Derives the hydrology settings from the public engine settings.
     *
     * <p>The legacy {@code riverScale} setting remains meaningful by controlling drainage-grid
     * density around the historical default. Larger values create a denser network, while smaller
     * values increase drainage-node spacing.</p>
     *
     * @param settings engine settings
     * @return derived river settings
     */
    public static RiverSettings from(EngineSettings settings) {
        Objects.requireNonNull(settings, "settings");
        double density = Math.sqrt(settings.riverScale() / 0.00110D);
        int spacing = clampMultiple((int) Math.round(24.0D / Math.max(0.25D, density)), 12, 48, 4);
        int region = spacing * 8;
        return new RiverSettings(
                region,
                spacing,
                6,
                4.0D,
                3.0D,
                14.0D,
                settings.riverDepth(),
                1.55D,
                1.10D,
                48);
    }

    private static int clampMultiple(int value, int min, int max, int multiple) {
        int clamped = Math.max(min, Math.min(max, value));
        return Math.max(min, Math.min(max, Math.round((float) clamped / multiple) * multiple));
    }
}
