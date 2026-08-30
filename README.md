# FlTerraForged Engine

Default external terrain engine for **FlTerraForged**.

This repository is intentionally independent from Minecraft, Fabric, NeoForge,
TerraBlender and Conquest Reforged. It implements the public
`flterraforged-engine-api` contract and can therefore be replaced by another
compatible engine.

## Snapshot status

`0.1.0-SNAPSHOT` is the architectural/bootstrap snapshot. It proves the engine
boundary with a deterministic reference terrain implementation. The current
noise, terrain, climate, river and erosion algorithms are deliberately small
bootstrap implementations; they are **not yet the ported TerraForged engine**.
They will be replaced incrementally after the upstream functional diff has been
classified.

## Requirements

- Java 17+
- FlTerraForged Engine API `0.1.0-SNAPSHOT`

Keeping the engine at Java 17 allows the same engine artifact to be used by the
full FlTerraForged Minecraft matrix, including 1.20.1.

## Local development with the sibling FlTerraForged repository

Recommended checkout layout:

```text
workspace/
├── FlTerraForged/
└── FlTerraForged-Engine/
```

The Gradle build automatically detects `../FlTerraForged` and substitutes the
published API dependency with `:engine-api` from that build.

For a different location:

```bash
./gradlew check -Pflterraforged_api_project_dir=/path/to/FlTerraForged
```

or set `FLTERRAFORGED_API_PROJECT_DIR`.

Once the API is published, the engine can instead resolve
`dev.foucaultleon:flterraforged-engine-api` through Maven Local or a repository
specified by `flterraforged_api_repository_url` /
`FLTERRAFORGED_API_REPOSITORY_URL`.

## Architectural rules

1. No Minecraft classes in the engine.
2. No Fabric, NeoForge, Forge or TerraBlender classes in the engine.
3. No Conquest-specific block/material knowledge in the engine.
4. Sampling must be deterministic for `(seed, x, z, config)`.
5. Sampling must be order-independent and thread-safe.
6. Continuous surface height is preserved as a `double`.
7. Minecraft adaptation belongs to FlTerraForged, not here.

See [ARCHITECTURE.md](ARCHITECTURE.md) and [UPSTREAMS.md](UPSTREAMS.md).
