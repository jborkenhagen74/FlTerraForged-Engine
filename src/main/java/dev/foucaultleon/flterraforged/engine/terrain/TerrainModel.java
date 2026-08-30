package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.erosion.ErosionModel;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import dev.foucaultleon.flterraforged.engine.river.RiverModel;

/** Bootstrap terrain-shaping pipeline. */
public final class TerrainModel {

    private final EngineContext context;
    private final Noise2D continent;
    private final Noise2D ridge;
    private final Noise2D detail;
    private final ErosionModel erosion;
    private final RiverModel river;
    private final EngineSettings settings;

    public TerrainModel(
            EngineContext context,
            Noise2D continent,
            Noise2D ridge,
            Noise2D detail,
            ErosionModel erosion,
            RiverModel river,
            EngineSettings settings) {
        this.context = context;
        this.continent = continent;
        this.ridge = ridge;
        this.detail = detail;
        this.erosion = erosion;
        this.river = river;
        this.settings = settings;
    }

    public Point samplePoint(int x, int z) {
        double continentalness = continent.sample(
                x * settings.continentScale(),
                z * settings.continentScale());
        double erosionValue = erosion.sample(x, z);
        double height = baseHeight(x, z, continentalness, erosionValue);
        height -= river.incision(x, z, continentalness);
        height = Maths.clamp(height, context.minY() + 1.0D, context.maxYExclusive() - 2.0D);
        return new Point(height, erosionValue, continentalness);
    }

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

    public record Point(double surfaceHeight, double erosion, double continentalness) {
    }
}
