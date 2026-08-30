package dev.foucaultleon.flterraforged.engine.cell;

import dev.foucaultleon.flterraforged.engine.noise.Noise2D;

/**
 * Reserved semantic cell field for later TerraForged-style cell migration.
 * The bootstrap engine uses it only as a stable macro-region modulation.
 */
public final class CellField {

    private final Noise2D source;
    private final double scale;

    public CellField(Noise2D source, double scale) {
        this.source = source;
        this.scale = scale;
    }

    public double sample(int x, int z) {
        return source.sample(x * scale, z * scale);
    }
}
