# Continent migration boundary

## Purpose

`continent` is the third engine subsystem migrated after `noise` and `cell`.
It owns mathematical partitioning of the horizontal plane into tectonic
continent cells and produces semantic continent signals for later stages.

## Completed advanced-continent foundation

The advanced implementation now contains two distinct cell concepts:

- `continent.ContinentCell` is a caller-owned geometric workspace used only
  while resolving one warped Voronoi continent. It tracks the warped sample,
  nearest jittered owner point, nearest boundary-forming neighbor, owner
  distance, boundary distance, neighbor centroid and skipping state.
- `cell.Cell` is the general cross-stage engine carrier. It receives the
  finished continent ID, edge value and corrected center, then continues
  through terrain, erosion, river and climate stages.

This split preserves the useful intermediate geometry without polluting the
shared generation cell with implementation-specific Voronoi details.

## Migrated concepts

The completed `AdvancedContinent` foundation retains the useful modern
TerraForged-family ideas:

- domain-warped tectonic coordinates;
- deterministic jittered cell points;
- nearest-cell continent ownership;
- explicit caller-owned continent-cell workspace;
- nearest boundary-forming neighbor tracking;
- perpendicular-bisector distance to neighboring points;
- neighbor-centroid based center correction;
- stable normalized continent IDs;
- corrected world-space continent centers;
- optional cell skipping;
- per-cell size variance;
- small-scale coast modulation;
- directional distance search to the owning continent boundary;
- directional distance search to an engine-neutral edge threshold.

## Deliberate differences

The implementation is not a direct package copy.

### No river-map ownership

ReTerraForged's historical advanced continent base also owns river-cache state.
FlTerraForged-Engine deliberately does not. Continent geometry and hydrology are
separate subsystems so either can later be replaced or cached independently.

### No Minecraft control points

ReTerraForged's distance-to-ocean helper consumes terrain control points. The
external engine instead exposes `distanceToEdgeThreshold(...)`, which receives a
normalized threshold. FlTerraForged or later engine terrain policy may map its
own shallow-ocean semantics to that value without introducing Minecraft types.

### No shared mutable sample pool

The upstream implementation obtains reusable general cells from a resource
pool. FlTerraForged-Engine uses caller-owned `ContinentCell` workspaces instead.
A caller may reuse a workspace explicitly, but there is no hidden ThreadLocal or
global mutable sample cache in the continent foundation.

### No biome ownership

The continent layer emits geometric and semantic continent signals only. Biome
selection remains outside the external engine's continent implementation.

## Active integration

`TerrainModel` obtains continentalness from `AdvancedContinent`. The old
bootstrap fractal continent noise is no longer active. `Continent` also
implements `CellPopulator`, so the final sample is copied into the general
engine `Cell` as:

```text
continentId
continentEdge
continentX
continentZ
```

## Deferred strategies

FreeTerraForged 1.21.1 contains an `uplift` continent strategy in addition to
`advanced` and `simple`. Those are alternative `Continent` implementations, not
missing pieces of the completed advanced foundation. They can be added later
without changing the current `Continent` contract.
