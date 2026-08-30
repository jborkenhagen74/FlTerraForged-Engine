package dev.foucaultleon.flterraforged.engine.noise.module;

import dev.foucaultleon.flterraforged.engine.noise.Noise;
import dev.foucaultleon.flterraforged.engine.noise.Vector2;
import dev.foucaultleon.flterraforged.engine.noise.domain.Domain;
import java.util.Objects;

/** Samples a source field through a transformed coordinate domain. */
public final class WarpNoise implements Noise {

    private final Noise source;
    private final Domain domain;

    /**
     * Creates a warped field.
     *
     * @param source source field
     * @param domain coordinate domain
     */
    public WarpNoise(Noise source, Domain domain) {
        this.source = Objects.requireNonNull(source, "source");
        this.domain = Objects.requireNonNull(domain, "domain");
    }

    /** {@inheritDoc} */
    @Override
    public double sample(double x, double z, long seed) {
        Vector2 point = domain.transform(x, z, seed);
        return source.sample(point.x(), point.z(), seed);
    }

    /** {@inheritDoc} */
    @Override
    public double minValue() {
        return source.minValue();
    }

    /** {@inheritDoc} */
    @Override
    public double maxValue() {
        return source.maxValue();
    }
}
