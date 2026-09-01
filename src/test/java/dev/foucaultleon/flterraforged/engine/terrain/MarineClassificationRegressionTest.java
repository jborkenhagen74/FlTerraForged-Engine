package dev.foucaultleon.flterraforged.engine.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.foucaultleon.flterraforged.engine.EnginePreset;
import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import org.junit.jupiter.api.Test;

final class MarineClassificationRegressionTest {

    @Test
    void oceanContinentalnessCannotTurnDryLandIntoOcean() {
        TerrainClassifier classifier = new TerrainClassifier(
                TerrainClassificationSettings.from(EngineSettings.preset(EnginePreset.CENTRAL_EUROPE)));
        assertNotEquals(
                StandardTerrainTypes.OCEAN,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        72.0D,
                        63,
                        0.1D,
                        -0.95D,
                        RiverSample.UNAVAILABLE));
    }

    @Test
    void lowInlandTerrainCannotBecomeCoastWithoutCoastalContinentalness() {
        TerrainClassifier classifier = new TerrainClassifier(
                TerrainClassificationSettings.from(EngineSettings.preset(EnginePreset.CENTRAL_EUROPE)));
        assertEquals(
                StandardTerrainTypes.PLAINS,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        64.0D,
                        63,
                        0.1D,
                        0.25D,
                        RiverSample.UNAVAILABLE));
    }

    @Test
    void submergedOceanwardTerrainStillBecomesOcean() {
        TerrainClassifier classifier = new TerrainClassifier(
                TerrainClassificationSettings.from(EngineSettings.preset(EnginePreset.CENTRAL_EUROPE)));
        assertEquals(
                StandardTerrainTypes.OCEAN,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        58.0D,
                        63,
                        0.1D,
                        -0.80D,
                        RiverSample.UNAVAILABLE));
    }
}
