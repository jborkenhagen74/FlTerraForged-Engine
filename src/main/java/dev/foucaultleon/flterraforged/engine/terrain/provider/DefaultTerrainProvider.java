package dev.foucaultleon.flterraforged.engine.terrain.provider;

import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import dev.foucaultleon.flterraforged.engine.terrain.ConfiguredTerrain;
import dev.foucaultleon.flterraforged.engine.terrain.Terrain;
import dev.foucaultleon.flterraforged.engine.terrain.TerrainCategory;
import java.util.List;
import java.util.Objects;

/** Default distribution of engine-neutral landform definitions across terrain regions. */
public final class DefaultTerrainProvider implements TerrainProvider {

    private final List<Terrain> palette;

    /**
     * Creates the default landform palette.
     *
     * @param rolling broad rolling noise field
     * @param ridges ridge-oriented mountain noise field
     * @param detail fine terrain detail field
     * @param relief base relief in blocks
     * @param mountainRelief mountain relief in blocks
     */
    public DefaultTerrainProvider(
            Noise2D rolling,
            Noise2D ridges,
            Noise2D detail,
            double relief,
            double mountainRelief) {
        Objects.requireNonNull(rolling, "rolling");
        Objects.requireNonNull(ridges, "ridges");
        Objects.requireNonNull(detail, "detail");
        if (!Double.isFinite(relief) || relief <= 0.0D
                || !Double.isFinite(mountainRelief) || mountainRelief <= 0.0D) {
            throw new IllegalArgumentException("terrain relief values must be finite and > 0");
        }
        this.palette = List.of(
                new ConfiguredTerrain(
                        StandardTerrainTypes.PLAINS,
                        TerrainCategory.FLAT,
                        rolling,
                        detail,
                        4.0D,
                        relief * 0.18D,
                        2.5D,
                        0.0D),
                new ConfiguredTerrain(
                        StandardTerrainTypes.HILLS,
                        TerrainCategory.HILLS,
                        rolling,
                        detail,
                        8.0D,
                        relief * 0.55D,
                        4.0D,
                        0.20D),
                new ConfiguredTerrain(
                        StandardTerrainTypes.PLATEAU,
                        TerrainCategory.PLATEAU,
                        rolling,
                        detail,
                        17.0D,
                        relief * 0.28D,
                        2.5D,
                        0.05D),
                new ConfiguredTerrain(
                        StandardTerrainTypes.VALLEY,
                        TerrainCategory.VALLEY,
                        rolling,
                        detail,
                        2.0D,
                        relief * 0.22D,
                        2.0D,
                        0.0D),
                new ConfiguredTerrain(
                        StandardTerrainTypes.MOUNTAINS,
                        TerrainCategory.MOUNTAINS,
                        ridges,
                        detail,
                        13.0D,
                        mountainRelief,
                        5.0D,
                        0.88D));
    }

    /** {@inheritDoc} */
    @Override
    public Terrain resolve(double selector) {
        if (!Double.isFinite(selector) || selector < 0.0D || selector > 1.0D) {
            throw new IllegalArgumentException("selector must be finite and in [0, 1]");
        }
        if (selector < 0.24D) {
            return palette.get(0);
        }
        if (selector < 0.47D) {
            return palette.get(1);
        }
        if (selector < 0.63D) {
            return palette.get(3);
        }
        if (selector < 0.80D) {
            return palette.get(2);
        }
        return palette.get(4);
    }
}
