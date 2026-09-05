#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
provider = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/DefaultEngineProvider.java").read_text(encoding="utf-8")
pipeline = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/pipeline/WorldgenPipeline.java").read_text(encoding="utf-8")
connectivity_path = root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverWetCoreConnectivity.java"
errors = []

if not connectivity_path.is_file():
    errors.append("missing RiverWetCoreConnectivity")
else:
    connectivity = connectivity_path.read_text(encoding="utf-8")
    for token in (
            "GUARANTEED_CORE_FRACTION",
            "MINIMUM_CORE_RADIUS",
            "FRINGE_MAXIMUM_CORRECTION",
            "delegate.nearest(x, z)",
            "target.riverWaterSurfaceHeight = hit.waterSurfaceHeight()",
            "target.height = finalHeight",
    ):
        if token not in connectivity:
            errors.append(f"RiverWetCoreConnectivity missing invariant: {token}")

for token in (
        "private final CellLookup river;",
        "new RiverWetCoreConnectivity(context, riverModel)",
        "this.terrain = new TerrainModel(context, river)",
):
    if token not in pipeline:
        errors.append(f"WorldgenPipeline missing R37 routing invariant: {token}")

if 'VERSION = "0.1.0-SNAPSHOT-r37"' not in provider:
    errors.append("DefaultEngineProvider must report r37")

if errors:
    print("R37 wet-core verification failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("R37 river wet-core connectivity verification passed")
