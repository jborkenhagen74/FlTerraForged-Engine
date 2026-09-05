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
 * keep its own narrow bed once the preserved post-erosion surface already belongs to a material lake
 * or the open ocean. River stages retain that immutable surface in {@link Cell#heightErosion}; this
 * overlay therefore restores receiver geometry without re-running terrain or erosion sampling.</p>
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
    private final RiverModel receivers;
    private final CellLookup riverTerrain;
    private final TerrainClassificationSettings classification;

    /**
     * Creates the final receiver-ownership stage.
     *
     * @param world immutable world context
     * @param receivers cached river-map provider containing the immutable lake fields
     * @param riverTerrain fully shaped river/wet-core delegate that preserves {@code heightErosion}
     * @param classification coordinated final terrain-classification thresholds
     */
    public ReceivingWaterOverlay(
            EngineContext world,
            RiverModel receivers,
            CellLookup riverTerrain,
            TerrainClassificationSettings classification) {
        this.world = Objects.requireNonNull(world, "world");
        this.receivers = Objects.requireNonNull(receivers, "receivers");
        this.riverTerrain = Objects.requireNonNull(riverTerrain, "riverTerrain");
        this.classification = Objects.requireNonNull(classification, "classification");
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        riverTerrain.lookup(x, z, target);
        LakeHit lake = receivers.lake(x, z);

        if (lake.materialWater() || shouldPromoteLakeMouth(lake, target)) {
            applyLakeReceiver(lake, target);
            return;
        }
        if (isOceanReceiver(target)) {
            applyOceanReceiver(target);
        }
    }

    private boolean shouldPromoteLakeMouth(LakeHit lake, Cell target) {
        if (!lake.shore()
                || !Double.isFinite(lake.waterSurfaceHeight())
                || !Double.isFinite(target.riverWaterSurfaceHeight)) {
            return false;
        }
        if (lake.shoreDistance() < LAKE_MOUTH_SHORE_DISTANCE) {
            return false;
        }
        return target.heightErosion
                < lake.waterSurfaceHeight() - MATERIAL_WATER_EPSILON;
    }

    private void applyLakeReceiver(LakeHit lake, Cell target) {
        double naturalBed = target.heightErosion;
        double depth = lake.materialWater()
                ? Math.max(MINIMUM_RECEIVER_DEPTH, lake.minimumDepth())
                : MINIMUM_RECEIVER_DEPTH;
        double bed = Maths.clamp(
                Math.min(naturalBed, lake.waterSurfaceHeight() - depth),
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

    private void applyOceanReceiver(Cell target) {
        target.height = Maths.clamp(
                target.heightErosion,
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

    private boolean isOceanReceiver(Cell target) {
        double height = target.heightErosion;
        double continentalness = target.continentEdge * 2.0D - 1.0D;
        boolean belowSea = height < world.seaLevel() - 1.50D;
        boolean oceanward = continentalness < classification.coastContinentalness();
        boolean submergedMarine = oceanward && height < world.seaLevel();
        boolean deepEnough = height < world.seaLevel() - classification.oceanDepthBelowSea();
        return submergedMarine
                || (deepEnough && oceanward)
                || (continentalness < classification.oceanContinentalness() && belowSea);
    }
}
