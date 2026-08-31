package dev.foucaultleon.flterraforged.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import dev.foucaultleon.flterraforged.engine.climate.ClimateLayout;
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
                "erosionStrength", "0.31",
                "terrainMountainWeight", "0.08")));
        assertEquals(61.5D, settings.mountainRelief());
        assertEquals(0.31D, settings.erosionStrength());
        assertEquals(0.08D, settings.terrainMountainWeight());
        assertEquals(EngineSettings.preset(EnginePreset.RUGGED).terrainScale(), settings.terrainScale());
    }


    @Test
    void centralEuropePresetUsesTemperateRandomizedClimateByDefault() {
        EngineSettings settings = EngineSettings.preset(EnginePreset.CENTRAL_EUROPE);
        assertEquals(ClimateLayout.RANDOMIZED, settings.climateLayout());
        assertNotEquals(EngineSettings.preset(EnginePreset.GENTLE).relief(), settings.relief());
        assertEquals(0.32D, settings.terrainHillsWeight());
        assertEquals(0.20D, settings.terrainValleyWeight());
        assertEquals(0.10D, settings.terrainMountainWeight());
    }

    @Test
    void northSouthLayoutIsAnExplicitOption() {
        EngineSettings settings = EngineSettings.from(EngineConfig.of(Map.of(
                "preset", "central_europe",
                "climateLayout", "north_south",
                "climateNorthSouthSpan", "30000")));
        assertEquals(ClimateLayout.NORTH_SOUTH, settings.climateLayout());
        assertEquals(30000.0D, settings.climateNorthSouthSpan());
    }

    @Test
    void unknownPresetFailsFast() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EngineSettings.from(EngineConfig.of(Map.of("preset", "unknown"))));
    }
}
