# FlTerraForged Engine

Default external terrain-engine implementation for FlTerraForged.

The engine is deliberately independent of Minecraft, Fabric, NeoForge,
TerraBlender and Conquest Reforged. It implements only the Java 17
`flterraforged-engine-api` SPI.

## Dependency direction

```text
FlTerraForged
  └─ publishes flterraforged-engine-api
             ↓
      public Maven repository
             ↓
FlTerraForged-Engine
  └─ consumes engine-api
```

The Engine CI does not check out or build FlTerraForged.

## Engine API resolution

Resolution follows the same pattern used by the FEF examples/components:

1. Explicit local build repository via
   `flterraforged_api_local_repository` or
   `FLTERRAFORGED_API_LOCAL_REPOSITORY`.
2. `../FlTerraForged/build/maven-repository` automatically when that sibling
   repository exists.
3. `mavenLocal()`.
4. `mavenCentral()`.
5. Public remote repository via `flterraforged_api_repository_url` or
   `FLTERRAFORGED_API_REPOSITORY_URL`.

The build has this public URL as its default remote repository:

```text
https://raw.githubusercontent.com/jborkenhagen74/FlTerraForged/maven/
```

Current API dependency:

```text
dev.foucaultleon:flterraforged-engine-api:0.1.0-SNAPSHOT
```

No token is required to consume the public Maven repository.

## Local development with both repositories

Directory layout:

```text
workspace/
├── FlTerraForged/
└── FlTerraForged-Engine/
```

First publish the API into FlTerraForged's build repository:

```bash
cd FlTerraForged
gradle --no-daemon :engine-api:publish
```

Then build the Engine:

```bash
cd ../FlTerraForged-Engine
gradle --no-daemon --refresh-dependencies clean check
```

The Engine automatically detects
`../FlTerraForged/build/maven-repository`.

For an arbitrary path you can instead use:

```bash
gradle --no-daemon check \
  -Pflterraforged_api_local_repository=/path/to/FlTerraForged/build/maven-repository
```

## Engine Maven publication

The Engine itself uses the same model. `gradle publish` writes to:

```text
build/maven-repository
```

On `develop`, GitHub Actions mirrors that repository to:

```text
https://raw.githubusercontent.com/jborkenhagen74/FlTerraForged-Engine/maven/
```

Current Engine coordinate:

```text
dev.foucaultleon:flterraforged-engine:0.1.0-SNAPSHOT
```

## Current implementation

`0.1.0-SNAPSHOT` is a bootstrap engine used to validate the API boundary,
deterministic sampling, ServiceLoader discovery and concurrent access.
TerraForged/ReTerraForged/FreeTerraForged algorithms are not imported yet.
