# Climate migration

## Scope

`r13` replaces the bootstrap temperature/moisture sampler with a first-class climate
stage after River/Rivermap shaping. The implementation stays inside the Java-only
external engine boundary.

```text
Continent
   ↓
Terrain
   ↓
Erosion
   ↓
River / Rivermap
   ↓
ClimateModel
   ↓
TerrainSample.climate
```

## Upstream concepts retained

ReTerraForged 1.20.1 and FreeTerraForged 1.21.1 both keep climate generation in a
separate `cell/climate` package with `Climate` and `ClimateModule`. ReTerraForged's
`Climate` writes climate/biome-region data into `Cell` and uses `biomeRegionEdge` plus
coordinate offsets to avoid rigid biome boundaries.

FlTerraForged-Engine keeps the engine-neutral parts of that model:

- continuous temperature and moisture;
- independent macro climate regions;
- a normalized climate-region edge signal;
- smooth transition between owner and neighboring climate anchors;
- semantic region identifiers stored in `Cell`.

It does not copy Minecraft biome selection or Mojang/preset plumbing.

## Climate region structure

`ClimateRegionSampler` is a stateless jittered Voronoi partition independent of terrain
regions and continent ownership. Each sample resolves:

```text
owner region
├── id
├── temperature anchor
└── moisture anchor

nearest neighbor
├── id
├── temperature anchor
└── moisture anchor

edge = normalized distance from their boundary
```

Near a region boundary, `ClimateModel` blends the two regional anchors. Broad fractal
noise is then mixed with those macro anchors, preventing climate from collapsing into a
pure cellular map while still providing stable large-scale zones.

## Terrain feedback

After the broad/regional climate is established, the final signals are adjusted by
already-generated terrain:

- altitude above sea level cools temperature;
- positions near a continental/coastal boundary are temperature-moderated;
- deep continental interiors lose some moisture;
- coastal positions regain part of that moisture;
- river centerlines add local moisture according to `Cell.riverMask`.

These are semantic climate effects only. They do not place snow, vegetation, fluids or
surface blocks.

## Cell fields

The climate stage now owns these previously prepared fields:

```text
Cell.regionTemperature
Cell.regionMoisture
Cell.biomeRegionId
Cell.biomeRegionEdge
Cell.macroBiomeId
Cell.temperature
Cell.moisture
```

`macroBiomeId` is intentionally not a Minecraft biome id. It is a stable normalized
semantic grouping derived from temperature/moisture bands so later integration layers
can use it as a coarse hint if useful.

## API boundary

No Engine API change is necessary. `ClimateSample` already exposes:

```text
temperature
moisture
```

The richer region data remains engine-internal in `Cell`. FlTerraForged can later map
those signals to vanilla/modded biomes through native integration or optional
TerraBlender adapters.

## Explicitly deferred

- Minecraft biome registry/holder selection;
- TerraBlender integration;
- seasonal climate/time variation;
- wind vectors and rain-shadow simulation;
- snow/ice placement;
- vegetation/ecosystem placement;
- cave/3D climate.

Those features should be layered on top of the stable 2D climate foundation rather than
coupled into the external Engine API prematurely.
