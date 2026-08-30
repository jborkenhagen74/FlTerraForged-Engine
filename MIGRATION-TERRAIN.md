# Terrain migration — r10

## Scope

This step migrates the engine-neutral terrain layer after `noise`, `cell` and
`continent`. It uses the ReTerraForged 1.20.1 and FreeTerraForged 1.21.1 terrain
package layout as the architectural reference. Both branches split terrain into
core terrain definitions plus `populator`, `provider` and `region` packages and
contain `Blender`, `CompositeTerrain` and `ConfiguredTerrain` concepts.

The FlTerraForged implementation is a Java-only rewrite around the external
engine boundary. No Mojang codecs, density functions, biome holders, surface
rules or loader hooks are included.

## Added engine structures

```text
terrain/
├── Terrain.java
├── TerrainCategory.java
├── TerrainContext.java
├── ConfiguredTerrain.java
├── CompositeTerrain.java
├── Blender.java
├── TerrainClassifier.java
├── TerrainModel.java
│
├── provider/
│   ├── TerrainProvider.java
│   └── DefaultTerrainProvider.java
│
├── populator/
│   └── TerrainPopulator.java
│
└── region/
    ├── TerrainRegionSample.java
    └── TerrainRegionSampler.java
```

## Pipeline

```text
AdvancedContinent
       │
       ▼
ContinentSample
       │
       ├───────────────┐
       ▼               ▼
TerrainRegionSampler   ErosionModel
       │               │
       └──────┬────────┘
              ▼
       TerrainProvider
              │
      primary + neighbor
              │
              ▼
           Blender
              │
              ▼
       TerrainPopulator
              │
              ▼
             Cell
```

The terrain-region partition is independent from continent ownership. Large
continents can therefore contain several landform regions such as plains,
hills, valleys, plateaus and mountains.

## Terrain regions

`TerrainRegionSampler` uses a deterministic jittered Voronoi partition. Each
sample exposes:

- a stable owner selector (`id`);
- a stable nearest-neighbor selector (`neighborId`);
- normalized distance from the closest region boundary (`edge`);
- owner cell coordinates.

The owner and neighbor selectors are mapped by `TerrainProvider` to terrain
definitions. `Blender` uses the region edge to produce a `CompositeTerrain`
close to boundaries, avoiding hard height discontinuities between landforms.

## Cell integration

`TerrainPopulator` writes the following common pipeline signals:

- `continentId`, `continentEdge`, `continentX`, `continentZ`;
- `terrainRegionId`, `terrainRegionEdge`;
- `erosion`;
- `weirdness`;
- `terrain`;
- `height` and `heightErosion`.

This is the intended role of the general engine `Cell`: it carries finished or
intermediate signals between generation stages. Temporary geometric details
remain in specialized subsystem workspaces such as `ContinentCell`.

## Deliberately deferred

The following upstream concepts are not part of r10 yet:

- Minecraft/Mojang terrain codecs;
- biome-aware terrain selection;
- density-function adapters;
- surface rules and block placement;
- FreeTerraForged island-specific blending (`IslandBlender`);
- full migrated erosion filters;
- full migrated river/hydrology maps;
- climate-based terrain providers.

`ErosionModel` and `RiverModel` remain bootstrap post/input stages until their
own dedicated migrations.
