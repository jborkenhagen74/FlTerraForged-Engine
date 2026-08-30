package dev.foucaultleon.flterraforged.engine.cell;

/** A generation stage that mutates one caller-owned cell for a coordinate. */
@FunctionalInterface
public interface CellPopulator {

    /**
     * Applies this stage to a cell.
     *
     * @param cell mutable target cell
     * @param x world X coordinate
     * @param z world Z coordinate
     */
    void apply(Cell cell, int x, int z);
}
