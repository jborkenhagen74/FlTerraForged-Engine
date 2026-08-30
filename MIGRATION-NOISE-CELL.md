# Noise + Cell migration (r7)

## Scope

This migration introduces the first two TerraForged-family engine foundations
without changing `flterraforged-engine-api` and without introducing Minecraft,
Fabric, NeoForge or Mojang serialization dependencies.

## Noise

Reference structure:

```text
ReTerraForged / FreeTerraForged
worldgen/noise/
├── domain/
├── function/
├── module/
└── NoiseUtil
```

FlTerraForged Engine:

```text
engine/noise/
├── Noise.java
├── NoiseMath.java
├── Vector2.java
├── Interpolation.java
├── ValueNoise.java
├── GradientNoise.java
├── FractalNoise.java
├── SeededNoise2D.java
├── Noises.java
├── domain/
├── function/
└── module/
```

The FlTerraForged implementation keeps the composable architecture but leaves
Mojang codecs outside the engine. The existing `Noise2D` contract is retained
only as a seed-bound bridge for the bootstrap terrain models; new engine code
should prefer `Noise`.

## Cell

Reference structure:

```text
ReTerraForged / FreeTerraForged
worldgen/cell/
├── Cell.java
├── CellLookup.java
├── CellPopulator.java
└── specialized continent/climate/terrain stages
```

FlTerraForged Engine r7:

```text
engine/cell/
├── Cell.java
├── CellLookup.java
├── CellPopulator.java
├── CellField.java
└── NoiseCellPopulator.java
```

The external engine intentionally excludes the upstream `BiomeType` reference.
`Cell.terrain` uses the semantic Engine API `TerrainType`; biome resolution
belongs to FlTerraForged's Minecraft integration layer.

The upstream thread-local pooling/caching model is not copied at this stage.
`CellLookup` instead fills caller-owned cells and `CellField` owns no mutable
sampling state. This avoids making shared cache ownership part of the engine
contract and is safer for parallel chunk generation.

## Deliberately deferred

The following cell subpackages are not migrated in r7 because they belong to
later functional stages:

- `cell/continent` -> continent migration;
- `cell/terrain` -> terrain migration;
- `cell/filter` -> terrain/erosion migration;
- `cell/rivermap` -> river migration;
- `cell/climate` -> climate migration;
- `cell/biome/type` -> not copied into the external engine as a Minecraft biome
  model; only generic climate/region signals will remain engine-side.

## Verification

The r7 sources are checked with:

- Java 17 compilation;
- `javac -Xlint:all`;
- strict Javadoc (`-Xdoclint:all -Werror`);
- isolation verification;
- deterministic noise smoke tests;
- domain-warp smoke tests;
- cell reset/copy/pipeline smoke tests;
- caller-owned cell concurrency tests in the JUnit suite.
