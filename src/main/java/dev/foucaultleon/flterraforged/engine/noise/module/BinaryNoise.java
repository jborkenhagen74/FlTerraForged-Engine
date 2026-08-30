package dev.foucaultleon.flterraforged.engine.noise.module;

import dev.foucaultleon.flterraforged.engine.noise.Noise;
import java.util.Objects;
import java.util.function.DoubleBinaryOperator;

/** Binary arithmetic noise field. */
public final class BinaryNoise implements Noise {

    private final Noise left;
    private final Noise right;
    private final DoubleBinaryOperator operation;
    private final double min;
    private final double max;

    private BinaryNoise(Noise left, Noise right, DoubleBinaryOperator operation, double min, double max) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.min = min;
        this.max = max;
    }

    /**
     * Creates an addition field.
     *
     * @param left left field
     * @param right right field
     * @return addition field
     */
    public static BinaryNoise add(Noise left, Noise right) {
        return new BinaryNoise(
                left,
                right,
                (a, b) -> a + b,
                left.minValue() + right.minValue(),
                left.maxValue() + right.maxValue());
    }

    /**
     * Creates a multiplication field.
     *
     * @param left left field
     * @param right right field
     * @return multiplication field
     */
    public static BinaryNoise multiply(Noise left, Noise right) {
        double a = left.minValue() * right.minValue();
        double b = left.minValue() * right.maxValue();
        double c = left.maxValue() * right.minValue();
        double d = left.maxValue() * right.maxValue();
        return new BinaryNoise(
                left,
                right,
                (x, y) -> x * y,
                Math.min(Math.min(a, b), Math.min(c, d)),
                Math.max(Math.max(a, b), Math.max(c, d)));
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z, long seed) {
        return operation.applyAsDouble(left.sample(x, z, seed), right.sample(x, z, seed));
    }

    /** {@inheritDoc} */
    @Override
    public double minValue() {
        return min;
    }

    /** {@inheritDoc} */
    @Override
    public double maxValue() {
        return max;
    }
}
