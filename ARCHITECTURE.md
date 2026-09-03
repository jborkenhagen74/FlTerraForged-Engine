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
        |    +-- pre-river ClimateModel / runoff weights
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
to engine-neutral landforms, `Blender` smooths transitions across all active neighboring regions at region
boundaries, and `TerrainPopulator` writes the selected terrain, region, base height and
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

Hydrology is terrain-driven rather than an independent fractal mask. `RivermapGenerator`
samples a globally aligned drainage grid, runs a priority-flood pass to resolve local
depressions/spill elevations, then uses D8 only as a deterministic topology skeleton.
Flow accumulation promotes channels. r18 weights each node by a pre-river climate sample, so
hot/dry catchments contribute much less local runoff than humid catchments while established
upstream rivers can continue through arid regions. Each visible edge is refined into a multi-point
terrain-guided path so axis/diagonal grid directions are not exposed as river geometry.

The depression-fill delta is retained as an immutable `LakeField`. Meaningful inland
sinks therefore become irregular ponds/lakes at a coherent spill elevation, while the
flood parent supplies an outlet across flats instead of terminating the network.

`RiverModel` wraps the physical `ErosionPipeline`: actual incision is applied to the fully
eroded `Cell.heightErosion`. It reserves a bank freeboard and minimum wet core, and can
deepen the local bed when post-erosion detail would otherwise interrupt water. The water
surface itself remains downstream-monotonic. `Cell.lake` distinguishes pond/lake samples
from linear channels; `Cell.height` is the final hydrology-shaped surface. Immutable
`Rivermap` objects remain bounded-LRU cached and boundary-aware. r18 expands map padding to 16
drainage cells, giving neighboring maps more shared upstream context and reducing edge cutoffs.

The stable Engine API still exposes distance, width, depth, water-surface height and flow
through `RiverSample`; lake samples reuse those numeric hydrology signals while final
`TerrainType` identifies `LAKE`. Minecraft fluids, biome objects and surface rules remain
outside the Engine. Explicit waterfall/rapid shaping is still deferred.

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

### Integrated pipeline composition (r14)

The seven migrated foundations are now composed by a single `WorldgenPipeline`. This class is the
world-seed-bound composition root. r18 evaluates a pre-river climate view for runoff only, yielding
`continent -> terrain -> erosion -> climate-runoff -> river -> final climate`. `DefaultTerrainWorld`
no longer constructs stage dependencies itself and only
projects pipeline results to the stable Engine API.

The integration also closes a previous internal data gap: `RiverModel` writes nearest-channel
`riverDistance`, `riverWidth` and local `riverDepth` into the shared `Cell`, allowing climate and the
final API projection to reuse exactly the same hydrology result without an additional Rivermap
query. Final slope is calculated after river shaping while `heightErosion` retains the pre-river
surface for downstream comparisons.

Cross-stage defaults are grouped into `EnginePreset` profiles (`BALANCED`, `GENTLE`, `RUGGED`).
Numeric EngineConfig values still override individual profile fields. Semantic ocean/coast/river
classification is coordinated through `TerrainClassificationSettings.from(EngineSettings)` rather
than a separate set of unrelated constants.


## River water-level signal

Directed `RiverSegment` sampling now produces a continuous channel water-surface elevation in world
Y. `RiverModel` stores it in `Cell.riverWaterSurfaceHeight` together with `Cell.riverFlow`, and
`WorldgenPipeline` projects both into `RiverSample`. This remains semantic Java-only data; Minecraft
fluid placement belongs to the host adapter.

## World-scoped final-sample cache (r19)

The engine now places a bounded cache above the fully assembled `WorldgenPipeline`. Minecraft can
ask the same X/Z column repeatedly while selecting biomes, reshaping density, answering height
queries and repairing the final surface; all of those consumers now receive the same immutable
`TerrainSample` object from a chunk-aligned 16x16 tile.

The cache retains at most 256 completed tiles with access-order LRU eviction. Cache lookup and
insertion use short synchronized sections only. Tile generation never runs under the cache lock,
and the design intentionally avoids recursive `computeIfAbsent`/future wait graphs. A racing
duplicate tile may be computed, but only one immutable result is retained.

`WorldgenPipeline.sampleTile(...)` also shares a one-block border while deriving gradients. For a
16x16 core, point-by-point sampling would run one full climate/hydrology lookup plus four separate
post-river height lookups for each of 256 columns (1280 hydrology-bearing lookups). Bulk sampling
evaluates an 18x18 cell field once (324 lookups) and derives all 256 slopes from that field.

