#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
engine = root / "src/main/java/dev/foucaultleon/flterraforged/engine"
pipeline = (engine / "pipeline/WorldgenPipeline.java").read_text(encoding="utf-8")
overlay_path = engine / "river/ReceivingWaterOverlay.java"
connectivity = (engine / "river/RiverWetCoreConnectivity.java").read_text(encoding="utf-8")
provider = (engine / "DefaultEngineProvider.java").read_text(encoding="utf-8")
errors = []

if '0.1.0-SNAPSHOT-r40' not in provider:
    errors.append("DefaultEngineProvider is not R40")

if not overlay_path.is_file():
    errors.append("missing ReceivingWaterOverlay")
else:
    overlay = overlay_path.read_text(encoding="utf-8")
    for token in (
            "target.heightErosion",
            "applyOceanReceiver",
            "applyLakeReceiver",
            "shouldPromoteLakeMouth",
            "target.riverWaterSurfaceHeight = Double.NaN",
            "target.lake = true",
    ):
        if token not in overlay:
            errors.append(f"ReceivingWaterOverlay missing invariant: {token}")
    if "preHydrologyTerrain.lookup" in overlay:
        errors.append("receiver overlay must not re-run terrain/erosion sampling")

for token in (
        "erosion,\n                erosion,\n                drainageClimate",
        "new RiverWetCoreConnectivity(context, riverModel)",
        "new ReceivingWaterOverlay(",
        "classificationSettings",
):
    if token not in pipeline:
        errors.append(f"WorldgenPipeline missing R40 ordering invariant: {token}")

if pipeline.find("new RiverWetCoreConnectivity") > pipeline.find("new ReceivingWaterOverlay"):
    errors.append("receiver overlay must be composed after wet-core connectivity")

for token in (
        "Math.max(target.height, desiredBed)",
        "receiverLevel - MINIMUM_WATER_DEPTH",
        "Receiver alignment may fill/raise an over-incised river mouth, never deepen it further",
):
    if token not in connectivity:
        errors.append(f"RiverWetCoreConnectivity missing non-deepening invariant: {token}")

if errors:
    print("R40 receiver-overlay verification failed:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("R40 post-erosion drainage and final receiving-water overlay verification passed")
