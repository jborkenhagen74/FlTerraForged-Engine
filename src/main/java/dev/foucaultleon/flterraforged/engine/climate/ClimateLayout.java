package dev.foucaultleon.flterraforged.engine.climate;

import java.util.Locale;

/** Defines the large-scale climate arrangement used by the engine. */
public enum ClimateLayout {

    /** Seeded climate regions may occur in every direction but remain smoothly blended. */
    RANDOMIZED,

    /** Temperature and moisture follow a configurable north-to-south baseline with local variation. */
    NORTH_SOUTH;

    /**
     * Parses a configured climate-layout name.
     *
     * @param value configured name
     * @return parsed layout
     * @throws IllegalArgumentException if the value is unknown
     */
    public static ClimateLayout parse(String value) {
        if (value == null || value.isBlank()) {
            return RANDOMIZED;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown climate layout '" + value
                            + "'. Expected randomized or north_south.",
                    exception);
        }
    }
}
