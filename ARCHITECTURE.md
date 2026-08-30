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
        +-- ClimateModel
        +-- RiverModel
        +-- ErosionModel
        +-- modular Noise graph
        +-- Cell foundation
        |
        v
TerrainSample
```

`DefaultTerrainWorld` and all model/noise objects are immutable after
construction. `sample(x, z)` therefore has no shared mutable state and is safe
for concurrent chunk-generation calls.

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
and `TerrainPopulator` writes the selected terrain, region, height, erosion and
weirdness signals into the general engine `Cell`.

`ConfiguredTerrain` and `CompositeTerrain` deliberately contain no Mojang
codecs or Minecraft density functions. FlTerraForged remains responsible for
translating the engine's continuous height and semantic `TerrainType` into the
Minecraft-version-specific density/chunk pipeline. FreeTerraForged's later
`IslandBlender` concept is deferred until island generation is migrated as an
optional terrain strategy.

## Planned upstream migration boundary

### Engine candidates

- noise primitives and composition;
- cell/Voronoi systems;
- erosion mathematics;
- river/hydrology mathematics;
- climate mathematics;
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
