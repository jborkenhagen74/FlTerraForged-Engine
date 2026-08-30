package dev.foucaultleon.flterraforged.engine.cell;

import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import java.util.Objects;

/**
 * Reserved semantic cell field for later TerraForged-style cell migration.
 * The bootstrap engine uses it only as a stable macro-region modulation.
 */
public final class CellField {

    private final Noise2D source;
    private final double scale;

    /**
     * Creates a scaled cell field.
     *
     * @param source backing noise source
     * @param scale spatial sampling scale
     */
    public CellField(Noise2D source, double scale) {
        this.source = Objects.requireNonNull(source, "source");
        this.scale = scale;
    }

    /**
     * Samples the macro-region field at an X/Z position.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return field value
     */
    public double sample(int x, int z) {
        return source.sample(x * scale, z * scale);
    }
}
