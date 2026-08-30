package dev.foucaultleon.flterraforged.engine.terrain;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.Objects;

/** Utility methods for smooth landform transitions across terrain-region boundaries. */
public final class Blender {

    private Blender() {
    }

    /**
     * Returns a terrain definition blended according to a normalized region-edge signal.
     *
     * <p>An edge value of zero represents the exact region boundary and therefore yields an even
     * blend. Once the edge reaches {@code blendWidth}, the owning terrain is returned unchanged.</p>
     *
     * @param primary owning terrain
     * @param secondary neighboring terrain
     * @param edge normalized distance from the terrain-region boundary
     * @param blendWidth normalized blend width in {@code (0, 1]}
     * @return primary terrain or a boundary composite
     */
    public static Terrain blend(Terrain primary, Terrain secondary, double edge, double blendWidth) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(secondary, "secondary");
        if (!Double.isFinite(edge) || edge < 0.0D || edge > 1.0D) {
            throw new IllegalArgumentException("edge must be finite and in [0, 1]");
        }
        if (!Double.isFinite(blendWidth) || blendWidth <= 0.0D || blendWidth > 1.0D) {
            throw new IllegalArgumentException("blendWidth must be finite and in (0, 1]");
        }
        if (primary.type().equals(secondary.type()) || edge >= blendWidth) {
            return primary;
        }
        double alpha = Maths.smooth(Maths.clamp(edge / blendWidth, 0.0D, 1.0D));
        double primaryWeight = 0.5D + alpha * 0.5D;
        return new CompositeTerrain(primary, secondary, primaryWeight);
    }
}
