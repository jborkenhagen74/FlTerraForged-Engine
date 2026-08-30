package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import java.util.Objects;

/** Applies final semantic overrides to an engine-selected base terrain landform. */
public final class TerrainClassifier {

    /** Creates a terrain classifier using the default ocean, coast and river thresholds. */
    public TerrainClassifier() {
    }

    /**
     * Classifies a terrain position while preserving the engine-selected landform on normal land.
     *
     * @param baseType terrain type selected by the terrain-region pipeline
     * @param height final surface height
     * @param seaLevel world sea level
     * @param slope local terrain slope
     * @param continentalness continentalness signal
     * @param river river sample
     * @return final semantic terrain type
     */
    public TerrainType classify(
            TerrainType baseType,
            double height,
            int seaLevel,
            double slope,
            double continentalness,
            RiverSample river) {
        Objects.requireNonNull(baseType, "baseType");
        Objects.requireNonNull(river, "river");
        if (height < seaLevel - 4.0D || continentalness < -0.72D) {
            return StandardTerrainTypes.OCEAN;
        }
        if (river.depth() > 1.25D && height <= seaLevel + 18.0D) {
            return StandardTerrainTypes.RIVER;
        }
        if (height <= seaLevel + 2.0D || continentalness < -0.35D) {
            return StandardTerrainTypes.COAST;
        }
        if (baseType.equals(StandardTerrainTypes.VALLEY) && slope > 2.5D) {
            return StandardTerrainTypes.HILLS;
        }
        return baseType;
    }

    /**
     * Legacy threshold-only classification retained for internal compatibility.
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
        TerrainType base;
        if (slope >= 2.25D || height >= seaLevel + 58.0D) {
            base = StandardTerrainTypes.MOUNTAINS;
        } else if (slope >= 0.90D || height >= seaLevel + 28.0D) {
            base = StandardTerrainTypes.HILLS;
        } else if (continentalness > 0.55D && slope < 0.45D) {
            base = StandardTerrainTypes.PLATEAU;
        } else {
            base = StandardTerrainTypes.PLAINS;
        }
        return classify(base, height, seaLevel, slope, continentalness, river);
    }
}
