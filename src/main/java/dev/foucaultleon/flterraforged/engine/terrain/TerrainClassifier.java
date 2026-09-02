package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import java.util.Objects;

/** Applies final semantic overrides to an engine-selected base terrain landform. */
public final class TerrainClassifier {

    /**
     * Canonical dry shoreline semantic.
     *
     * <p>Defined locally instead of referencing the newer standard shoreline convenience constant so the
     * engine remains source-compatible with the previously published 0.1 API, which did not yet
     * expose that convenience constant.</p>
     */
    private static final TerrainType LAKE_SHORE =
            TerrainType.of(StandardTerrainTypes.NAMESPACE, "lake_shore");

    private final TerrainClassificationSettings settings;

    /** Creates a terrain classifier using conservative standalone defaults. */
    public TerrainClassifier() {
        this(new TerrainClassificationSettings(4.0D, -0.72D, 2.0D, -0.35D, 0.75D, 2.5D));
    }

    /**
     * Creates a terrain classifier coordinated with the rest of the pipeline.
     *
     * @param settings classification thresholds
     */
    public TerrainClassifier(TerrainClassificationSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
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
        return classify(baseType, height, seaLevel, slope, continentalness, river, false, false);
    }

    /**
     * Classifies a terrain position with an explicit inland-water semantic.
     *
     * @param baseType terrain type selected by the terrain-region pipeline
     * @param height final surface height
     * @param seaLevel world sea level
     * @param slope local terrain slope
     * @param continentalness continentalness signal
     * @param river hydrology sample
     * @param lake whether depression-fill hydrology marks this point as pond/lake
     * @return final semantic terrain type
     */
    public TerrainType classify(
            TerrainType baseType,
            double height,
            int seaLevel,
            double slope,
            double continentalness,
            RiverSample river,
            boolean lake) {
        return classify(baseType, height, seaLevel, slope, continentalness, river, lake, false);
    }

    /**
     * Classifies a terrain position with explicit inland-water and shoreline semantics.
     *
     * @param baseType terrain type selected by the terrain-region pipeline
     * @param height final surface height
     * @param seaLevel world sea level
     * @param slope local terrain slope
     * @param continentalness continentalness signal
     * @param river hydrology sample
     * @param lake whether depression-fill hydrology marks this point as material pond/lake water
     * @param lakeShore whether this point lies in the dry lake/pond shoreline transition
     * @return final semantic terrain type
     */
    public TerrainType classify(
            TerrainType baseType,
            double height,
            int seaLevel,
            double slope,
            double continentalness,
            RiverSample river,
            boolean lake,
            boolean lakeShore) {
        Objects.requireNonNull(baseType, "baseType");
        Objects.requireNonNull(river, "river");
        boolean belowSea = height < seaLevel - 1.50D;
        boolean oceanward = continentalness < settings.coastContinentalness();
        boolean deepEnough = height < seaLevel - settings.oceanDepthBelowSea();
        if ((deepEnough && oceanward)
                || (continentalness < settings.oceanContinentalness() && belowSea)) {
            return StandardTerrainTypes.OCEAN;
        }
        if (lake && river.hasWaterSurfaceHeight()) {
            return StandardTerrainTypes.LAKE;
        }
        if (river.hasWaterSurfaceHeight() && river.depth() >= settings.riverDepth()) {
            return StandardTerrainTypes.RIVER;
        }
        if (lakeShore) {
            return LAKE_SHORE;
        }
        // A low elevation alone does not make an inland plain a coast. Coastal semantics require
        // both proximity to the continent edge and a surface near sea level. This also prevents
        // beach biomes (and their structures) from leaking far inland.
        if (continentalness < settings.coastContinentalness()
                && height <= seaLevel + settings.coastHeightAboveSea()) {
            return StandardTerrainTypes.COAST;
        }
        if (baseType.equals(StandardTerrainTypes.VALLEY) && slope > settings.valleySlope()) {
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
