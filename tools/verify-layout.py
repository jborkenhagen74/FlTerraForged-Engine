#!/usr/bin/env python3
"""Verify Engine isolation, retained hydrology guarantees and R47 chunk ownership."""

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
if "release/r47-engine-owned-worldgen" not in workflow_text:
    ERRORS.append("R47 workflow must publish the architecture branch snapshot")

provider = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/DefaultEngineProvider.java",
    ('VERSION = "0.1.0-SNAPSHOT-r47"', "EngineApiVersion.CURRENT"),
    "R47 provider")

world_cache = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/WorldSampleCache.java",
    ("TILE_SIZE = 16", "DEFAULT_MAXIMUM_TILES = 1024", "BoundedConcurrentCache", "ownedKeys", "inFlight"),
    "final sample cache")
if "synchronized (cache)" in world_cache:
    ERRORS.append("final sample cache hit path must not use a global monitor")

bounded_cache = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/internal/BoundedConcurrentCache.java",
    ("ConcurrentHashMap", "ConcurrentLinkedQueue", "putIfAbsent", "entries.remove(eldest.key(), eldest)"),
    "bounded concurrent cache")

erosion = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/erosion/ErosionPipeline.java",
    ("BoundedConcurrentCache", "ConcurrentMap", "inFlight", "ownedRegionKeys", "putIfAbsent"),
    "erosion exact-key single-flight")
for forbidden in ("LinkedHashMap", "generationLocks", "synchronized (cache)"):
    if forbidden in erosion:
        ERRORS.append(f"erosion pipeline retains old lock-convoy mechanism: {forbidden}")

river_segment = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/RiverSegment.java",
    ("startWaterHeight", "List<RiverPathPoint>", "waterSurfaceHeight()", "bankAlpha"),
    "river segment")
river_generator = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/RivermapGenerator.java",
    ("fillDepressions", "refineVisiblePath", "containmentCeiling", "bankProbe", "LakeField",
     "accumulateFlow", "localRunoff", "resolveWaterSurface", "hydraulicProfile",
     "enforceMonotonicWaterSurface", "MAX_WATER_SURFACE_GRADE"),
    "river map generator")
river_model = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/RiverModel.java",
    ("riverWaterSurfaceHeight", "riverFlow", "minimumWaterDepth", "nearestLake", "drainageClimate",
     "ownedMapKeys", "centeredGeneratorLookup", "toHydrologyCoordinate", "int hydrologyX",
     "return map(regionX, regionZ).lake(hydrologyX, hydrologyZ)"),
    "river model")
if "Math.floorDiv(x, settings.regionSize())" in river_model:
    ERRORS.append("R47 river ownership must not use the old world-origin boundary lattice")
rivermap = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/Rivermap.java",
    ("MAXIMUM_LOCAL_RIVER_SEARCH = 96.0D", "IndexedSegment", "mayReach"),
    "river map hot path")
lake = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/river/LakeField.java",
    ("identifyBasins", "basinWaterLevels", "dominantBasin", "LakeZone.SHORE", "LakeZone.SHALLOW", "LakeZone.CORE"),
    "lake field")
if "bilinear(filledHeight" in lake:
    ERRORS.append("lake field must not interpolate spill heights into a tilted water surface")

# R47 ownership boundary: complete immutable chunks are generated below the host adapter.
default_world = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/DefaultTerrainWorld.java",
    ("ChunkSnapshotCache", "chunkSnapshot(int chunkX, int chunkZ)", "return chunkCache.get(chunkX, chunkZ)"),
    "R47 terrain world")
chunk_cache = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/ChunkSnapshotCache.java",
    ("BoundedConcurrentCache", "ConcurrentMap", "inFlight", "ownedKeys", "putIfAbsent",
     "samples.sample", "generator.generate"),
    "R47 chunk snapshot cache")
for forbidden in ("synchronized", "ForkJoinPool", "ExecutorService"):
    if forbidden in chunk_cache:
        ERRORS.append(f"R47 chunk snapshot cache must not own a global/secondary scheduler: {forbidden}")

snapshot = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/EngineChunkSnapshot.java",
    ("implements ChunkSnapshot", ".clone()", "NaturalMaterial materialAt"),
    "R47 immutable chunk snapshot")
subsurface = require(
    JAVA_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/SubsurfaceGenerator.java",
    ("GeologyType", "NaturalMaterial.BEDROCK", "NaturalMaterial.SOIL", "NaturalMaterial.ROCK",
     "NaturalMaterial.DEEP_ROCK", "NaturalMaterial.AIR", "NaturalMaterial.WATER", "NaturalMaterial.LAVA",
     "isNaturalVoid", "groundwaterY", "smoothNoise3D"),
    "R47 subsurface generator")
for forbidden in BANNED:
    if forbidden in subsurface:
        ERRORS.append(f"R47 subsurface generator leaked host dependency: {forbidden}")

require(
    TEST_ROOT / "dev/foucaultleon/flterraforged/engine/chunk/R47ChunkSnapshotTest.java",
    ("repeatedAndConcurrentRequestsShareOneImmutableSnapshot", "snapshotOwnsFullVerticalNaturalGeometry", "assertSame"),
    "R47 chunk snapshot regression test")
require(
    TEST_ROOT / "dev/foucaultleon/flterraforged/engine/erosion/R46ErosionConcurrencyTest.java",
    ("formerlyCollidingStripeKeysCanGenerateConcurrently",),
    "retained R46 erosion concurrency regression test")
require(
    TEST_ROOT / "dev/foucaultleon/flterraforged/engine/river/R45WorldgenStallGuardTest.java",
    ("interiorTerrainLookupBuildsOnlyCanonicalHydrologyMap", "model.sample(120, 120)"),
    "retained R45 hydrology fanout regression test")
require(
    TEST_ROOT / "dev/foucaultleon/flterraforged/engine/river/R47SpawnHydrologyFanoutTest.java",
    ("spawnOriginBuildsOnlyOneCanonicalHydrologyMap", "model.sample(0, 0)",
     "model.cachedMaps()", "model.inFlightMaps()"),
    "R47 spawn hydrology fanout regression test")

if ERRORS:
    print("\n".join(ERRORS), file=sys.stderr)
    raise SystemExit(1)

print("Engine R47 layout verified: immutable engine-owned chunks, centered spawn hydrology, exact-key single-flight caches and retained erosion guards")
