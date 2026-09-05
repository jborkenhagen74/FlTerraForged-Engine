# FlTerraForged Engine R43

R43 refines the R42 resolved-water field at confined receiver transitions and adds bounded single-flight reuse for lightweight placement probes.

## Base

- Parent revision: R42 (`revision/r42-resolved-water-field`)
- Runtime pair: FlTerraForged R53
- R41 startup-liveness constraints remain mandatory.
- R42 remains the authoritative single resolved hydraulic field; no Minecraft-facing logic is introduced into the Engine.

## Confined river mouths

R42 intentionally gave open ocean higher ownership priority than river water. Its local ocean-receiver predicate, however, accepted a marginal coast column as soon as an oceanward eroded surface fell below sea level. A narrow river channel can satisfy that condition before it has actually reached open ocean.

R43 keeps a material river authoritative through that marginal coast band. Ocean ownership may replace an active river only when both stronger ocean conditions are met:

1. continentalness is beyond `oceanContinentalness`;
2. the post-erosion bed is at least `oceanDepthBelowSea` below sea level.

Once both are true, normal `OCEAN > RIVER` ownership resumes and the ocean removes the residual river trench. The change therefore moves the ownership hand-off seaward without reintroducing a second hydraulic solution.

## Narrow lake outlets

A material river is no longer replaced by the shore-only lake-mouth promotion. The river owns the narrow outlet until the immutable lake field itself reports material lake water. This prevents the minimum lake receiver depth from being forced into an outlet corridor and producing an abrupt step between connected inland water bodies.

Material lake water still has normal higher ownership priority.

## Lightweight environment cache

`DefaultTerrainWorld.environment(x, z)` now uses `EnvironmentSampleCache`:

- sparse X/Z entries rather than eager 16x16 tiles;
- bounded to 8192 completed samples by default;
- wet and dry results are both retained;
- synchronous inline single-flight for identical cold keys;
- no executor submission and no future scheduled onto Minecraft worldgen workers;
- no biome lookup, chunk access or final-sample-cache recursion in the loader.

This forms the Engine side of the R53/R43 multi-stage placement cache. FlTerraForged can ask several environment rules for the same X/Z position without causing repeated post-receiver sampling.

## Provider boundary

R43 does not know Minecraft blocks, voxel shapes, Conquest Reforged or materializer capabilities. It continues to return continuous terrain/hydrology semantics only. R53 resolves those semantics through the selected block provider's physical surface geometry before deciding whether a water body is physically open marine water.

## Regression targets

- a river mouth below sea level remains a river while still in the confined coastal corridor;
- ownership transfers to ocean once strong ocean depth and continentalness are both satisfied;
- a narrow river outlet between lakes is not promoted to the lake shore profile prematurely;
- material lake cells still own overlapping river water;
- repeated placement probes reuse one bounded cached environment sample;
- concurrent identical placement probes perform one inline computation;
- no worldgen-executor scheduling or cache-cycle regression is introduced;
- R41/R42 startup-liveness behavior remains intact.
