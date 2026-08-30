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
        +-- TerrainModel
        +-- ClimateModel
        +-- RiverModel
        +-- ErosionModel
        +-- modular Noise graph
        +-- Cell foundation (next terrain stages)
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

The cell layer is not yet wired into the bootstrap `TerrainModel`. Continents
and terrain shaping will be migrated next and will become the first real users
of the cell pipeline.

## Planned upstream migration boundary

### Engine candidates

- noise primitives and composition;
- cell/Voronoi systems;
- continent mathematics;
- terrain shaping;
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
