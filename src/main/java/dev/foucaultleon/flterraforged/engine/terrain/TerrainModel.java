package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import java.util.Objects;

/**
 * Engine terrain pipeline that resolves base landforms and then applies currently available
 * post-shaping stages.
 */
public final class TerrainModel {

    private final EngineContext context;
    private final CellLookup terrain;

    /**
     * Creates the terrain pipeline.
     *
     * @param context immutable world context
     * @param terrain fully shaped terrain lookup including erosion and rivers
     */
    public TerrainModel(EngineContext context, CellLookup terrain) {
        this.context = Objects.requireNonNull(context, "context");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
    }

    /**
     * Resolves all currently implemented terrain signals into a new cell.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return populated cell
     */
    public Cell sampleCell(int x, int z) {
        return sampleCell(x, z, new Cell());
    }

    /**
     * Resolves all currently implemented terrain signals into caller-owned storage.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param target reusable target cell
     * @return {@code target}
     */
    public Cell sampleCell(int x, int z, Cell target) {
        terrain.lookup(x, z, target);
        target.height = Maths.clamp(
                target.height,
                context.minY() + 1.0D,
                context.maxYExclusive() - 2.0D);
        return target;
    }

    /**
     * Samples the final continuous surface height.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return continuous surface height
     */
    public double surfaceHeight(int x, int z) {
        return sampleCell(x, z).height;
    }
}
