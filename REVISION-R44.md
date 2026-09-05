# FlTerraForged Engine R44

## Baseline

R44 is built from the promoted R32 source tree. R32 itself preserves the R29 terrain, river, lake, shoreline and climate semantics while adding bounded single-flight reuse for completed world samples and other expensive regions.

R44 changes hydrologic continuity. It does not introduce Minecraft, loader, biome, block, structure or Conquest Reforged dependencies into the Engine.

## Receiver-dominant resolved water network

The drainage graph is solved once per immutable `Rivermap` before visible river segments are constructed.

1. Priority flood and D8 drainage still produce the coarse topology.
2. Runoff accumulation now also returns a deterministic upstream-to-downstream topological order.
3. The hydraulic solve walks that order in reverse, from receivers toward headwaters.
4. Every drainage node receives one canonical water-surface Y.
5. Every incoming and outgoing segment uses the exact same canonical value at that node.

This is the central confluence invariant: a junction can no longer expose different water heights depending on which locally nearest segment happened to win a later X/Z query.

The normal river profile is receiver-dominant. A source may be lowered to satisfy the maximum ordinary water-surface grade, but it may never fall below its downstream receiver.

## Continuous cascades and waterfalls

A required vertical drop is represented by one continuous monotonic hydraulic path instead of by an upper wet segment ending and a lower segment beginning independently.

- drops below 2 blocks use the ordinary grade-limited profile;
- drops from 2 up to 4 blocks use a smooth cascade profile;
- drops of 4 blocks or more use a waterfall-style profile that keeps the upstream level through most of the segment and concentrates the drop before the receiver;
- the downstream endpoint remains pinned to the canonical receiver level.

The Engine describes continuous water-surface semantics only. Minecraft block/fluid realization remains the responsibility of FlTerraForged R54 and its active materializer provider.

## Local containment without hydraulic discontinuity

Terrain/bank probing may still lower interior path samples where local containment requires it. It is no longer allowed to push an interior water sample below the canonical receiver level, because that would force the river to rise again before its downstream node.

The first and last path samples are pinned to their canonical drainage-node levels and are no longer overwritten by path-specific containment results.

## River-map cache

R44 replaces the former 64 striped generation locks with exact-key synchronous single flight.

- one caller owns a cold river-region key;
- the owner generates the immutable map inline on its current worker;
- callers requesting the same key reuse the same future/result;
- unrelated keys never serialize merely because they hash to the same lock stripe;
- expensive generation never runs while the bounded completed-map LRU monitor is held;
- failures are not retained as completed cache entries.

No work is submitted to a world-generation executor and synchronously waited on.

## Required invariants

R44 must preserve the following properties:

- water surfaces never rise downstream along a resolved segment;
- all segments sharing one drainage node share one endpoint water level;
- normal river grades remain bounded;
- large drops remain continuous and monotonic;
- river-map cold misses are coalesced per exact key;
- negative region coordinates continue to use floor-division semantics;
- the Engine remains Minecraft-agnostic and provider-agnostic.

## Paired host revision

R44 is intended to be used with FlTerraForged R54. R54 consumes the resolved Engine hydrology and materializes it through the provider geometry contract, including partial/variable-height terrain surfaces.
