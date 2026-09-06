#!/usr/bin/env python3
"""Verify Engine isolation, strict Javadocs and R53 water-state/performance invariants."""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "src/main/java"
TEST_ROOT = ROOT / "src/test/java"
BANNED = (
    "net.minecraft.",
    "net.fabricmc.",
    "net.neoforged.",
    "net.minecraftforge.",
    "com.mojang.serialization.",
)
ERRORS = []


def require(path: Path, tokens, label: str) -> str:
    if not path.is_file():
        ERRORS.append(f"missing {label}: {path.relative_to(ROOT)}")
        return ""
    text = path.read_text(encoding="utf-8")
    for token in tokens:
        if token not in text:
            ERRORS.append(f"{label} missing invariant: {token}")
    return text


def split_record_components(component_text: str):
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


def verify_compact_record_javadocs(source: Path, text: str) -> None:
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
            ERRORS.append(f"{source.relative_to(ROOT)}: public compact constructor {record_name} is missing Javadoc")
            continue
        doc = prefix[doc_start:doc_end + 2]
        for component in components:
            if re.search(r"@param\s+" + re.escape(component) + r"(?:\s|$)", doc) is None:
                ERRORS.append(f"{source.relative_to(ROOT)}: compact constructor {record_name} missing @param {component}")


for source in JAVA_ROOT.rglob("*.java"):
    text = source.read_text(encoding="utf-8")
    for token in BANNED:
        if token in text:
            ERRORS.append(f"{source.relative_to(ROOT)}: forbidden dependency {token}")
    verify_compact_record_javadocs(source, text)

service = ROOT / "src/main/resources/META-INF/services/dev.foucaultleon.flterraforged.engine.api.EngineProvider"
if not service.is_file():
    ERRORS.append("missing EngineProvider ServiceLoader descriptor")

build_text = (ROOT / "build.gradle").read_text(encoding="utf-8")
workflow_text = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
gitignore = set((ROOT / ".gitignore").read_text(encoding="utf-8").splitlines())
if "options.addBooleanOption('Werror', true)" not in build_text:
    ERRORS.append("strict Javadoc -Werror verification is missing")
if "dependsOn 'javadoc'" not in build_text:
    ERRORS.append("check must depend on javadoc")
for ignored in ("gradlew", "gradlew.bat", "gradle/wrapper/"):
    if ignored not in gitignore:
        ERRORS.append(f".gitignore missing required wrapper rule: {ignored}")
if "raw.githubusercontent.com/jborkenhagen74/FlTerraForged/maven/" not in build_text:
    ERRORS.append("missing public FlTerraForged Engine API Maven repository")
if "maven.pkg.github.com" in build_text or "packages: read" in workflow_text or "packages: write" in workflow_text:
    ERRORS.append("Engine must not depend on GitHub Packages")

require(
    ROOT / ".github/workflows/build.yml",
    ("release/r53-water-state-consistency", "FlTerraForged-Engine-R53-${short_sha}.jar", "Publish Engine R53"),
    "R53 workflow")
require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/DefaultEngineProvider.java",
    ('VERSION = "0.1.0-SNAPSHOT-r53"', "EngineApiVersion.CURRENT"),
    "R53 provider")

world_cache = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/WorldSampleCache.java",
    ("TILE_SIZE = 16", "sampleChunk(int chunkX, int chunkZ)", "sampleChunkShared", "sharedSamples", "inFlight", "ownedKeys"),
    "R53 final sample cache")
if "synchronized" in world_cache:
    ERRORS.append("final sample cache must not use a global synchronized monitor")
require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/internal/BoundedConcurrentCache.java",
    ("ConcurrentHashMap", "ConcurrentLinkedQueue", "putIfAbsent"),
    "bounded concurrent cache")
require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/erosion/ErosionSettings.java",
    ("\n                64,\n", "\n                16,\n", "cacheSize", "return new ErosionSettings"),
    "R53 erosion settings")
erosion = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/erosion/ErosionPipeline.java",
    ("centeredRegion", "Math.addExact(coordinate, regionSize / 2)", "inFlight", "ownedRegionKeys"),
    "R53 centered erosion ownership")
if "Math.floorDiv(x, settings.regionSize())" in erosion:
    ERRORS.append("R53 erosion must not place world origin on a region boundary")

require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/terrain/TerrainClassificationSettings.java",
    ("-0.34D", "1.25D", "0.08D"),
    "R53 physical coast thresholds")
classifier = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/terrain/TerrainClassifier.java",
    ("physicalMarineShelf", "!submerged", "StandardTerrainTypes.COAST"),
    "R53 shoreline classifier")
if "SUBMERGED_SHELF_CONTINENTALNESS_EXTENSION" in classifier:
    ERRORS.append("R53 must not classify ocean through an independent shelf extension")

pipeline = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/pipeline/WorldgenPipeline.java",
    ("placementClimate", "sampleTile(int originX", "RIVER_MOUTH_SEAWARD", "RIVER_MOUTH_LANDWARD", "targetWater", "targetBed"),
    "R53 canonical hydrology pipeline")
for banned in ("MARINE_APRON_CONTINENTALNESS", "minimumDryHeight", "cell.riverWaterSurfaceHeight = seaLevel"):
    if banned in pipeline:
        ERRORS.append(f"R53 pipeline must not contain post-hoc hard shoreline correction: {banned}")

world = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/DefaultTerrainWorld.java",
    ("placementSample(int x, int z)", "sampleChunk(int chunkX, int chunkZ)", "sampleCache.sampleChunkShared"),
    "R53 terrain world")
require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/ChunkSnapshotCache.java",
    ("samples.sampleChunk(chunkX, chunkZ)", "terrain.length != 256", "inFlight", "ownedKeys"),
    "R53 chunk snapshot cache")

snapshot = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/EngineChunkSnapshot.java",
    ("private static final NaturalMaterial[] MATERIALS", "MATERIALS[ordinal]", "Taking ownership here avoids cloning"),
    "R53 immutable chunk snapshot")
if "NaturalMaterial.values()[" in snapshot:
    ERRORS.append("materialAt must not allocate an enum values array per voxel")
if 'Objects.requireNonNull(columns, "columns").clone()' in snapshot or 'Objects.requireNonNull(materials, "materials").clone()' in snapshot:
    ERRORS.append("R53 snapshot must not duplicate its exclusively-owned column/material arrays")

subsurface = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/SubsurfaceGenerator.java",
    ("naturalTopY", "VerticalNoiseSampler", "resolveSurfaceWaterTop", "WATER_QUANTIZATION_EPSILON", "hydrologyTop = Math.max(hydrologyTop, solidTop + 1)"),
    "R53 subsurface generator")
for banned in ("MOUTH_CLAMP_HEIGHT", "Math.min(waterSurface, context.seaLevel())", "hydrology.waterSurfaceHeight() > sample.surfaceHeight()"):
    if banned in subsurface:
        ERRORS.append(f"R53 subsurface must not independently re-decide or re-clamp surface water: {banned}")

require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/LakeHit.java",
    ("long basinKey", "hasBasinKey", "withWaterSurfaceHeight"),
    "R53 lake hit")
require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/LakeField.java",
    ("basinKeys", "stable hydrology-grid anchor", "keys.add(key(anchorX, anchorZ))"),
    "R53 lake basin anchors")
river_model = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/RiverModel.java",
    ("canonicalLakeLevels", "ownerRegionX", "owner.basinKey() == local.basinKey()", "ownedMapKeys"),
    "R53 lake reconciliation")
if "for (int dz = -1; dz <= 1; dz++)" in river_model and "nearestLake" in river_model:
    ERRORS.append("R53 must not restore unconditional eight-neighbor lake probing")
require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/RiverNetworkFilter.java",
    ("visibleNetwork", "streamOrder"),
    "visible river hierarchy")

require(
    TEST_ROOT / "dev/foucaultleon/flterraforged/engine/R52CoastOceanConsistencyTest.java",
    ("sampledCoastUsesPhysicalHeightWithoutArtificialWall", "chunkSnapshotsContainContiguousCanonicalOceanWater"),
    "coast/ocean sampling regressions")
require(
    TEST_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/R53SurfaceWaterConsistencyTest.java",
    ("inlandRiverAboveSeaLevelKeepsItsHydrologySurface", "nearSeaInlandRiverIsNotClampedBySubsurfacePass", "inlandLakeAboveSeaLevelStaysCompletelyFilled", "dryLakeShoreDoesNotBecomeWaterFromFiniteReferenceLevel"),
    "R53 inland surface-water regressions")
require(
    TEST_ROOT / "dev/foucaultleon/flterraforged/engine/erosion/R46ErosionConcurrencyTest.java",
    ("formerlyCollidingStripeKeysCanGenerateConcurrently",),
    "erosion concurrency regression")
require(
    TEST_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/R47ChunkSnapshotTest.java",
    ("repeatedAndConcurrentRequestsShareOneImmutableSnapshot",),
    "chunk snapshot concurrency regression")

if ERRORS:
    print("\n".join(ERRORS), file=sys.stderr)
    raise SystemExit(1)

print("Engine R53 layout verified: one canonical surface-water authority, inland water regressions and reduced chunk-copy overhead")
