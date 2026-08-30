package dev.foucaultleon.flterraforged.engine.continent;

/**
 * Deterministic jittered point owned by one tectonic continent grid cell.
 *
 * @param cellX integer tectonic-grid X coordinate
 * @param cellZ integer tectonic-grid Z coordinate
 * @param x point X coordinate in normalized continent-cell space
 * @param z point Z coordinate in normalized continent-cell space
 */
public record ContinentPoint(int cellX, int cellZ, double x, double z) {
}
