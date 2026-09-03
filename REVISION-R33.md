# FlTerraForged Engine R33

## Runtime-liveness correction

R33 responds to a Minecraft 1.20.1 runtime regression in the R32 cache design: CI and isolated concurrency tests succeeded, but real spawn generation could remain at 0% because world-generation workers were allowed to wait synchronously for cache ownership and sparse host probes could force complete 16x16 terrain tiles.

R33 therefore makes liveness a hard cache invariant: a cache miss never waits for another generating thread.

## Adaptive final-sample cache

The final terrain cache now combines two bounded completed caches:

- dense 16x16 terrain tiles for normal chunk generation;
- exact X/Z point samples for sparse environment and structure probes.

A cold tile starts in sparse mode. After four misses address the same tile it is promoted to a complete 16x16 tile. Sparse structure probes separated by 32 or 64 blocks therefore calculate individual points instead of materializing an entire tile for every probe, while dense chunk access quickly returns to bulk tile sampling.

All expensive Engine work runs outside cache monitors. Concurrent cold callers may calculate the same deterministic value more than once, but only one completed value is retained. Bounded duplicate calculation is preferred over any synchronous dependency between Minecraft world-generation workers.

## Regional caches

The R32 key-striped ownership locks in erosion-region and river-map caches are removed. These caches again follow the proven R29 optimistic pattern:

1. short completed-cache lookup;
2. calculate outside the cache monitor;
3. short second lookup and insertion.

This preserves deterministic shared completed state without turning cache reuse into a worldgen scheduling dependency.

## Regression policy

The cold-cache concurrency regression now verifies bounded completion and deterministic equality rather than object identity. Sequential repeated samples still reuse their cached immutable value.

R33 intentionally preserves the R29 terrain, hydrology, shoreline and climate semantics used by R32. The change is limited to cache execution strategy and sparse-vs-dense reuse.

## Paired host revision

R33 is intended for FlTerraForged R37, which removes blocking single-flight waits from the marine environment cache and rejects implausible beached shipwreck starts before perimeter sampling.
