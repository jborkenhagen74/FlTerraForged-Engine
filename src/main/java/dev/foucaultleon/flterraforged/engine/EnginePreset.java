package dev.foucaultleon.flterraforged.engine;

import java.util.Locale;

/**
 * Built-in tuning profiles for the complete world-generation pipeline.
 *
 * <p>Presets only select engine-internal defaults. Every numeric value can still be overridden
 * through {@code EngineConfig} after the preset has been selected.</p>
 */
public enum EnginePreset {

    /** Balanced default with broad continents, varied relief and moderate physical shaping. */
    BALANCED,

    /** Softer relief, wider blends, gentler erosion and somewhat sparser rivers. */
    GENTLE,

    /** Stronger relief, tighter landform transitions, denser rivers and more active erosion. */
    RUGGED;

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
                    "Unknown engine preset '" + value + "'. Expected balanced, gentle or rugged.",
                    exception);
        }
    }
}
