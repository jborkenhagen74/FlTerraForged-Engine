package dev.foucaultleon.flterraforged.engine.terrain.populator;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.continent.Continent;
import dev.foucaultleon.flterraforged.engine.continent.ContinentSample;
import dev.foucaultleon.flterraforged.engine.terrain.Blender;
import dev.foucaultleon.flterraforged.engine.terrain.Terrain;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainContext;
import dev.foucaultleon.flterraforged.engine.terrain.provider.TerrainProvider;
import dev.foucaultleon.flterraforged.engine.terrain.region.TerrainRegionSample;
import dev.foucaultleon.flterraforged.engine.terrain.region.TerrainRegionSampler;
import java.util.Objects;

/**
 * Populates continent, terrain-region and base landform signals into a caller-owned engine cell.
 */
public final class TerrainPopulator {

    private final EngineContext world;
    private final Continent continent;
    private final TerrainRegionSampler regions;
    private final TerrainProvider terrains;
    private final double blendWidth;

    /**
     * Creates a terrain cell populator.
     *
     * @param world world bounds and sea level
     * @param continent continent source
     * @param regions terrain-region partition
     * @param terrains terrain definition provider
     * @param blendWidth normalized terrain-region blend width
     */
    public TerrainPopulator(
            EngineContext world,
            Continent continent,
            TerrainRegionSampler regions,
            TerrainProvider terrains,
            double blendWidth) {
        this.world = Objects.requireNonNull(world, "world");
        this.continent = Objects.requireNonNull(continent, "continent");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.terrains = Objects.requireNonNull(terrains, "terrains");
        if (!Double.isFinite(blendWidth) || blendWidth <= 0.0D || blendWidth > 1.0D) {
            throw new IllegalArgumentException("blendWidth must be finite and in (0, 1]");
        }
        this.blendWidth = blendWidth;
    }

    /**
     * Populates a base terrain cell.
     *
     * @param cell caller-owned target cell
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return {@code cell}
     */
    public Cell populate(Cell cell, int x, int z) {
        Objects.requireNonNull(cell, "cell").reset();
        ContinentSample continentSample = continent.sample(x, z);
        TerrainRegionSample region = regions.sample(x, z);
        Terrain primary = terrains.resolve(region.id());
        Terrain secondary = terrains.resolve(region.neighborId());
        Terrain terrain = Blender.blend(primary, secondary, region.edge(), blendWidth);
        TerrainContext context = new TerrainContext(world, x, z, continentSample, region);

        cell.continentId = continentSample.id();
        cell.continentEdge = continentSample.edge();
        cell.continentX = continentSample.center().x();
        cell.continentZ = continentSample.center().z();
        cell.terrainRegionId = region.id();
        cell.terrainRegionEdge = region.edge();
        cell.weirdness = terrain.weirdness(context);
        cell.terrain = primary.type();
        cell.height = terrain.height(context);
        cell.heightErosion = cell.height;
        return cell;
    }
}
