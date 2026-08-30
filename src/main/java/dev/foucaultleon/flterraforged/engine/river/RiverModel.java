package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;

/** Bootstrap hydrology field represented as a distance to a procedural centerline. */
public final class RiverModel {

    private final Noise2D noise;
    private final double scale;
    private final double maximumDepth;

    public RiverModel(Noise2D noise, double scale, double maximumDepth) {
        this.noise = noise;
        this.scale = scale;
        this.maximumDepth = maximumDepth;
    }

    public RiverSample sample(int x, int z, double continentalness) {
        double field = Math.abs(noise.sample(x * scale, z * scale));
        double width = 4.0D + Maths.map01(continentalness) * 5.0D;
        double distance = field * 64.0D;
        double depth = maximumDepth * (1.0D - Maths.smooth(Maths.clamp(distance / width, 0.0D, 1.0D)));
        return new RiverSample(distance, width, depth);
    }

    public double incision(int x, int z, double continentalness) {
        return sample(x, z, continentalness).depth();
    }
}
