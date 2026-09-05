#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
provider = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/DefaultEngineProvider.java").read_text(encoding="utf-8")
pipeline = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/pipeline/WorldgenPipeline.java").read_text(encoding="utf-8")
river_generator = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RivermapGenerator.java").read_text(encoding="utf-8")
connectivity_path = root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverWetCoreConnectivity.java"
errors = []

if not connectivity_path.is_file():
    errors.append("missing RiverWetCoreConnectivity")
else:
    connectivity = connectivity_path.read_text(encoding="utf-8")
    for token in (
            "GUARANTEED_CORE_FRACTION",
            "delegate.nearest(x, z)",
            "applyReceivingWaterAuthority",
            "isOpenOceanReceiver",
            "nearbyLakeLevel",
            "RECEIVER_PROBES",
            "receiverLevel = world.seaLevel()",
            "WATERFALL_MINIMUM_WATER_DROP",
            "preserveWaterfallApproach",
    ):
        if token not in connectivity:
            errors.append(f"RiverWetCoreConnectivity missing R38 invariant: {token}")
    for forbidden in (
            "net.minecraft.",
            "CompletableFuture",
            ".join()",
            "synchronized (",
    ):
        if forbidden in connectivity:
            errors.append(f"RiverWetCoreConnectivity contains forbidden dependency/blocking path: {forbidden}")

for token in (
        "WATERFALL_MINIMUM_TERRAIN_DROP",
        "WATERFALL_MINIMUM_WATER_DROP",
        "WATERFALL_MINIMUM_TERRAIN_GRADE",
        "boolean waterfall =",
        "if (waterfall)",
        "limitWaterSurfaceGrade(pathX, pathZ, terrainHeight, waterHeight)",
):
    if token not in river_generator:
        errors.append(f"RivermapGenerator missing waterfall invariant: {token}")

for token in (
        "private final CellLookup river;",
        "new RiverWetCoreConnectivity(context, riverModel)",
        "this.terrain = new TerrainModel(context, river)",
):
    if token not in pipeline:
        errors.append(f"WorldgenPipeline missing R38 routing invariant: {token}")

if 'VERSION = "0.1.0-SNAPSHOT-r38"' not in provider:
    errors.append("DefaultEngineProvider must report r38")

if errors:
    print("R38 receiving-water authority verification failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("R38 receiving-water authority and waterfall preservation verification passed")
