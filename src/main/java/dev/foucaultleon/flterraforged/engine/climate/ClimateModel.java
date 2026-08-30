package dev.foucaultleon.flterraforged.engine.climate;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import java.util.Objects;

/**
 * Minecraft-neutral climate stage combining broad noise, macro regions and terrain feedback.
 *
 * <p>The model writes continuous climate signals and semantic region hints into the shared
 * {@link Cell}. It deliberately does not choose biomes.</p>
 */
public final class ClimateModel implements CellLookup {

    private final EngineContext world;
    private final CellLookup terrain;
    private final Noise2D temperatureNoise;
    private final Noise2D moistureNoise;
    private final ClimateRegionSampler regions;
    private final ClimateSettings settings;

    /**
     * Creates a climate model.
     *
     * @param world immutable world context
     * @param terrain fully shaped terrain lookup
     * @param temperatureNoise broad temperature noise source
     * @param moistureNoise broad moisture noise source
     * @param regions macro climate-region sampler
     * @param settings climate settings
     */
    public ClimateModel(
            EngineContext world,
            CellLookup terrain,
            Noise2D temperatureNoise,
            Noise2D moistureNoise,
            ClimateRegionSampler regions,
            ClimateSettings settings) {
        this.world = Objects.requireNonNull(world, "world");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.temperatureNoise = Objects.requireNonNull(temperatureNoise, "temperatureNoise");
        this.moistureNoise = Objects.requireNonNull(moistureNoise, "moistureNoise");
        this.regions = Objects.requireNonNull(regions, "regions");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        terrain.lookup(x, z, target);
        ClimateRegionSample region = regions.sample(x, z);

        double broadTemperature = Maths.map01(temperatureNoise.sample(
                x * settings.scale(), z * settings.scale()));
        double broadMoisture = Maths.map01(moistureNoise.sample(
                x * settings.scale(), z * settings.scale()));

        double boundary = 1.0D - Maths.smooth(Maths.clamp(region.edge() / settings.regionBlend(), 0.0D, 1.0D));
        double regionalTemperature = Maths.lerp(region.temperature(), region.neighborTemperature(), boundary * 0.5D);
        double regionalMoisture = Maths.lerp(region.moisture(), region.neighborMoisture(), boundary * 0.5D);

        double temperature = broadTemperature * 0.62D + regionalTemperature * 0.38D;
        double moisture = broadMoisture * 0.58D + regionalMoisture * 0.42D;

        double altitude = Math.max(0.0D, target.height - world.seaLevel());
        temperature -= (altitude / 256.0D) * settings.altitudeCooling();

        double coastInfluence = 1.0D - Maths.smooth(target.continentEdge);
        temperature = Maths.lerp(
                temperature,
                0.5D + (temperature - 0.5D) * 0.45D,
                coastInfluence * settings.oceanModeration());

        double interior = Maths.smooth(target.continentEdge);
        moisture -= interior * settings.continentalDryness() * 0.20D;
        moisture += coastInfluence * settings.continentalDryness() * 0.16D;

        double riverInfluence = 1.0D - Maths.smooth(target.riverMask);
        moisture += riverInfluence * settings.riverMoisture();

        temperature = Maths.clamp(temperature, 0.0D, 1.0D);
        moisture = Maths.clamp(moisture, 0.0D, 1.0D);

        target.regionTemperature = regionalTemperature;
        target.regionMoisture = regionalMoisture;
        target.biomeRegionId = region.id();
        target.biomeRegionEdge = region.edge();
        target.macroBiomeId = macroBiomeId(temperature, moisture);
        target.temperature = temperature;
        target.moisture = moisture;
    }

    /**
     * Samples the stable Engine API climate representation.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return continuous temperature and moisture sample
     */
    public ClimateSample sample(int x, int z) {
        Cell cell = lookup(x, z);
        return new ClimateSample(cell.temperature, cell.moisture);
    }

    private static double macroBiomeId(double temperature, double moisture) {
        int temperatureBand = Math.min(4, (int) Math.floor(temperature * 5.0D));
        int moistureBand = Math.min(4, (int) Math.floor(moisture * 5.0D));
        int index = temperatureBand * 5 + moistureBand;
        return index / 24.0D;
    }
}
