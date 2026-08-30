package dev.foucaultleon.flterraforged.engine.cell;

/**
 * Allocation-conscious lookup contract that populates a caller-owned {@link Cell}.
 *
 * <p>Using a caller-owned target avoids global mutable caches and keeps the lookup safe for parallel
 * chunk-generation workloads.</p>
 */
@FunctionalInterface
public interface CellLookup {

    /**
     * Populates a target cell for a coordinate.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param target caller-owned target cell
     */
    void lookup(int x, int z, Cell target);

    /**
     * Allocates and populates a new cell for convenience code and tests.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return populated cell
     */
    default Cell lookup(int x, int z) {
        Cell result = new Cell();
        lookup(x, z, result);
        return result;
    }
}
