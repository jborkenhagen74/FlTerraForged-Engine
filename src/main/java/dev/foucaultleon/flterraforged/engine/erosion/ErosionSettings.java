package dev.foucaultleon.flterraforged.engine.erosion;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import java.util.Objects;

/**
 * Immutable parameters for deterministic terrain erosion.
 *
 * <p>The default model uses a padded region, globally aligned droplet launch points and a final
 * thermal-relaxation pass. Only immutable completed regions are cached per sampling thread.</p>
 *
 * @param regionSize width and depth of the erosion core region in blocks
 * @param border padding around each region in blocks
 * @param launchSpacing spacing of deterministic hydraulic-droplet launch cells in blocks
 * @param maxDropletLifetime maximum number of steps travelled by one droplet
 * @param erosionRadius radius of the hydraulic erosion brush in blocks
 * @param inertia amount of previous droplet direction retained per step
 * @param sedimentCapacity sediment-carrying capacity multiplier
 * @param minimumSedimentCapacity minimum non-zero carrying capacity
 * @param erosionRate fraction of missing capacity eroded per step
 * @param depositionRate fraction of excess sediment deposited per step
 * @param evaporationRate fraction of water volume lost per step
 * @param gravity speed gain applied while a droplet descends
 * @param hydraulicStrength multiplier applied to hydraulic erosion deltas
 * @param depositionStrength multiplier applied to hydraulic deposition deltas
 * @param thermalStrength fraction of slope excess redistributed by thermal erosion
 * @param talusSlope maximum stable height difference per block before thermal erosion
 * @param maximumHeightChange maximum absolute erosion/deposition change at one point in blocks
 * @param cacheSize maximum number of immutable erosion regions retained by the world sampler
 */
public record ErosionSettings(
        int regionSize,
        int border,
        int launchSpacing,
        int maxDropletLifetime,
        int erosionRadius,
        double inertia,
        double sedimentCapacity,
        double minimumSedimentCapacity,
        double erosionRate,
        double depositionRate,
        double evaporationRate,
        double gravity,
        double hydraulicStrength,
        double depositionStrength,
        double thermalStrength,
        double talusSlope,
        double maximumHeightChange,
        int cacheSize) {

    /**
     * Validates erosion parameters.
     *
     * @param regionSize width and depth of the erosion core region in blocks
     * @param border padding around each region in blocks
     * @param launchSpacing spacing of deterministic hydraulic-droplet launch cells in blocks
     * @param maxDropletLifetime maximum number of steps travelled by one droplet
     * @param erosionRadius radius of the hydraulic erosion brush in blocks
     * @param inertia amount of previous droplet direction retained per step
     * @param sedimentCapacity sediment-carrying capacity multiplier
     * @param minimumSedimentCapacity minimum non-zero carrying capacity
     * @param erosionRate fraction of missing capacity eroded per step
     * @param depositionRate fraction of excess sediment deposited per step
     * @param evaporationRate fraction of water volume lost per step
     * @param gravity speed gain applied while a droplet descends
     * @param hydraulicStrength multiplier applied to hydraulic erosion deltas
     * @param depositionStrength multiplier applied to hydraulic deposition deltas
     * @param thermalStrength fraction of slope excess redistributed by thermal erosion
     * @param talusSlope maximum stable height difference per block before thermal erosion
     * @param maximumHeightChange maximum absolute erosion/deposition change at one point in blocks
     * @param cacheSize maximum number of immutable erosion regions retained by the world sampler
     */
    public ErosionSettings {
        if (regionSize < 8) {
            throw new IllegalArgumentException("regionSize must be >= 8");
        }
        if (border < maxDropletLifetime + erosionRadius) {
            throw new IllegalArgumentException("border must cover maxDropletLifetime + erosionRadius");
        }
        if (launchSpacing < 2 || maxDropletLifetime < 1 || erosionRadius < 1 || cacheSize < 1) {
            throw new IllegalArgumentException("erosion integer parameters are out of range");
        }
        unit(inertia, "inertia");
        positive(sedimentCapacity, "sedimentCapacity");
        positive(minimumSedimentCapacity, "minimumSedimentCapacity");
        unit(erosionRate, "erosionRate");
        unit(depositionRate, "depositionRate");
        unit(evaporationRate, "evaporationRate");
        positive(gravity, "gravity");
        nonNegative(hydraulicStrength, "hydraulicStrength");
        nonNegative(depositionStrength, "depositionStrength");
        unit(thermalStrength, "thermalStrength");
        positive(talusSlope, "talusSlope");
        positive(maximumHeightChange, "maximumHeightChange");
    }

    /**
     * Creates the default hydraulic/thermal erosion settings from the public engine settings.
     *
     * @param settings parsed engine settings
     * @return erosion settings
     */
    public static ErosionSettings from(EngineSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new ErosionSettings(
                32,
                16,
                6,
                12,
                2,
                0.08D,
                3.5D,
                0.02D,
                0.24D,
                0.28D,
                0.025D,
                4.0D,
                settings.erosionStrength(),
                settings.erosionDeposition(),
                settings.thermalErosionStrength(),
                1.35D,
                settings.erosionMaxDelta(),
                64);
    }

    private static void unit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
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
}
