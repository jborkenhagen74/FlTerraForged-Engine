## 0.1.0-SNAPSHOT-r22

- Replace bilinear interpolation of depression-fill spill heights with connected basin identification. Every pond/lake basin now owns one constant continuous water surface.
- Split inland-water sampling into explicit `SHORE`, `SHALLOW` and `CORE` zones. Only shallow/core zones materialize water; the shore is a separate dry semantic transition.
- Guarantee a stable core depth in the Engine while leaving final full-block quantization to the Minecraft host.
- Add `Cell.lakeShore` and route it through final terrain classification as additive `StandardTerrainTypes.LAKE_SHORE` semantics supplied by the companion API snapshot.
- Add layout guards that reject a return to `bilinear(filledHeight, ...)` water-surface interpolation and require basin-aware lake logic.
- Preserve r21 mountain-river containment, r20 multi-region terrain blending and r19 world-sample caching unchanged.
- Report provider version `0.1.0-SNAPSHOT-r22`.

## 0.1.0-SNAPSHOT-r21

- River water profiles are now resolved along the refined path instead of being linearly projected only between coarse drainage nodes.
- Each refined path point samples both banks and clamps the local water surface below the lower bank while preserving downstream monotonicity.
- Mountain rivers therefore lower their local water level or become temporarily dry where a channel cannot be contained, instead of spilling across a descending slope.
- `RiverPathPoint` now carries the pre-river terrain height and the bank-contained local water surface used by `RiverSegment.hit`.
- Strict compile/Javadoc verification remains unchanged.

## 0.1.0-SNAPSHOT-r20

- Replaced the two-region terrain boundary blend with a continuous four-influence Voronoi blender.
- Terrain-region triple junctions no longer switch abruptly between different secondary landforms; all active neighbors inside a guarded Voronoi neighborhood participate continuously.
- Neighbor influences fade smoothly and normalize together with the owning region, preserving distinct biome ownership while smoothing the underlying surface height.
- Adjacent regions selecting the same terrain definition are merged before height evaluation.
- Broadened terrain transition widths to 0.50 (balanced), 0.60 (gentle) and 0.42 (rugged).
- Kept the multi-region scan off the hot path away from boundaries: ordinary region interiors still use the single owning terrain directly.
- Preserved the r19 world sample cache and all r18 hydrology/runoff behavior.

# Changelog

## 0.1.0-SNAPSHOT-r19

- Add a world-scoped overarching final-sample cache above the complete pipeline so biome, density, height and surface consumers reuse the same immutable `TerrainSample` values.
- Cache immutable 16x16 sample tiles in a bounded 256-tile access-ordered LRU; expensive tile generation happens outside the cache lock and concurrent duplicate generation is resolved only during short insertion locks.
- Add bulk pipeline tile sampling with a shared one-block gradient border. A 16x16 tile now needs 324 hydrology-bearing cell lookups for center heights/slopes instead of 1280 point-sampling lookups, a 74.7% reduction before cross-stage cache hits.
- Clear final-sample tiles when the world-scoped `TerrainWorld` closes.
- Preserve r18 climate-weighted runoff, expanded hydrology padding and river/lake geometry unchanged.
- Report provider version `0.1.0-SNAPSHOT-r19`.

## 0.1.0-SNAPSHOT-r18

- Weight drainage accumulation by pre-river climate runoff: humid catchments contribute strongly, while hot/dry catchments contribute only a small fraction. Major rivers can still cross deserts when their upstream catchment is wet.
- Reduce overall river density with wider drainage-grid spacing and higher visible-flow/headwater thresholds.
- Increase hydrology padding from 10 to 16 grid cells so neighboring maps share substantially more upstream context, reducing channels that disappear at region boundaries.
- Keep the final climate pass after hydrology so river-local moisture feedback remains intact; a separate pre-river climate view is used only for runoff weighting and avoids a dependency cycle.
- Preserve strict Javadoc `-Werror` verification through `check`; all new public constructor parameters are documented.
- Report provider version `0.1.0-SNAPSHOT-r18`.

## 0.1.0-SNAPSHOT-r17

- Fix strict Javadoc generation for compact constructors in `Rivermap`, `RiverSegment` and `RiverSettings` by documenting every implicit record-constructor parameter.
- Keep Javadoc warnings fatal with `-Werror` and make `check` depend on `javadoc`, so normal CI verification catches documentation regressions before publication.
- Ignore `gradlew`, `gradlew.bat` and the complete `gradle/wrapper/` directory because CI uses the explicitly provisioned Gradle version.
- Report provider version `0.1.0-SNAPSHOT-r17`.

## 0.1.0-SNAPSHOT-r16

- Replace sink-prone strict-lower D8 routing with depression-aware priority-flood hydrology and deterministic spill routing.
- Keep D8 as the hidden drainage skeleton only; refine every visible channel edge into a terrain-guided multi-point centerline so the eight grid directions are no longer the rendered river geometry.
- Guarantee a wet channel core by coordinating incision depth, bank freeboard and minimum water depth, then enforce the minimum again against the actual eroded X/Z terrain during sampling.
- Add depression-filled `LakeField`/`LakeHit` sampling so meaningful inland sinks become irregular ponds/lakes at their spill elevation and their overflow can continue downstream.
- Reduce headwater abruptness with finer drainage spacing and a headwater threshold feeding established channels.
- Add explicit `Cell.lake` semantics and classify depression-filled inland water as `StandardTerrainTypes.LAKE` without changing the stable `RiverSample` record shape.
- Retune balanced/gentle/rugged climate scales and region blends toward larger, softer biome fields.
- Report provider version `0.1.0-SNAPSHOT-r16` and add regression coverage for curved centerlines, minimum wet depth and depression lakes.

## 0.1.0-SNAPSHOT-r15

- Report provider version `0.1.0-SNAPSHOT-r15` so host debug output can distinguish this hydrology build from r14.
- Extend river hydrology with a continuous `waterSurfaceHeight` derived directly from each directed `RiverSegment`.
- Keep a constant bank inset across segment endpoints so connected drainage segments share the same node-relative water elevation instead of using noisy per-column terrain guesses.
- Carry `riverWaterSurfaceHeight` and `riverFlow` through the shared `Cell` and project both through the additive `RiverSample` API fields.
- Advertise `RIVER_WATER_LEVEL` from the default Engine.
- Add tests for water-surface availability, Cell/API projection, downhill segment continuity and concurrent deterministic sampling.
- Lakes/basin filling and explicit waterfall shaping remain intentionally deferred.

## 0.1.0-SNAPSHOT-r14

- Combined continent, terrain, erosion, river/rivermap and climate under a single immutable `WorldgenPipeline` composition root.
- Simplified `DefaultTerrainWorld` so it delegates complete sampling to the integrated pipeline.
- Added `EnginePreset` with coordinated `balanced`, `gentle` and `rugged` defaults; explicit numeric EngineConfig keys override the selected preset.
- Retuned terrain-region scale/blending so large continents contain several landforms while gentle/rugged profiles retain distinct transition styles.
- Added `TerrainClassificationSettings` derived from Engine settings for coordinated ocean/coast/river semantic thresholds.
- Extended the shared `Cell` with `riverDistance`, `riverWidth` and `riverDepth`; final `RiverSample` no longer requires a second Rivermap query.
- Added full-pipeline Cell invariants, API-projection, preset-override and concurrent determinism tests.
- Added `MIGRATION-PIPELINE.md` documenting stage ownership, data contracts, presets and remaining integration boundaries.

## 0.1.0-SNAPSHOT-r13

- Added the seventh migrated engine foundation: `climate`.
- Replaced the bootstrap two-noise climate sampler with a dedicated `ClimateModel` cell stage.
- Added deterministic jittered-Voronoi `ClimateRegionSampler`/`ClimateRegionSample` for semantic biome-region hints.
- Added smooth neighboring climate-region blending instead of hard region boundaries.
- Added altitude cooling, continental/interior drying, coastal temperature moderation and river-local moisture feedback.
- Climate now writes `regionTemperature`, `regionMoisture`, `biomeRegionId`, `biomeRegionEdge`, `macroBiomeId`, `temperature` and `moisture` into the shared `Cell`.
- Added configurable climate region scale/jitter/blend and terrain-feedback strengths.
- Added climate determinism, bounds, altitude, river-moisture, semantic-cell and concurrent-sampling tests.
- Added `MIGRATION-CLIMATE.md`; Minecraft biome selection, TerraBlender and registries remain outside the engine.

## 0.1.0-SNAPSHOT-r12

- Replaced the bootstrap independent river-noise field with terrain-driven River/Rivermap hydrology.
- Added globally aligned D8 drainage nodes, downhill routing and deterministic flow accumulation.
- Added immutable `RiverSegment`, `Rivermap` and internal `RiverHit` representations.
- Added flow-derived channel width/depth and post-erosion river incision into `Cell.height`.
- `Cell.heightErosion` now remains the explicit pre-river surface while `riverMask` records channel proximity.
- Added bounded immutable-rivermap caching with generation outside the cache lock and boundary-aware neighbor lookup.
- Changed `TerrainModel` to consume the full post-river `CellLookup`; the old scalar `RiverModel.incision(...)` path is gone.
- High-altitude rivers may now retain semantic `RIVER` classification instead of being limited to sea-level terrain.
- Added deterministic, centerline-incision, boundary and concurrent river tests plus `MIGRATION-RIVER.md`.

## 0.1.0-SNAPSHOT-r11

- Replaced the bootstrap scalar `ErosionModel` with a dedicated physical erosion pipeline.
- Added deterministic padded erosion regions with globally aligned hydraulic droplet launches.
- Added sediment capacity, erosion brush, carry/deposition, gravity and evaporation behavior.
- Added thermal talus relaxation as a second erosion pass.
- Added bounded shared caching of immutable erosion tiles; expensive generation occurs outside the cache lock.
- Moved erosion after base terrain shaping and before river incision.
- `Cell.heightErosion`, `erosion`, `sediment`, `gradient` and `erosionMask` now carry real erosion-stage data.
- Removed erosion noise from `TerrainContext`/`TerrainPopulator`; landform generation is no longer suppressed by a fake erosion field.
- Added configurable `erosionStrength`, `erosionDeposition`, `thermalErosionStrength` and `erosionMaxDelta`.
- Added erosion determinism, deposition, boundary and concurrent-sampling tests plus standalone smoke tests.
- Added `MIGRATION-EROSION.md`.

## 0.1.0-SNAPSHOT-r10

- Added the fourth migrated engine foundation: `terrain`.
- Added engine-neutral `Terrain`, `TerrainCategory`, `TerrainContext`, `ConfiguredTerrain` and `CompositeTerrain` abstractions.
- Added deterministic jittered-Voronoi terrain regions independent from continent ownership.
- Added `TerrainProvider`/`DefaultTerrainProvider` for plains, hills, valleys, plateaus and mountains.
- Added `Blender` for smooth owner/neighbor landform transitions at terrain-region boundaries.
- Added `TerrainPopulator` to write continent, terrain-region, erosion, weirdness, semantic terrain and height signals into the general engine `Cell`.
- Refactored `TerrainModel` to orchestrate the real cell terrain pipeline rather than the previous bootstrap height formula.
- Updated final terrain classification to preserve engine-selected landforms while applying ocean, coast and river overrides.
- Added terrain-region, provider, blending, world-diversity and concurrent-sampling tests.
- Added `MIGRATION-TERRAIN.md` and kept Minecraft codecs, density functions, biome logic and FreeTerraForged island-specific blending outside this migration.

## 0.1.0-SNAPSHOT-r9

- Completed the advanced continent foundation with a dedicated caller-owned `ContinentCell` workspace.
- Added `ContinentPoint` for deterministic jittered tectonic-grid points.
- `ContinentCell` now records warped sample coordinates, owner point, nearest boundary neighbor, owner distance, boundary distance, neighbor centroid/count and skipping state.
- Refactored `AdvancedContinent.sample(...)` to derive its final immutable sample from the specialized continent workspace.
- Added reusable `sampleCell(..., target)` sampling without hidden ThreadLocal/global pools.
- Added directional `distanceToEdge(...)` and engine-neutral `distanceToEdgeThreshold(...)` searches inspired by ReTerraForged's advanced continent helpers without importing control points or river caches.
- Added tests/smoke checks for continent workspace geometry, reuse, boundary search, determinism and Java 17/Javadoc strictness.
- Documented the distinction between specialized `ContinentCell` geometry and the general cross-stage engine `cell.Cell`.

## 0.1.0-SNAPSHOT-r8

- Added the third migrated engine foundation: `continent`.
- Added `Continent`, `ContinentSample`, `ContinentCenter`, `ContinentSettings` and `AdvancedContinent`.
- Implemented deterministic warped jittered-Voronoi continent ownership, stable centers, size variance, optional skipping and coast modulation.
- Made `Continent` a `CellPopulator` while retaining immutable direct sampling for thread-safe hot paths.
- Replaced the active bootstrap fractal continentalness field in `TerrainModel` with the new continent sampler.
- Kept river-map/cache ownership out of the continent contract so hydrology remains independently replaceable.
- Added configurable continent jitter, skipping, size variance, warp strength and coast roughness settings.
- Added deterministic, bounds, coastline/interior, cell-population and concurrent-sampling tests.
- Verified Java 17 compilation with `-Xlint:all`, strict Javadoc with `-Werror`, engine isolation and standalone continent/world smoke tests.

## 0.1.0-SNAPSHOT-r7

- Migrated the first two external-engine foundations: `noise` and `cell`.
- Added seed-aware `Noise` contract, value/gradient sources, interpolation and fractal composition.
- Added composable `domain`, `function` and `module` packages without Mojang codecs.
- Replaced the active bootstrap noise chain with the new modular noise foundation via `SeededNoise2D`.
- Replaced the placeholder `CellField` with `Cell`, `CellLookup`, `CellPopulator`, ordered `CellField` pipelines and `NoiseCellPopulator`.
- Kept biome objects, registries, Minecraft classes and loader types outside the engine.
- Added deterministic, range, domain-warp, cell-reset/copy, pipeline and concurrent-cell tests.
- Verified Java 17 compilation, `-Xlint:all`, strict Javadoc (`-Werror`) and standalone noise/cell smoke tests.
- Documented ReTerraForged 1.20.1, FreeTerraForged 1.21.1 and TerraForged Noise2D provenance.

## 0.1.0-SNAPSHOT-r3

- Converted `EngineSmokeTest` from a standalone `main()` program to JUnit 5.
- Added JUnit Jupiter and the JUnit Platform launcher to the test classpath.
- Removed the duplicate `JavaExec` smoke-test path; `check` now uses Gradle's standard `test` lifecycle.
- Build workflow checks `develop` and `main`; snapshot publishing remains limited to `develop`.

## 0.1.0-SNAPSHOT-r1

- Fixed CI bootstrap of `flterraforged-engine-api:0.1.0-SNAPSHOT`.
- CI now publishes `:engine-api` from FlTerraForged to Maven Local before building the external engine.
- Removed implicit sibling-repository composite substitution; composite builds are now explicitly opt-in.
- Added fail-fast validation for an explicitly configured composite API path.

## 0.1.0-SNAPSHOT

- Initialized independent Java 17 engine repository.
- Added FlTerraForged Engine API dependency and local composite-build support.
- Added ServiceLoader `DefaultEngineProvider`.
- Added deterministic, immutable bootstrap terrain engine.
- Added continuous/fractional surface height.
- Added slope, erosion, continentalness, climate, river and terrain-type output.
- Added isolation, provider and smoke-test verification.
- No upstream TerraForged-family code imported yet.

## 0.1.0-SNAPSHOT-r2

- Removed CI checkout/build/publish of the FlTerraForged API.
- Resolves `flterraforged-engine-api` from the FlTerraForged GitHub Packages repository.
- Keeps Maven Local disabled by default; it is only enabled with `-Pflterraforged_use_maven_local=true`.
- Keeps the explicit local composite-build option for simultaneous API/engine development.
- Publishes the engine artifact to its own GitHub Packages repository after successful checks on `main`.
- Disables caching of changing API modules for `-SNAPSHOT` versions.


### r4
- Replaced GitHub Packages API resolution with the FEF-style Maven repository chain.
- Added local build repository, sibling build repository and `mavenLocal()` resolution.
- Default API repository is the public FlTerraForged `maven` branch.
- Removed API package credentials and cross-repository token requirements.
- Removed source-level composite coupling from `settings.gradle`.
- Engine publishing now builds `build/maven-repository` and mirrors it to its public `maven` branch.
- Retained the JUnit 5 test correction from r3.


### r5
- Updated GitHub Actions to Node 24 compatible releases: `actions/checkout@v6`, `actions/setup-java@v6`, and `gradle/actions/setup-gradle@v6`.
- Pinned `peaceiris/actions-gh-pages@v4.1.0`, whose action runtime uses Node 24.
- Removes Node 20 deprecation warnings from the engine build and Maven-branch publishing workflow.

## 0.1.0-SNAPSHOT-r6

- Added complete Javadoc coverage for the default engine implementation.
- Added package-level engine documentation.
- Configured Javadoc with `-Werror` so Maven publication cannot silently emit Javadoc warnings.
