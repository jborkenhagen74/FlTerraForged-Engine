# Notice

FlTerraForged Engine is an independent external terrain-engine implementation
for FlTerraForged.

Starting with `0.1.0-SNAPSHOT-r7`, the project reimplements selected modular
noise and cell-generation concepts from the TerraForged project family under
its own Minecraft-independent architecture and namespace. Snapshot r8 extends
that work with a rewritten continent-partition and coastline stage.

Upstream reference projects include:

- TerraForged — MIT License, Copyright (c) 2020 TerraForged.
- TerraForged/Noise2D — MIT License, Copyright (c) 2018 dags.
- ReTerraForged — MIT-licensed continuation of TerraForged.
- FreeTerraForged — community continuation under the MIT license.

Detailed source paths and migration boundaries are recorded in `UPSTREAMS.md`.
No Mojang/Minecraft, Fabric, NeoForge, TerraBlender, Conquest Reforged or
AronaLayers runtime dependency is introduced by this migration.
