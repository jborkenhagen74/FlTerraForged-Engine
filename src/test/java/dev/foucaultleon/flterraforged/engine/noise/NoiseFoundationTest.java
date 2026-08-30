package dev.foucaultleon.flterraforged.engine.noise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.noise.domain.NoiseDomain;
import org.junit.jupiter.api.Test;

final class NoiseFoundationTest {

    @Test
    void deterministicAcrossRepeatedSamples() {
        Noise noise = new FractalNoise(
                new GradientNoise(17L, 0.02D, Interpolation.QUINTIC),
                5,
                0.5D,
                2.0D);
        double first = noise.sample(1234.5D, -998.25D, 42L);
        double second = noise.sample(1234.5D, -998.25D, 42L);
        assertEquals(first, second);
        assertNotEquals(first, noise.sample(1234.5D, -998.25D, 43L));
    }

    @Test
    void composedNoiseHonoursDeclaredRange() {
        Noise base = new ValueNoise(91L, 0.1D, Interpolation.HERMITE);
        Noise mapped = Noises.clamp(Noises.map(base, 0.0D, 1.0D), 0.2D, 0.8D);
        for (int x = -32; x <= 32; x++) {
            for (int z = -32; z <= 32; z++) {
                double value = mapped.sample(x, z, 123L);
                assertTrue(value >= 0.2D && value <= 0.8D);
            }
        }
    }

    @Test
    void domainWarpChangesCoordinatesDeterministically() {
        Noise source = new GradientNoise(11L, 0.05D, Interpolation.QUINTIC);
        Noise xWarp = new ValueNoise(21L, 0.01D, Interpolation.QUINTIC);
        Noise zWarp = new ValueNoise(22L, 0.01D, Interpolation.QUINTIC);
        Noise warped = Noises.warp(source, new NoiseDomain(xWarp, zWarp, 12.0D));
        double direct = source.sample(100.0D, 200.0D, 9L);
        double first = warped.sample(100.0D, 200.0D, 9L);
        double second = warped.sample(100.0D, 200.0D, 9L);
        assertEquals(first, second);
        assertNotEquals(direct, first);
    }
}
