package dev.foucaultleon.flterraforged.engine.climate;

import dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;

/** Bootstrap continuous temperature/moisture model. */
public final class ClimateModel {

    private final Noise2D temperature;
    private final Noise2D moisture;
    private final double scale;

    public ClimateModel(Noise2D temperature, Noise2D moisture, double scale) {
        this.temperature = temperature;
        this.moisture = moisture;
        this.scale = scale;
    }

    public ClimateSample sample(int x, int z, double height, int seaLevel) {
        double temperatureValue = Maths.map01(temperature.sample(x * scale, z * scale));
        double moistureValue = Maths.map01(moisture.sample(x * scale, z * scale));
        double altitudeCooling = Math.max(0.0D, height - seaLevel) / 300.0D;
        return new ClimateSample(
                Maths.clamp(temperatureValue - altitudeCooling, 0.0D, 1.0D),
                moistureValue);
    }
}
