# FlTerraForged Engine

Default external terrain-engine implementation for FlTerraForged.

The engine is deliberately independent of Minecraft, Fabric, NeoForge,
TerraBlender and Conquest Reforged. It implements only the Java 17
`flterraforged-engine-api` SPI.

## Dependency direction

```text
FlTerraForged
  └─ publishes flterraforged-engine-api
             ↓
      public Maven repository
             ↓
FlTerraForged-Engine
  └─ consumes engine-api
```

The Engine CI does not check out or build FlTerraForged.

## Engine API resolution

Resolution follows the same pattern used by the FEF examples/components:

1. Explicit local build repository via
   `flterraforged_api_local_repository` or
   `FLTERRAFORGED_API_LOCAL_REPOSITORY`.
2. `../FlTerraForged/build/maven-repository` automatically when that sibling
   repository exists.
3. `mavenLocal()`.
4. `mavenCentral()`.
5. Public remote repository via `flterraforged_api_repository_url` or
   `FLTERRAFORGED_API_REPOSITORY_URL`.

The build has this public URL as its default remote repository:

```text
https://raw.githubusercontent.com/jborkenhagen74/FlTerraForged/maven/
```

Current API dependency:

```text
dev.foucaultleon:flterraforged-engine-api:0.1.0-SNAPSHOT
```

No token is required to consume the public Maven repository.

## Local development with both repositories

Directory layout:

```text
workspace/
├── FlTerraForged/
└── FlTerraForged-Engine/
```

First publish the API into FlTerraForged's build repository:

```bash
cd FlTerraForged
gradle --no-daemon :engine-api:publish
```

Then build the Engine:

```bash
cd ../FlTerraForged-Engine
gradle --no-daemon --refresh-dependencies clean check
```

The Engine automatically detects
`../FlTerraForged/build/maven-repository`.

For an arbitrary path you can instead use:

```bash
gradle --no-daemon check \
  -Pflterraforged_api_local_repository=/path/to/FlTerraForged/build/maven-repository
```

## Engine Maven publication

The Engine itself uses the same model. `gradle publish` writes to:

```text
build/maven-repository
```

On `develop`, GitHub Actions mirrors that repository to:

```text
https://raw.githubusercontent.com/jborkenhagen74/FlTerraForged-Engine/maven/
```

Current Engine coordinate:

```text
dev.foucaultleon:flterraforged-engine:0.1.0-SNAPSHOT
```

## Current implementation

`0.1.0-SNAPSHOT-r13` contains the first seven migrated engine foundations:

1. **Noise** — seed-aware modular scalar fields, interpolation, gradient/value
   sources, octave composition, arithmetic modules, curve/distance functions and
   coordinate-domain warping. The implementation is Minecraft- and Codec-free.
2. **Cell** — a mutable semantic terrain cell plus allocation-conscious
   `CellLookup`, ordered `CellPopulator` pipelines and noise-backed cell stages.
   Biome objects and Minecraft registry types are intentionally excluded.
3. **Continent** — a warped jittered-Voronoi partition with a dedicated
   caller-owned `ContinentCell` workspace for owner, neighbor, boundary and
   center geometry. It produces stable continent IDs, corrected world-space
   centers and an inward coast/edge signal.
4. **Terrain** — an independent terrain-region partition, terrain provider,
   configured/composite landforms, boundary blending and `TerrainPopulator`.
   Large continents can contain multiple deterministic plains, hills, valleys,
   plateaus and mountain regions without exposing Minecraft worldgen classes.
5. **Erosion** — deterministic padded erosion regions, hydraulic virtual droplets,
   sediment carry/deposition, thermal talus relaxation and a bounded immutable-tile
   cache. Erosion writes the explicit pre-river surface to `Cell.heightErosion`.
6. **River / Rivermap** — globally aligned D8 drainage graphs, deterministic flow
   accumulation, immutable directed channel segments, flow-derived width/depth,
   bounded map caching and post-erosion river incision. `Cell.riverMask` represents
   centerline-to-bank proximity while `Cell.height` becomes the final river-shaped
   surface.
7. **Climate** — broad continuous temperature/moisture fields, jittered macro climate
   regions, smooth region-boundary blending, altitude cooling, continental/coastal
   moisture effects and river-local moisture. Climate writes semantic region signals
   into `Cell` but deliberately does not select Minecraft biomes.

The active world flow is now continent → terrain → erosion → river → climate. Lakes,
waterfalls, public river-water elevation, biome routing and Minecraft water/surface
placement remain later integration work.
