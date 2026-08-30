package dev.foucaultleon.flterraforged.engine.climate;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import java.util.Objects;

/**
 * Immutable climate-generation settings.
 *
 * @param scale frequency of broad continuous climate noise
 * @param regionScale frequency of macro climate-region partitioning
 * @param regionJitter jitter applied to macro climate-region points
 * @param regionBlend normalized width used to blend neighboring climate regions
 * @param altitudeCooling temperature reduction per 256 blocks above sea level
 * @param continentalDryness moisture reduction toward continental interiors
 * @param riverMoisture moisture bonus at river centerlines
 * @param oceanModeration strength with which coasts moderate temperature toward neutral
 */
public record ClimateSettings(
        double scale,
        double regionScale,
        double regionJitter,
        double regionBlend,
        double altitudeCooling,
        double continentalDryness,
        double riverMoisture,
        double oceanModeration) {

    /**
     * Validates climate settings.
     *
     * @param scale frequency of broad continuous climate noise
     * @param regionScale frequency of macro climate-region partitioning
     * @param regionJitter jitter applied to macro climate-region points
     * @param regionBlend normalized width used to blend neighboring climate regions
     * @param altitudeCooling temperature reduction per 256 blocks above sea level
     * @param continentalDryness moisture reduction toward continental interiors
     * @param riverMoisture moisture bonus at river centerlines
     * @param oceanModeration strength with which coasts moderate temperature toward neutral
     */
    public ClimateSettings {
        positive(scale, "scale");
        positive(regionScale, "regionScale");
        unit(regionJitter, "regionJitter");
        positiveUnit(regionBlend, "regionBlend");
        nonNegative(altitudeCooling, "altitudeCooling");
        unit(continentalDryness, "continentalDryness");
        unit(riverMoisture, "riverMoisture");
        unit(oceanModeration, "oceanModeration");
    }

    /**
     * Derives climate settings from the engine configuration.
     *
     * @param settings parsed engine settings
     * @return climate settings
     */
    public static ClimateSettings from(EngineSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new ClimateSettings(
                settings.climateScale(),
                settings.climateRegionScale(),
                settings.climateRegionJitter(),
                settings.climateRegionBlend(),
                settings.climateAltitudeCooling(),
                settings.climateContinentalDryness(),
                settings.climateRiverMoisture(),
                settings.climateOceanModeration());
    }

    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }
    }

    private static void nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and >= 0");
        }
    }

    private static void unit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }

    private static void positiveUnit(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(name + " must be finite and in (0, 1]");
        }
    }
}
