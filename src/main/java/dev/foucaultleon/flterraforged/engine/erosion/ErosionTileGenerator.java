package dev.foucaultleon.flterraforged.engine.erosion;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import java.util.Objects;

final class ErosionTileGenerator {

    private final long seed;
    private final EngineContext world;
    private final CellLookup baseTerrain;
    private final ErosionSettings settings;
    private final ErosionFilter thermal = new ThermalErosionFilter();

    ErosionTileGenerator(long seed, EngineContext world, CellLookup baseTerrain, ErosionSettings settings) {
        this.seed = seed;
        this.world = Objects.requireNonNull(world, "world");
        this.baseTerrain = Objects.requireNonNull(baseTerrain, "baseTerrain");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    ErosionTile generate(int regionX, int regionZ) {
        int coreX = regionX * settings.regionSize();
        int coreZ = regionZ * settings.regionSize();
        int originX = coreX - settings.border();
        int originZ = coreZ - settings.border();
        int width = settings.regionSize() + settings.border() * 2 + 1;
        double[] base = new double[width * width];
        Cell workspace = new Cell();
        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) {
                baseTerrain.lookup(originX + x, originZ + z, workspace);
                base[z * width + x] = workspace.height;
            }
        }

        double[] heights = base.clone();
        double[] erosion = new double[base.length];
        double[] deposition = new double[base.length];
        ErosionFilter hydraulic = new HydraulicErosionFilter(
                seed,
                originX,
                originZ);
        hydraulic.apply(heights, erosion, deposition, width, settings, world.seaLevel());
        thermal.apply(heights, erosion, deposition, width, settings, world.seaLevel());

        double maxDelta = settings.maximumHeightChange();
        for (int i = 0; i < heights.length; i++) {
            double delta = heights[i] - base[i];
            if (delta > maxDelta) {
                heights[i] = base[i] + maxDelta;
            } else if (delta < -maxDelta) {
                heights[i] = base[i] - maxDelta;
            }
            heights[i] = Math.max(world.minY() + 1.0D, Math.min(world.maxYExclusive() - 2.0D, heights[i]));
        }
        return new ErosionTile(originX, originZ, width, base, heights, erosion, deposition);
    }

}
