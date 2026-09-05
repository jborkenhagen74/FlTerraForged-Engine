# FlTerraForged Engine R42

R42 replaces the last receiver-specific write paths with one final continuous hydraulic resolution before Minecraft materialization.

## Base

- Parent revision: R41 (`revision/r41-startup-liveness`)
- R41 bounded inline single-flight caches and startup-liveness guarantees are retained.
- No Minecraft classes, chunks, biome sources or worldgen executors are introduced into the Engine.

## Final water ownership

Each final X/Z column is resolved through `ResolvedWaterResolver` into an immutable `ResolvedWaterField`.

Priority is deterministic:

1. `OCEAN`
2. `LAKE`
3. `RIVER`
4. `DRY`

The winning field contains the continuous bed height, water surface, hydraulic mask and owner metadata. `ReceivingWaterOverlay` only projects this already-resolved result back into the existing `Cell` representation.

## River mouths

Lake and ocean receivers own the final water surface as soon as the approach is accepted as connected receiver space. The approaching river may no longer preserve a lower narrow water plane inside the receiver.

The bed uses a bounded 12-block mouth blend toward the receiving bed. The blend may raise/fill an over-incised channel but never deepens the mouth beyond the already-shaped river bed.

Existing waterfall preservation remains active where both the hydraulic drop and terrain head justify a real drop.

## Confluences and linear river joins

`RiverWetCoreConnectivity` now probes only the immediate wet-core neighborhood for a higher-flow compatible river. A main stem wins deterministically by:

1. greater flow,
2. then greater width,
3. then smaller probe distance,
4. then lower water level as a stable final tie break.

A candidate must remain inside the wet core and within the configured water-level compatibility delta. This avoids forcing unrelated nearby channels together.

## Runtime and cache behavior

R42 adds no additional world-scale cache and no asynchronous executor work. Main-stem and receiver probes reuse the existing bounded `RiverModel` maps and R41 inline single-flight ownership. The resolver itself is allocation-small and contains no lock or cache.

## Verification targets

- River mouths inherit receiving lake/ocean surface levels.
- River bed trenches do not continue unchanged into receiving standing water.
- Main-stem joins do not retain two competing local water planes.
- Receiver priority is deterministic and independent of thread/order.
- No post-generation repair pass is required.
- R41 cold-start and single-flight tests remain mandatory.
