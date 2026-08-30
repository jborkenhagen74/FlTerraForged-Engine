# Pipeline integration and tuning — r14

## Goal

Revisions r7-r13 established seven independently testable foundations: noise, cell, continent,
terrain, erosion, river/rivermap and climate. r14 turns them into one deliberately ordered world
pipeline instead of leaving composition logic spread across `DefaultTerrainWorld`.

## Single composition root

`WorldgenPipeline` is now the only class that assembles generation stages for a world seed:

```text
AdvancedContinent
      ↓
TerrainRegionSampler + TerrainProvider + TerrainPopulator
      ↓
ErosionPipeline
      ↓
RiverModel / Rivermap
      ↓
ClimateModel
      ↓
final slope + TerrainClassifier
      ↓
TerrainSample
```

All stage seeds are derived from the same world seed through stable, stage-specific salts. The same
continent and terrain sources therefore feed every downstream subsystem.

## Cell contract after integration

The general `Cell` is the cross-stage state carrier. r14 extends its river contract with:

- `riverDistance`
- `riverWidth`
- `riverDepth`

`RiverModel.lookup(...)` writes these together with `riverMask` and the post-river height. Climate
then forwards the same Cell unchanged except for climate fields. The final Engine API sample can
therefore project `RiverSample` directly from the completed Cell instead of querying Rivermap a
second time.

Stage meaning of height fields remains:

```text
Cell.heightErosion = post-terrain/post-erosion, pre-river surface
Cell.height        = final post-river surface
```

River incision is constrained to lowering terrain, so normally:

```text
height <= heightErosion
heightErosion - height == riverDepth
```

subject only to world-height clamping at extreme bounds.

## Coordinated presets

The new `EnginePreset` selects a coherent set of cross-stage defaults:

### balanced

Default profile. Large continents contain multiple terrain regions, terrain transitions remain
visible but blended, erosion is moderate, and rivers are neither extremely dense nor extremely
sparse.

### gentle

Lower broad/mountain relief, wider terrain-region blends, lower hydraulic/thermal erosion and a
coarser drainage grid. Intended for softer traversable landscapes.

### rugged

Higher relief, tighter terrain transitions, stronger erosion, a denser drainage grid and deeper
river incision. Intended for dramatic terrain.

The generic Engine API remains unchanged. Integrations select a profile with:

```text
preset=balanced|gentle|rugged
```

Explicit numeric keys are applied after the preset and override only the requested fields.

## Tuning changes from r13

- Balanced terrain regions are smaller than in r13 so a large continent contains several different
  landforms rather than one dominant terrain region over long distances.
- Terrain-region blend width is coordinated with region scale: gentler profiles blend farther,
  rugged profiles blend more tightly.
- Erosion strength, deposition, maximum delta and thermal relaxation are tuned as one profile rather
  than independently.
- River density and maximum incision are tuned together with terrain relief.
- Climate scale and climate-region scale remain broader than normal terrain detail so climate does
  not flicker between nearby hills.
- Final ocean/coast/river semantic thresholds now derive from the same `EngineSettings` through
  `TerrainClassificationSettings`.

## Validation targets

r14 adds integration tests for:

- all cross-stage Cell ranges;
- post-river height never exceeding pre-river height;
- RiverSample fields matching the integrated Cell;
- TerrainSample being a projection of the same integrated Cell;
- preset parsing and numeric override precedence;
- deterministic concurrent full-pipeline sampling.

A broad deterministic smoke scan of the balanced profile found ocean, coast, plains, hills, valleys,
plateaus, mountains and rivers across a 12k × 12k area, while temperature/moisture and heights
remained continuous and bounded.

## Still outside the engine-integration step

- Minecraft DensityFunctions and chunk-generation adapters;
- biome registry/source selection;
- TerraBlender integration;
- surface rules and block/material placement;
- lakes/basin filling;
- waterfalls and 3D river water surfaces;
- seasons, vegetation and decoration providers.
