# FlTerraForged Engine R41

R41 is the runtime-liveness correction for the R40 receiver-overlay line.

## Cause of the 0% startup stall

R40 changed both `RiverModel` inputs from the previous split wiring to the post-erosion lookup. That made the padded `RivermapGenerator` drainage graph and visible-path refinement call the hydraulic erosion stage for every coarse planning probe.

A normal river map spans twenty core drainage cells plus sixteen padding cells on every side. The first hydrology lookup therefore touched a broad planning area long before Minecraft could finish its first spawn chunk. Because erosion is region based, this fanned one river-map miss out into many hydraulic erosion-region builds. Concurrent cold callers could also duplicate final terrain and erosion misses because the previous completed-only caches intentionally generated outside their locks without miss coalescing.

The result was not a compile-time failure: CI remained green while world creation could sit at `0%` for minutes.

## R41 pipeline

The physical final order remains:

`continent -> terrain -> erosion -> river -> wet-core -> receiver overlay -> climate/classification`

The expensive and cheap responsibilities are separated again:

- coarse drainage topology and visible-path search use the deterministic pre-erosion base terrain;
- final local river incision still uses post-erosion terrain;
- `ReceivingWaterOverlay` remains authoritative after river shaping;
- ocean and lake receiver beds therefore still override an incoming river where the final post-erosion surface belongs to the receiver;
- no Minecraft-side post-generation repair pass is introduced.

This deliberately avoids trying to evaluate hydraulic erosion for the entire padded drainage planning region. The receiver overlay is the reconciliation boundary between broad drainage planning and exact final local terrain.

## Single-flight caching

R41 adds `InlineSingleFlightCache` for expensive canonical datasets.

- The winning caller computes a cold key inline on its current thread.
- No work is submitted to the Minecraft/worldgen executor by the cache.
- No cache monitor is held during generation.
- Concurrent callers for the same key wait for the same future and reuse one result.
- Same-thread recursion for an owned key fails immediately instead of self-deadlocking.
- Completed values remain bounded by an access-ordered LRU.
- Failed generation is not retained as a completed value.

R41 applies this first to hydraulic erosion regions and final 16x16 terrain-sample tiles. Their loader dependency is acyclic: final tile -> hydrology -> erosion -> base terrain.

## Runtime regression coverage

The normal test suite now includes hard-timeout cold-start tests for:

- a structure-environment sample from a completely cold world;
- a cold 16x16 spawn-area terrain tile;
- eight concurrent callers requesting the same cold final tile.

A separate single-flight test asserts that one cold key executes its loader exactly once and that recursive ownership is rejected.

These tests specifically cover the runtime failure class that R40's compile/build checks did not detect.
