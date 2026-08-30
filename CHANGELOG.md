# Changelog

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
