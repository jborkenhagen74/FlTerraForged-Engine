# Changelog

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
