#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
java_root = root / "src/main/java"
banned = (
    "net.minecraft.", "net.fabricmc.", "net.neoforged.", "net.minecraftforge.",
    "com.mojang.serialization.",
)
errors = []

for source in java_root.rglob("*.java"):
    text = source.read_text(encoding="utf-8")
    for token in banned:
        if token in text:
            errors.append(f"{source.relative_to(root)}: forbidden {token}")

service = root / "src/main/resources/META-INF/services/dev.foucaultleon.flterraforged.engine.api.EngineProvider"
if not service.is_file():
    errors.append("missing EngineProvider ServiceLoader descriptor")

build_text = (root / "build.gradle").read_text(encoding="utf-8")
workflow_text = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
if "maven.pkg.github.com" in build_text:
    errors.append("Engine API must not be resolved from GitHub Packages")
if "FLTERRAFORGED_PACKAGES_TOKEN" in build_text or "FLTERRAFORGED_PACKAGES_TOKEN" in workflow_text:
    errors.append("Engine API resolution must not require a package token")
if "packages: read" in workflow_text or "packages: write" in workflow_text:
    errors.append("Workflow must not require GitHub Packages permissions")
if "raw.githubusercontent.com/jborkenhagen74/FlTerraForged/maven/" not in build_text:
    errors.append("missing default public FlTerraForged API Maven repository")

river_segment = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverSegment.java").read_text(encoding="utf-8")
river_model = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverModel.java").read_text(encoding="utf-8")
cell = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/cell/Cell.java").read_text(encoding="utf-8")
engine = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/DefaultTerrainEngine.java").read_text(encoding="utf-8")
for token, label in (("waterSurfaceHeight", "directed river water surface"), ("WATER_SURFACE_INSET", "stable bank inset")):
    if token not in river_segment:
        errors.append(f"RiverSegment missing {label}")
for token in ("riverWaterSurfaceHeight", "riverFlow"):
    if token not in river_model or token not in cell:
        errors.append(f"Engine hydrology pipeline missing {token}")
if "EngineCapability.RIVER_WATER_LEVEL" not in engine:
    errors.append("Default engine does not advertise RIVER_WATER_LEVEL")

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)

print("Engine layout verified: Java-only, ServiceLoader provider present, public Maven API model")
