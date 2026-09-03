# FlTerraForged Engine R32

## Baseline

R32 is intentionally rebuilt from the actual `0.1.0-SNAPSHOT-r29` baseline (`6fc06491d212d2ad38ece463e81925393828b984`). It does not inherit the later experimental world-generation cache/guard line.

The terrain, river, lake, shoreline and climate semantics therefore remain those of R29. R32 is a concurrency and reuse revision.

## Final terrain-sample cache

`WorldSampleCache` remains a bounded 16x16-tile LRU, but a cold miss now uses single-flight ownership per exact tile key.

- The first caller becomes the owner and computes synchronously on its current worker.
- Concurrent callers for the same tile wait for and reuse the owner's immutable result.
- No additional task is submitted to a Minecraft/world-generation executor.
- Expensive pipeline work never runs while the completed-tile LRU monitor is held.
- Different tile keys remain independent.
- Negative coordinates continue to use `Math.floorDiv` semantics.

This removes the previous generate-then-discard amplification in which several workers could calculate the same final 16x16 tile simultaneously.

## Erosion-region cache

The bounded erosion-region cache uses small key-striped generation locks. A missing erosion region is rechecked after acquiring its stripe and is generated once while unrelated stripes remain parallel. The completed LRU lock is held only for lookup and insertion.

## River-map cache

The bounded `RiverModel` map cache uses the same key-striped cold-miss rule. Neighboring final-sample tiles frequently query the same drainage region; only the first worker for that region now executes `RivermapGenerator.generate(...)`, while other workers reuse the completed immutable map. Unrelated stripes remain parallel and the LRU monitor is held only for lookup/insertion.

Together, final terrain tiles, erosion regions and river maps no longer use the previous generate-then-discard pattern for identical concurrent misses.

## Layering rule

A cache loader in R32 must remain lower-level and deterministic. In particular it must not:

- request Minecraft chunks,
- call Minecraft biome or structure generation,
- submit work to a world-generation executor and synchronously wait for it,
- hold a global cache lock while terrain is calculated.

Higher-level FlTerraForged environment checks are expected to consume `TerrainWorld.sample(...)`, thereby sharing R32's final immutable tile data instead of recreating terrain observations themselves.

## Regression coverage

`WorldSampleCacheConcurrencyTest` starts eight workers on the same cold sample and verifies that all workers receive the same cached `TerrainSample` instance. Existing strict Javadoc and hydrology regression checks remain enabled.

## Paired host revision

R32 is intended to be used with FlTerraForged R36, whose marine environment cache adds a second, Minecraft-facing reuse layer without moving Minecraft concepts into the Engine.
