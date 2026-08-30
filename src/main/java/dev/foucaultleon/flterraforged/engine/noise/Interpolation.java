package dev.foucaultleon.flterraforged.engine.noise;

/** Interpolation curves used between lattice samples. */
public enum Interpolation {
    /** Linear interpolation without smoothing. */
    LINEAR {
        @Override
        public double apply(double value) {
            return value;
        }
    },
    /** Cubic Hermite interpolation. */
    HERMITE {
        @Override
        public double apply(double value) {
            return value * value * (3.0D - 2.0D * value);
        }
    },
    /** Quintic interpolation with continuous first and second derivatives at cell boundaries. */
    QUINTIC {
        @Override
        public double apply(double value) {
            return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
        }
    };

    /**
     * Applies the interpolation curve to a normalized coordinate.
     *
     * @param value normalized coordinate, normally in {@code [0, 1]}
     * @return interpolated coordinate
     */
    public abstract double apply(double value);
}
