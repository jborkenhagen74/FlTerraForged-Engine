package dev.foucaultleon.flterraforged.engine.noise.function;

import dev.foucaultleon.flterraforged.engine.noise.NoiseMath;

/** Common reusable curve functions. */
public final class CurveFunctions {

    /** Identity curve. */
    public static final CurveFunction LINEAR = value -> value;
    /** Cubic Hermite S-curve. */
    public static final CurveFunction HERMITE = value -> value * value * (3.0D - 2.0D * value);
    /** Quintic S-curve. */
    public static final CurveFunction QUINTIC = value -> value * value * value
            * (value * (value * 6.0D - 15.0D) + 10.0D);

    private CurveFunctions() {
    }

    /**
     * Creates a clamped terrace/step curve.
     *
     * @param steps number of steps, at least two
     * @return step curve
     */
    public static CurveFunction steps(int steps) {
        if (steps < 2) {
            throw new IllegalArgumentException("steps must be >= 2");
        }
        return value -> {
            double clamped = NoiseMath.clamp(value, 0.0D, 1.0D);
            return Math.round(clamped * (steps - 1)) / (double) (steps - 1);
        };
    }
}
