package dev.foucaultleon.flterraforged.engine.noise;

import dev.foucaultleon.flterraforged.engine.noise.domain.Domain;
import dev.foucaultleon.flterraforged.engine.noise.module.BinaryNoise;
import dev.foucaultleon.flterraforged.engine.noise.module.ClampNoise;
import dev.foucaultleon.flterraforged.engine.noise.module.ConstantNoise;
import dev.foucaultleon.flterraforged.engine.noise.module.MapNoise;
import dev.foucaultleon.flterraforged.engine.noise.module.WarpNoise;

/** Factory methods for composing engine noise graphs without serialization dependencies. */
public final class Noises {

    private Noises() {
    }

    /**
     * Creates a constant field.
     *
     * @param value constant value
     * @return constant field
     */
    public static Noise constant(double value) {
        return new ConstantNoise(value);
    }

    /**
     * Adds two fields.
     *
     * @param left left field
     * @param right right field
     * @return sum field
     */
    public static Noise add(Noise left, Noise right) {
        return BinaryNoise.add(left, right);
    }

    /**
     * Multiplies two fields.
     *
     * @param left left field
     * @param right right field
     * @return product field
     */
    public static Noise multiply(Noise left, Noise right) {
        return BinaryNoise.multiply(left, right);
    }

    /**
     * Clamps a field.
     *
     * @param source source field
     * @param min minimum
     * @param max maximum
     * @return clamped field
     */
    public static Noise clamp(Noise source, double min, double max) {
        return new ClampNoise(source, min, max);
    }

    /**
     * Maps a field from its nominal range to another range.
     *
     * @param source source field
     * @param min target minimum
     * @param max target maximum
     * @return mapped field
     */
    public static Noise map(Noise source, double min, double max) {
        return new MapNoise(source, min, max);
    }

    /**
     * Samples a field through a coordinate domain.
     *
     * @param source source field
     * @param domain coordinate domain
     * @return warped field
     */
    public static Noise warp(Noise source, Domain domain) {
        return new WarpNoise(source, domain);
    }
}
