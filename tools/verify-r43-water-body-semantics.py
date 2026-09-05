#!/usr/bin/env python3
"""Static invariants for Engine R43 confined-channel receiver ownership and caching."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


resolver = read("src/main/java/dev/foucaultleon/flterraforged/engine/river/ResolvedWaterResolver.java")
environment_cache = read("src/main/java/dev/foucaultleon/flterraforged/engine/EnvironmentSampleCache.java")
world = read("src/main/java/dev/foucaultleon/flterraforged/engine/DefaultTerrainWorld.java")

required_resolver = [
    "boolean riverOwned",
    "if (riverOwned) {\n            return establishedOcean;",
    "continentalness < classification.oceanContinentalness()",
    "height < world.seaLevel() - classification.oceanDepthBelowSea()",
    "shouldPromoteLakeMouth(lake, shaped, riverOwned)",
    "if (riverOwned) {\n            return false;",
]
for marker in required_resolver:
    if marker not in resolver:
        raise SystemExit(f"missing R43 receiver invariant: {marker}")

required_cache = [
    "InlineSingleFlightCache<Long, TerrainEnvironmentSample>",
    "DEFAULT_MAXIMUM_SAMPLES = 8192",
    "cache.get(key, () -> pipeline.environment(x, z))",
]
for marker in required_cache:
    if marker not in environment_cache:
        raise SystemExit(f"missing R43 environment-cache invariant: {marker}")

if "CompletableFuture" in environment_cache or "Executor" in environment_cache:
    raise SystemExit("R43 environment cache must not schedule asynchronous worldgen work")

if "return environmentCache.sample(x, z);" not in world:
    raise SystemExit("DefaultTerrainWorld.environment must use the R43 sparse cache")
if "environmentCache.clear();" not in world:
    raise SystemExit("R43 environment cache must be cleared with the world")

print("R43 water-body semantics and placement cache invariants verified")
