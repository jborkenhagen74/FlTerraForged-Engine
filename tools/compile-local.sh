#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
API_ROOT="${1:-${FLTERRAFORGED_API_PROJECT_DIR:-$ROOT/../FlTerraForged}}"
API_SRC="$API_ROOT/engine-api/src/main/java"
if [[ ! -d "$API_SRC" ]]; then
  echo "Engine API sources not found: $API_SRC" >&2
  echo "Usage: tools/compile-local.sh /path/to/FlTerraForged" >&2
  exit 2
fi
OUT="$ROOT/build/local-compile"
rm -rf "$OUT"
mkdir -p "$OUT/api" "$OUT/engine" "$OUT/test"
find "$API_SRC" -name '*.java' -print0 | xargs -0 javac --release 17 -d "$OUT/api"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac --release 17 -cp "$OUT/api" -d "$OUT/engine"
cp -R "$ROOT/src/main/resources/." "$OUT/engine/"
find "$ROOT/src/test/java" -name '*.java' -print0 | xargs -0 javac --release 17 -cp "$OUT/api:$OUT/engine" -d "$OUT/test"
java -ea -cp "$OUT/api:$OUT/engine:$OUT/test" dev.foucaultleon.flterraforged.engine.EngineSmokeTest
echo "Local Java 17 compile + smoke test passed"
