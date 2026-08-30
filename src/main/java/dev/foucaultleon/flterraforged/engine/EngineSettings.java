package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import java.util.Objects;

/**
 * Parsed and validated engine configuration.
 *
 * @param continentScale base spatial frequency used to derive tectonic continent-cell size
 * @param continentJitter fractional displacement of tectonic continent points
 * @param continentSkipping threshold for suppressing non-origin continent cells
 * @param continentSizeVariance maximum continent-cell size variance
 * @param continentWarpStrength continent warp strength relative to tectonic cell size
 * @param continentCoastRoughness normalized coastline modulation strength
 * @param terrainScale spatial scale of ridge and terrain noise
 * @param detailScale spatial scale of small terrain detail
 * @param climateScale spatial scale of temperature and moisture fields
 * @param riverScale spatial scale of the procedural river field
 * @param relief base continental relief in blocks
 * @param mountainRelief additional mountain relief in blocks
 * @param riverDepth maximum procedural river incision depth in blocks
 */
public record EngineSettings(
        double continentScale,
        double continentJitter,
        double continentSkipping,
        double continentSizeVariance,
        double continentWarpStrength,
        double continentCoastRoughness,
        double terrainScale,
        double detailScale,
        double climateScale,
        double riverScale,
        double relief,
        double mountainRelief,
        double riverDepth) {

    /**
     * Returns the default engine settings.
     *
     * @return default settings
     */
    public static EngineSettings defaults() {
        return new EngineSettings(
                0.00055D,
                0.85D,
                0.0D,
                0.25D,
                0.33D,
                0.30D,
                0.00170D,
                0.00800D,
                0.00080D,
                0.00110D,
                36.0D,
                52.0D,
                7.0D);
    }

    /**
     * Parses engine settings from the generic engine configuration.
     *
     * @param config source configuration
     * @return validated settings
     * @throws IllegalArgumentException if a configured numeric value is invalid
     */
    public static EngineSettings from(EngineConfig config) {
        Objects.requireNonNull(config, "config");
        EngineSettings d = defaults();
        return new EngineSettings(
                positive(config, "continentScale", d.continentScale),
                unit(config, "continentJitter", d.continentJitter),
                unit(config, "continentSkipping", d.continentSkipping),
                unit(config, "continentSizeVariance", d.continentSizeVariance),
                unit(config, "continentWarpStrength", d.continentWarpStrength),
                unit(config, "continentCoastRoughness", d.continentCoastRoughness),
                positive(config, "terrainScale", d.terrainScale),
                positive(config, "detailScale", d.detailScale),
                positive(config, "climateScale", d.climateScale),
                positive(config, "riverScale", d.riverScale),
                positive(config, "relief", d.relief),
                positive(config, "mountainRelief", d.mountainRelief),
                positive(config, "riverDepth", d.riverDepth));
    }

    private static double positive(EngineConfig config, String key, double fallback) {
        double parsed = number(config, key, fallback);
        if (parsed <= 0.0D) {
            throw new IllegalArgumentException("Engine config '" + key + "' must be > 0");
        }
        return parsed;
    }

    private static double unit(EngineConfig config, String key, double fallback) {
        double parsed = number(config, key, fallback);
        if (parsed < 0.0D || parsed > 1.0D) {
            throw new IllegalArgumentException("Engine config '" + key + "' must be in [0, 1]");
        }
        return parsed;
    }

    private static double number(EngineConfig config, String key, double fallback) {
        String value = config.get(key).orElse(null);
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException("Engine config '" + key + "' must be finite");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Engine config '" + key + "' is not a number: " + value, exception);
        }
    }
}
