package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import dev.foucaultleon.flterraforged.engine.climate.ClimateModel;
import dev.foucaultleon.flterraforged.engine.erosion.ErosionModel;
import dev.foucaultleon.flterraforged.engine.noise.FractalNoise;
import dev.foucaultleon.flterraforged.engine.noise.GradientNoise;
import dev.foucaultleon.flterraforged.engine.noise.Interpolation;
import dev.foucaultleon.flterraforged.engine.noise.Noise;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import dev.foucaultleon.flterraforged.engine.noise.SeededNoise2D;
import dev.foucaultleon.flterraforged.engine.river.RiverModel;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainClassifier;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainModel;
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

        Noise2D continentNoise = fractal(seed, 0x27D4EB2F165667C5L, 4, 0.50D, 2.0D);
        Noise2D ridgeNoise = fractal(seed, 0x9E3779B97F4A7C15L, 5, 0.52D, 2.05D);
        Noise2D detailNoise = fractal(seed, 0xC2B2AE3D27D4EB4FL, 3, 0.45D, 2.15D);
        Noise2D erosionNoise = fractal(seed, 0x165667B19E3779F9L, 3, 0.50D, 2.0D);
        Noise2D riverNoise = fractal(seed, 0x85EBCA77C2B2AE63L, 4, 0.48D, 2.0D);
        Noise2D temperatureNoise = fractal(seed, 0xD6E8FEB86659FD93L, 3, 0.50D, 2.0D);
        Noise2D moistureNoise = fractal(seed, 0xA5A3564E27F3A21DL, 3, 0.50D, 2.0D);

        ErosionModel erosion = new ErosionModel(erosionNoise, settings.terrainScale() * 0.70D);
        this.river = new RiverModel(riverNoise, settings.riverScale(), settings.riverDepth());
        this.terrain = new TerrainModel(
                context,
                continentNoise,
                ridgeNoise,
                detailNoise,
                erosion,
                river,
                settings);
        this.climate = new ClimateModel(
                temperatureNoise,
                moistureNoise,
                settings.climateScale());
        this.classifier = new TerrainClassifier();
    }

    private static Noise2D fractal(
            long worldSeed,
            long seedOffset,
            int octaves,
            double gain,
            double lacunarity) {
        Noise source = new GradientNoise(seedOffset, 1.0D, Interpolation.QUINTIC);
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
        TerrainModel.Point center = terrain.samplePoint(x, z);
        double west = terrain.surfaceHeight(x - 1, z);
        double east = terrain.surfaceHeight(x + 1, z);
        double north = terrain.surfaceHeight(x, z - 1);
        double south = terrain.surfaceHeight(x, z + 1);
        double dx = (east - west) * 0.5D;
        double dz = (south - north) * 0.5D;
        double slope = Math.hypot(dx, dz);

        ClimateSample climateSample = climate.sample(x, z, center.surfaceHeight(), context.seaLevel());
        RiverSample riverSample = river.sample(x, z, center.continentalness());
        TerrainType type = classifier.classify(
                center.surfaceHeight(),
                context.seaLevel(),
                slope,
                center.continentalness(),
                riverSample);

        return new TerrainSample(
                center.surfaceHeight(),
                slope,
                center.erosion(),
                center.continentalness(),
                type,
                climateSample,
                riverSample);
    }
}
