package dev.foucaultleon.flterraforged.engine.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.foucaultleon.flterraforged.engine.EnginePreset;
import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import org.junit.jupiter.api.Test;

/** Regression coverage for R49 narrow marine/coast semantics. */
final class MarineClassificationRegressionTest {

    @Test
    void oceanContinentalnessCannotTurnDryLandIntoOcean() {
        TerrainClassifier classifier = classifier();
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
        TerrainClassifier classifier = classifier();
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
    void submergedShelfBecomesOceanNotBeachCoast() {
        TerrainClassifier classifier = classifier();
        assertEquals(
                StandardTerrainTypes.OCEAN,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        62.0D,
                        63,
                        0.1D,
                        -0.65D,
                        RiverSample.UNAVAILABLE));
    }

    @Test
    void dryCoastExistsOnlyInsideNarrowContinentalnessBand() {
        TerrainClassifier classifier = classifier();
        assertEquals(
                StandardTerrainTypes.COAST,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        63.5D,
                        63,
                        0.1D,
                        -0.70D,
                        RiverSample.UNAVAILABLE));
        assertEquals(
                StandardTerrainTypes.PLAINS,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        63.5D,
                        63,
                        0.1D,
                        -0.60D,
                        RiverSample.UNAVAILABLE));
    }

    @Test
    void coastDoesNotExtendHighAboveSeaLevel() {
        TerrainClassifier classifier = classifier();
        assertEquals(
                StandardTerrainTypes.PLAINS,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        65.0D,
                        63,
                        0.1D,
                        -0.70D,
                        RiverSample.UNAVAILABLE));
    }

    @Test
    void dryRiverIncisionCannotTurnSubmergedShelfIntoBeach() {
        TerrainClassifier classifier = classifier();
        RiverSample dryRiverBank = new RiverSample(12.0D, 4.0D, 4.5D, Double.NaN, 5.0D);
        assertEquals(
                StandardTerrainTypes.OCEAN,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        59.0D,
                        63,
                        0.2D,
                        -0.70D,
                        dryRiverBank));
    }

    private static TerrainClassifier classifier() {
        return new TerrainClassifier(
                TerrainClassificationSettings.from(EngineSettings.preset(EnginePreset.CENTRAL_EUROPE)));
    }
}
