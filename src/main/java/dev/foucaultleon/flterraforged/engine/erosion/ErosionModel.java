package dev.foucaultleon.flterraforged.engine.erosion;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import java.util.Objects;

/** Bootstrap erosion field. Higher values suppress ridge relief. */
public final class ErosionModel {

    private final Noise2D noise;
    private final double scale;

    /**
     * Creates an erosion field.
     *
     * @param noise backing erosion noise
     * @param scale spatial erosion scale
     */
    public ErosionModel(Noise2D noise, double scale) {
        this.noise = Objects.requireNonNull(noise, "noise");
        this.scale = scale;
    }

    /**
     * Samples normalized erosion at an X/Z position.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return erosion value in the nominal {@code [0, 1]} range
     */
    public double sample(int x, int z) {
        return Maths.map01(noise.sample(x * scale, z * scale));
    }
}
