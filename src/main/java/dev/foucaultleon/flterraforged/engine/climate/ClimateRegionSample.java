package dev.foucaultleon.flterraforged.engine.climate;

/**
 * Deterministic macro climate-region sample.
 *
 * @param id stable owning-region identifier in {@code [0, 1]}
 * @param neighborId stable nearest neighboring-region identifier in {@code [0, 1]}
 * @param edge normalized distance from the nearest region boundary in {@code [0, 1]}
 * @param temperature owning-region temperature anchor in {@code [0, 1]}
 * @param moisture owning-region moisture anchor in {@code [0, 1]}
 * @param neighborTemperature neighboring-region temperature anchor in {@code [0, 1]}
 * @param neighborMoisture neighboring-region moisture anchor in {@code [0, 1]}
 */
public record ClimateRegionSample(
        double id,
        double neighborId,
        double edge,
        double temperature,
        double moisture,
        double neighborTemperature,
        double neighborMoisture) {

    /**
     * Validates climate-region signals.
     *
     * @param id stable owning-region identifier in {@code [0, 1]}
     * @param neighborId stable nearest neighboring-region identifier in {@code [0, 1]}
     * @param edge normalized distance from the nearest region boundary in {@code [0, 1]}
     * @param temperature owning-region temperature anchor in {@code [0, 1]}
     * @param moisture owning-region moisture anchor in {@code [0, 1]}
     * @param neighborTemperature neighboring-region temperature anchor in {@code [0, 1]}
     * @param neighborMoisture neighboring-region moisture anchor in {@code [0, 1]}
     */
    public ClimateRegionSample {
        if (!unit(id)
                || !unit(neighborId)
                || !unit(edge)
                || !unit(temperature)
                || !unit(moisture)
                || !unit(neighborTemperature)
                || !unit(neighborMoisture)) {
            throw new IllegalArgumentException("climate region signals must be finite and in [0, 1]");
        }
    }

    private static boolean unit(double value) {
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D;
    }
}
