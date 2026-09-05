package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainClassificationSettings;
import java.util.Objects;

/**
 * Re-applies receiving water bodies after river shaping so lakes and oceans own their final beds.
 *
 * <p>The river network is allowed to shape land and approach a receiver, but it is not allowed to
 * keep its own narrow bed once the pre-hydrology terrain is already part of a material lake or the
 * open ocean. The overlay therefore samples the immutable post-erosion terrain independently from
 * the river-shaped delegate and restores receiver-owned geometry as the final hydrology stage.</p>
 *
 * <p>This is an Engine-space composition stage, not a Minecraft repair pass. Materializers receive
 * one already-resolved continuous terrain sample and may quantize it to full blocks, slabs or other
 * provider-specific geometry afterwards.</p>
 */
public final class ReceivingWaterOverlay implements CellLookup {

    private static final double MINIMUM_RECEIVER_DEPTH = 1.10D;
    private static final double MATERIAL_WATER_EPSILON = 0.05D;
    private static final double LAKE_MOUTH_SHORE_DISTANCE = -2.0D;

    private final EngineContext world;
    private final CellLookup preHydrologyTerrain;
    private final RiverModel receivers;
    private final CellLookup riverTerrain;
    private final TerrainClassificationSettings classification;

    /**
     * Creates the final receiver-ownership stage.
     *
     * @param world immutable world context
     * @param preHydrologyTerrain post-erosion terrain before any river or lake incision
     * @param receivers cached river-map provider containing the immutable lake fields
     * @param riverTerrain fully shaped river/wet-core delegate
     * @param classification coordinated final terrain-classification thresholds
     */
    public ReceivingWaterOverlay(
            EngineContext world,
            CellLookup preHydrologyTerrain,
            RiverModel receivers,
            CellLookup riverTerrain,
            TerrainClassificationSettings classification) {
        this.world = Objects.requireNonNull(world, "world");
        this.preHydrologyTerrain = Objects.requireNonNull(preHydrologyTerrain, "preHydrologyTerrain");
        this.receivers = Objects.requireNonNull(receivers, "receivers");
        this.riverTerrain = Objects.requireNonNull(riverTerrain, "riverTerrain");
        this.classification = Objects.requireNonNull(classification, "classification");
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        riverTerrain.lookup(x, z, target);

        Cell natural = new Cell();
        preHydrologyTerrain.lookup(x, z, natural);
        LakeHit lake = receivers.lake(x, z);

        if (lake.materialWater() || shouldPromoteLakeMouth(lake, natural, target)) {
            applyLakeReceiver(natural, lake, target);
            return;
        }
        if (isOceanReceiver(natural)) {
            applyOceanReceiver(natural, target);
        }
    }

    private boolean shouldPromoteLakeMouth(LakeHit lake, Cell natural, Cell riverShaped) {
        if (!lake.shore()
                || !Double.isFinite(lake.waterSurfaceHeight())
                || !Double.isFinite(riverShaped.riverWaterSurfaceHeight)) {
            return false;
        }
        if (lake.shoreDistance() < LAKE_MOUTH_SHORE_DISTANCE) {
            return false;
        }
        return natural.heightErosion
                < lake.waterSurfaceHeight() - MATERIAL_WATER_EPSILON;
    }

    private void applyLakeReceiver(Cell natural, LakeHit lake, Cell target) {
        target.copyFrom(natural);
        double depth = lake.materialWater()
                ? Math.max(MINIMUM_RECEIVER_DEPTH, lake.minimumDepth())
                : MINIMUM_RECEIVER_DEPTH;
        double bed = Maths.clamp(
                Math.min(natural.heightErosion, lake.waterSurfaceHeight() - depth),
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);

        target.height = bed;
        target.lake = true;
        target.lakeShore = false;
        target.riverMask = 1.0D - lake.influence();
        target.riverDistance = -Math.max(0.0D, lake.shoreDistance());
        target.riverWidth = LakeField.SHORE_TRANSITION_WIDTH;
        target.riverDepth = Math.max(MINIMUM_RECEIVER_DEPTH, lake.waterSurfaceHeight() - bed);
        target.riverWaterSurfaceHeight = lake.waterSurfaceHeight();
        target.riverFlow = 0.0D;
    }

    private void applyOceanReceiver(Cell natural, Cell target) {
        target.copyFrom(natural);
        target.height = Maths.clamp(
                natural.heightErosion,
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
        target.lake = false;
        target.lakeShore = false;
        target.riverMask = 1.0D;
        target.riverDistance = Double.POSITIVE_INFINITY;
        target.riverWidth = 0.0D;
        target.riverDepth = 0.0D;
        target.riverWaterSurfaceHeight = Double.NaN;
        target.riverFlow = Double.NaN;
    }

    private boolean isOceanReceiver(Cell natural) {
        double height = natural.heightErosion;
        double continentalness = natural.continentEdge * 2.0D - 1.0D;
        boolean belowSea = height < world.seaLevel() - 1.50D;
        boolean oceanward = continentalness < classification.coastContinentalness();
        boolean submergedMarine = oceanward && height < world.seaLevel();
        boolean deepEnough = height < world.seaLevel() - classification.oceanDepthBelowSea();
        return submergedMarine
                || (deepEnough && oceanward)
                || (continentalness < classification.oceanContinentalness() && belowSea);
    }
}
