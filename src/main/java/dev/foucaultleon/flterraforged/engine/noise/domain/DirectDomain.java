package dev.foucaultleon.flterraforged.engine.noise.domain;

import dev.foucaultleon.flterraforged.engine.noise.Vector2;

/** Identity coordinate domain. */
public final class DirectDomain implements Domain {

    /** Shared identity domain. */
    public static final DirectDomain INSTANCE = new DirectDomain();

    private DirectDomain() {
    }

    /** {@inheritDoc} */
    @Override
    public Vector2 transform(double x, double z, long seed) {
        return new Vector2(x, z);
    }
}
