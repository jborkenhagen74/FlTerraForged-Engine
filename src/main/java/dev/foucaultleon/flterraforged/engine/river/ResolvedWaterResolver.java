package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainClassificationSettings;
import java.util.Objects;

/**
 * Resolves competing river, lake and ocean candidates into one final continuous water field.
 *
 * <p>The resolver performs no Minecraft or chunk access. All inputs are immutable Engine data or the
 * already-shaped caller-owned cell. Ocean, lake and river candidates are evaluated independently and
 * then selected by {@link ResolvedWaterOwner#priority()}, which prevents later stages from partially
 * applying two hydraulic profiles to the same column.</p>
 */
public final class ResolvedWaterResolver {

    private static final double MINIMUM_RECEIVER_DEPTH = 1.10D;
    private static final double MATERIAL_WATER_EPSILON = 0.05D;
    private static final double LAKE_MOUTH_SHORE_DISTANCE = -2.0D;

    private final EngineContext world;
    private final RiverModel receivers;
    private final TerrainClassificationSettings classification;

    /**
     * Creates a final hydraulic resolver.
     *
     * @param world immutable world context
     * @param receivers cached river/lake provider
     * @param classification coordinated terrain-classification thresholds
     */
    public ResolvedWaterResolver(
            EngineContext world,
            RiverModel receivers,
            TerrainClassificationSettings classification) {
        this.world = Objects.requireNonNull(world, "world");
        this.receivers = Objects.requireNonNull(receivers, "receivers");
        this.classification = Objects.requireNonNull(classification, "classification");
    }

    /**
     * Resolves the final water owner, bed and surface for one column.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @param shaped already-shaped post-river cell
     * @return authoritative final continuous water field
     */
    public ResolvedWaterField resolve(int x, int z, Cell shaped) {
        Objects.requireNonNull(shaped, "shaped");
        ResolvedWaterField resolved = riverCandidate(shaped);

        LakeHit lake = receivers.lake(x, z);
        if (lake.materialWater() || shouldPromoteLakeMouth(lake, shaped)) {
            resolved = choose(resolved, lakeCandidate(lake, shaped));
        }
        if (isOceanReceiver(shaped)) {
            ResolvedWaterField ocean = oceanCandidate(shaped);
            if (ocean != null) {
                resolved = choose(resolved, ocean);
            }
        }
        return resolved;
    }

    private ResolvedWaterField riverCandidate(Cell target) {
        double bed = target.height;
        if (!Double.isFinite(target.riverWaterSurfaceHeight)
                || target.riverWaterSurfaceHeight <= bed + MATERIAL_WATER_EPSILON) {
            return ResolvedWaterField.dry(bed);
        }
        return new ResolvedWaterField(
                ResolvedWaterOwner.RIVER,
                bed,
                target.riverWaterSurfaceHeight,
                target.riverDistance,
                Math.max(0.0D, target.riverWidth),
                target.riverFlow,
                Maths.clamp(target.riverMask, 0.0D, 1.0D));
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

    private ResolvedWaterField lakeCandidate(LakeHit lake, Cell target) {
        double naturalBed = target.heightErosion;
        double depth = lake.materialWater()
                ? Math.max(MINIMUM_RECEIVER_DEPTH, lake.minimumDepth())
                : MINIMUM_RECEIVER_DEPTH;
        double bed = Maths.clamp(
                Math.min(naturalBed, lake.waterSurfaceHeight() - depth),
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
        return new ResolvedWaterField(
                ResolvedWaterOwner.LAKE,
                bed,
                lake.waterSurfaceHeight(),
                -Math.max(0.0D, lake.shoreDistance()),
                LakeField.SHORE_TRANSITION_WIDTH,
                0.0D,
                Maths.clamp(1.0D - lake.influence(), 0.0D, 1.0D));
    }

    private ResolvedWaterField oceanCandidate(Cell target) {
        double bed = Maths.clamp(
                target.heightErosion,
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
        double surface = world.seaLevel();
        if (surface <= bed + MATERIAL_WATER_EPSILON) {
            return null;
        }
        return new ResolvedWaterField(
                ResolvedWaterOwner.OCEAN,
                bed,
                surface,
                Double.POSITIVE_INFINITY,
                0.0D,
                Double.NaN,
                1.0D);
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

    private static ResolvedWaterField choose(
            ResolvedWaterField current,
            ResolvedWaterField candidate) {
        return candidate.owner().priority() > current.owner().priority()
                ? candidate
                : current;
    }
}
