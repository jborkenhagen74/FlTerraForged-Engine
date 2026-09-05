package dev.foucaultleon.flterraforged.engine.river;

/**
 * Authoritative owner of the final water state for one Engine X/Z column.
 *
 * <p>The order is intentional. Open ocean owns overlapping marine receiver space, an explicit lake
 * owns bounded inland receiver space, a river owns its connected channel, and dry terrain has no
 * water owner. The resolver uses this priority only when multiple hydraulic candidates are valid for
 * the same column.</p>
 */
public enum ResolvedWaterOwner {
    /** No material water is present. */
    DRY(0),
    /** A linear river channel owns the column. */
    RIVER(1),
    /** A bounded lake receiver owns the column. */
    LAKE(2),
    /** Open ocean or sea owns the column. */
    OCEAN(3);

    private final int priority;

    ResolvedWaterOwner(int priority) {
        this.priority = priority;
    }

    /**
     * Returns the deterministic ownership priority.
     *
     * @return increasing priority where a larger value wins
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns whether this owner represents material water.
     *
     * @return {@code true} for river, lake and ocean ownership
     */
    public boolean wet() {
        return this != DRY;
    }
}
