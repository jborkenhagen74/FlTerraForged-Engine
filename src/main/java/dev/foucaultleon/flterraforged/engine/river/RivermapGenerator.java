package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Builds deterministic coarse D8 drainage graphs from the broad pre-incision terrain surface.
 *
 * <p>Nodes are aligned to a global grid rather than to chunk coordinates. Flow is routed to the
 * lowest strictly lower neighbor, accumulated from high to low elevation, and promoted to channel
 * segments once the configured drainage threshold is reached. This keeps river centerlines tied to
 * actual valleys in the eroded height field instead of to an unrelated noise mask.</p>
 */
public final class RivermapGenerator {

    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DZ = {-1, -1, -1, 0, 0, 1, 1, 1};

    private final long seed;
    private final EngineContext world;
    private final CellLookup terrain;
    private final RiverSettings settings;

    /**
     * Creates a river-map generator.
     *
     * @param seed hydrology seed
     * @param world immutable world context
     * @param terrain broad terrain lookup used for drainage topology
     * @param settings river settings
     */
    public RivermapGenerator(long seed, EngineContext world, CellLookup terrain, RiverSettings settings) {
        this.seed = seed;
        this.world = Objects.requireNonNull(world, "world");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
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
        Arrays.fill(flow, 1.0D);

        Cell scratch = new Cell();
        for (int gz = 0; gz < nodesPerAxis; gz++) {
            for (int gx = 0; gx < nodesPerAxis; gx++) {
                int index = gz * nodesPerAxis + gx;
                int x = originX + gx * spacing;
                int z = originZ + gz * spacing;
                terrain.lookup(x, z, scratch);
                heights[index] = scratch.heightErosion;
                continentEdges[index] = scratch.continentEdge;
            }
        }

        for (int gz = 1; gz < nodesPerAxis - 1; gz++) {
            for (int gx = 1; gx < nodesPerAxis - 1; gx++) {
                int index = gz * nodesPerAxis + gx;
                downstream[index] = selectDownstream(
                        gx, gz, nodesPerAxis, originX, originZ, spacing, heights);
            }
        }

        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer index) -> heights[index]).reversed());
        for (int index : order) {
            int next = downstream[index];
            if (next >= 0) {
                flow[next] += flow[index];
            }
        }

        int coreMinX = regionX * settings.regionSize();
        int coreMinZ = regionZ * settings.regionSize();
        int coreMaxX = coreMinX + settings.regionSize();
        int coreMaxZ = coreMinZ + settings.regionSize();
        List<RiverSegment> segments = new ArrayList<>();

        for (int gz = 1; gz < nodesPerAxis - 1; gz++) {
            for (int gx = 1; gx < nodesPerAxis - 1; gx++) {
                int index = gz * nodesPerAxis + gx;
                int next = downstream[index];
                if (next < 0 || flow[index] < settings.minimumFlow()) {
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
                double normalizedFlow = Math.max(0.0D, flow[index] - settings.minimumFlow() + 1.0D);
                double width = Maths.clamp(
                        settings.minimumWidth() + Math.sqrt(normalizedFlow) * settings.widthGrowth(),
                        settings.minimumWidth(),
                        settings.maximumWidth());
                double depth = Math.min(
                        settings.maximumDepth(),
                        0.85D + Math.log1p(normalizedFlow) * settings.depthGrowth());
                double slope = Math.max(0.0D, heights[index] - heights[next]) / Math.max(1.0D, spacing);
                depth *= 0.72D + Math.min(0.35D, slope * 1.8D);

                segments.add(new RiverSegment(
                        startX,
                        startZ,
                        endX,
                        endZ,
                        heights[index],
                        heights[next],
                        flow[index],
                        width,
                        Math.min(settings.maximumDepth(), depth)));
            }
        }

        return new Rivermap(regionX, regionZ, segments);
    }

    private int selectDownstream(
            int gx,
            int gz,
            int width,
            int originX,
            int originZ,
            int spacing,
            double[] heights) {
        int index = gz * width + gx;
        double current = heights[index];
        int best = -1;
        double bestScore = current;
        int worldGridX = Math.floorDiv(originX, spacing) + gx;
        int worldGridZ = Math.floorDiv(originZ, spacing) + gz;
        double bestTie = deterministicTieBreak(worldGridX, worldGridZ);
        for (int i = 0; i < DX.length; i++) {
            int nx = gx + DX[i];
            int nz = gz + DZ[i];
            int candidate = nz * width + nx;
            double score = heights[candidate];
            double tie = deterministicTieBreak(worldGridX + DX[i], worldGridZ + DZ[i]);
            if (score + 1.0E-7D < bestScore
                    || (Math.abs(score - bestScore) <= 1.0E-7D && score < current && tie < bestTie)) {
                bestScore = score;
                bestTie = tie;
                best = candidate;
            }
        }
        return best;
    }

    private boolean eligibleForChannel(int index, int next, double[] heights, double[] continentEdges) {
        double sourceHeight = heights[index];
        double targetHeight = heights[next];
        if (sourceHeight <= world.seaLevel() - 1.0D && targetHeight <= world.seaLevel() - 1.0D) {
            return false;
        }
        return continentEdges[index] > 0.12D || continentEdges[next] > 0.12D;
    }

    private double deterministicTieBreak(int gx, int gz) {
        long value = seed;
        value ^= (long) gx * 0x9E3779B97F4A7C15L;
        value ^= (long) gz * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value & 0x1FFFFFL) / (double) 0x1FFFFF;
    }
}
