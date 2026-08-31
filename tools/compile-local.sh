#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/local-compile"
MAIN="$OUT/main"
SMOKE="$OUT/smoke"
API_CP="${FLTERRAFORGED_API_CLASSES:-}"

if [[ -z "$API_CP" ]]; then
    for candidate in \
        "$ROOT/../FlTerraForged/build/manual-compile/api" \
        "$ROOT/../FlTerraForged-0.1.0-SNAPSHOT-r27-work/build/manual-compile/api"; do
        if [[ -d "$candidate" ]]; then
            API_CP="$candidate"
            break
        fi
    done
fi

if [[ -z "$API_CP" || ! -e "$API_CP" ]]; then
    echo "Missing Engine API classes. Run FlTerraForged/tools/compile-api.sh first or set FLTERRAFORGED_API_CLASSES." >&2
    exit 2
fi

rm -rf "$OUT"
mkdir -p "$MAIN" "$SMOKE"
find "$ROOT/src/main/java" -name '*.java' -print0 \
  | xargs -0 javac --release 17 -Xlint:all -Werror -cp "$API_CP" -d "$MAIN"

cat > "$OUT/Smoke.java" <<'JAVA'
import dev.foucaultleon.flterraforged.engine.DefaultEngineProvider;
import dev.foucaultleon.flterraforged.engine.EnginePreset;
import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.climate.ClimateLayout;

public final class Smoke {
    private Smoke() {}

    public static void main(String[] args) {
        EngineSettings settings = EngineSettings.preset(EnginePreset.CENTRAL_EUROPE);
        if (settings.climateLayout() != ClimateLayout.RANDOMIZED) {
            throw new IllegalStateException("central_europe must default to randomized climate");
        }
        if (!DefaultEngineProvider.VERSION.endsWith("-r24")) {
            throw new IllegalStateException("unexpected provider revision");
        }
    }
}
JAVA

javac --release 17 -Xlint:all -Werror -cp "$API_CP:$MAIN" -d "$SMOKE" "$OUT/Smoke.java"
java -cp "$API_CP:$MAIN:$SMOKE" Smoke
echo "Engine local compile/smoke OK"
