package dev.foucaultleon.flterraforged.engine.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.DefaultTerrainWorld;
import dev.foucaultleon.flterraforged.engine.EnginePreset;
import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import org.junit.jupiter.api.Test;

final class MarineClassificationRegressionTest {

    private static final TerrainType LAKE_SHORE = TerrainType.of("flterraforged", "lake_shore");

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
    void submergedOceanwardTerrainStillBecomesOcean() {
        TerrainClassifier classifier = classifier();
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

    @Test
    void shallowSubmergedCoastBandIsOceanNotCoast() {
        TerrainClassifier classifier = classifier();
        assertEquals(
                StandardTerrainTypes.OCEAN,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        62.80D,
                        63,
                        0.1D,
                        -0.56D,
                        RiverSample.UNAVAILABLE));
    }

    @Test
    void dryShoreAtSeaLevelRemainsCoast() {
        TerrainClassifier classifier = classifier();
        assertEquals(
                StandardTerrainTypes.COAST,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        63.0D,
                        63,
                        0.1D,
                        -0.56D,
                        RiverSample.UNAVAILABLE));
    }

    @Test
    void dryRiverIncisionCannotHideSubmergedMarineTerrain() {
        TerrainClassifier classifier = classifier();
        RiverSample dryRiverBank = new RiverSample(12.0D, 4.0D, 4.5D, Double.NaN, 5.0D);
        assertEquals(
                StandardTerrainTypes.OCEAN,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        59.0D,
                        63,
                        0.2D,
                        -0.56D,
                        dryRiverBank));
    }

    @Test
    void materialLakeKeepsInlandIdentityInsideCoastalBand() {
        TerrainClassifier classifier = classifier();
        RiverSample lakeWater = new RiverSample(-2.0D, 10.0D, 3.0D, 61.0D, 0.0D);
        assertEquals(
                StandardTerrainTypes.LAKE,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        58.0D,
                        63,
                        0.1D,
                        -0.56D,
                        lakeWater,
                        true,
                        false));
    }

    @Test
    void dryLakeShoreKeepsInlandIdentityInsideCoastalBand() {
        TerrainClassifier classifier = classifier();
        assertEquals(
                LAKE_SHORE,
                classifier.classify(
                        StandardTerrainTypes.PLAINS,
                        60.0D,
                        63,
                        0.1D,
                        -0.56D,
                        RiverSample.UNAVAILABLE,
                        false,
                        true));
    }

    @Test
    void marineWaterToLandEdgeRisesByAtMostOneBlock() {
        try (DefaultTerrainWorld world = new DefaultTerrainWorld(
                new EngineContext(123456789L, -64, 320, 63),
                EngineSettings.preset(EnginePreset.CENTRAL_EUROPE))) {
            for (int z = -512; z < -448; z++) {
                for (int x = -384; x < -320; x++) {
                    TerrainSample wet = world.sample(x, z);
                    if (!isMarineWet(wet)) {
                        continue;
                    }
                    assertMarineEdge(wet, world.sample(x + 1, z));
                    assertMarineEdge(wet, world.sample(x, z + 1));
                }
            }
        }
    }

    private static TerrainClassifier classifier() {
        return new TerrainClassifier(
                TerrainClassificationSettings.from(EngineSettings.preset(EnginePreset.CENTRAL_EUROPE)));
    }

    private static void assertMarineEdge(TerrainSample wet, TerrainSample candidate) {
        if (isMarineWet(candidate) || materialHydrology(candidate)) {
            return;
        }
        int dryY = (int) Math.floor(candidate.surfaceHeight());
        assertTrue(dryY >= 63, "dry coast must not remain below sea level");
        assertTrue(dryY <= 64, "dry coast must rise by at most one block");
    }

    private static boolean isMarineWet(TerrainSample sample) {
        return StandardTerrainTypes.OCEAN.equals(sample.terrainType())
                && sample.surfaceHeight() < 63.0D;
    }

    private static boolean materialHydrology(TerrainSample sample) {
        return sample.river().hasWaterSurfaceHeight()
                && sample.river().waterSurfaceHeight() > sample.surfaceHeight() + 0.05D;
    }
}
