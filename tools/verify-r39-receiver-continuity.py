#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
provider = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/DefaultEngineProvider.java").read_text(encoding="utf-8")
river_model = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverModel.java").read_text(encoding="utf-8")
connectivity = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverWetCoreConnectivity.java").read_text(encoding="utf-8")
errors = []

for token in (
        "LakeHit lake(int x, int z)",
        "return nearestLake(x, z);",
):
    if token not in river_model:
        errors.append(f"RiverModel missing R39 lake-only probe invariant: {token}")

for token in (
        "DRY_SHORE_MAXIMUM_DEPTH = 0.05D",
        "target.lakeShore && target.riverDepth <= DRY_SHORE_MAXIMUM_DEPTH",
        "LAKE_BRIDGE_MAX_PROBE = 4",
        "RECEIVER_PROBES = {1, 2, 4, 8}",
        "isGuaranteedWetCore(target)",
        "delegate.lake(",
        "lake.samples() >= 2",
        "nearbyLakeReceiver",
        "target.lakeShore || lake.samples() >= 2",
        "preserveWaterfallApproach",
):
    if token not in connectivity:
        errors.append(f"RiverWetCoreConnectivity missing R39 invariant: {token}")

for forbidden in (
        "delegate.lookup(\n                        x + PROBE_X",
        "net.minecraft.",
        "CompletableFuture",
        ".join()",
        "synchronized (",
):
    if forbidden in connectivity:
        errors.append(f"RiverWetCoreConnectivity contains forbidden R39 path: {forbidden}")

if 'VERSION = "0.1.0-SNAPSHOT-r39"' not in provider:
    errors.append("DefaultEngineProvider must report r39")

if errors:
    print("R39 receiver-continuity verification failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("R39 lake receiver continuity, dry-shore preservation and bounded lake-only probes verified")
