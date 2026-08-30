# Upstream provenance

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

- continent mathematics;
- terrain shaping;
- erosion mathematics;
- river/hydrology mathematics;
- climate mathematics;
- deterministic caches that do not depend on Minecraft.

AronaLayers-gen and AronaLayers-extras remain references for FlTerraForged's
surface/layer integration and are not part of the external engine migration.
