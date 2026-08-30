package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.erosion.ErosionPipeline;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.river.RiverModel;
import java.util.Objects;

/**
 * Engine terrain pipeline that resolves base landforms and then applies currently available
 * post-shaping stages.
 */
public final class TerrainModel {

    private final EngineContext context;
    private final ErosionPipeline erosion;
    private final RiverModel river;

    /**
     * Creates the terrain pipeline.
     *
     * @param context immutable world context
     * @param erosion physical erosion stage
     * @param river current hydrology stage
     */
    public TerrainModel(
            EngineContext context,
            ErosionPipeline erosion,
            RiverModel river) {
        this.context = Objects.requireNonNull(context, "context");
        this.erosion = Objects.requireNonNull(erosion, "erosion");
        this.river = Objects.requireNonNull(river, "river");
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
        erosion.lookup(x, z, target);
        double continentalness = target.continentEdge * 2.0D - 1.0D;
        target.height -= river.incision(x, z, continentalness);
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
