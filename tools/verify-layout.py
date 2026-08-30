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
if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)
print("Engine layout verified: Java-only, ServiceLoader provider present")
