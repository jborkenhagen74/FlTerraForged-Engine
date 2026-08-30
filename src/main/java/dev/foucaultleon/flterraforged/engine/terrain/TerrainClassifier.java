package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import java.util.Objects;

/** Converts numeric terrain signals into loader-independent semantic terrain ids. */
public final class TerrainClassifier {

    /** Creates a terrain classifier using the bootstrap thresholds. */
    public TerrainClassifier() {
    }

    /**
     * Classifies a terrain position.
     *
     * @param height surface height
     * @param seaLevel world sea level
     * @param slope local terrain slope
     * @param continentalness continentalness signal
     * @param river river sample
     * @return semantic terrain type
     */
    public TerrainType classify(
            double height,
            int seaLevel,
            double slope,
            double continentalness,
            RiverSample river) {
        Objects.requireNonNull(river, "river");
        if (height < seaLevel - 4.0D) {
            return StandardTerrainTypes.OCEAN;
        }
        if (river.depth() > 1.25D && height <= seaLevel + 18.0D) {
            return StandardTerrainTypes.RIVER;
        }
        if (height <= seaLevel + 2.0D) {
            return StandardTerrainTypes.COAST;
        }
        if (slope >= 2.25D || height >= seaLevel + 58.0D) {
            return StandardTerrainTypes.MOUNTAINS;
        }
        if (slope >= 0.90D || height >= seaLevel + 28.0D) {
            return StandardTerrainTypes.HILLS;
        }
        if (continentalness > 0.55D && slope < 0.45D) {
            return StandardTerrainTypes.PLATEAU;
        }
        return StandardTerrainTypes.PLAINS;
    }
}
