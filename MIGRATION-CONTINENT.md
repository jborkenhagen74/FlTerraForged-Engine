# Continent migration boundary

## Purpose

`continent` is the third engine subsystem migrated after `noise` and `cell`.
It owns mathematical partitioning of the horizontal plane into tectonic
continent cells and produces semantic continent signals for later stages.

## Migrated concepts

The new `AdvancedContinent` retains the useful modern TerraForged-family ideas:

- domain-warped tectonic coordinates;
- deterministic jittered cell points;
- nearest-cell continent ownership;
- perpendicular-bisector distance to neighboring cells;
- stable normalized continent IDs;
- corrected world-space continent centers;
- optional cell skipping;
- per-cell size variance;
- small-scale coast modulation.

## Deliberate differences

The implementation is not a direct package copy.

### No river-map ownership

ReTerraForged's `Continent` exposes a `Rivermap` and its advanced continent owns
a river cache. FlTerraForged-Engine deliberately does not. Continent geometry
and hydrology are separate subsystems so either can later be replaced or cached
independently.

### No Minecraft control points

The continent module returns a normalized inward-edge signal in `[0, 1]` and a
conventional continentalness mapping in `[-1, 1]`. Minecraft-specific ocean,
coast, biome and density thresholds belong to FlTerraForged integration or to
later engine terrain policy, not to the continent partition itself.

### No shared mutable sample pool

`ContinentSample` is immutable. `Continent` also implements `CellPopulator` for
pipeline use, but direct hot-path sampling does not require thread-local pooled
cells.

## Active integration

`TerrainModel` now obtains continentalness from `AdvancedContinent`. The old
bootstrap fractal continent noise is no longer active.

## Deferred strategies

FreeTerraForged 1.21.1 contains an `uplift` continent strategy in addition to
`advanced` and `simple`. The external-engine design leaves room for additional
`Continent` implementations, but r8 intentionally establishes only the shared
advanced baseline.
