# FlTerraForged Engine R35 – Dry coast semantics

R35 is a narrow corrective revision on top of the R34 lightweight-placement Engine, which itself was rebuilt from the exact R29 control baseline.

## Semantic correction

- Explicit inland hydrology is classified before marine semantics: material lakes remain `LAKE`, material rivers remain `RIVER`, and dry lake transitions remain `flterraforged:lake_shore` even inside the broad continental coast band.
- Every non-inland-hydrology oceanward column physically below sea level is classified as `OCEAN`, including the shallow shelf.
- `COAST` is restricted to dry terrain at or above sea level and remains bounded by the configured coastal height.
- Continuous terrain heights, erosion, river routing, lake basin construction, climate and normal `sample()` behavior otherwise remain unchanged.

This removes the semantic state that allowed the Minecraft host to treat a disconnected shallow coastal depression as sea and fill it to global sea level behind a dry beach ridge.

## Placement API

The R34 lightweight `TerrainWorld.environment(x,z)` path is retained unchanged in shape: it still avoids final climate sampling and gradient-neighbor work and remains independent from Minecraft classes.

## Validation

Regression tests cover shallow submerged coastal shelf classification, dry sea-level coast, material lake precedence and dry lake-shore precedence. Provider version is `0.1.0-SNAPSHOT-r35`.
