package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.climate.ClimateModel;
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
import dev.foucaultleon.flterraforged.engine.terrain.TerrainClassifier;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainModel;
import dev.foucaultleon.flterraforged.engine.terrain.populator.TerrainPopulator;
import dev.foucaultleon.flterraforged.engine.terrain.provider.DefaultTerrainProvider;
import dev.foucaultleon.flterraforged.engine.terrain.provider.TerrainProvider;
import dev.foucaultleon.flterraforged.engine.terrain.region.TerrainRegionSampler;
import java.util.Objects;

/** Seed-bound, immutable and thread-safe world sampler. */
public final class DefaultTerrainWorld implements TerrainWorld {

    private final EngineContext context;
    private final TerrainModel terrain;
    private final ClimateModel climate;
    private final RiverModel river;
    private final TerrainClassifier classifier;

    /**
     * Creates a deterministic terrain view for one world.
     *
     * @param context immutable world context
     * @param settings immutable engine settings
     */
    public DefaultTerrainWorld(EngineContext context, EngineSettings settings) {
        this.context = Objects.requireNonNull(context, "context");
        Objects.requireNonNull(settings, "settings");
        long seed = context.seed();

        Continent continent = new AdvancedContinent(
                seed ^ 0x27D4EB2F165667C5L,
                ContinentSettings.from(settings));
        Noise2D rollingNoise = fractal(
                seed,
                0x9E3779B97F4A7C15L,
                settings.terrainScale(),
                4,
                0.52D,
                2.02D);
        Noise2D ridgeNoise = fractal(
                seed,
                0x94D049BB133111EBL,
                settings.terrainScale() * 0.78D,
                5,
                0.52D,
                2.05D);
        Noise2D detailNoise = fractal(
                seed,
                0xC2B2AE3D27D4EB4FL,
                settings.detailScale(),
                3,
                0.45D,
                2.15D);
        Noise2D temperatureNoise = fractal(seed, 0xD6E8FEB86659FD93L, 1.0D, 3, 0.50D, 2.0D);
        Noise2D moistureNoise = fractal(seed, 0xA5A3564E27F3A21DL, 1.0D, 3, 0.50D, 2.0D);

        TerrainRegionSampler regions = new TerrainRegionSampler(
                seed ^ 0xDB4F0B9175AE2165L,
                settings.terrainRegionScale(),
                settings.terrainRegionJitter());
        TerrainProvider provider = new DefaultTerrainProvider(
                rollingNoise,
                ridgeNoise,
                detailNoise,
                settings.relief(),
                settings.mountainRelief());
        TerrainPopulator populator = new TerrainPopulator(
                context,
                continent,
                regions,
                provider,
                settings.terrainBlendWidth());
        ErosionPipeline erosion = new ErosionPipeline(
                seed ^ 0x165667B19E3779F9L,
                context,
                (x, z, target) -> populator.populate(target, x, z),
                ErosionSettings.from(settings));
        this.river = new RiverModel(
                seed ^ 0x85EBCA77C2B2AE63L,
                context,
                erosion,
                (x, z, target) -> populator.populate(target, x, z),
                RiverSettings.from(settings));
        this.terrain = new TerrainModel(context, this.river);
        this.climate = new ClimateModel(
                temperatureNoise,
                moistureNoise,
                settings.climateScale());
        this.classifier = new TerrainClassifier();
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

    /** {@inheritDoc} */
    @Override
    public EngineContext context() {
        return context;
    }

    /** {@inheritDoc} */
    @Override
    public TerrainSample sample(int x, int z) {
        Cell center = terrain.sampleCell(x, z);
        double west = terrain.surfaceHeight(x - 1, z);
        double east = terrain.surfaceHeight(x + 1, z);
        double north = terrain.surfaceHeight(x, z - 1);
        double south = terrain.surfaceHeight(x, z + 1);
        double dx = (east - west) * 0.5D;
        double dz = (south - north) * 0.5D;
        double slope = Math.hypot(dx, dz);
        center.gradient = slope;

        double continentalness = center.continentEdge * 2.0D - 1.0D;
        ClimateSample climateSample = climate.sample(x, z, center.height, context.seaLevel());
        RiverSample riverSample = river.sample(x, z);
        TerrainType type = classifier.classify(
                center.terrain,
                center.height,
                context.seaLevel(),
                slope,
                continentalness,
                riverSample);

        return new TerrainSample(
                center.height,
                slope,
                center.erosion,
                continentalness,
                type,
                climateSample,
                riverSample);
    }
}
