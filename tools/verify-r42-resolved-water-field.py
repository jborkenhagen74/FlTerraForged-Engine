#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RIVER = ROOT / "src/main/java/dev/foucaultleon/flterraforged/engine/river"


def require(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        raise SystemExit(f"{path}: missing required invariants: {missing}")


def forbid(path: Path, *needles: str) -> None:
    text = path.read_text(encoding="utf-8")
    present = [needle for needle in needles if needle in text]
    if present:
        raise SystemExit(f"{path}: forbidden legacy patterns remain: {present}")


def main() -> None:
    owner = RIVER / "ResolvedWaterOwner.java"
    field = RIVER / "ResolvedWaterField.java"
    resolver = RIVER / "ResolvedWaterResolver.java"
    overlay = RIVER / "ReceivingWaterOverlay.java"
    connectivity = RIVER / "RiverWetCoreConnectivity.java"

    for path in (owner, field, resolver, overlay, connectivity):
        if not path.is_file():
            raise SystemExit(f"missing R42 source: {path}")

    require(owner, "DRY(0)", "RIVER(1)", "LAKE(2)", "OCEAN(3)")
    require(
        field,
        "double bedHeight",
        "double waterSurfaceHeight",
        "double mask",
        "wet owners require water above the resolved bed",
    )
    require(
        resolver,
        "ResolvedWaterField resolve",
        "ResolvedWaterOwner.RIVER",
        "ResolvedWaterOwner.LAKE",
        "ResolvedWaterOwner.OCEAN",
        "candidate.owner().priority() > current.owner().priority()",
    )
    require(
        overlay,
        "resolver.resolve(x, z, target)",
        "case RIVER",
        "case LAKE",
        "case OCEAN",
    )
    forbid(overlay, "applyLakeReceiver(", "applyOceanReceiver(")
    require(
        connectivity,
        "MainStemReceiver",
        "nearbyMainStemReceiver",
        "MAIN_STEM_MAXIMUM_LEVEL_DELTA",
        "MOUTH_BLEND_DISTANCE",
        "mouthBlend(receiverDistance)",
        "ResolvedWaterOwner.OCEAN.priority()",
        "ResolvedWaterOwner.LAKE.priority()",
    )

    # R41 liveness rules remain non-negotiable in the new resolver path.
    for path in (resolver, overlay, connectivity):
        forbid(
            path,
            "CompletableFuture",
            "supplyAsync",
            ".join()",
            "getChunk(",
            "BiomeSource",
        )

    print("R42 resolved-water-field invariants verified")


if __name__ == "__main__":
    main()
