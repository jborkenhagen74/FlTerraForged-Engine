package dev.foucaultleon.flterraforged.engine.noise.domain;

import dev.foucaultleon.flterraforged.engine.noise.Noise;
import dev.foucaultleon.flterraforged.engine.noise.Vector2;
import java.util.Objects;

/** Domain warp driven by independent X and Z displacement fields. */
public final class NoiseDomain implements Domain {

    private final Noise xWarp;
    private final Noise zWarp;
    private final double strength;

    /**
     * Creates a noise-driven domain warp.
     *
     * @param xWarp X-displacement field
     * @param zWarp Z-displacement field
     * @param strength displacement strength in coordinate units
     */
    public NoiseDomain(Noise xWarp, Noise zWarp, double strength) {
        this.xWarp = Objects.requireNonNull(xWarp, "xWarp");
        this.zWarp = Objects.requireNonNull(zWarp, "zWarp");
        if (!Double.isFinite(strength)) {
            throw new IllegalArgumentException("strength must be finite");
        }
        this.strength = strength;
    }

    /** {@inheritDoc} */
    @Override
    public Vector2 transform(double x, double z, long seed) {
        double dx = xWarp.sample(x, z, seed) * strength;
        double dz = zWarp.sample(x, z, seed ^ 0xD1B54A32D192ED03L) * strength;
        return new Vector2(x + dx, z + dz);
    }
}
