package dev.foucaultleon.flterraforged.engine.cell;

import java.util.List;
import java.util.Objects;

/**
 * Ordered, immutable pipeline of {@link CellPopulator} stages.
 *
 * <p>The pipeline object is thread-safe as long as its populators are thread-safe. Each invocation
 * resets and fills a caller-owned {@link Cell}, so the field itself owns no mutable sampling state.</p>
 */
public final class CellField implements CellLookup {

    private final List<CellPopulator> populators;

    /**
     * Creates a cell field from ordered generation stages.
     *
     * @param populators ordered generation stages
     */
    public CellField(List<? extends CellPopulator> populators) {
        Objects.requireNonNull(populators, "populators");
        this.populators = List.copyOf(populators);
        if (this.populators.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("populators contains null");
        }
    }

    /**
     * Creates a cell field from ordered generation stages.
     *
     * @param populators ordered generation stages
     * @return cell field
     */
    public static CellField of(CellPopulator... populators) {
        Objects.requireNonNull(populators, "populators");
        return new CellField(List.of(populators));
    }

    /**
     * Returns the immutable generation-stage list.
     *
     * @return generation stages
     */
    public List<CellPopulator> populators() {
        return populators;
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target").reset();
        for (CellPopulator populator : populators) {
            populator.apply(target, x, z);
        }
    }
}
