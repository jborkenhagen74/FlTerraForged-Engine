package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Builds deterministic depression-aware drainage graphs and terrain-refined visible watercourses.
 *
 * <p>D8 remains only the coarse hydrologic skeleton. A priority-flood pass first resolves local
 * depressions and produces spill elevations, so inland sinks become ponds/lakes instead of dead
 * river ends. Each visible D8 edge is then refined against the local terrain into a curved path;
 * therefore the eight grid directions no longer become the visible river geometry.</p>
 */
public final class RivermapGenerator {

    private static final double MAX_WATER_SURFACE_GRADE = 0.18D;
    private static final double WATERFALL_MINIMUM_TERRAIN_DROP = 2.25D;
    private static final double WATERFALL_MINIMUM_WATER_DROP = 1.25D;
    private static final double WATERFALL_MINIMUM_TERRAIN_GRADE = 0.55D;

    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DZ = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final double HEIGHT_EPSILON = 1.0E-7D;
    private static final long LAKE_EDGE_SALT = 0xA0761D6478BD642FL;
    private static final long PATH_SALT = 0xE7037ED1A0B428DBL;

    private final long seed;
    private final EngineContext world;
    private final CellLookup terrain;
    private final CellLookup climate;
    private final RiverSettings settings;

    /**
     * Creates a river-map generator.
     *
     * @param seed hydrology seed
     * @param world immutable world context
     * @param terrain broad terrain lookup used for drainage topology and visible-path refinement
     * @param climate pre-river climate lookup used to weight local runoff, or {@code null}
     * @param settings river settings
     */
    public RivermapGenerator(
            long seed,
            EngineContext world,
            CellLookup terrain,
            CellLookup climate,
            RiverSettings settings) {
        this.seed = seed;
        this.world = Objects.requireNonNull(world, "world");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.climate = climate;
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Generates one immutable river map.
     *
     * @param regionX river-region X index
     * @param regionZ river-region Z index
     * @return completed rivermap
     */
    public Rivermap generate(int regionX, int regionZ) {
        int spacing = settings.gridSpacing();
        int coreCells = settings.regionSize() / spacing;
        int padding = settings.paddingCells();
        int nodesPerAxis = coreCells + padding * 2 + 1;
        int originX = regionX * settings.regionSize() - padding * spacing;
        int originZ = regionZ * settings.regionSize() - padding * spacing;
        int count = nodesPerAxis * nodesPerAxis;

        double[] heights = new double[count];
        double[] continentEdges = new double[count];
        int[] downstream = new int[count];
        double[] flow = new double[count];
        Arrays.fill(downstream, -1);

        Cell scratch = new Cell();
        Cell climateScratch = climate == null ? null : new Cell();
        for (int gz = 0; gz < nodesPerAxis; gz++) {
            for (int gx = 0; gx < nodesPerAxis; gx++) {
                int index = gz * nodesPerAxis + gx;
                int x = originX + gx * spacing;
                int z = originZ + gz * spacing;
                terrain.lookup(x, z, scratch);
                heights[index] = scratch.heightErosion;
                continentEdges[index] = scratch.continentEdge;
                if (climateScratch == null) {
                    flow[index] = 1.0D;
                } else {
                    climate.lookup(x, z, climateScratch);
                    flow[index] = localRunoff(climateScratch.temperature, climateScratch.moisture);
                }
            }
        }

        FloodResult flood = fillDepressions(nodesPerAxis, heights);
        for (int gz = 1; gz < nodesPerAxis - 1; gz++) {
            for (int gx = 1; gx < nodesPerAxis - 1; gx++) {
                int index = gz * nodesPerAxis + gx;
                downstream[index] = selectDownstream(
                        gx,
                        gz,
                        nodesPerAxis,
                        originX,
                        originZ,
                        spacing,
                        heights,
                        flood.filledHeight(),
                        flood.parent());
            }
        }
        accumulateFlow(downstream, flow);

        int coreMinX = regionX * settings.regionSize();
        int coreMinZ = regionZ * settings.regionSize();
        int coreMaxX = coreMinX + settings.regionSize();
        int coreMaxZ = coreMinZ + settings.regionSize();
        List<RiverSegment> segments = new ArrayList<>();

        for (int gz = 1; gz < nodesPerAxis - 1; gz++) {
            for (int gx = 1; gx < nodesPerAxis - 1; gx++) {
                int index = gz * nodesPerAxis + gx;
                int next = downstream[index];
                if (next < 0 || !visibleChannel(flow[index], flow[next])) {
                    continue;
                }
                int startX = originX + gx * spacing;
                int startZ = originZ + gz * spacing;
                if (startX < coreMinX || startX >= coreMaxX || startZ < coreMinZ || startZ >= coreMaxZ) {
                    continue;
                }
                if (!eligibleForChannel(index, next, heights, continentEdges)) {
                    continue;
                }

                int nextXIndex = next % nodesPerAxis;
                int nextZIndex = next / nodesPerAxis;
                int endX = originX + nextXIndex * spacing;
                int endZ = originZ + nextZIndex * spacing;
                double normalizedFlow = Math.max(0.0D, flow[index] - settings.headwaterFlow() + 1.0D);
                double width = Maths.clamp(
                        settings.minimumWidth() + Math.sqrt(normalizedFlow) * settings.widthGrowth(),
                        settings.minimumWidth(),
                        settings.maximumWidth());
                double shapeDepth = 0.75D + Math.log1p(normalizedFlow) * settings.depthGrowth();
                double waterDepth = Maths.clamp(
                        settings.minimumWaterDepth() + Math.log1p(normalizedFlow) * 0.34D,
                        settings.minimumWaterDepth(),
                        settings.maximumWaterDepth());
                double slope = Math.max(0.0D, flood.filledHeight()[index] - flood.filledHeight()[next])
                        / Math.max(1.0D, spacing);
                shapeDepth *= 0.78D + Math.min(0.28D, slope * 1.4D);
                double depth = Math.min(
                        settings.maximumDepth(),
                        Math.max(shapeDepth, waterDepth + settings.bankFreeboard()));

                double startWater = flood.filledHeight()[index] - settings.bankFreeboard();
                double endWater = Math.min(
                        startWater,
                        flood.filledHeight()[next] - settings.bankFreeboard());
                List<RiverPathPoint> path = refineVisiblePath(
                        startX,
                        startZ,
                        endX,
                        endZ,
                        heights[index],
                        heights[next],
                        startWater,
                        endWater,
                        width);
                startWater = path.get(0).waterSurfaceHeight();
                endWater = path.get(path.size() - 1).waterSurfaceHeight();

                segments.add(new RiverSegment(
                        startX,
                        startZ,
                        endX,
                        endZ,
                        heights[index],
                        heights[next],
                        startWater,
                        endWater,
                        flow[index],
                        width,
                        depth,
                        path));
            }
        }

        LakeField lakes = new LakeField(
                seed ^ LAKE_EDGE_SALT,
                originX,
                originZ,
                spacing,
                nodesPerAxis,
                heights,
                flood.filledHeight(),
                settings.lakeMinimumDepth(),
                settings.lakeShoreBlend(),
                world.seaLevel());
        return new Rivermap(regionX, regionZ, segments, lakes);
    }

    private FloodResult fillDepressions(int width, double[] heights) {
        double[] filled = heights.clone();
        int[] parent = new int[heights.length];
        boolean[] visited = new boolean[heights.length];
        Arrays.fill(parent, -1);
        PriorityQueue<FloodNode> queue = new PriorityQueue<>(Comparator
                .comparingDouble(FloodNode::height)
                .thenComparingInt(FloodNode::index));

        for (int x = 0; x < width; x++) {
            seedBoundary(x, 0, width, filled, visited, queue);
            seedBoundary(x, width - 1, width, filled, visited, queue);
        }
        for (int z = 1; z < width - 1; z++) {
            seedBoundary(0, z, width, filled, visited, queue);
            seedBoundary(width - 1, z, width, filled, visited, queue);
        }

        while (!queue.isEmpty()) {
            FloodNode current = queue.remove();
            int gx = current.index() % width;
            int gz = current.index() / width;
            for (int direction = 0; direction < DX.length; direction++) {
                int nx = gx + DX[direction];
                int nz = gz + DZ[direction];
                if (nx < 0 || nz < 0 || nx >= width || nz >= width) {
                    continue;
                }
                int candidate = nz * width + nx;
                if (visited[candidate]) {
                    continue;
                }
                visited[candidate] = true;
                parent[candidate] = current.index();
                filled[candidate] = Math.max(heights[candidate], current.height());
                queue.add(new FloodNode(candidate, filled[candidate]));
            }
        }
        return new FloodResult(filled, parent);
    }

    private static void seedBoundary(
            int gx,
            int gz,
            int width,
            double[] filled,
            boolean[] visited,
            PriorityQueue<FloodNode> queue) {
        int index = gz * width + gx;
        if (!visited[index]) {
            visited[index] = true;
            queue.add(new FloodNode(index, filled[index]));
        }
    }

    private int selectDownstream(
            int gx,
            int gz,
            int width,
            int originX,
            int originZ,
            int spacing,
            double[] heights,
            double[] filled,
            int[] floodParent) {
        int index = gz * width + gx;
        double current = filled[index];
        int best = -1;
        double bestHeight = current;
        double bestOriginal = Double.POSITIVE_INFINITY;
        double bestTie = Double.POSITIVE_INFINITY;
        int worldGridX = Math.floorDiv(originX, spacing) + gx;
        int worldGridZ = Math.floorDiv(originZ, spacing) + gz;
        for (int direction = 0; direction < DX.length; direction++) {
            int nx = gx + DX[direction];
            int nz = gz + DZ[direction];
            int candidate = nz * width + nx;
            double candidateHeight = filled[candidate];
            if (candidateHeight > current + HEIGHT_EPSILON) {
                continue;
            }
            double original = heights[candidate];
            double tie = deterministicUnit(
                    PATH_SALT,
                    worldGridX + DX[direction],
                    worldGridZ + DZ[direction],
                    direction);
            boolean lower = candidateHeight + HEIGHT_EPSILON < bestHeight;
            boolean sameFilled = Math.abs(candidateHeight - bestHeight) <= HEIGHT_EPSILON;
            if (lower || (sameFilled && (original < bestOriginal - HEIGHT_EPSILON
                    || (Math.abs(original - bestOriginal) <= HEIGHT_EPSILON && tie < bestTie)))) {
                best = candidate;
                bestHeight = candidateHeight;
                bestOriginal = original;
                bestTie = tie;
            }
        }

        if (best >= 0 && bestHeight + HEIGHT_EPSILON < current) {
            return best;
        }
        int parent = floodParent[index];
        if (parent >= 0 && filled[parent] <= current + HEIGHT_EPSILON) {
            return parent;
        }
        return best;
    }

    private static void accumulateFlow(int[] downstream, double[] flow) {
        int[] upstreamCount = new int[downstream.length];
        for (int next : downstream) {
            if (next >= 0) {
                upstreamCount[next]++;
            }
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < upstreamCount.length; index++) {
            if (upstreamCount[index] == 0) {
                queue.add(index);
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int next = downstream[index];
            if (next >= 0) {
                flow[next] += flow[index];
                upstreamCount[next]--;
                if (upstreamCount[next] == 0) {
                    queue.addLast(next);
                }
            }
        }
    }

    private static double localRunoff(double temperature, double moisture) {
        double boundedMoisture = Maths.clamp(moisture, 0.0D, 1.0D);
        double wetness = Maths.smooth(Maths.clamp((boundedMoisture - 0.14D) / 0.72D, 0.0D, 1.0D));
        double runoff = 0.16D + wetness * 1.04D;

        double hot = Maths.smooth(Maths.clamp((temperature - 0.66D) / 0.24D, 0.0D, 1.0D));
        double dryness = 1.0D - wetness;
        runoff *= 1.0D - hot * dryness * 0.58D;
        return Maths.clamp(runoff, 0.07D, 1.20D);
    }

    private boolean visibleChannel(double sourceFlow, double targetFlow) {
        if (sourceFlow >= settings.minimumFlow()) {
            return true;
        }
        return sourceFlow >= settings.headwaterFlow() && targetFlow >= settings.minimumFlow();
    }

    private boolean eligibleForChannel(int index, int next, double[] heights, double[] continentEdges) {
        double sourceHeight = heights[index];
        double targetHeight = heights[next];
        if (sourceHeight <= world.seaLevel() - 1.0D && targetHeight <= world.seaLevel() - 1.0D) {
            return false;
        }
        return continentEdges[index] > 0.12D || continentEdges[next] > 0.12D;
    }

    private List<RiverPathPoint> refineVisiblePath(
            int startX,
            int startZ,
            int endX,
            int endZ,
            double startHeight,
            double endHeight,
            double startWaterHeight,
            double endWaterHeight,
            double width) {
        int samples = settings.pathSamples();
        double dx = endX - startX;
        double dz = endZ - startZ;
        double length = Math.max(1.0D, Math.hypot(dx, dz));
        double perpendicularX = -dz / length;
        double perpendicularZ = dx / length;
        double maximumOffset = settings.gridSpacing() * settings.meanderStrength();
        double[] offsets = new double[samples];
        Cell scratch = new Cell();

        for (int index = 1; index < samples - 1; index++) {
            double alpha = index / (double) (samples - 1);
            double baseX = Maths.lerp(startX, endX, alpha);
            double baseZ = Maths.lerp(startZ, endZ, alpha);
            double desiredOffset = (deterministicUnit(PATH_SALT, startX, startZ, index) * 2.0D - 1.0D)
                    * maximumOffset
                    * Math.sin(Math.PI * alpha);
            double expectedHeight = Maths.lerp(startHeight, endHeight, alpha);
            double bestScore = Double.POSITIVE_INFINITY;
            double bestOffset = 0.0D;
            double[] candidates = {
                    -maximumOffset,
                    -maximumOffset * 0.55D,
                    0.0D,
                    maximumOffset * 0.55D,
                    maximumOffset
            };
            for (double candidateOffset : candidates) {
                int sampleX = (int) Math.round(baseX + perpendicularX * candidateOffset);
                int sampleZ = (int) Math.round(baseZ + perpendicularZ * candidateOffset);
                terrain.lookup(sampleX, sampleZ, scratch);
                double uphillPenalty = Math.max(0.0D, scratch.heightErosion - expectedHeight) * 0.35D;
                double meanderPenalty = Math.abs(candidateOffset - desiredOffset) * 0.055D;
                double score = scratch.heightErosion + uphillPenalty + meanderPenalty;
                if (score < bestScore) {
                    bestScore = score;
                    bestOffset = candidateOffset;
                }
            }
            offsets[index] = bestOffset;
        }

        double[] smoothOffsets = offsets.clone();
        for (int index = 1; index < samples - 1; index++) {
            smoothOffsets[index] = (offsets[index - 1] + offsets[index] * 2.0D + offsets[index + 1]) * 0.25D;
        }

        double[] pathX = new double[samples];
        double[] pathZ = new double[samples];
        for (int index = 0; index < samples; index++) {
            double alpha = index / (double) (samples - 1);
            double offset = smoothOffsets[index];
            pathX[index] = Maths.lerp(startX, endX, alpha) + perpendicularX * offset;
            pathZ[index] = Maths.lerp(startZ, endZ, alpha) + perpendicularZ * offset;
        }

        double[] terrainHeight = new double[samples];
        double[] waterHeight = new double[samples];
        double bankProbe = Math.max(2.5D, width * 0.62D + 1.0D);
        Cell center = new Cell();
        Cell leftBank = new Cell();
        Cell rightBank = new Cell();
        for (int index = 0; index < samples; index++) {
            double tangentX;
            double tangentZ;
            if (index == 0) {
                tangentX = pathX[1] - pathX[0];
                tangentZ = pathZ[1] - pathZ[0];
            } else if (index == samples - 1) {
                tangentX = pathX[index] - pathX[index - 1];
                tangentZ = pathZ[index] - pathZ[index - 1];
            } else {
                tangentX = pathX[index + 1] - pathX[index - 1];
                tangentZ = pathZ[index + 1] - pathZ[index - 1];
            }
            double tangentLength = Math.max(1.0D, Math.hypot(tangentX, tangentZ));
            double localPerpendicularX = -tangentZ / tangentLength;
            double localPerpendicularZ = tangentX / tangentLength;

            lookupTerrain(pathX[index], pathZ[index], center);
            lookupTerrain(
                    pathX[index] + localPerpendicularX * bankProbe,
                    pathZ[index] + localPerpendicularZ * bankProbe,
                    leftBank);
            lookupTerrain(
                    pathX[index] - localPerpendicularX * bankProbe,
                    pathZ[index] - localPerpendicularZ * bankProbe,
                    rightBank);

            terrainHeight[index] = center.heightErosion;
            double alpha = index / (double) (samples - 1);
            double desiredWater = Maths.lerp(startWaterHeight, endWaterHeight, alpha);
            double containmentCeiling = Math.min(
                    center.heightErosion,
                    Math.min(leftBank.heightErosion, rightBank.heightErosion))
                    - settings.bankFreeboard();
            waterHeight[index] = Math.min(desiredWater, containmentCeiling);
        }

        limitWaterSurfaceGrade(pathX, pathZ, terrainHeight, waterHeight);

        List<RiverPathPoint> path = new ArrayList<>(samples);
        for (int index = 0; index < samples; index++) {
            path.add(new RiverPathPoint(
                    pathX[index],
                    pathZ[index],
                    terrainHeight[index],
                    waterHeight[index]));
        }
        return List.copyOf(path);
    }

    private static void limitWaterSurfaceGrade(
            double[] pathX,
            double[] pathZ,
            double[] terrainHeight,
            double[] waterHeight) {
        // Water may never run uphill. Ordinary reaches are grade-limited to avoid the former
        // one-block stair-step rivers, but a real terrain-backed downstream cliff is retained as a
        // hydraulic discontinuity. This gives the block adapter enough vertical head to materialize
        // an actual waterfall instead of flattening every river into a long shallow ramp.
        for (int index = 1; index < waterHeight.length; index++) {
            waterHeight[index] = Math.min(waterHeight[index - 1], waterHeight[index]);
        }
        for (int index = waterHeight.length - 2; index >= 0; index--) {
            double distance = Math.max(1.0E-6D, Math.hypot(
                    pathX[index + 1] - pathX[index],
                    pathZ[index + 1] - pathZ[index]));
            double terrainDrop = terrainHeight[index] - terrainHeight[index + 1];
            double waterDrop = waterHeight[index] - waterHeight[index + 1];
            boolean waterfall = terrainDrop >= WATERFALL_MINIMUM_TERRAIN_DROP
                    && terrainDrop / distance >= WATERFALL_MINIMUM_TERRAIN_GRADE
                    && waterDrop >= WATERFALL_MINIMUM_WATER_DROP;
            if (waterfall) {
                continue;
            }

            double maximumUpstreamHeight = waterHeight[index + 1]
                    + Math.max(0.10D, distance * MAX_WATER_SURFACE_GRADE);
            waterHeight[index] = Math.min(waterHeight[index], maximumUpstreamHeight);
        }
    }

    private void lookupTerrain(double x, double z, Cell target) {
        terrain.lookup((int) Math.round(x), (int) Math.round(z), target);
    }

    private double deterministicUnit(long salt, int x, int z, int extra) {
        long value = seed ^ salt;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= (long) extra * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value & 0x1FFFFFL) / (double) 0x1FFFFF;
    }

    private record FloodNode(int index, double height) {
    }

    private record FloodResult(double[] filledHeight, int[] parent) {
    }
}
