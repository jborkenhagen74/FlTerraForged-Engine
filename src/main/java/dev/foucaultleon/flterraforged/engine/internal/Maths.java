package dev.foucaultleon.flterraforged.engine.internal;

public final class Maths {

    private Maths() {
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double lerp(double a, double b, double alpha) {
        return a + (b - a) * alpha;
    }

    public static double smooth(double value) {
        double t = clamp(value, 0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t);
    }

    public static double map01(double value) {
        return clamp(value * 0.5D + 0.5D, 0.0D, 1.0D);
    }
}
