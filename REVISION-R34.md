# FlTerraForged-Engine R34

R34 is a deliberately narrow rebuild from the exact, runtime-proven R29 baseline (`6fc06491d212d2ad38ece463e81925393828b984`). It does not inherit the R30-R33 blocking/single-flight cache experiments.

## Purpose

R34 adds one placement-time query to support FlTerraForged R43 without forcing Minecraft structure placement through the complete final terrain sample pipeline.

`TerrainWorld.environment(x, z)` is implemented by `DefaultTerrainWorld` and delegates directly to `WorldgenPipeline.environment(x, z)`.

The lightweight path:

1. executes the existing R29 post-erosion river/lake lookup for the requested coordinate;
2. derives continentalness and the existing final water semantics;
3. classifies OCEAN, COAST, RIVER, LAKE and LAKE_SHORE with the existing R29 classifier;
4. exposes continuous terrain height and hydrologic water-surface height;
5. does not enter `WorldSampleCache`;
6. does not calculate final local gradient from four neighboring post-river samples;
7. does not run the final climate projection used by a complete `TerrainSample`.

The drainage climate used internally by R29 river generation remains unchanged because it is part of the hydrology model itself.

## Compatibility

The Engine remains Java-only and Minecraft-agnostic. It contains no block-provider, Fabric, Minecraft or Conquest dependency.

The Engine API contract is 0.1.1. Third-party engines that do not override `TerrainWorld.environment()` remain compatible through the default method, which derives the lightweight result from `sample()`.

## Normal world generation

`sample()`, `sampleTile()`, R29 river/lake geometry, erosion, climate, bathymetry and normal final-sample caching are unchanged. R34 therefore limits the regression surface to the new placement environment API and its direct implementation.

## Runtime gate

R34 is intended to be tested only through the coupled R43 Fabric artifact first. It must not be promoted to `develop` before the R43 Minecraft 1.20.1 world-creation gate succeeds.
