#!/usr/bin/env python3
from pathlib import Path
import re
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


def split_record_components(component_text):
    """Returns record component declarations split on top-level commas."""
    parts = []
    current = []
    angle = paren = bracket = 0
    for char in component_text:
        if char == '<':
            angle += 1
        elif char == '>':
            angle = max(0, angle - 1)
        elif char == '(':
            paren += 1
        elif char == ')':
            paren = max(0, paren - 1)
        elif char == '[':
            bracket += 1
        elif char == ']':
            bracket = max(0, bracket - 1)
        if char == ',' and angle == 0 and paren == 0 and bracket == 0:
            parts.append(''.join(current).strip())
            current = []
        else:
            current.append(char)
    if current:
        parts.append(''.join(current).strip())
    return [part for part in parts if part]


def verify_compact_record_javadocs(source, text):
    """Requires complete @param docs on every public compact record constructor."""
    for match in re.finditer(r"public\s+record\s+(\w+)\s*\((.*?)\)\s*\{", text, re.DOTALL):
        record_name = match.group(1)
        components = []
        for declaration in split_record_components(match.group(2)):
            name_match = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*$", declaration)
            if name_match:
                components.append(name_match.group(1))

        ctor_match = re.search(r"\bpublic\s+" + re.escape(record_name) + r"\s*\{", text[match.end():])
        if ctor_match is None:
            continue
        ctor_start = match.end() + ctor_match.start()
        prefix = text[:ctor_start]
        doc_start = prefix.rfind('/**')
        doc_end = prefix.find('*/', doc_start) if doc_start >= 0 else -1
        if doc_start < 0 or doc_end < 0 or prefix[doc_end + 2:].strip():
            errors.append(f"{source.relative_to(root)}: public compact constructor {record_name} is missing Javadoc")
            continue
        doc = prefix[doc_start:doc_end + 2]
        for component in components:
            if re.search(r"@param\s+" + re.escape(component) + r"(?:\s|$)", doc) is None:
                errors.append(
                    f"{source.relative_to(root)}: compact constructor {record_name} missing @param {component}"
                )


for source in java_root.rglob("*.java"):
    verify_compact_record_javadocs(source, source.read_text(encoding="utf-8"))

service = root / "src/main/resources/META-INF/services/dev.foucaultleon.flterraforged.engine.api.EngineProvider"
if not service.is_file():
    errors.append("missing EngineProvider ServiceLoader descriptor")

build_text = (root / "build.gradle").read_text(encoding="utf-8")
workflow_text = (root / ".github/workflows/build.yml").read_text(encoding="utf-8")
gitignore_text = (root / ".gitignore").read_text(encoding="utf-8")
if "options.addBooleanOption('Werror', true)" not in build_text:
    errors.append("strict Javadoc -Werror verification is missing")
if "dependsOn 'javadoc'" not in build_text:
    errors.append("check must depend on javadoc so documentation warnings fail before publish")
for ignored in ("gradlew", "gradlew.bat", "gradle/wrapper/"):
    if ignored not in gitignore_text.splitlines():
        errors.append(f".gitignore missing required wrapper rule: {ignored}")
if "maven.pkg.github.com" in build_text:
    errors.append("Engine API must not be resolved from GitHub Packages")
if "FLTERRAFORGED_PACKAGES_TOKEN" in build_text or "FLTERRAFORGED_PACKAGES_TOKEN" in workflow_text:
    errors.append("Engine API resolution must not require a package token")
if "packages: read" in workflow_text or "packages: write" in workflow_text:
    errors.append("Workflow must not require GitHub Packages permissions")
if "raw.githubusercontent.com/jborkenhagen74/FlTerraForged/maven/" not in build_text:
    errors.append("missing default public FlTerraForged API Maven repository")

river_segment = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverSegment.java").read_text(encoding="utf-8")
river_generator = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RivermapGenerator.java").read_text(encoding="utf-8")
river_model = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/RiverModel.java").read_text(encoding="utf-8")
lake_field = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/river/LakeField.java").read_text(encoding="utf-8")
cell = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/cell/Cell.java").read_text(encoding="utf-8")
engine = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/DefaultTerrainEngine.java").read_text(encoding="utf-8")
for token, label in (
        ("startWaterHeight", "directed water-surface endpoints"),
        ("List<RiverPathPoint>", "terrain-refined visible path"),
        ("waterSurfaceHeight()", "path-local contained water profile"),
        ("bankAlpha", "stable wet-core channel profile"),
):
    if token not in river_segment:
        errors.append(f"RiverSegment missing {label}")
for token, label in (
        ("fillDepressions", "priority-flood depression resolution"),
        ("refineVisiblePath", "terrain-guided centerline refinement"),
        ("containmentCeiling", "cross-bank water containment"),
        ("bankProbe", "local river-bank probing"),
        ("LakeField", "pond/lake construction"),
        ("accumulateFlow", "acyclic flow accumulation"),
        ("localRunoff", "climate-weighted runoff"),
):
    if token not in river_generator:
        errors.append(f"RivermapGenerator missing {label}")
for token in ("riverWaterSurfaceHeight", "riverFlow", "minimumWaterDepth", "nearestLake", "drainageClimate"):
    if token not in river_model:
        errors.append(f"Engine hydrology pipeline missing {token}")
if "public boolean lake" not in cell:
    errors.append("Cell is missing inland-water semantic")
if "public boolean lakeShore" not in cell:
    errors.append("Cell is missing explicit lake-shore semantic")
for token in ("identifyBasins", "basinWaterLevels", "dominantBasin", "smoothValueNoise", "LakeZone.SHORE", "LakeZone.SHALLOW", "LakeZone.CORE"):
    if token not in lake_field:
        errors.append(f"LakeField missing basin-aware lake logic: {token}")
if "bilinear(filledHeight" in lake_field:
    errors.append("LakeField must not bilinearly interpolate depression spill heights into a tilted water surface")
if "lake.materialWater()" not in river_model or "lake.shore()" not in river_model:
    errors.append("RiverModel must distinguish material lake water from the dry shore transition")
if "EngineCapability.RIVER_WATER_LEVEL" not in engine:
    errors.append("Default engine does not advertise RIVER_WATER_LEVEL")

terrain_classifier = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/terrain/TerrainClassifier.java").read_text(encoding="utf-8")
if "StandardTerrainTypes.LAKE_SHORE" in terrain_classifier:
    errors.append("TerrainClassifier must remain compatible with the baseline API that predates the LAKE_SHORE convenience constant")
if 'TerrainType.of(StandardTerrainTypes.NAMESPACE, "lake_shore")' not in terrain_classifier:
    errors.append("TerrainClassifier missing canonical flterraforged:lake_shore compatibility semantic")


terrain_sampler = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/terrain/region/TerrainRegionSampler.java").read_text(encoding="utf-8")
terrain_blender = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/terrain/Blender.java").read_text(encoding="utf-8")
for token, label in (("sampleBlend", "multi-region terrain sampling"), ("NEIGHBORHOOD_SIZE", "full local Voronoi blending"), ("neighborScore", "continuous neighbor influence")):
    if token not in terrain_sampler:
        errors.append(f"TerrainRegionSampler missing {label}")
if "TerrainRegionBlendSample" not in terrain_blender:
    errors.append("Blender missing multi-region terrain composite support")

climate_layout = root / "src/main/java/dev/foucaultleon/flterraforged/engine/climate/ClimateLayout.java"
engine_settings = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/EngineSettings.java").read_text(encoding="utf-8")
climate_model = (root / "src/main/java/dev/foucaultleon/flterraforged/engine/climate/ClimateModel.java").read_text(encoding="utf-8")
if not climate_layout.is_file():
    errors.append("missing ClimateLayout strategy enum")
for token in ("ClimateLayout.RANDOMIZED", 'ClimateLayout.parse(config.getOrDefault("climateLayout"', "case CENTRAL_EUROPE"):
    if token not in engine_settings:
        errors.append(f"EngineSettings missing configurable climate/preset support: {token}")
if "settings.layout() == ClimateLayout.NORTH_SOUTH" not in climate_model:
    errors.append("ClimateModel must apply north-south climate only when explicitly selected")
if "double broadTemperature = contrast(" not in climate_model or "double broadMoisture = contrast(" not in climate_model:
    errors.append("ClimateModel must apply preset climate contrast to broad randomized fields")

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)

print("Engine layout verified: Java-only, ServiceLoader provider present, public Maven API model")
