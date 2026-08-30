package dev.foucaultleon.flterraforged.engine.erosion;

/**
 * Applies one deterministic erosion operation to a mutable height region.
 */
@FunctionalInterface
public interface ErosionFilter {

    /**
     * Applies the filter in place.
     *
     * @param heights mutable terrain heights
     * @param erosion accumulated eroded-material amounts
     * @param deposition accumulated deposited-material amounts
     * @param width square region width
     * @param settings erosion settings
     * @param seaLevel engine sea level
     */
    void apply(
            double[] heights,
            double[] erosion,
            double[] deposition,
            int width,
            ErosionSettings settings,
            int seaLevel);
}
