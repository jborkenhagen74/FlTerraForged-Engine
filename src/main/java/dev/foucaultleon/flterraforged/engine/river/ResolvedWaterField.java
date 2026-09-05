package dev.foucaultleon.flterraforged.engine.river;

import java.util.Objects;

/**
 * Immutable final hydraulic state for one Engine X/Z column.
 *
 * <p>The record is produced after terrain erosion and river shaping but before climate projection or
 * Minecraft materialization. It therefore forms the single authoritative contract for the final
 * continuous bed and water plane. Platform materializers may quantize these values to full blocks,
 * slabs, layers or other provider-specific geometry without changing hydraulic ownership.</p>
 *
 * @param owner final water owner
 * @param bedHeight continuous top of the resolved solid bed
 * @param waterSurfaceHeight continuous water surface, or {@link Double#NaN} for dry terrain
 * @param lateralDistance owner-specific lateral distance; river centerline distance or lake shore
 *     distance, otherwise {@link Double#POSITIVE_INFINITY}
 * @param width owner-specific channel/transition width, or zero for ocean/dry terrain
 * @param flow resolved river flow, zero for lakes, or {@link Double#NaN} when not applicable
 * @param mask final hydraulic mask in the inclusive range {@code [0, 1]}
 */
public record ResolvedWaterField(
        ResolvedWaterOwner owner,
        double bedHeight,
        double waterSurfaceHeight,
        double lateralDistance,
        double width,
        double flow,
        double mask) {

    private static final double MATERIAL_WATER_EPSILON = 0.05D;

    /**
     * Validates one resolved hydraulic state.
     *
     * @param owner final water owner
     * @param bedHeight continuous top of the resolved solid bed
     * @param waterSurfaceHeight continuous water surface or {@link Double#NaN}
     * @param lateralDistance owner-specific lateral distance
     * @param width owner-specific width
     * @param flow owner-specific flow
     * @param mask final hydraulic mask
     */
    public ResolvedWaterField {
        owner = Objects.requireNonNull(owner, "owner");
        if (!Double.isFinite(bedHeight)) {
            throw new IllegalArgumentException("bedHeight must be finite");
        }
        if (width < 0.0D || Double.isNaN(width)) {
            throw new IllegalArgumentException("width must be >= 0 and not NaN");
        }
        if (!Double.isFinite(mask) || mask < 0.0D || mask > 1.0D) {
            throw new IllegalArgumentException("mask must be finite and inside [0, 1]");
        }
        if (owner.wet()) {
            if (!Double.isFinite(waterSurfaceHeight)) {
                throw new IllegalArgumentException("wet owners require a finite water surface");
            }
            if (waterSurfaceHeight <= bedHeight + MATERIAL_WATER_EPSILON) {
                throw new IllegalArgumentException("wet owners require water above the resolved bed");
            }
        } else if (!Double.isNaN(waterSurfaceHeight)) {
            throw new IllegalArgumentException("dry ownership requires a NaN water surface");
        }
    }

    /**
     * Creates a dry field using the neutral hydraulic mask.
     *
     * @param bedHeight continuous terrain bed
     * @return dry resolved water field
     */
    public static ResolvedWaterField dry(double bedHeight) {
        return dry(bedHeight, 1.0D);
    }

    /**
     * Creates a dry field that preserves an existing bank/shore hydraulic mask.
     *
     * @param bedHeight continuous terrain bed
     * @param mask existing hydraulic mask
     * @return dry resolved water field
     */
    public static ResolvedWaterField dry(double bedHeight, double mask) {
        return new ResolvedWaterField(
                ResolvedWaterOwner.DRY,
                bedHeight,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                0.0D,
                Double.NaN,
                mask);
    }

    /**
     * Returns the continuous material water depth.
     *
     * @return water depth, or zero for dry terrain
     */
    public double depth() {
        return owner.wet() ? waterSurfaceHeight - bedHeight : 0.0D;
    }
}
