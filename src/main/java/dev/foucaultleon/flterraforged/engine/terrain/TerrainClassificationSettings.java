package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import java.util.Objects;

/**
 * Cross-stage thresholds used only for the final semantic terrain classification.
 *
 * @param oceanDepthBelowSea minimum depth below sea level considered ocean
 * @param oceanContinentalness continentalness threshold considered ocean
 * @param coastHeightAboveSea maximum lowland height considered coast
 * @param coastContinentalness continentalness threshold considered coast
 * @param riverDepth minimum river incision depth considered a semantic river
 * @param valleySlope maximum valley slope before the semantic type is promoted to hills
 */
public record TerrainClassificationSettings(
        double oceanDepthBelowSea,
        double oceanContinentalness,
        double coastHeightAboveSea,
        double coastContinentalness,
        double riverDepth,
        double valleySlope) {

    /**
     * Creates validated classification settings.
     *
     * @param oceanDepthBelowSea minimum ocean depth below sea level
     * @param oceanContinentalness ocean continentalness threshold
     * @param coastHeightAboveSea coast height above sea level
     * @param coastContinentalness coast continentalness threshold
     * @param riverDepth river incision threshold
     * @param valleySlope valley-to-hills slope threshold
     */
    public TerrainClassificationSettings {
        positive(oceanDepthBelowSea, "oceanDepthBelowSea");
        signedUnit(oceanContinentalness, "oceanContinentalness");
        nonNegative(coastHeightAboveSea, "coastHeightAboveSea");
        signedUnit(coastContinentalness, "coastContinentalness");
        positive(riverDepth, "riverDepth");
        positive(valleySlope, "valleySlope");
        if (oceanContinentalness >= coastContinentalness) {
            throw new IllegalArgumentException("ocean continentalness must be lower than coast continentalness");
        }
    }

    /**
     * Derives classification thresholds from the same settings that shape the pipeline.
     *
     * @param settings engine settings
     * @return coordinated classification thresholds
     */
    public static TerrainClassificationSettings from(EngineSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new TerrainClassificationSettings(
                Math.max(3.0D, settings.relief() * 0.10D),
                -0.72D,
                Math.max(2.0D, settings.relief() * 0.06D),
                -0.34D,
                Math.max(0.60D, settings.riverDepth() * 0.11D),
                Math.max(2.25D, settings.relief() * 0.07D));
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

    private static void signedUnit(double value, String name) {
        if (!Double.isFinite(value) || value < -1.0D || value > 1.0D) {
            throw new IllegalArgumentException(name + " must be finite and in [-1, 1]");
        }
    }
}
