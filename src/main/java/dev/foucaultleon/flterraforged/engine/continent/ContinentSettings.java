package dev.foucaultleon.flterraforged.engine.continent;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import java.util.Objects;

/**
 * Internal settings for Voronoi-style continent partitioning.
 *
 * @param cellSize base tectonic cell size in blocks
 * @param jitter fractional displacement of each cell point
 * @param skipping probability-like threshold for suppressing non-origin cells
 * @param sizeVariance maximum cell-size modulation
 * @param warpStrength coordinate-warp strength in blocks
 * @param coastRoughness strength of small-scale coastline modulation
 */
public record ContinentSettings(
        double cellSize,
        double jitter,
        double skipping,
        double sizeVariance,
        double warpStrength,
        double coastRoughness) {

    /**
     * Validates continent settings.
     *
     * @param cellSize base tectonic cell size in blocks
     * @param jitter fractional cell-point displacement
     * @param skipping cell-skipping threshold
     * @param sizeVariance cell-size variance
     * @param warpStrength warp strength in blocks
     * @param coastRoughness coastline modulation strength
     */
    public ContinentSettings {
        if (!Double.isFinite(cellSize) || cellSize <= 0.0D) {
            throw new IllegalArgumentException("cellSize must be > 0");
        }
        checkUnit("jitter", jitter);
        checkUnit("skipping", skipping);
        checkUnit("sizeVariance", sizeVariance);
        if (!Double.isFinite(warpStrength) || warpStrength < 0.0D) {
            throw new IllegalArgumentException("warpStrength must be >= 0");
        }
        checkUnit("coastRoughness", coastRoughness);
    }

    /**
     * Creates continent settings from the engine-wide configuration.
     *
     * @param settings engine settings
     * @return continent settings
     */
    public static ContinentSettings from(EngineSettings settings) {
        Objects.requireNonNull(settings, "settings");
        double baseScale = 1.0D / settings.continentScale();
        double cellSize = Math.max(512.0D, baseScale * 4.0D);
        return new ContinentSettings(
                cellSize,
                settings.continentJitter(),
                settings.continentSkipping(),
                settings.continentSizeVariance(),
                cellSize * settings.continentWarpStrength(),
                settings.continentCoastRoughness());
    }

    private static void checkUnit(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
