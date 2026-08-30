package dev.foucaultleon.flterraforged.engine.continent;

import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellPopulator;

/**
 * Deterministic continent source used by the engine terrain pipeline.
 *
 * <p>The contract deliberately stops before rivers, biomes and Minecraft registries. Those stages
 * may consume the continent center and edge signals later without becoming part of the continent
 * implementation itself.</p>
 */
public interface Continent extends CellPopulator {

    /**
     * Samples continent data for a world coordinate.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return immutable continent sample
     */
    ContinentSample sample(double x, double z);

    /**
     * Returns only the normalized inward edge value.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return value in {@code [0, 1]}
     */
    default double edgeValue(double x, double z) {
        return sample(x, z).edge();
    }

    /**
     * Returns the nearest stable continent center.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return continent center
     */
    default ContinentCenter nearestCenter(double x, double z) {
        return sample(x, z).center();
    }

    /** {@inheritDoc} */
    @Override
    default void apply(Cell cell, int x, int z) {
        ContinentSample sample = sample(x, z);
        cell.continentId = sample.id();
        cell.continentEdge = sample.edge();
        cell.continentX = sample.center().x();
        cell.continentZ = sample.center().z();
    }
}
