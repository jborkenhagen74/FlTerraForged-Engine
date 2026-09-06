package dev.foucaultleon.flterraforged.engine.river;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class R50LakeBoundaryReconciliationTest {

    @Test
    void overlappingPaddedLakeFieldsExposeSameStableBasinAnchor() {
        LakeField first = field(0, 75.0D);
        LakeField second = field(16, 76.0D);

        LakeHit left = first.sample(24.0D, 24.0D);
        LakeHit right = second.sample(24.0D, 24.0D);

        assertTrue(left.present());
        assertTrue(right.present());
        assertTrue(left.hasBasinKey());
        assertEquals(left.basinKey(), right.basinKey());
        assertNotEquals(left.waterSurfaceHeight(), right.waterSurfaceHeight());
        assertEquals(
                left.waterSurfaceHeight(),
                right.withWaterSurfaceHeight(left.waterSurfaceHeight()).waterSurfaceHeight());
    }

    private static LakeField field(int originX, double spillLevel) {
        int spacing = 8;
        int width = 7;
        double[] original = new double[width * width];
        double[] filled = new double[width * width];
        for (int gz = 0; gz < width; gz++) {
            for (int gx = 0; gx < width; gx++) {
                int x = originX + gx * spacing;
                int z = gx >= 0 ? gz * spacing : gz * spacing;
                int index = gz * width + gx;
                boolean basin = x >= 16 && x <= 40 && z >= 16 && z <= 40;
                double height = basin ? 70.0D : 82.0D;
                if (x == 24 && z == 24) {
                    height = 68.0D;
                }
                original[index] = height;
                filled[index] = basin ? spillLevel : height;
            }
        }
        return new LakeField(
                1234L,
                originX,
                0,
                spacing,
                width,
                original,
                filled,
                0.85D,
                1.35D,
                63);
    }
}
