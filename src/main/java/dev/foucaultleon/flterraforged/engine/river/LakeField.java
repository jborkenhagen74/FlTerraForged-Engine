package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable depression-fill field used to materialize irregular ponds and lakes.
 *
 * <p>The field is sampled bilinearly from the same globally aligned hydrology grid used by the
 * drainage graph. Low-frequency deterministic edge noise only perturbs the shoreline threshold;
 * the water level itself always comes from the depression spill surface.</p>
 */
public final class LakeField {

    private final long seed;
    private final int originX;
    private final int originZ;
    private final int spacing;
    private final int width;
    private final double[] originalHeight;
    private final double[] filledHeight;
    private final double minimumDepth;
    private final double shoreBlend;
    private final int seaLevel;

    /**
     * Creates an immutable lake field.
     *
     * @param seed hydrology seed
     * @param originX grid origin X
     * @param originZ grid origin Z
     * @param spacing grid spacing
     * @param width node count per axis
     * @param originalHeight original drainage-surface heights
     * @param filledHeight depression-filled hydrology heights
     * @param minimumDepth minimum fill depth for inland water
     * @param shoreBlend shore-softening depth interval
     * @param seaLevel world sea level
     */
    public LakeField(
            long seed,
            int originX,
            int originZ,
            int spacing,
            int width,
            double[] originalHeight,
            double[] filledHeight,
            double minimumDepth,
            double shoreBlend,
            int seaLevel) {
        this.seed = seed;
        this.originX = originX;
        this.originZ = originZ;
        this.spacing = spacing;
        this.width = width;
        this.originalHeight = Objects.requireNonNull(originalHeight, "originalHeight").clone();
        this.filledHeight = Objects.requireNonNull(filledHeight, "filledHeight").clone();
        this.minimumDepth = minimumDepth;
        this.shoreBlend = shoreBlend;
        this.seaLevel = seaLevel;
        if (this.originalHeight.length != width * width || this.filledHeight.length != width * width) {
            throw new IllegalArgumentException("Lake-field arrays do not match grid width");
        }
    }

    /**
     * Samples the depression-filled inland-water field.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return lake/pond hit or {@link LakeHit#NONE}
     */
    public LakeHit sample(double x, double z) {
        double gridX = (x - originX) / spacing;
        double gridZ = (z - originZ) / spacing;
        int gx = (int) Math.floor(gridX);
        int gz = (int) Math.floor(gridZ);
        if (gx < 0 || gz < 0 || gx >= width - 1 || gz >= width - 1) {
            return LakeHit.NONE;
        }
        double tx = gridX - gx;
        double tz = gridZ - gz;
        double original = bilinear(originalHeight, gx, gz, tx, tz);
        double filled = bilinear(filledHeight, gx, gz, tx, tz);
        if (filled <= seaLevel + 0.75D) {
            return LakeHit.NONE;
        }

        double rawDepth = filled - original;
        double shorelineNoise = smoothValueNoise(x * 0.035D, z * 0.035D) * 0.38D;
        double effectiveDepth = rawDepth + shorelineNoise;
        double influence = Maths.smooth(Maths.clamp(
                (effectiveDepth - minimumDepth) / shoreBlend,
                0.0D,
                1.0D));
        if (influence <= 0.0D) {
            return LakeHit.NONE;
        }

        double waterSurface = filled - 0.25D;
        double desiredDepth = 1.15D + influence * Math.min(3.25D, Math.max(0.0D, rawDepth));
        return new LakeHit(influence, waterSurface, desiredDepth);
    }

    private double bilinear(double[] values, int gx, int gz, double tx, double tz) {
        double a = values[gz * width + gx];
        double b = values[gz * width + gx + 1];
        double c = values[(gz + 1) * width + gx];
        double d = values[(gz + 1) * width + gx + 1];
        double top = Maths.lerp(a, b, tx);
        double bottom = Maths.lerp(c, d, tx);
        return Maths.lerp(top, bottom, tz);
    }

    private double smoothValueNoise(double x, double z) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        double tx = Maths.smooth(x - xi);
        double tz = Maths.smooth(z - zi);
        double a = hash(xi, zi);
        double b = hash(xi + 1, zi);
        double c = hash(xi, zi + 1);
        double d = hash(xi + 1, zi + 1);
        return Maths.lerp(Maths.lerp(a, b, tx), Maths.lerp(c, d, tx), tz);
    }

    private double hash(int x, int z) {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value & 0x1FFFFFL) / (double) 0x1FFFFF) * 2.0D - 1.0D;
    }
    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LakeField that)) {
            return false;
        }
        return seed == that.seed
                && originX == that.originX
                && originZ == that.originZ
                && spacing == that.spacing
                && width == that.width
                && Double.doubleToLongBits(minimumDepth) == Double.doubleToLongBits(that.minimumDepth)
                && Double.doubleToLongBits(shoreBlend) == Double.doubleToLongBits(that.shoreBlend)
                && seaLevel == that.seaLevel
                && Arrays.equals(originalHeight, that.originalHeight)
                && Arrays.equals(filledHeight, that.filledHeight);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = Objects.hash(
                seed, originX, originZ, spacing, width, minimumDepth, shoreBlend, seaLevel);
        result = 31 * result + Arrays.hashCode(originalHeight);
        result = 31 * result + Arrays.hashCode(filledHeight);
        return result;
    }

}
