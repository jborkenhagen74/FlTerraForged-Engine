package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.continent.Continent;
import dev.foucaultleon.flterraforged.engine.continent.ContinentSample;
import dev.foucaultleon.flterraforged.engine.erosion.ErosionModel;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import dev.foucaultleon.flterraforged.engine.river.RiverModel;
import java.util.Objects;

/** Bootstrap terrain-shaping pipeline. */
public final class TerrainModel {

    private final EngineContext context;
    private final Continent continent;
    private final Noise2D ridge;
    private final Noise2D detail;
    private final ErosionModel erosion;
    private final RiverModel river;
    private final EngineSettings settings;

    /**
     * Creates the bootstrap terrain model.
     *
     * @param context immutable world context
     * @param continent continent partition and coastline source
     * @param ridge ridge/mountain noise source
     * @param detail fine-detail noise source
     * @param erosion erosion model
     * @param river river model
     * @param settings engine settings
     */
    public TerrainModel(
            EngineContext context,
            Continent continent,
            Noise2D ridge,
            Noise2D detail,
            ErosionModel erosion,
            RiverModel river,
            EngineSettings settings) {
        this.context = Objects.requireNonNull(context, "context");
        this.continent = Objects.requireNonNull(continent, "continent");
        this.ridge = Objects.requireNonNull(ridge, "ridge");
        this.detail = Objects.requireNonNull(detail, "detail");
        this.erosion = Objects.requireNonNull(erosion, "erosion");
        this.river = Objects.requireNonNull(river, "river");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Samples all base terrain signals required by the world sampler.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return terrain point
     */
    public Point samplePoint(int x, int z) {
        ContinentSample continentSample = continent.sample(x, z);
        double continentalness = continentSample.continentalness();
        double erosionValue = erosion.sample(x, z);
        double height = baseHeight(x, z, continentalness, erosionValue);
        height -= river.incision(x, z, continentalness);
        height = Maths.clamp(height, context.minY() + 1.0D, context.maxYExclusive() - 2.0D);
        return new Point(height, erosionValue, continentalness);
    }

    /**
     * Samples only the continuous surface height.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return continuous surface height
     */
    public double surfaceHeight(int x, int z) {
        return samplePoint(x, z).surfaceHeight();
    }

    private double baseHeight(int x, int z, double continentalness, double erosionValue) {
        double ridgeValue = 1.0D - Math.abs(ridge.sample(
                x * settings.terrainScale(),
                z * settings.terrainScale()));
        ridgeValue = ridgeValue * ridgeValue * ridgeValue;
        double detailValue = detail.sample(
                x * settings.detailScale(),
                z * settings.detailScale());

        double landBias = continentalness * settings.relief();
        double mountainMask = Maths.smooth(Maths.map01(continentalness + 0.15D));
        double erosionMask = 1.0D - 0.65D * erosionValue;
        double mountains = ridgeValue * settings.mountainRelief() * mountainMask * erosionMask;
        double detailRelief = detailValue * 5.5D;
        return context.seaLevel() + landBias + mountains + detailRelief;
    }

    /**
     * Base terrain signals for one X/Z position.
     *
     * @param surfaceHeight continuous surface height
     * @param erosion normalized erosion value
     * @param continentalness continentalness signal
     */
    public record Point(double surfaceHeight, double erosion, double continentalness) {
    }
}
