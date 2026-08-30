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
        +-- Noise channels
        |
        v
TerrainSample
```

`DefaultTerrainWorld` and all model/noise objects are immutable after
construction. `sample(x, z)` therefore has no shared mutable state and is safe
for concurrent chunk-generation calls.

## Bootstrap algorithm

The 0.1.0 snapshot includes a small deterministic reference generator so the
SPI can be tested end-to-end before upstream code is imported. It uses:

- seeded value noise;
- fractal octave composition;
- broad continentalness;
- ridge-derived relief;
- a simple erosion modulation;
- a simple river-distance field;
- central-difference slope estimation;
- semantic terrain classification.

These algorithms are scaffolding, not compatibility claims with TerraForged.

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

Normal builds resolve `flterraforged-engine-api` from the FlTerraForged GitHub Packages repository. The Engine CI never checks out FlTerraForged. Local composite substitution is an explicit developer-only mechanism and Maven Local is opt-in, preventing accidental coupling to stale local artifacts.
