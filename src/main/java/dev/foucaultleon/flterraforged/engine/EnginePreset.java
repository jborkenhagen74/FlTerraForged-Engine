package dev.foucaultleon.flterraforged.engine;

import java.util.Locale;

/** Built-in tuning profiles for the complete world-generation pipeline. */
public enum EnginePreset {

    /** Balanced default with broad continents, varied relief and moderate physical shaping. */
    BALANCED,

    /** Softer relief, wider blends, gentler erosion and somewhat sparser rivers. */
    GENTLE,

    /** Stronger relief, tighter landform transitions, denser rivers and more active erosion. */
    RUGGED,

    /**
     * Temperate European-style landscape with rolling relief, stronger regional variety and a
     * configurable cool-north to warm-south climate gradient.
     */
    CENTRAL_EUROPE;

    /**
     * Parses a case-insensitive preset name.
     *
     * @param value configured preset name
     * @return parsed preset
     * @throws IllegalArgumentException if the name is unknown
     */
    public static EnginePreset parse(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown engine preset '" + value
                            + "'. Expected balanced, gentle, rugged or central_europe.",
                    exception);
        }
    }
}
