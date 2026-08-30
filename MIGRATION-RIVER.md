# River / Rivermap migration

## Goal

Replace the bootstrap independent river-noise field with an engine-owned hydrology layer whose
channel topology follows the terrain itself. The implementation must stay Java-only and may not
own Minecraft water blocks, biome selection, surface rules, codecs or loader hooks.

## Upstream concepts used

ReTerraForged keeps river-map logic as a distinct world-generation concern and historically couples
parts of it to continent generation. FreeTerraForged continues the TerraForged lineage and publicly
advertises later 3D-river and waterfall work. FlTerraForged-Engine keeps the useful conceptual split
but deliberately makes hydrology independent from `Continent` so either subsystem can be replaced.

This implementation is a Java-only rewrite rather than a source-compatible copy.

## Pipeline

```text
Continent
    |
TerrainPopulator
    |\
    | +----------------------> RivermapGenerator
    |                           |
    |                           +-- globally aligned drainage grid
    |                           +-- D8 downhill routing
    |                           +-- flow accumulation
    |                           +-- RiverSegment network
    |                                      |
    v                                      v
ErosionPipeline                       immutable Rivermap
    |                                      |
    +--------------------+-----------------+
                         v
                    RiverModel
                         |
                         +-- nearest channel
                         +-- width / depth
                         +-- Cell.riverMask
                         +-- post-erosion incision
                         v
                    final Cell.height
```

The coarse topology uses the broad terrain field rather than recursively requesting complete
physical erosion regions for every drainage node. River incision itself is applied after
`ErosionPipeline`, so local erosion and sediment detail remains in `heightErosion` and the river bed
is cut from that final eroded surface. This avoids an expensive erosion-region dependency graph
inside rivermap generation.

## Drainage grid

Each `Rivermap` owns channel segments whose upstream node is inside one aligned map region. The
generator samples an expanded globally aligned grid. Every interior node routes to its lowest
strictly lower D8 neighbor. Nodes are then processed from high to low elevation so upstream flow
accumulates into downstream nodes.

A node becomes a river segment when:

- it has a valid lower downstream neighbor;
- accumulated flow reaches `minimumFlow`;
- the segment is not wholly below the ocean floor; and
- at least one endpoint remains inside a usable continent/coast signal.

Width grows approximately with the square root of accumulated flow. Depth grows logarithmically
and receives a small local-slope adjustment before being capped by `maximumDepth`.

## Region ownership and boundaries

Only the segment whose upstream node belongs to a map's core region is stored in that map. Global
node coordinates therefore have one stable owner even at map borders. `RiverModel` always samples
the current map and only adds neighboring maps when the query is close enough to a region boundary
for their channels to matter. This avoids the nine-map cost for normal interior samples while still
making cross-boundary channels queryable.

`Rivermap` and `RiverSegment` are immutable. `RiverModel` uses a bounded synchronized LRU containing
only completed maps; map generation occurs outside the cache lock, matching the no-recursive-cache
policy already used by erosion.

## Cell semantics

After river application:

- `Cell.heightErosion` remains the post-erosion / pre-river surface;
- `Cell.height` is the final post-river bed/surface height;
- `Cell.riverMask == 0` represents the channel center;
- `Cell.riverMask == 1` represents terrain outside the channel width.

`RiverSample` continues to report nearest centerline distance, full channel width and local incision
depth. r15 adds two optional signals while retaining the legacy three-argument constructor:

- `waterSurfaceHeight`: continuous world-space Y of the active channel water surface;
- `flow`: accumulated drainage weight represented by the nearest segment.

The water surface is calculated from the same directed segment interpolation used by the channel
centerline with a constant bank inset. It is therefore stable across the river cross-section and
monotonically follows the segment's upstream/downstream terrain elevations instead of being rebuilt
from each local terrain column.

## Deliberately deferred

The base hydrology layer does not yet add:

- lake/basin water-surface solving across closed depressions;
- explicit waterfall/rapid shaping beyond the directed segment grade;
- FreeTerraForged-style 3D river water placement inside a Minecraft density graph;
- biome routing for riverbanks;
- Minecraft fluids, blocks or surface rules.

Those features can build on the directed `RiverSegment` graph without changing continent, terrain
or erosion ownership.
