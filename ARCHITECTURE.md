# Architecture

## Boundary

```text
FlTerraForged (Minecraft integration)
          |
          | flterraforged-engine-api
          v
FlTerraForged-Engine (this repository)
```

The engine produces semantic terrain information. It never produces Minecraft
`BlockState`, biome holders, registry keys or density-function objects.

## Runtime flow

```text
DefaultEngineProvider
        |
        v
DefaultTerrainEngine
        |
        +-- immutable EngineSettings
        |
        v
DefaultTerrainWorld (one seed/world)
        |
        +-- AdvancedContinent
        +-- TerrainModel
        |    +-- TerrainRegionSampler
        |    +-- TerrainProvider
        |    +-- TerrainPopulator
        |    +-- Blender / CompositeTerrain
        |    +-- ErosionPipeline
        |    |    +-- HydraulicErosionFilter
        |    |    +-- ThermalErosionFilter
        |    |    +-- bounded immutable ErosionTile cache
        |    +-- RiverModel
        |         +-- RivermapGenerator / D8 drainage
        |         +-- immutable RiverSegment network
        |         +-- bounded immutable Rivermap cache
        +-- ClimateModel
        |    +-- ClimateRegionSampler
        |    +-- broad temperature/moisture noise
        |    +-- altitude/coast/river feedback
        +-- modular Noise graph
        +-- Cell foundation
        |
        v
TerrainSample
```

`DefaultTerrainWorld` and all model/noise definitions are immutable after
construction. Erosion and River/Rivermap each use bounded synchronized LRUs
containing only immutable completed regions/maps; expensive generation happens
outside their locks. Sampling therefore remains deterministic and safe for
concurrent chunk-generation calls without recursive cache wait graphs.

## Migration state

### Migrated foundation: noise

The bootstrap-only value/fractal classes have been replaced in active terrain
sampling by a seed-aware modular noise foundation. Its organization follows the
functional split found in ReTerraForged/FreeTerraForged (`domain`, `function`,
`module`) while removing Mojang codecs and Minecraft types. It currently
provides:

- deterministic value and gradient lattice fields;
- interpolation and octave/fractal composition;
- arithmetic/range modules;
- curve and distance functions;
- coordinate-domain warping;
- a seed-bound adapter for the existing terrain models.

### Migrated foundation: cell

The cell layer now models the engine's mutable intermediate terrain state with
caller-owned instances. It retains the useful cell-oriented generation model
from ReTerraForged while deliberately excluding biome objects and shared global
cache ownership. `CellLookup` fills caller-owned targets and `CellField` is an
immutable ordered population pipeline. This design keeps the foundation safe for
parallel chunk-generation use.

### Migrated foundation: continent

The first real cell-generation stage is now present. `AdvancedContinent` uses a
warped, jittered Voronoi partition to calculate a stable continent owner,
world-space center and normalized inward distance from ocean-producing cell
boundaries. Its dedicated caller-owned `ContinentCell` stores transient Voronoi
geometry such as the owner point, closest boundary neighbor, boundary distance
and neighbor centroid. The finished values are then copied into the general
engine `cell.Cell`, which continues through later generation stages.

`AdvancedContinent` implements `CellPopulator`, but also exposes immutable
`ContinentSample` values so hot-path terrain sampling does not require shared
mutable cell ownership. Directional boundary and normalized edge-threshold
searches are available without importing ReTerraForged river caches or
Minecraft control points.

The active `TerrainModel` consumes this continent signal directly. The former
bootstrap fractal-continent field has been removed from active terrain shaping.
Unlike ReTerraForged's historical `Continent` contract, the FlTerraForged engine
continent layer does not own a river-map cache; hydrology remains a separate
stage. Biome interpretation and Minecraft control points likewise remain outside
the external engine boundary.

### Migrated foundation: terrain

Terrain is now a first-class cell stage instead of a single bootstrap height
formula. A deterministic `TerrainRegionSampler` partitions land independently
inside continents. `TerrainProvider` maps each owner and nearest neighbor region
to engine-neutral landforms, `Blender` smooths transitions at region boundaries,
and `TerrainPopulator` writes the selected terrain, region, base height and
weirdness signals into the general engine `Cell`.

`ConfiguredTerrain` and `CompositeTerrain` deliberately contain no Mojang
codecs or Minecraft density functions. FlTerraForged remains responsible for
translating the engine's continuous height and semantic `TerrainType` into the
Minecraft-version-specific density/chunk pipeline. FreeTerraForged's later
`IslandBlender` concept is deferred until island generation is migrated as an
optional terrain strategy.

### Migrated foundation: erosion

Physical erosion is now a distinct post-terrain stage rather than an input noise
field. `ErosionPipeline` first requests the un-eroded base height field from
`TerrainPopulator`, builds padded deterministic regions and applies two filters:

- `HydraulicErosionFilter` launches globally aligned deterministic virtual water
  droplets. Droplets follow the bilinear gradient, gain/lose sediment capacity,
  erode downhill surfaces and deposit carried material when capacity drops.
- `ThermalErosionFilter` redistributes material from local slopes above the talus
  threshold, providing a cheap rock/soil relaxation pass after hydraulic carving.

Each region is generated with a border wider than the maximum droplet travel plus
brush radius. Globally aligned droplet launch coordinates and a world-stable seed
make overlapping region calculations consistent at core boundaries. Completed
`ErosionTile` objects are immutable and stored in a bounded synchronized LRU;
region generation never occurs while the cache lock is held.

The common `Cell` now uses `heightErosion` for post-erosion/pre-river height,
`erosion` for normalized erosion intensity, `sediment` for deposited material,
`gradient` for local eroded slope and `erosionMask` to mark modified positions.
Minecraft terrain blocks and decoration remain outside this stage.

### Migrated foundation: river / rivermap

Hydrology is now terrain-driven rather than an independent fractal mask.
`RivermapGenerator` samples a globally aligned coarse drainage grid from the broad
terrain surface, chooses the lowest D8 downstream neighbor and accumulates flow
from high to low elevation. Nodes above the configured drainage threshold become
directed `RiverSegment`s whose width and depth grow with accumulated flow.

`RiverModel` wraps the physical `ErosionPipeline`: the graph topology stays cheap
to construct from broad terrain, while actual incision is applied to the fully
eroded `Cell.heightErosion`. The resulting `Cell.height` is therefore the
post-river surface and `riverMask` ranges from zero at a centerline to one outside
the channel width. `Rivermap` objects are immutable and cached in a bounded LRU;
normal interior samples touch one map, while neighboring maps are consulted only
near region boundaries.

The stable Engine API continues to expose nearest centerline distance, full width
and local depth through `RiverSample`. Lakes, waterfalls, explicit river-water
elevation, Minecraft fluids, river biomes and surface rules remain outside this
engine stage.

### Migrated foundation: climate

Climate is now a first-class final semantic stage after river shaping. `ClimateModel`
implements `CellLookup`: it requests the fully shaped terrain/river cell, samples broad
temperature and moisture fields, resolves an independent jittered Voronoi
`ClimateRegionSample`, blends climate anchors near region boundaries, applies altitude
cooling, continental/coastal moisture modulation and local river moisture, then writes
`regionTemperature`, `regionMoisture`, `biomeRegionId`, `biomeRegionEdge`,
`macroBiomeId`, `temperature` and `moisture` into the shared `Cell`.

The structure follows the useful separation visible in ReTerraForged and
FreeTerraForged (`Climate` plus `ClimateModule`) without retaining their Minecraft biome
objects, control points or codec/registry dependencies. `macroBiomeId` is only a stable
semantic grouping hint; FlTerraForged remains responsible for mapping climate and
terrain signals to version-specific Minecraft biomes.

## Planned upstream migration boundary

### Engine candidates

- noise primitives and composition;
- cell/Voronoi systems;
- erosion mathematics;
- deterministic caches that do not depend on Minecraft.

### Remain in FlTerraForged

- Mojang codecs;
- density-function adapters;
- chunk generators;
- biome registries and biome sources;
- surface/block placement;
- structures/features;
- Fabric/NeoForge hooks;
- TerraBlender integration;
- Conquest/Arona-inspired layers and decoration.

## Published API boundary

Normal builds resolve `flterraforged-engine-api` as a Maven artifact. The Engine
CI never checks out or builds FlTerraForged. Resolution prefers a configured
local build repository, then a sibling FlTerraForged build repository,
`mavenLocal()`, normal public repositories and finally the configured public
FlTerraForged API repository.

The default remote is the public `maven` branch:

```text
https://raw.githubusercontent.com/jborkenhagen74/FlTerraForged/maven/
```

No GitHub Packages credential is part of the Engine API contract.
