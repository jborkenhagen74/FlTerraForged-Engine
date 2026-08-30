package dev.foucaultleon.flterraforged.engine.cell;

import dev.foucaultleon.flterraforged.engine.noise.Noise;
import dev.foucaultleon.flterraforged.engine.noise.NoiseMath;
import java.util.Objects;

/**
 * Basic cell stage exposing a seed-aware noise field through a selected semantic cell channel.
 */
public final class NoiseCellPopulator implements CellPopulator {

    private final Noise noise;
    private final long seed;
    private final double scale;
    private final Channel channel;

    /**
     * Creates a noise-backed cell stage.
     *
     * @param noise source noise
     * @param seed sampling seed
     * @param scale coordinate scale
     * @param channel destination channel
     */
    public NoiseCellPopulator(Noise noise, long seed, double scale, Channel channel) {
        this.noise = Objects.requireNonNull(noise, "noise");
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("scale must be finite and > 0");
        }
        this.seed = seed;
        this.scale = scale;
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    /** {@inheritDoc} */
    @Override
    public void apply(Cell cell, int x, int z) {
        double value = noise.sample(x * scale, z * scale, seed);
        channel.write(cell, value);
    }

    /** Semantic destination for a noise-backed cell stage. */
    public enum Channel {
        /** Writes normalized erosion. */
        EROSION {
            @Override
            void write(Cell cell, double value) {
                cell.erosion = NoiseMath.clamp((value + 1.0D) * 0.5D, 0.0D, 1.0D);
            }
        },
        /** Writes terrain weirdness. */
        WEIRDNESS {
            @Override
            void write(Cell cell, double value) {
                cell.weirdness = value;
            }
        },
        /** Writes normalized temperature. */
        TEMPERATURE {
            @Override
            void write(Cell cell, double value) {
                cell.temperature = NoiseMath.clamp((value + 1.0D) * 0.5D, 0.0D, 1.0D);
            }
        },
        /** Writes normalized moisture. */
        MOISTURE {
            @Override
            void write(Cell cell, double value) {
                cell.moisture = NoiseMath.clamp((value + 1.0D) * 0.5D, 0.0D, 1.0D);
            }
        },
        /** Writes region moisture. */
        REGION_MOISTURE {
            @Override
            void write(Cell cell, double value) {
                cell.regionMoisture = NoiseMath.clamp((value + 1.0D) * 0.5D, 0.0D, 1.0D);
            }
        },
        /** Writes region temperature. */
        REGION_TEMPERATURE {
            @Override
            void write(Cell cell, double value) {
                cell.regionTemperature = NoiseMath.clamp((value + 1.0D) * 0.5D, 0.0D, 1.0D);
            }
        };

        abstract void write(Cell cell, double value);
    }
}
