package dev.foucaultleon.flterraforged.engine.river;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class R48RiverNetworkFilterTest {

    @Test
    void denseParallelHeadwatersYieldToConfluencesAndMatureTrunks() {
        List<RiverSegment> segments = new ArrayList<>();

        RiverSegment tributaryA = segment(0, 0, 16, 16, 9.0D);
        RiverSegment tributaryB = segment(32, 0, 16, 16, 9.0D);
        RiverSegment confluenceTrunk = segment(16, 16, 16, 32, 18.0D);
        RiverSegment matureIndependent = segment(96, 0, 96, 16, 24.0D);
        segments.add(tributaryA);
        segments.add(tributaryB);
        segments.add(confluenceTrunk);
        segments.add(matureIndependent);

        for (int index = 0; index < 12; index++) {
            int x = 160 + index * 16;
            segments.add(segment(x, 0, x, 16, 9.0D));
        }

        List<RiverSegment> visible = RiverNetworkFilter.visibleNetwork(segments);

        assertTrue(visible.contains(tributaryA), "last tributary edge into a confluence must remain");
        assertTrue(visible.contains(tributaryB), "last tributary edge into a confluence must remain");
        assertTrue(visible.contains(confluenceTrunk), "higher-order trunk must remain visible");
        assertTrue(visible.contains(matureIndependent), "mature independent trunk must remain visible");
        assertTrue(visible.size() < segments.size(), "parallel first-order drainage stripes must be reduced");
        assertFalse(visible.contains(segments.get(4)), "isolated low-flow first-order fall line must be hidden");
    }

    @Test
    void smallIsolatedCreekNetworkIsNotErased() {
        List<RiverSegment> segments = List.of(
                segment(0, 0, 16, 0, 9.0D),
                segment(16, 0, 32, 0, 10.0D));

        List<RiverSegment> visible = RiverNetworkFilter.visibleNetwork(segments);

        assertTrue(visible.containsAll(segments));
    }

    private static RiverSegment segment(int startX, int startZ, int endX, int endZ, double flow) {
        double startWater = 70.0D;
        double endWater = 69.5D;
        return new RiverSegment(
                startX,
                startZ,
                endX,
                endZ,
                72.0D,
                71.0D,
                startWater,
                endWater,
                flow,
                4.0D,
                3.0D,
                List.of(
                        new RiverPathPoint(startX, startZ, 72.0D, startWater),
                        new RiverPathPoint(endX, endZ, 71.0D, endWater)));
    }
}
