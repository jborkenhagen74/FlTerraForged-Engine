package dev.foucaultleon.flterraforged.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class EngineSettingsPresetTest {

    @Test
    void balancedIsTheDefaultPreset() {
        assertEquals(EngineSettings.preset(EnginePreset.BALANCED), EngineSettings.defaults());
        assertEquals(EngineSettings.defaults(), EngineSettings.from(EngineConfig.empty()));
    }

    @Test
    void presetsChangeCoordinatedStageSettings() {
        EngineSettings gentle = EngineSettings.preset(EnginePreset.GENTLE);
        EngineSettings balanced = EngineSettings.preset(EnginePreset.BALANCED);
        EngineSettings rugged = EngineSettings.preset(EnginePreset.RUGGED);

        assertNotEquals(gentle.mountainRelief(), balanced.mountainRelief());
        assertNotEquals(balanced.mountainRelief(), rugged.mountainRelief());
        assertNotEquals(gentle.erosionStrength(), rugged.erosionStrength());
        assertNotEquals(gentle.riverScale(), rugged.riverScale());
        assertNotEquals(gentle.terrainBlendWidth(), rugged.terrainBlendWidth());
    }

    @Test
    void explicitValuesOverrideTheSelectedPreset() {
        EngineSettings settings = EngineSettings.from(EngineConfig.of(Map.of(
                "preset", "rugged",
                "mountainRelief", "61.5",
                "erosionStrength", "0.31")));
        assertEquals(61.5D, settings.mountainRelief());
        assertEquals(0.31D, settings.erosionStrength());
        assertEquals(EngineSettings.preset(EnginePreset.RUGGED).terrainScale(), settings.terrainScale());
    }

    @Test
    void unknownPresetFailsFast() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EngineSettings.from(EngineConfig.of(Map.of("preset", "unknown"))));
    }
}
