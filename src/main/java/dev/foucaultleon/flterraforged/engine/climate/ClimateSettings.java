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
 * @param regionalContrast contrast of region anchors around neutral climate
 * @param altitudeCooling temperature reduction per 256 blocks above sea level
 * @param continentalDryness moisture reduction toward continental interiors
 * @param riverMoisture moisture bonus at river centerlines
 * @param oceanModeration strength with which coasts moderate temperature toward neutral
 * @param layout configured large-scale climate arrangement
 * @param northSouthCenterZ world Z coordinate at the center of the north-south profile
 * @param northSouthSpan distance from northern to southern anchor in blocks
 * @param northSouthStrength contribution of the north-south baseline to final climate
 * @param northTemperature northern temperature anchor
 * @param southTemperature southern temperature anchor
 * @param northMoisture northern moisture anchor
 * @param southMoisture southern moisture anchor
 */
public record ClimateSettings(
        double scale,
        double regionScale,
        double regionJitter,
        double regionBlend,
        double regionalContrast,
        double altitudeCooling,
        double continentalDryness,
        double riverMoisture,
        double oceanModeration,
        ClimateLayout layout,
        double northSouthCenterZ,
        double northSouthSpan,
        double northSouthStrength,
        double northTemperature,
        double southTemperature,
        double northMoisture,
        double southMoisture) {

    /** Validates climate settings. */
    public ClimateSettings {
        positive(scale, "scale");
        positive(regionScale, "regionScale");
        unit(regionJitter, "regionJitter");
        positiveUnit(regionBlend, "regionBlend");
        unit(regionalContrast, "regionalContrast");
        nonNegative(altitudeCooling, "altitudeCooling");
        unit(continentalDryness, "continentalDryness");
        unit(riverMoisture, "riverMoisture");
        unit(oceanModeration, "oceanModeration");
        Objects.requireNonNull(layout, "layout");
        finite(northSouthCenterZ, "northSouthCenterZ");
        positive(northSouthSpan, "northSouthSpan");
        unit(northSouthStrength, "northSouthStrength");
        unit(northTemperature, "northTemperature");
        unit(southTemperature, "southTemperature");
        unit(northMoisture, "northMoisture");
        unit(southMoisture, "southMoisture");
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
                settings.climateRegionalContrast(),
                settings.climateAltitudeCooling(),
                settings.climateContinentalDryness(),
                settings.climateRiverMoisture(),
                settings.climateOceanModeration(),
                settings.climateLayout(),
                settings.climateNorthSouthCenterZ(),
                settings.climateNorthSouthSpan(),
                settings.climateNorthSouthStrength(),
                settings.climateNorthTemperature(),
                settings.climateSouthTemperature(),
                settings.climateNorthMoisture(),
                settings.climateSouthMoisture());
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

    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
