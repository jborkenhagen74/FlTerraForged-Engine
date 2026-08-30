# Upstream provenance

## r12: river / rivermap foundation

The hydrology migration uses the TerraForged-family separation between terrain
shape and river-map generation as its architectural reference. ReTerraForged
keeps river-map concepts as a distinct world-generation concern, while
FreeTerraForged continues that lineage and publicly describes later 3D rivers and
waterfalls among its added features.

FlTerraForged-Engine does not copy Minecraft-specific river placement. The r12
implementation is a Java-only rewrite using globally aligned D8 drainage nodes,
flow accumulation and immutable directed segments. It intentionally keeps
`Rivermap` ownership out of `Continent`, applies incision after the physical
erosion stage, and leaves water blocks, river biomes, surface rules, lakes,
waterfalls and explicit public water-surface elevation for later layers.

Reference repositories:

- https://github.com/racoonman2/ReTerraForged (1.20.1 lineage)
- https://github.com/ETcodehome/FreeTerraForged (1.21.1 lineage)
- https://github.com/TerraForged/TerraForged

## r11: erosion foundation

The erosion migration uses TerraForged's documented simulated-erosion behavior
and the ReTerraForged cell/filter separation as architectural references.

- **TerraForged project documentation** describes hydraulic erosion as virtual
  water droplets moving downhill while eroding, carrying and depositing material.
- **ReTerraForged branch `1.20.1`** exposes a dedicated `worldgen/cell/filter/`
  layer in addition to heightmap/continent/terrain/rivermap stages.
- **FreeTerraForged branch `1.21.1_V0.0.6005`** was checked as the later target
  branch; its root `worldgen/cell/` layout no longer exposes the same `filter/`
  directory, so the old package structure is not treated as a compatibility
  contract.

FlTerraForged-Engine implements its hydraulic and thermal erosion code as a
Java-only rewrite over the continuous base terrain field. No Minecraft density
filters, world/chunk objects, biome features, Mojang codecs or loader hooks are
imported. The region/cache strategy is new to this engine and is designed around
its deterministic concurrent-sampling contract.

## r10: terrain foundation

The terrain migration uses the package architecture visible in both target
reference branches as its primary structural reference.

- **ReTerraForged branch `1.20.1`**
  - `cell/terrain/` contains `Blender`, `CompositeTerrain`, `ConfiguredTerrain`,
    `ITerrain`, `Terrain`, `TerrainCategory` and `TerrainType`;
  - the package is separated further into `populator`, `provider` and `region`.
- **FreeTerraForged branch `1.21.1_V0.0.6005`**
  - retains the same core terrain/provider/populator/region split;
  - additionally contains `IslandBlender`, which is deliberately deferred until
    island generation is treated as an optional terrain strategy.

FlTerraForged-Engine rewrites these concepts around its Java-only `Cell`,
`TerrainType`, noise and continent contracts. No Mojang codec, Minecraft density
function, biome holder, block, surface rule or loader type is imported. The
current default provider and region partition are new engine-neutral
implementations rather than source-compatible copies.

## r8: continent foundation

The continent migration uses the modern ReTerraForged/FreeTerraForged continent
layout as an algorithmic reference, but intentionally narrows the external
engine contract.

Reference sources used for this step:

- **ReTerraForged branch `1.20.1`**
  - `cell/continent/Continent.java` — continent-as-cell-populator contract,
    edge sampling and stable center semantics. Its direct `Rivermap` ownership
    is deliberately *not* carried into FlTerraForged-Engine.
  - `cell/continent/advanced/AbstractContinent.java` — continent scale, jitter,
    optional cell skipping, stable cell values and center/edge search concepts.
  - `cell/continent/advanced/AdvancedContinentGenerator.java` — warped jittered
    cell points, nearest-owner selection, perpendicular-bisector boundary
    distance, size variance, coast modulation and corrected continent centers.
- **FreeTerraForged branch `1.21.1_V0.0.6005`**
  - later continent package layout was checked before fixing the new boundary;
    it retains `advanced` and `simple` strategies and introduces an `uplift`
    strategy that is intentionally deferred to a later optional implementation.

FlTerraForged's implementation is rewritten around Java-only `double` samples,
its own deterministic hashing/noise primitives and immutable `ContinentSample`
results. Minecraft control points, biome decisions and river-map caches are not
part of this module.

## r7: noise and cell foundation

The first migration step uses the TerraForged family as an architectural and
algorithmic reference while keeping the external engine independent from
Minecraft. The implementation in this repository is rewritten under the
`dev.foucaultleon.flterraforged.engine` namespace and deliberately removes
Mojang `Codec`, Minecraft registry/biome types and loader integration.

Reference sources used for this step:

- **ReTerraForged branch `1.20.1`**
  - `world/worldgen/noise/` — modular split into `domain`, `function`, `module`
    and utility primitives.
  - `world/worldgen/cell/Cell.java`, `CellLookup.java`, `CellPopulator.java` —
    cell-oriented intermediate generation model.
- **FreeTerraForged branch `1.21.1_V0.0.6005`**
  - `world/worldgen/noise/` and `world/worldgen/cell/` — checked as the later
    continuation of the same architecture before defining the FlTerraForged
    boundary.
- **TerraForged/Noise2D**
  - original modular 2D-noise design lineage and deterministic noise concepts.

The migrated code is not a drop-in copy of those packages. FlTerraForged uses
its own seed-aware Java-only contracts and caller-owned cell state so the engine
can remain replaceable and safe for parallel sampling.

### Licensing/attribution

The referenced TerraForged-family projects are MIT-licensed. TerraForged's
original project carries Copyright (c) 2020 TerraForged, and TerraForged/Noise2D
carries Copyright (c) 2018 dags. ReTerraForged is also published under MIT, and
FreeTerraForged states that historic contributions continue under MIT. Keep
these sources and notices recorded when later migration steps become more
closely derived from upstream implementations.

## Planned references for later stages

- erosion mathematics;
- climate mathematics;
- deterministic caches that do not depend on Minecraft.

AronaLayers-gen and AronaLayers-extras remain references for FlTerraForged's
surface/layer integration and are not part of the external engine migration.
