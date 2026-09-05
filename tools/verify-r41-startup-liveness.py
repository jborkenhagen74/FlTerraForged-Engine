#!/usr/bin/env python3
"""Static invariants for the R41 startup-liveness correction."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PIPELINE = ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/pipeline/WorldgenPipeline.java"
EROSION = ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/erosion/ErosionPipeline.java"
RIVER = ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverModel.java"
WORLD_CACHE = ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/WorldSampleCache.java"
SINGLE_FLIGHT = ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/internal/InlineSingleFlightCache.java"
STARTUP_TEST = ROOT / "src/test/java/dev/foucaultleon/flterraforged/engine/StartupLivenessTest.java"


def require(text: str, needle: str, message: str) -> None:
    if needle not in text:
        raise SystemExit(message)


def reject(text: str, needle: str, message: str) -> None:
    if needle in text:
        raise SystemExit(message)


pipeline = PIPELINE.read_text(encoding="utf-8")
erosion = EROSION.read_text(encoding="utf-8")
river = RIVER.read_text(encoding="utf-8")
world_cache = WORLD_CACHE.read_text(encoding="utf-8")
single_flight = SINGLE_FLIGHT.read_text(encoding="utf-8")
startup_test = STARTUP_TEST.read_text(encoding="utf-8")

require(
    pipeline,
    "context,\n                erosion,\n                baseLookup,\n                drainageClimate,",
    "R41 must use base terrain for coarse drainage while retaining erosion for final incision",
)
reject(
    pipeline,
    "context,\n                erosion,\n                erosion,\n                drainageClimate,",
    "R40 post-erosion drainage fan-out must not return",
)
require(
    pipeline,
    "new ReceivingWaterOverlay(",
    "R41 must retain R40 receiving-water ownership",
)
require(
    erosion,
    "InlineSingleFlightCache<Long, ErosionTile>",
    "erosion regions must use inline single-flight caching",
)
require(
    river,
    "InlineSingleFlightCache<Long, Rivermap>",
    "river maps must use inline single-flight caching",
)
require(
    river,
    "return cache.get(key, () -> generator.generate(regionX, regionZ));",
    "river-map cold misses must be coalesced before generator execution",
)
require(
    world_cache,
    "InlineSingleFlightCache<Long, TerrainSampleTile>",
    "final terrain tiles must use inline single-flight caching",
)
require(
    single_flight,
    "inFlight.putIfAbsent(key, mine)",
    "single-flight cache must elect one direct owner",
)
require(
    single_flight,
    "Recursive single-flight load for key",
    "same-thread recursive loads must fail instead of self-waiting",
)
reject(
    single_flight,
    "supplyAsync",
    "cache loaders must never schedule async work and synchronously wait for it",
)
require(
    startup_test,
    "coldStructureEnvironmentProbeCompletesWithinStartupBudget",
    "cold structure-environment startup regression test is missing",
)
require(
    startup_test,
    "concurrentColdSameTileCompletesAndSharesCanonicalSamples",
    "cold concurrent same-tile liveness test is missing",
)

print("R41 startup-liveness, receiver ownership and single-flight invariants passed")
