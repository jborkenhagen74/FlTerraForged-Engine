package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import java.util.Objects;

/** Bootstrap hydrology field represented as a distance to a procedural centerline. */
public final class RiverModel {

    private final Noise2D noise;
    private final double scale;
    private final double maximumDepth;

    /**
     * Creates a procedural river model.
     *
     * @param noise backing river field
     * @param scale spatial river scale
     * @param maximumDepth maximum river incision depth
     */
    public RiverModel(Noise2D noise, double scale, double maximumDepth) {
        this.noise = Objects.requireNonNull(noise, "noise");
        this.scale = scale;
        this.maximumDepth = maximumDepth;
    }

    /**
     * Samples hydrology at an X/Z position.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param continentalness continentalness signal at the position
     * @return river sample
     */
    public RiverSample sample(int x, int z, double continentalness) {
        double field = Math.abs(noise.sample(x * scale, z * scale));
        double width = 4.0D + Maths.map01(continentalness) * 5.0D;
        double distance = field * 64.0D;
        double depth = maximumDepth * (1.0D - Maths.smooth(Maths.clamp(distance / width, 0.0D, 1.0D)));
        return new RiverSample(distance, width, depth);
    }

    /**
     * Returns the terrain incision caused by the procedural river field.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param continentalness continentalness signal at the position
     * @return river incision depth
     */
    public double incision(int x, int z, double continentalness) {
        return sample(x, z, continentalness).depth();
    }
}
