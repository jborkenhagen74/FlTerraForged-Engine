package dev.foucaultleon.flterraforged.engine.internal;

/** Small numerical helpers shared by the bootstrap engine models. */
public final class Maths {

    private Maths() {
    }

    /**
     * Clamps a value to an inclusive numeric range.
     *
     * @param value value to clamp
     * @param min inclusive minimum
     * @param max inclusive maximum
     * @return clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Performs linear interpolation.
     *
     * @param a start value
     * @param b end value
     * @param alpha interpolation factor
     * @return interpolated value
     */
    public static double lerp(double a, double b, double alpha) {
        return a + (b - a) * alpha;
    }

    /**
     * Applies a cubic smoothstep curve to a value clamped to {@code [0, 1]}.
     *
     * @param value source value
     * @return smoothed value in {@code [0, 1]}
     */
    public static double smooth(double value) {
        double t = clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    /**
     * Maps a nominal {@code [-1, 1]} value into {@code [0, 1]}.
     *
     * @param value source value
     * @return mapped and clamped value
     */
    public static double map01(double value) {
        return clamp(value * 0.5D + 0.5D, 0.0D, 1.0D);
    }
}
