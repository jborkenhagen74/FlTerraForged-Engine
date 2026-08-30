package dev.foucaultleon.flterraforged.engine.noise.function;

/** Scalar curve function used to reshape normalized noise signals. */
@FunctionalInterface
public interface CurveFunction {

    /**
     * Applies the curve.
     *
     * @param value input value
     * @return curved value
     */
    double apply(double value);
}
