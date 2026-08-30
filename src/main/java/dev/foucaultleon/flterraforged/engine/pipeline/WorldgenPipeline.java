package dev.foucaultleon.flterraforged.engine.pipeline;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.climate.ClimateModel;
import dev.foucaultleon.flterraforged.engine.climate.ClimateRegionSampler;
import dev.foucaultleon.flterraforged.engine.climate.ClimateSettings;
import dev.foucaultleon.flterraforged.engine.continent.AdvancedContinent;
import dev.foucaultleon.flterraforged.engine.continent.Continent;
import dev.foucaultleon.flterraforged.engine.continent.ContinentSettings;
import dev.foucaultleon.flterraforged.engine.erosion.ErosionPipeline;
import dev.foucaultleon.flterraforged.engine.erosion.ErosionSettings;
import dev.foucaultleon.flterraforged.engine.noise.FractalNoise;
import dev.foucaultleon.flterraforged.engine.noise.GradientNoise;
import dev.foucaultleon.flterraforged.engine.noise.Interpolation;
import dev.foucaultleon.flterraforged.engine.noise.Noise;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import dev.foucaultleon.flterraforged.engine.noise.SeededNoise2D;
import dev.foucaultleon.flterraforged.engine.river.RiverModel;
import dev.foucaultleon.flterraforged.engine.river.RiverSettings;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainClassificationSettings;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainClassifier;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainModel;
import dev.foucaultleon.flterraforged.engine.terrain.populator.TerrainPopulator;
import dev.foucaultleon.flterraforged.engine.terrain.provider.DefaultTerrainProvider;
import dev.foucaultleon.flterraforged.engine.terrain.provider.TerrainProvider;
import dev.foucaultleon.flterraforged.engine.terrain.region.TerrainRegionSampler;
import java.util.Objects;

/**
 * Fully assembled, immutable world-generation pipeline for one world seed.
 *
 * <p>The class is the single composition root for all engine stages. It guarantees the ordering
 * {@code continent -> terrain -> erosion -> river -> climate} and prevents individual stages from
 * being accidentally wired against different continent or terrain sources.</p>
 */
public final class WorldgenPipeline implements CellLookup {

    private static final long CONTINENT_SEED = 0x27D4EB2F165667C5L;
    private static final long ROLLING_SEED = 0x9E3779B97F4A7C15L;
    private static final long RIDGE_SEED = 0x94D049BB133111EBL;
    private static final long DETAIL_SEED = 0xC2B2AE3D27D4EB4FL;
    private static final long TEMPERATURE_SEED = 0xD6E8FEB86659FD93L;
    private static final long MOISTURE_SEED = 0xA5A3564E27F3A21DL;
    private static final long TERRAIN_REGION_SEED = 0xDB4F0B9175AE2165L;
    private static final long EROSION_SEED = 0x165667B19E3779F9L;
    private static final long RIVER_SEED = 0x85EBCA77C2B2AE63L;
    private static final long CLIMATE_REGION_SEED = 0xC6BC279692B5C323L;

    private final EngineContext context;
    private final TerrainModel terrain;
    private final RiverModel river;
    private final ClimateModel climate;
    private final TerrainClassifier classifier;

    /**
     * Creates and coordinates every world-generation stage for one world.
     *
     * @param context immutable world context
     * @param settings tuned engine settings
     */
    public WorldgenPipeline(EngineContext context, EngineSettings settings) {
        this.context = Objects.requireNonNull(context, "context");
        Objects.requireNonNull(settings, "settings");
        long seed = context.seed();

        Continent continent = new AdvancedContinent(seed ^ CONTINENT_SEED, ContinentSettings.from(settings));
        Noise2D rollingNoise = fractal(seed, ROLLING_SEED, settings.terrainScale(), 4, 0.52D, 2.02D);
        Noise2D ridgeNoise = fractal(seed, RIDGE_SEED, settings.terrainScale() * 0.78D, 5, 0.52D, 2.05D);
        Noise2D detailNoise = fractal(seed, DETAIL_SEED, settings.detailScale(), 3, 0.45D, 2.15D);

        TerrainRegionSampler terrainRegions = new TerrainRegionSampler(
                seed ^ TERRAIN_REGION_SEED,
                settings.terrainRegionScale(),
                settings.terrainRegionJitter());
        TerrainProvider terrainProvider = new DefaultTerrainProvider(
                rollingNoise,
                ridgeNoise,
                detailNoise,
                settings.relief(),
                settings.mountainRelief());
        TerrainPopulator baseTerrain = new TerrainPopulator(
                context,
                continent,
                terrainRegions,
                terrainProvider,
                settings.terrainBlendWidth());

        CellLookup baseLookup = (x, z, target) -> baseTerrain.populate(target, x, z);
        ErosionPipeline erosion = new ErosionPipeline(
                seed ^ EROSION_SEED,
                context,
                baseLookup,
                ErosionSettings.from(settings));
        this.river = new RiverModel(
                seed ^ RIVER_SEED,
                context,
                erosion,
                baseLookup,
                RiverSettings.from(settings));
        this.terrain = new TerrainModel(context, river);

        ClimateSettings climateSettings = ClimateSettings.from(settings);
        ClimateRegionSampler climateRegions = new ClimateRegionSampler(
                seed ^ CLIMATE_REGION_SEED,
                climateSettings.regionScale(),
                climateSettings.regionJitter());
        Noise2D temperatureNoise = fractal(seed, TEMPERATURE_SEED, 1.0D, 3, 0.50D, 2.0D);
        Noise2D moistureNoise = fractal(seed, MOISTURE_SEED, 1.0D, 3, 0.50D, 2.0D);
        this.climate = new ClimateModel(
                context,
                river,
                temperatureNoise,
                moistureNoise,
                climateRegions,
                climateSettings);
        this.classifier = new TerrainClassifier(TerrainClassificationSettings.from(settings));
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        sampleCell(x, z, target);
    }

    /**
     * Samples all pipeline stages and final derived signals into a new cell.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return fully populated cell
     */
    public Cell sampleCell(int x, int z) {
        return sampleCell(x, z, new Cell());
    }

    /**
     * Samples all pipeline stages and final derived signals into caller-owned storage.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param target reusable target cell
     * @return {@code target}
     */
    public Cell sampleCell(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        climate.lookup(x, z, target);
        double west = terrain.surfaceHeight(x - 1, z);
        double east = terrain.surfaceHeight(x + 1, z);
        double north = terrain.surfaceHeight(x, z - 1);
        double south = terrain.surfaceHeight(x, z + 1);
        target.gradient = Math.hypot((east - west) * 0.5D, (south - north) * 0.5D);
        return target;
    }

    /**
     * Produces the stable Engine API sample for a world position.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return completed terrain sample
     */
    public TerrainSample sample(int x, int z) {
        Cell center = sampleCell(x, z);
        double continentalness = center.continentEdge * 2.0D - 1.0D;
        ClimateSample climateSample = new ClimateSample(center.temperature, center.moisture);
        RiverSample riverSample = new RiverSample(center.riverDistance, center.riverWidth, center.riverDepth);
        TerrainType type = classifier.classify(
                center.terrain,
                center.height,
                context.seaLevel(),
                center.gradient,
                continentalness,
                riverSample);
        return new TerrainSample(
                center.height,
                center.gradient,
                center.erosion,
                continentalness,
                type,
                climateSample,
                riverSample);
    }

    /**
     * Samples only the final post-river surface height without running climate.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return final continuous surface height
     */
    public double surfaceHeight(int x, int z) {
        return terrain.surfaceHeight(x, z);
    }

    /**
     * Returns the immutable world context used by this pipeline.
     *
     * @return world context
     */
    public EngineContext context() {
        return context;
    }

    private static Noise2D fractal(
            long worldSeed,
            long seedOffset,
            double frequency,
            int octaves,
            double gain,
            double lacunarity) {
        Noise source = new GradientNoise(seedOffset, frequency, Interpolation.QUINTIC);
        return new SeededNoise2D(new FractalNoise(source, octaves, gain, lacunarity), worldSeed);
    }
}
