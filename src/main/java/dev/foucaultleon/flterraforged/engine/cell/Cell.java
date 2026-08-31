package dev.foucaultleon.flterraforged.engine.cell;

import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import java.util.Objects;

/**
 * Mutable engine-internal sample cell carrying the intermediate signals produced by terrain stages.
 *
 * <p>The type deliberately contains no Minecraft biome, registry, codec, block or loader objects.
 * Callers own each instance and may safely reuse it after {@link #reset()}.</p>
 */
public final class Cell {

    /** Continuous terrain height. */
    public double height;
    /** Terrain height after erosion stages. */
    public double heightErosion;
    /** Deposited sediment signal. */
    public double sediment;
    /** Local height gradient. */
    public double gradient;
    /** Macro-region moisture signal. */
    public double regionMoisture;
    /** Macro-region temperature signal. */
    public double regionTemperature;
    /** Stable numeric continent identifier. */
    public double continentId;
    /** Normalized distance to the current continent edge. */
    public double continentEdge;
    /** Stable terrain-region identifier. */
    public double terrainRegionId;
    /** Normalized distance to the current terrain-region edge. */
    public double terrainRegionEdge;
    /** Stable biome-region identifier supplied by engine-side climate partitioning. */
    public double biomeRegionId;
    /** Normalized distance to the current biome-region edge. */
    public double biomeRegionEdge;
    /** Stable macro-biome identifier used as a semantic grouping hint. */
    public double macroBiomeId;
    /** River mask where one represents unaffected terrain and zero represents river center. */
    public double riverMask;
    /** Distance in blocks to the nearest river centerline. */
    public double riverDistance;
    /** Full width in blocks of the nearest river channel. */
    public double riverWidth;
    /** Incision depth in blocks contributed by the nearest river channel. */
    public double riverDepth;
    /** Continuous world-space Y coordinate of the nearest river water surface. */
    public double riverWaterSurfaceHeight;
    /** Accumulated drainage flow represented by the nearest river segment. */
    public double riverFlow;
    /** Whether the active hydrology sample represents material pond or lake water. */
    public boolean lake;
    /** Whether the active position lies in the dry shoreline transition of a pond or lake. */
    public boolean lakeShore;
    /** Continent-cell X coordinate. */
    public int continentX;
    /** Continent-cell Z coordinate. */
    public int continentZ;
    /** Whether an erosion stage marked this position. */
    public boolean erosionMask;
    /** Semantic terrain type. */
    public TerrainType terrain;
    /** Normalized erosion signal. */
    public double erosion;
    /** Terrain weirdness/ridge signal. */
    public double weirdness;
    /** Normalized temperature signal. */
    public double temperature;
    /** Normalized moisture signal. */
    public double moisture;

    /** Creates a reset cell with neutral defaults. */
    public Cell() {
        reset();
    }

    /**
     * Copies every engine signal from another cell.
     *
     * @param other source cell
     * @return this cell
     */
    public Cell copyFrom(Cell other) {
        Objects.requireNonNull(other, "other");
        height = other.height;
        heightErosion = other.heightErosion;
        sediment = other.sediment;
        gradient = other.gradient;
        regionMoisture = other.regionMoisture;
        regionTemperature = other.regionTemperature;
        continentId = other.continentId;
        continentEdge = other.continentEdge;
        terrainRegionId = other.terrainRegionId;
        terrainRegionEdge = other.terrainRegionEdge;
        biomeRegionId = other.biomeRegionId;
        biomeRegionEdge = other.biomeRegionEdge;
        macroBiomeId = other.macroBiomeId;
        riverMask = other.riverMask;
        riverDistance = other.riverDistance;
        riverWidth = other.riverWidth;
        riverDepth = other.riverDepth;
        riverWaterSurfaceHeight = other.riverWaterSurfaceHeight;
        riverFlow = other.riverFlow;
        lake = other.lake;
        lakeShore = other.lakeShore;
        continentX = other.continentX;
        continentZ = other.continentZ;
        erosionMask = other.erosionMask;
        terrain = other.terrain;
        erosion = other.erosion;
        weirdness = other.weirdness;
        temperature = other.temperature;
        moisture = other.moisture;
        return this;
    }

    /**
     * Restores neutral defaults suitable for the start of a generation pipeline.
     *
     * @return this cell
     */
    public Cell reset() {
        height = 0.0D;
        heightErosion = 0.0D;
        sediment = 0.0D;
        gradient = 0.0D;
        regionMoisture = 0.5D;
        regionTemperature = 0.5D;
        continentId = 0.0D;
        continentEdge = 1.0D;
        terrainRegionId = 0.0D;
        terrainRegionEdge = 1.0D;
        biomeRegionId = 0.0D;
        biomeRegionEdge = 1.0D;
        macroBiomeId = 0.0D;
        riverMask = 1.0D;
        riverDistance = Double.POSITIVE_INFINITY;
        riverWidth = 0.0D;
        riverDepth = 0.0D;
        riverWaterSurfaceHeight = Double.NaN;
        riverFlow = Double.NaN;
        lake = false;
        lakeShore = false;
        continentX = 0;
        continentZ = 0;
        erosionMask = false;
        terrain = StandardTerrainTypes.UNKNOWN;
        erosion = 0.0D;
        weirdness = 0.0D;
        temperature = 0.5D;
        moisture = 0.5D;
        return this;
    }

    /**
     * Receives a cell together with the world coordinate that produced it.
     */
    @FunctionalInterface
    public interface Visitor {

        /**
         * Visits a cell.
         *
         * @param cell cell being visited
         * @param x world X coordinate
         * @param z world Z coordinate
         */
        void visit(Cell cell, int x, int z);
    }
}
