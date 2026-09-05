#!/usr/bin/env python3
"""Verify Engine R36 support for the host's one-time final wet pass."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise SystemExit(f"ERROR: {message}")


def main() -> None:
    provider = (ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/DefaultEngineProvider.java").read_text(encoding="utf-8")
    default_world = (ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/DefaultTerrainWorld.java").read_text(encoding="utf-8")
    cache = (ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/WorldSampleCache.java").read_text(encoding="utf-8")
    classification = (ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/terrain/TerrainClassificationSettings.java").read_text(encoding="utf-8")
    classifier = (ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/terrain/TerrainClassifier.java").read_text(encoding="utf-8")
    smoke = (ROOT / "src/test/java/dev/foucaultleon/flterraforged/engine/EngineSmokeTest.java").read_text(encoding="utf-8")

    if 'VERSION = "0.1.0-SNAPSHOT-r36"' not in provider:
        fail("Default engine provider must report R36")

    for required in (
        "public TerrainSample[] sampleTile(int originX, int originZ, int size)",
        "return sampleCache.sampleTile(originX, originZ, size);",
    ):
        if required not in default_world:
            fail(f"DefaultTerrainWorld missing bulk sampling hook: {required}")

    for required in (
        "TerrainSample[] sampleTile(int originX, int originZ, int size)",
        "size == TILE_SIZE",
        "Math.floorMod(originX, TILE_SIZE) == 0",
        "copySamples()",
        "loadTile(",
    ):
        if required not in cache:
            fail(f"WorldSampleCache missing aligned tile optimization: {required}")
    for forbidden in ("CompletableFuture", ".join()", "supplyAsync"):
        if forbidden in cache:
            fail(f"R36 final-sample cache must not wait on worldgen workers: {forbidden}")

    if "Math.max(4.0D, settings.relief() * 0.09D)" not in classification:
        fail("R36 dry coast band must be wide enough for host material blending")
    for required in (
        "boolean submergedMarine = oceanward && height < seaLevel",
        "&& height >= seaLevel",
    ):
        if required not in classifier:
            fail(f"R36 must retain dry-coast marine semantics: {required}")

    if "alignedTileSamplingMatchesPointSampling" not in smoke or "assertArrayEquals(expected, tile" not in smoke:
        fail("R36 bulk sampling regression test is missing")

    print("OK: Engine R36 exposes non-blocking aligned tile reuse and preserves dry coast semantics")


if __name__ == "__main__":
    main()
