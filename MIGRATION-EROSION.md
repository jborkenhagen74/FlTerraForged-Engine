# Erosion migration — r11

## Scope

This step replaces the bootstrap scalar `ErosionModel` with a real physical
post-terrain erosion stage. TerraForged documents hydraulic erosion as a process
that traces virtual water droplets downhill and models erosion, sediment carry
and deposition. ReTerraForged 1.20.1 also exposes a dedicated `cell/filter`
layer in its cell-generation package. FreeTerraForged 1.21.1 no longer exposes
that same root `cell/filter` directory, so FlTerraForged does not preserve the
old package boundary verbatim.

The implementation here is a Java-only rewrite around FlTerraForged's external
engine contract. It does not copy Minecraft density-tile filters, Mojang codecs,
biome features or chunk state.

## Added engine structures

```text
erosion/
├── ErosionSettings.java
├── ErosionSample.java
├── ErosionFilter.java
├── ErosionPipeline.java
├── HydraulicErosionFilter.java
├── ThermalErosionFilter.java
├── ErosionTile.java
├── ErosionTileGenerator.java
└── package-info.java
```

## Pipeline position

```text
Continent
   ↓
TerrainRegion / TerrainProvider
   ↓
TerrainPopulator
   ↓  base height
ErosionPipeline
   ├── HydraulicErosionFilter
   └── ThermalErosionFilter
   ↓  heightErosion / erosion / sediment / gradient
RiverModel
   ↓
final engine height
```

Erosion is therefore no longer a broad noise value used while constructing the
landform. It operates on the completed continuous base terrain and can physically
lower or raise the height field through erosion/deposition.

## Hydraulic model

The hydraulic pass uses deterministic virtual droplets. Each droplet:

1. starts from a globally aligned launch cell with a seed-derived sub-cell offset;
2. samples bilinear height and gradient;
3. follows downhill direction with configurable inertia;
4. derives sediment capacity from descent, speed and water volume;
5. removes material with a radial brush while below carrying capacity;
6. deposits material when moving uphill or carrying more sediment than capacity;
7. accelerates under gravity and loses water through evaporation.

Droplet contributions are accumulated against the immutable base height field and
combined after the pass. This avoids order-dependent mutation between droplets
and makes overlapping padded regions reproducible.

## Region continuity and cache design

Core regions are padded by at least `maxDropletLifetime + erosionRadius`. A
point in the core can therefore only be influenced by globally aligned droplets
whose launch positions are also present in every overlapping calculation that
can affect that point.

Completed `ErosionTile` instances are immutable. A small shared LRU cache stores
only finished tiles. Cache lookup/insertion is synchronized, but expensive tile
generation occurs outside the lock. This deliberately avoids recursive
`computeIfAbsent`/future wait graphs while allowing threads to share generated
regions.

## Thermal model

After hydraulic erosion, the thermal pass identifies four-neighbor slopes above
the configured talus threshold and transfers a bounded fraction of excess
material downslope. The transfer is accumulated simultaneously before being
applied, making the result independent of iteration order.

## Cell semantics after r11

- `height` — current pipeline height; after r11 erosion this is then modified by rivers.
- `heightErosion` — height after erosion and before river incision.
- `erosion` — normalized local erosion intensity.
- `sediment` — deposited sediment amount in blocks.
- `gradient` — local slope of the eroded surface.
- `erosionMask` — whether erosion/deposition meaningfully modified the point.

## Deliberately deferred

- Minecraft surface/block exposure based on erosion intensity;
- biome-dependent erodibility;
- material/geology-dependent hardness;
- full river/hydrology migration;
- glacial/wind erosion strategies;
- persistent disk caching of erosion tiles.

Those can be added behind the erosion contracts without changing the Engine API.
