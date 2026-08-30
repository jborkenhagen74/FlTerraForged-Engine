package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import java.util.Objects;

/**
 * Immutable terrain definition assembled from reusable noise fields and numeric shape parameters.
 */
public final class ConfiguredTerrain implements Terrain {

    private final TerrainType type;
    private final TerrainCategory category;
    private final Noise2D shape;
    private final Noise2D detail;
    private final double baseOffset;
    private final double relief;
    private final double detailRelief;
    private final double ridgeStrength;

    /**
     * Creates a configured terrain definition.
     *
     * @param type semantic terrain identifier
     * @param category broad terrain category
     * @param shape primary normalized shape field
     * @param detail fine normalized detail field
     * @param baseOffset height offset from sea level
     * @param relief primary relief in blocks
     * @param detailRelief fine-detail relief in blocks
     * @param ridgeStrength amount of ridged transformation in {@code [0, 1]}
     */
    public ConfiguredTerrain(
            TerrainType type,
            TerrainCategory category,
            Noise2D shape,
            Noise2D detail,
            double baseOffset,
            double relief,
            double detailRelief,
            double ridgeStrength) {
        this.type = Objects.requireNonNull(type, "type");
        this.category = Objects.requireNonNull(category, "category");
        this.shape = Objects.requireNonNull(shape, "shape");
        this.detail = Objects.requireNonNull(detail, "detail");
        this.baseOffset = finite(baseOffset, "baseOffset");
        this.relief = nonNegative(relief, "relief");
        this.detailRelief = nonNegative(detailRelief, "detailRelief");
        if (!Double.isFinite(ridgeStrength) || ridgeStrength < 0.0D || ridgeStrength > 1.0D) {
            throw new IllegalArgumentException("ridgeStrength must be finite and in [0, 1]");
        }
        this.ridgeStrength = ridgeStrength;
    }

    /** {@inheritDoc} */
    @Override
    public TerrainType type() {
        return type;
    }

    /** {@inheritDoc} */
    @Override
    public TerrainCategory category() {
        return category;
    }

    /** {@inheritDoc} */
    @Override
    public double height(TerrainContext context) {
        Objects.requireNonNull(context, "context");
        double shapeValue = shape.sample(context.x(), context.z());
        double normalized = shapedSignal(shapeValue);
        double coastMask = Maths.smooth(Maths.map01(context.continentalness() + 0.15D));
        double landBase = context.continentalness() * relief * 0.35D;
        double shaped = normalized * relief * coastMask;
        double fine = detail.sample(context.x(), context.z()) * detailRelief;
        return context.world().seaLevel() + baseOffset + landBase + shaped + fine;
    }

    /** {@inheritDoc} */
    @Override
    public double weirdness(TerrainContext context) {
        return Maths.clamp(shape.sample(context.x(), context.z()), -1.0D, 1.0D);
    }

    private double shapedSignal(double value) {
        double ridge = 1.0D - Math.abs(value);
        ridge = ridge * ridge;
        double ridged = ridge * 2.0D - 1.0D;
        double mixed = Maths.lerp(value, ridged, ridgeStrength);
        return switch (category) {
            case OCEAN -> -Math.abs(mixed);
            case FLAT -> mixed * 0.35D;
            case HILLS -> mixed;
            case MOUNTAINS -> Math.max(-0.25D, mixed);
            case PLATEAU -> Math.tanh(mixed * 3.0D) / Math.tanh(3.0D);
            case VALLEY -> -Math.abs(mixed) * 0.70D;
        };
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static double nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and >= 0");
        }
        return value;
    }
}
