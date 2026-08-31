package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import dev.foucaultleon.flterraforged.engine.climate.ClimateLayout;
import java.util.Objects;

/**
 * Parsed and validated engine configuration shared by all world-generation stages.
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
 * @param terrainPlainsWeight relative selection weight of plains regions
 * @param terrainHillsWeight relative selection weight of hills regions
 * @param terrainValleyWeight relative selection weight of valley regions
 * @param terrainPlateauWeight relative selection weight of plateau regions
 * @param terrainMountainWeight relative selection weight of mountain regions
 * @param climateScale spatial scale of broad temperature and moisture fields
 * @param climateRegionScale spatial frequency of macro climate-region partitioning
 * @param climateRegionJitter fractional displacement of climate-region points
 * @param climateRegionBlend normalized climate-region boundary blend width
 * @param climateRegionalContrast contrast of seeded regional climate anchors around neutral climate
 * @param climateAltitudeCooling temperature reduction per 256 blocks above sea level
 * @param climateContinentalDryness moisture reduction toward continental interiors
 * @param climateRiverMoisture moisture bonus at river centerlines
 * @param climateOceanModeration coastal temperature moderation strength
 * @param climateLayout large-scale climate arrangement strategy
 * @param climateNorthSouthCenterZ world Z coordinate representing the middle of a north-south profile
 * @param climateNorthSouthSpan distance in blocks from the cold northern anchor to the warm southern anchor
 * @param climateNorthSouthStrength blend strength of the north-south baseline over randomized climate
 * @param climateNorthTemperature northern temperature anchor in {@code [0, 1]}
 * @param climateSouthTemperature southern temperature anchor in {@code [0, 1]}
 * @param climateNorthMoisture northern moisture anchor in {@code [0, 1]}
 * @param climateSouthMoisture southern moisture anchor in {@code [0, 1]}
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
        double terrainPlainsWeight,
        double terrainHillsWeight,
        double terrainValleyWeight,
        double terrainPlateauWeight,
        double terrainMountainWeight,
        double climateScale,
        double climateRegionScale,
        double climateRegionJitter,
        double climateRegionBlend,
        double climateRegionalContrast,
        double climateAltitudeCooling,
        double climateContinentalDryness,
        double climateRiverMoisture,
        double climateOceanModeration,
        ClimateLayout climateLayout,
        double climateNorthSouthCenterZ,
        double climateNorthSouthSpan,
        double climateNorthSouthStrength,
        double climateNorthTemperature,
        double climateSouthTemperature,
        double climateNorthMoisture,
        double climateSouthMoisture,
        double riverScale,
        double relief,
        double mountainRelief,
        double riverDepth,
        double erosionStrength,
        double erosionDeposition,
        double thermalErosionStrength,
        double erosionMaxDelta) {

    /**
     * Validates engine settings.
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
     * @param terrainPlainsWeight relative selection weight of plains regions
     * @param terrainHillsWeight relative selection weight of hills regions
     * @param terrainValleyWeight relative selection weight of valley regions
     * @param terrainPlateauWeight relative selection weight of plateau regions
     * @param terrainMountainWeight relative selection weight of mountain regions
     * @param climateScale spatial scale of broad temperature and moisture fields
     * @param climateRegionScale spatial frequency of macro climate-region partitioning
     * @param climateRegionJitter fractional displacement of climate-region points
     * @param climateRegionBlend normalized climate-region boundary blend width
     * @param climateRegionalContrast contrast of seeded regional climate anchors around neutral climate
     * @param climateAltitudeCooling temperature reduction per 256 blocks above sea level
     * @param climateContinentalDryness moisture reduction toward continental interiors
     * @param climateRiverMoisture moisture bonus at river centerlines
     * @param climateOceanModeration coastal temperature moderation strength
     * @param climateLayout large-scale climate arrangement strategy
     * @param climateNorthSouthCenterZ world Z coordinate representing the middle of a north-south profile
     * @param climateNorthSouthSpan distance in blocks from the cold northern anchor to the warm southern anchor
     * @param climateNorthSouthStrength blend strength of the north-south baseline over randomized climate
     * @param climateNorthTemperature northern temperature anchor in {@code [0, 1]}
     * @param climateSouthTemperature southern temperature anchor in {@code [0, 1]}
     * @param climateNorthMoisture northern moisture anchor in {@code [0, 1]}
     * @param climateSouthMoisture southern moisture anchor in {@code [0, 1]}
     * @param riverScale hydrology density scale used to derive drainage-grid spacing
     * @param relief base continental relief in blocks
     * @param mountainRelief additional mountain relief in blocks
     * @param riverDepth maximum drainage-network river incision depth in blocks
     * @param erosionStrength hydraulic erosion strength multiplier
     * @param erosionDeposition hydraulic deposition strength multiplier
     * @param thermalErosionStrength thermal slope-relaxation strength
     * @param erosionMaxDelta maximum absolute erosion/deposition height change in blocks
     */
    public EngineSettings {
        double terrainWeightSum = terrainPlainsWeight + terrainHillsWeight + terrainValleyWeight
                + terrainPlateauWeight + terrainMountainWeight;
        if (!Double.isFinite(terrainWeightSum) || terrainWeightSum <= 0.0D) {
            throw new IllegalArgumentException("At least one terrain weight must be > 0");
        }
    }

    /**
     * Returns the balanced default settings.
     *
     * @return balanced default settings
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
            case BALANCED -> settings(
                    0.00050D, 0.82D, 0.0D, 0.20D, 0.27D, 0.24D,
                    0.00145D, 0.00650D, 0.00055D, 0.78D, 0.50D,
                    0.24D, 0.23D, 0.16D, 0.17D, 0.20D,
                    0.00034D, 0.000105D, 0.76D, 0.68D, 0.82D,
                    0.16D, 0.42D, 0.20D, 0.48D,
                    0.00095D, 34.0D, 56.0D, 6.5D,
                    0.62D, 0.52D, 0.14D, 4.5D);
            case GENTLE -> settings(
                    0.00048D, 0.75D, 0.0D, 0.15D, 0.20D, 0.18D,
                    0.00120D, 0.00500D, 0.00042D, 0.72D, 0.60D,
                    0.34D, 0.28D, 0.18D, 0.13D, 0.07D,
                    0.00031D, 0.000095D, 0.72D, 0.72D, 0.76D,
                    0.14D, 0.38D, 0.18D, 0.52D,
                    0.00080D, 24.0D, 36.0D, 5.0D,
                    0.44D, 0.50D, 0.10D, 3.5D);
            case RUGGED -> settings(
                    0.00058D, 0.88D, 0.0D, 0.28D, 0.35D, 0.34D,
                    0.00180D, 0.00900D, 0.00072D, 0.88D, 0.42D,
                    0.12D, 0.25D, 0.14D, 0.20D, 0.29D,
                    0.00040D, 0.000120D, 0.82D, 0.60D, 0.90D,
                    0.19D, 0.48D, 0.24D, 0.42D,
                    0.00125D, 42.0D, 72.0D, 8.5D,
                    0.82D, 0.58D, 0.18D, 6.0D);
            case CENTRAL_EUROPE -> settings(
                    0.00058D, 0.80D, 0.04D, 0.22D, 0.25D, 0.30D,
                    0.00142D, 0.00720D, 0.00105D, 0.78D, 0.54D,
                    0.22D, 0.32D, 0.20D, 0.16D, 0.10D,
                    0.00030D, 0.000155D, 0.70D, 0.82D, 0.56D,
                    0.17D, 0.30D, 0.20D, 0.58D,
                    0.00084D, 42.0D, 68.0D, 6.0D,
                    0.62D, 0.55D, 0.15D, 4.8D);
        };
    }

    private static EngineSettings settings(
            double continentScale, double continentJitter, double continentSkipping,
            double continentSizeVariance, double continentWarpStrength, double continentCoastRoughness,
            double terrainScale, double detailScale, double terrainRegionScale,
            double terrainRegionJitter, double terrainBlendWidth,
            double terrainPlainsWeight, double terrainHillsWeight, double terrainValleyWeight,
            double terrainPlateauWeight, double terrainMountainWeight, double climateScale,
            double climateRegionScale, double climateRegionJitter, double climateRegionBlend,
            double climateRegionalContrast, double climateAltitudeCooling,
            double climateContinentalDryness, double climateRiverMoisture, double climateOceanModeration,
            double riverScale, double relief, double mountainRelief, double riverDepth,
            double erosionStrength, double erosionDeposition, double thermalErosionStrength,
            double erosionMaxDelta) {
        return new EngineSettings(
                continentScale, continentJitter, continentSkipping, continentSizeVariance,
                continentWarpStrength, continentCoastRoughness, terrainScale, detailScale,
                terrainRegionScale, terrainRegionJitter, terrainBlendWidth,
                terrainPlainsWeight, terrainHillsWeight, terrainValleyWeight,
                terrainPlateauWeight, terrainMountainWeight, climateScale,
                climateRegionScale, climateRegionJitter, climateRegionBlend, climateRegionalContrast,
                climateAltitudeCooling, climateContinentalDryness, climateRiverMoisture,
                climateOceanModeration, ClimateLayout.RANDOMIZED,
                0.0D, 24000.0D, 0.78D, 0.18D, 0.80D, 0.60D, 0.36D,
                riverScale, relief, mountainRelief, riverDepth, erosionStrength,
                erosionDeposition, thermalErosionStrength, erosionMaxDelta);
    }

    /**
     * Parses settings from the generic engine configuration.
     *
     * @param config source configuration
     * @return validated settings
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
                nonNegative(config, "terrainPlainsWeight", d.terrainPlainsWeight),
                nonNegative(config, "terrainHillsWeight", d.terrainHillsWeight),
                nonNegative(config, "terrainValleyWeight", d.terrainValleyWeight),
                nonNegative(config, "terrainPlateauWeight", d.terrainPlateauWeight),
                nonNegative(config, "terrainMountainWeight", d.terrainMountainWeight),
                positive(config, "climateScale", d.climateScale),
                positive(config, "climateRegionScale", d.climateRegionScale),
                unit(config, "climateRegionJitter", d.climateRegionJitter),
                positiveUnit(config, "climateRegionBlend", d.climateRegionBlend),
                unit(config, "climateRegionalContrast", d.climateRegionalContrast),
                nonNegative(config, "climateAltitudeCooling", d.climateAltitudeCooling),
                unit(config, "climateContinentalDryness", d.climateContinentalDryness),
                unit(config, "climateRiverMoisture", d.climateRiverMoisture),
                unit(config, "climateOceanModeration", d.climateOceanModeration),
                ClimateLayout.parse(config.getOrDefault("climateLayout", d.climateLayout.name())),
                finite(config, "climateNorthSouthCenterZ", d.climateNorthSouthCenterZ),
                positive(config, "climateNorthSouthSpan", d.climateNorthSouthSpan),
                unit(config, "climateNorthSouthStrength", d.climateNorthSouthStrength),
                unit(config, "climateNorthTemperature", d.climateNorthTemperature),
                unit(config, "climateSouthTemperature", d.climateSouthTemperature),
                unit(config, "climateNorthMoisture", d.climateNorthMoisture),
                unit(config, "climateSouthMoisture", d.climateSouthMoisture),
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

    private static double finite(EngineConfig config, String key, double fallback) {
        return number(config, key, fallback);
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
            throw new IllegalArgumentException(
                    "Engine config '" + key + "' is not a number: " + value, exception);
        }
    }
}
