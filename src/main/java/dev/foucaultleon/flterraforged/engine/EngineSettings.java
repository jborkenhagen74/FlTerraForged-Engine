package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import java.util.Objects;

/**
 * Parsed and validated engine configuration shared by all world-generation stages.
 *
 * <p>The configuration deliberately uses a compact set of cross-stage controls. More detailed
 * hydraulic, drainage and climate constants are derived by their respective stage settings so the
 * public engine configuration does not expose implementation noise.</p>
 *
 * @param continentScale base spatial frequency used to derive tectonic continent-cell size
 * @param continentJitter fractional displacement of tectonic continent points
 * @param continentSkipping threshold for suppressing non-origin continent cells
 * @param continentSizeVariance maximum continent-cell size variance
 * @param continentWarpStrength continent warp strength relative to tectonic cell size
 * @param continentCoastRoughness normalized coastline modulation strength
 * @param terrainScale spatial scale of broad terrain-shape noise
 * @param detailScale spatial scale of small terrain detail
 * @param terrainRegionScale spatial frequency of terrain-region partitioning
 * @param terrainRegionJitter fractional displacement of terrain-region points
 * @param terrainBlendWidth normalized blend width around terrain-region boundaries
 * @param climateScale spatial scale of broad temperature and moisture fields
 * @param climateRegionScale spatial frequency of macro climate-region partitioning
 * @param climateRegionJitter fractional displacement of climate-region points
 * @param climateRegionBlend normalized climate-region boundary blend width
 * @param climateAltitudeCooling temperature reduction per 256 blocks above sea level
 * @param climateContinentalDryness moisture reduction toward continental interiors
 * @param climateRiverMoisture moisture bonus at river centerlines
 * @param climateOceanModeration coastal temperature moderation strength
 * @param riverScale hydrology density scale used to derive drainage-grid spacing
 * @param relief base continental relief in blocks
 * @param mountainRelief additional mountain relief in blocks
 * @param riverDepth maximum drainage-network river incision depth in blocks
 * @param erosionStrength hydraulic erosion strength multiplier
 * @param erosionDeposition hydraulic deposition strength multiplier
 * @param thermalErosionStrength thermal slope-relaxation strength
 * @param erosionMaxDelta maximum absolute erosion/deposition height change in blocks
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
        double terrainRegionScale,
        double terrainRegionJitter,
        double terrainBlendWidth,
        double climateScale,
        double climateRegionScale,
        double climateRegionJitter,
        double climateRegionBlend,
        double climateAltitudeCooling,
        double climateContinentalDryness,
        double climateRiverMoisture,
        double climateOceanModeration,
        double riverScale,
        double relief,
        double mountainRelief,
        double riverDepth,
        double erosionStrength,
        double erosionDeposition,
        double thermalErosionStrength,
        double erosionMaxDelta) {

    /**
     * Returns the balanced default settings.
     *
     * @return balanced settings
     */
    public static EngineSettings defaults() {
        return preset(EnginePreset.BALANCED);
    }

    /**
     * Returns the tuned settings for a built-in preset.
     *
     * @param preset requested preset
     * @return preset settings
     */
    public static EngineSettings preset(EnginePreset preset) {
        Objects.requireNonNull(preset, "preset");
        return switch (preset) {
            case BALANCED -> new EngineSettings(
                    0.00050D, 0.82D, 0.0D, 0.20D, 0.27D, 0.24D,
                    0.00145D, 0.00650D, 0.00055D, 0.78D, 0.36D,
                    0.00062D, 0.00018D, 0.82D, 0.42D,
                    0.16D, 0.42D, 0.20D, 0.48D,
                    0.00095D, 34.0D, 56.0D, 6.5D,
                    0.62D, 0.52D, 0.14D, 4.5D);
            case GENTLE -> new EngineSettings(
                    0.00048D, 0.75D, 0.0D, 0.15D, 0.20D, 0.18D,
                    0.00120D, 0.00500D, 0.00042D, 0.72D, 0.44D,
                    0.00058D, 0.00016D, 0.76D, 0.48D,
                    0.14D, 0.38D, 0.18D, 0.52D,
                    0.00080D, 24.0D, 36.0D, 5.0D,
                    0.44D, 0.50D, 0.10D, 3.5D);
            case RUGGED -> new EngineSettings(
                    0.00058D, 0.88D, 0.0D, 0.28D, 0.35D, 0.34D,
                    0.00180D, 0.00900D, 0.00072D, 0.88D, 0.30D,
                    0.00075D, 0.00022D, 0.88D, 0.34D,
                    0.19D, 0.48D, 0.24D, 0.42D,
                    0.00125D, 42.0D, 72.0D, 8.5D,
                    0.82D, 0.58D, 0.18D, 6.0D);
        };
    }

    /**
     * Parses settings from the generic engine configuration.
     *
     * <p>The optional {@code preset} key selects the base profile first; explicit numeric keys then
     * override individual values. This makes presets convenient without preventing precise tuning.</p>
     *
     * @param config source configuration
     * @return validated settings
     * @throws IllegalArgumentException if a configured value is invalid
     */
    public static EngineSettings from(EngineConfig config) {
        Objects.requireNonNull(config, "config");
        EnginePreset selected = EnginePreset.parse(config.getOrDefault("preset", "balanced"));
        EngineSettings d = preset(selected);
        return new EngineSettings(
                positive(config, "continentScale", d.continentScale),
                unit(config, "continentJitter", d.continentJitter),
                unit(config, "continentSkipping", d.continentSkipping),
                unit(config, "continentSizeVariance", d.continentSizeVariance),
                unit(config, "continentWarpStrength", d.continentWarpStrength),
                unit(config, "continentCoastRoughness", d.continentCoastRoughness),
                positive(config, "terrainScale", d.terrainScale),
                positive(config, "detailScale", d.detailScale),
                positive(config, "terrainRegionScale", d.terrainRegionScale),
                unit(config, "terrainRegionJitter", d.terrainRegionJitter),
                positiveUnit(config, "terrainBlendWidth", d.terrainBlendWidth),
                positive(config, "climateScale", d.climateScale),
                positive(config, "climateRegionScale", d.climateRegionScale),
                unit(config, "climateRegionJitter", d.climateRegionJitter),
                positiveUnit(config, "climateRegionBlend", d.climateRegionBlend),
                nonNegative(config, "climateAltitudeCooling", d.climateAltitudeCooling),
                unit(config, "climateContinentalDryness", d.climateContinentalDryness),
                unit(config, "climateRiverMoisture", d.climateRiverMoisture),
                unit(config, "climateOceanModeration", d.climateOceanModeration),
                positive(config, "riverScale", d.riverScale),
                positive(config, "relief", d.relief),
                positive(config, "mountainRelief", d.mountainRelief),
                positive(config, "riverDepth", d.riverDepth),
                nonNegative(config, "erosionStrength", d.erosionStrength),
                nonNegative(config, "erosionDeposition", d.erosionDeposition),
                unit(config, "thermalErosionStrength", d.thermalErosionStrength),
                positive(config, "erosionMaxDelta", d.erosionMaxDelta));
    }

    private static double positive(EngineConfig config, String key, double fallback) {
        double parsed = number(config, key, fallback);
        if (parsed <= 0.0D) {
            throw new IllegalArgumentException("Engine config '" + key + "' must be > 0");
        }
        return parsed;
    }

    private static double nonNegative(EngineConfig config, String key, double fallback) {
        double parsed = number(config, key, fallback);
        if (parsed < 0.0D) {
            throw new IllegalArgumentException("Engine config '" + key + "' must be >= 0");
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

    private static double positiveUnit(EngineConfig config, String key, double fallback) {
        double parsed = number(config, key, fallback);
        if (parsed <= 0.0D || parsed > 1.0D) {
            throw new IllegalArgumentException("Engine config '" + key + "' must be in (0, 1]");
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
