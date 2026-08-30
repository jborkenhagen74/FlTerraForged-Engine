package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;

/** Parsed, validated bootstrap-engine configuration. */
public record EngineSettings(
        double continentScale,
        double terrainScale,
        double detailScale,
        double climateScale,
        double riverScale,
        double relief,
        double mountainRelief,
        double riverDepth) {

    public static EngineSettings defaults() {
        return new EngineSettings(
                0.00055D,
                0.00170D,
                0.00800D,
                0.00080D,
                0.00110D,
                36.0D,
                52.0D,
                7.0D);
    }

    public static EngineSettings from(EngineConfig config) {
        EngineSettings d = defaults();
        return new EngineSettings(
                positive(config, "continentScale", d.continentScale),
                positive(config, "terrainScale", d.terrainScale),
                positive(config, "detailScale", d.detailScale),
                positive(config, "climateScale", d.climateScale),
                positive(config, "riverScale", d.riverScale),
                positive(config, "relief", d.relief),
                positive(config, "mountainRelief", d.mountainRelief),
                positive(config, "riverDepth", d.riverDepth));
    }

    private static double positive(EngineConfig config, String key, double fallback) {
        String value = config.get(key).orElse(null);
        if (value == null) {
            return fallback;
        }
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed) || parsed <= 0.0D) {
                throw new IllegalArgumentException("Engine config '" + key + "' must be > 0");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Engine config '" + key + "' is not a number: " + value, exception);
        }
    }
}
