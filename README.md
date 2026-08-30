# FlTerraForged Engine

Default external terrain-engine implementation for FlTerraForged.

The engine is deliberately independent of Minecraft, Fabric, NeoForge, TerraBlender and Conquest Reforged. It implements the Java-only `flterraforged-engine-api` SPI published by the FlTerraForged repository.

## Dependency direction

```text
FlTerraForged
  └─ publishes flterraforged-engine-api
             ↓
      GitHub Packages
             ↓
FlTerraForged-Engine
  └─ consumes engine-api
```

The Engine CI does **not** check out, build or publish FlTerraForged.

## GitHub Packages

The default API repository is:

```text
https://maven.pkg.github.com/jborkenhagen74/FlTerraForged
```

and the current API coordinate is:

```text
dev.foucaultleon:flterraforged-engine-api:0.1.0-SNAPSHOT
```

GitHub Actions reads the package using its `GITHUB_TOKEN` with `packages: read`. The Engine publishes its own artifact after a successful `main` build to:

```text
https://maven.pkg.github.com/jborkenhagen74/FlTerraForged-Engine
```

For repositories where the workflow token cannot read the FlTerraForged package, configure these repository secrets/environment values with a classic PAT that has `read:packages`:

```text
FLTERRAFORGED_PACKAGES_USER
FLTERRAFORGED_PACKAGES_TOKEN
```

The standard public-repository setup should not require them.

## Local development

### Preferred: composite build

When editing FlTerraForged API and Engine together:

```bash
gradle --no-daemon check \
  -Pflterraforged_api_project_dir=../FlTerraForged
```

This substitutes the published API dependency with the local `:engine-api` project.

### Explicit Maven Local fallback

Maven Local is deliberately disabled by default to prevent stale API snapshots from being selected silently.

If you explicitly published the API locally, enable it with:

```bash
gradle --no-daemon check \
  -Pflterraforged_use_maven_local=true
```

### Direct GitHub Packages build outside Actions

Set Gradle properties or environment variables containing a GitHub username and a classic PAT with `read:packages`:

```text
gpr.user / gpr.key
```

or:

```text
FLTERRAFORGED_PACKAGES_USER / FLTERRAFORGED_PACKAGES_TOKEN
```

## Current implementation

`0.1.0-SNAPSHOT` is a bootstrap engine used to validate the API boundary, deterministic sampling, ServiceLoader discovery and concurrent access. TerraForged/ReTerraForged/FreeTerraForged algorithms are not imported yet.
