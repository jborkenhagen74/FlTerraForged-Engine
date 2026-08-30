package dev.foucaultleon.flterraforged.engine.erosion;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;

/** Bootstrap erosion field. Higher values suppress ridge relief. */
public final class ErosionModel {

    private final Noise2D noise;
    private final double scale;

    public ErosionModel(Noise2D noise, double scale) {
        this.noise = noise;
        this.scale = scale;
    }

    public double sample(int x, int z) {
        return Maths.map01(noise.sample(x * scale, z * scale));
    }
}
