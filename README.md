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

`0.1.0-SNAPSHOT-r14` combines the seven migrated foundations into one coordinated pipeline:

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
   configured/composite landforms, multi-region boundary blending and `TerrainPopulator`.
   Large continents can contain multiple deterministic plains, hills, valleys,
   plateaus and mountain regions without exposing Minecraft worldgen classes.
5. **Erosion** — deterministic padded erosion regions, hydraulic virtual droplets,
   sediment carry/deposition, thermal talus relaxation and a bounded immutable-tile
   cache. Erosion writes the explicit pre-river surface to `Cell.heightErosion`.
6. **River / Rivermap** — globally aligned depression-aware drainage, climate-weighted runoff
   accumulation, D8 topology hidden behind terrain-refined visible paths, flow-derived
   width/depth, minimum wet-channel depth, irregular priority-flood ponds/lakes, bounded
   map caching and post-erosion hydrology incision. `Cell.riverMask` represents
   watercourse proximity while `Cell.height` becomes the final hydrology-shaped surface.
7. **Climate** — broad continuous temperature/moisture fields, jittered macro climate
   regions, smooth region-boundary blending, altitude cooling, continental/coastal
   moisture effects and river-local moisture. Climate writes semantic region signals
   into `Cell` but deliberately does not select Minecraft biomes.

The active world flow is now assembled exclusively by `WorldgenPipeline`:

```text
continent -> terrain -> erosion -> pre-river climate runoff -> river/rivermap -> final climate -> API sample
```

`DefaultTerrainWorld` delegates to that composition root instead of wiring stages itself.
The shared `Cell` now carries final river distance/width/depth in addition to the river mask,
so the API projection does not perform a duplicate Rivermap lookup.

### Pipeline presets

The optional EngineConfig key `preset` selects coordinated defaults before individual numeric
overrides are applied:

```text
balanced  default; broad continents, varied relief, moderate erosion/hydrology
gentle    softer relief, wider blends, gentler erosion, sparser rivers
rugged    stronger relief, tighter transitions, denser rivers, stronger erosion
```

For example, an integration layer can pass:

```text
preset=rugged
mountainRelief=61.5
erosionStrength=0.31
```

The two numeric values override only those fields while the remaining values still come from
`rugged`. Terrain classification thresholds are derived from the same pipeline settings rather
than being unrelated hard-coded constants.

Biome routing and Minecraft block/fluid placement remain host responsibilities. Explicit waterfall
shaping and a future fully 3D river/aquifer coupling remain later integration work.


## Hydrology water surface (r15)

The default Engine now exports a continuous river-water elevation and accumulated flow with each
active river sample. The Engine still owns no Minecraft blocks or fluids; loader adapters decide how
to materialize the semantic water surface.

## Depression-aware rivers and lakes (r16)

The drainage grid is now a topology layer rather than visible geometry. A priority-flood pass resolves
local sinks and spill elevations before flow accumulation. Meaningful filled depressions become
irregular `LakeField` water bodies, while overflow continues through the downstream graph instead of
terminating a river in a local minimum. Each visible D8 edge is refined into a multi-point path by
probing nearby terrain, which removes the old long axis/diagonal segment look.

Channel incision now reserves bank freeboard and a minimum wet core. During final sampling the Engine
checks that water still clears the actual eroded local bed and deepens the channel locally when needed;
it does not raise an individual water column and therefore preserves downstream-monotonic water levels.
Explicit waterfall shaping remains future work.

## Climate-weighted runoff and sparser rivers (r18)

r18 uses the same broad climate fields twice without creating a dependency cycle. A pre-river view
is sampled from base terrain only and contributes runoff weights to the drainage graph; the final
climate pass still runs after hydrology and retains river-local moisture feedback. Hot/dry drainage
cells therefore contribute little local runoff, while humid cells contribute strongly. Large rivers
that accumulated flow upstream can still cross dry regions.

Default drainage spacing is wider, visible/headwater thresholds are higher, and each Rivermap now
uses 16 padding cells instead of 10. The result is fewer small watercourses and substantially more
shared catchment context across neighboring map boundaries.

## Shared world sample cache (r19)

r19 adds a world-scoped cache above the complete pipeline. Final `TerrainSample` values are built
in immutable 16x16 tiles and reused across biome routing, density shaping, height sampling and
surface correction. The cache is bounded to 256 tiles, uses access-order eviction and never performs
expensive generation while holding the cache lock. Bulk tile generation shares the gradient border,
reducing the hydrology work required for a complete 16x16 tile from 1280 point-style lookups to 324.


## Multi-region terrain blending (r20)

r20 replaces the previous owner/one-neighbor terrain transition with a guarded multi-region
Voronoi blend. Biome/terrain ownership remains discrete for semantic routing, while the generated
surface height and weirdness are interpolated across every active neighboring landform. This
removes the large height seams that can occur when the second-nearest Voronoi region changes at a
triple junction. The balanced preset uses a 0.50 blend width, gentle 0.60 and rugged 0.42.

The more expensive neighborhood scan only runs inside the configured boundary band. Region
interiors still resolve directly to their owning terrain, preserving the r19 cache-oriented hot
path as much as possible.
## Bank-contained river profiles (r21)

r21 keeps the r18 drainage topology and r20 terrain blending, but changes how visible river water
levels are resolved through steep terrain. Every refined centerline point samples the local terrain
and both banks just outside the channel width. The water surface is clamped below the lowest of
those three terrain samples by the configured `bankFreeboard` and is then forced to remain
non-rising downstream. This prevents a coarse drainage-node water level from projecting across a
mountain side and flooding terrain outside the intended channel. If a steep section cannot contain
a wet channel within the maximum incision depth, it is allowed to remain dry rather than overflow.



## Flat basin lakes and explicit shores (r22)

r22 fixes inland-water surfaces at the hydrology source. Priority-flood nodes that belong to the
same connected depression are grouped into one basin, and that basin stores one spill-derived water
level. Terrain interpolation and deterministic edge noise shape only the shoreline; the water surface
itself is never bilinearly interpolated between drainage nodes.

Each basin sample is classified internally as `SHORE`, `SHALLOW` or `CORE`. Shallow/core samples
carry material water; the dry shore is exported separately through `StandardTerrainTypes.LAKE_SHORE`.
This prevents broad dry gravel fields from being mislabeled as lake bottoms while preserving a
continuous geometry for future hosts with sub-block terrain resolution.
