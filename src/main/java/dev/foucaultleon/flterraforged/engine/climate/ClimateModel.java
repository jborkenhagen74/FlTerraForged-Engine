package dev.foucaultleon.flterraforged.engine.climate;

import dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import java.util.Objects;

/** Bootstrap continuous temperature/moisture model. */
public final class ClimateModel {

    private final Noise2D temperature;
    private final Noise2D moisture;
    private final double scale;

    /**
     * Creates a climate model.
     *
     * @param temperature temperature noise source
     * @param moisture moisture noise source
     * @param scale spatial climate scale
     */
    public ClimateModel(Noise2D temperature, Noise2D moisture, double scale) {
        this.temperature = Objects.requireNonNull(temperature, "temperature");
        this.moisture = Objects.requireNonNull(moisture, "moisture");
        this.scale = scale;
    }

    /**
     * Samples temperature and moisture at a terrain position.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param height terrain surface height
     * @param seaLevel world sea level
     * @return climate sample
     */
    public ClimateSample sample(int x, int z, double height, int seaLevel) {
        double temperatureValue = Maths.map01(temperature.sample(x * scale, z * scale));
        double moistureValue = Maths.map01(moisture.sample(x * scale, z * scale));
        double altitudeCooling = Math.max(0.0D, height - seaLevel) / 300.0D;
        return new ClimateSample(
                Maths.clamp(temperatureValue - altitudeCooling, 0.0D, 1.0D),
                moistureValue);
    }
}
