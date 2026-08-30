package dev.foucaultleon.flterraforged.engine.noise.function;

/** Distance metric used by cellular-noise calculations. */
public enum DistanceFunction {
    /** Euclidean distance. */
    EUCLIDEAN {
        @Override
        public double distance(double x, double z) {
            return Math.hypot(x, z);
        }
    },
    /** Squared Euclidean distance, avoiding a square root. */
    EUCLIDEAN_SQUARED {
        @Override
        public double distance(double x, double z) {
            return x * x + z * z;
        }
    },
    /** Manhattan distance. */
    MANHATTAN {
        @Override
        public double distance(double x, double z) {
            return Math.abs(x) + Math.abs(z);
        }
    };

    /**
     * Computes the distance from an offset vector.
     *
     * @param x X offset
     * @param z Z offset
     * @return distance
     */
    public abstract double distance(double x, double z);
}
