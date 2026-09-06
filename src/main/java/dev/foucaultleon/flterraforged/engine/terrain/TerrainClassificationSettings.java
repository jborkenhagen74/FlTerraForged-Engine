package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import java.util.Objects;

/**
 * Cross-stage thresholds used only for the final semantic terrain classification.
 *
 * @param oceanDepthBelowSea minimum depth below sea level considered ocean
 * @param oceanContinentalness continentalness threshold considered open ocean
 * @param coastHeightAboveSea maximum dry height above sea level considered coast
 * @param coastContinentalness landward continentalness limit of the narrow coast band
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
     * @param oceanContinentalness open-ocean continentalness threshold
     * @param coastHeightAboveSea maximum dry coast height above sea level
     * @param coastContinentalness landward continentalness limit of the coast band
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
     * <p>R49 deliberately keeps {@code COAST} as a narrow semantic transition instead of using it
     * for the whole low continental shelf. The Minecraft adapter maps this semantic to beach-like
     * biomes, so a broad continentalness interval would turn complete lowland regions into beaches.
     * The ocean/coast thresholds are therefore intentionally close together and dry coast is kept
     * close to sea level.</p>
     *
     * @param settings engine settings
     * @return coordinated classification thresholds
     */
    public static TerrainClassificationSettings from(EngineSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new TerrainClassificationSettings(
                Math.max(3.0D, settings.relief() * 0.10D),
                -0.72D,
                1.25D,
                -0.69D,
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
