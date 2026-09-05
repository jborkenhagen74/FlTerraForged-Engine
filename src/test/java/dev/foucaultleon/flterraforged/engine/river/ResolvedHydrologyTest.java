package dev.foucaultleon.flterraforged.engine.river;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import org.junit.jupiter.api.Test;

/** Regression coverage for the R44 receiver-dominant hydraulic solve. */
final class ResolvedHydrologyTest {

    private static final double EPSILON = 1.0E-9D;
    private static final int SPACING = 16;

    @Test
    void confluenceUsesOneCanonicalReceiverLevel() {
        RivermapGenerator generator = generator();
        int[] downstream = {2, 2, 3, -1};
        double[] flow = {1.0D, 1.0D, 1.0D, 1.0D};
        double[] filled = {90.0D, 82.0D, 70.0D, 60.0D};

        int[] topology = RivermapGenerator.accumulateFlow(downstream, flow);
        double[] resolved = generator.resolveWaterSurface(
                downstream, filled, topology, 4, SPACING);

        assertTrue(resolved[0] >= resolved[2]);
        assertTrue(resolved[1] >= resolved[2]);
        assertTrue(resolved[2] >= resolved[3]);
        assertEquals(resolved[2], receiverLevelForIncomingSegment(resolved, 0, downstream), EPSILON);
        assertEquals(resolved[2], receiverLevelForIncomingSegment(resolved, 1, downstream), EPSILON);
    }

    @Test
    void normalSourceLevelIsLimitedByReceiverGrade() {
        RivermapGenerator generator = generator();
        int[] downstream = {1, -1};
        double[] flow = {1.0D, 1.0D};
        double[] filled = {120.0D, 60.0D};

        int[] topology = RivermapGenerator.accumulateFlow(downstream, flow);
        double[] resolved = generator.resolveWaterSurface(
                downstream, filled, topology, 2, SPACING);

        double maximumNormalRise = 0.18D * SPACING;
        assertTrue(resolved[0] - resolved[1] <= maximumNormalRise + EPSILON);
        assertTrue(resolved[0] >= resolved[1]);
    }

    @Test
    void drainageCycleIsRejectedBeforeHydraulicSolve() {
        int[] downstream = {1, 0};
        double[] flow = {1.0D, 1.0D};

        assertThrows(
                IllegalStateException.class,
                () -> RivermapGenerator.accumulateFlow(downstream, flow));
    }

    private static double receiverLevelForIncomingSegment(
            double[] resolved,
            int source,
            int[] downstream) {
        return resolved[downstream[source]];
    }

    private static RivermapGenerator generator() {
        EngineContext context = new EngineContext(1L, -64, 320, 63);
        CellLookup terrain = (x, z, target) -> {
            target.reset();
            target.height = 80.0D;
            target.heightErosion = 80.0D;
            target.continentEdge = 1.0D;
        };
        RiverSettings settings = new RiverSettings(
                64,
                SPACING,
                4,
                2.0D,
                1.0D,
                2.0D,
                16.0D,
                10.0D,
                1.0D,
                1.0D,
                1.35D,
                4.0D,
                0.5D,
                0.25D,
                5,
                0.85D,
                1.35D,
                8);
        return new RivermapGenerator(2L, context, terrain, null, settings);
    }
}
