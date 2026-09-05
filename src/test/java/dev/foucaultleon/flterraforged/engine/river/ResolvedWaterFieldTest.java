package dev.foucaultleon.flterraforged.engine.river;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ResolvedWaterFieldTest {

    @Test
    void ownershipPriorityMatchesReceiverAuthority() {
        assertTrue(ResolvedWaterOwner.OCEAN.priority() > ResolvedWaterOwner.LAKE.priority());
        assertTrue(ResolvedWaterOwner.LAKE.priority() > ResolvedWaterOwner.RIVER.priority());
        assertTrue(ResolvedWaterOwner.RIVER.priority() > ResolvedWaterOwner.DRY.priority());
        assertFalse(ResolvedWaterOwner.DRY.wet());
        assertTrue(ResolvedWaterOwner.RIVER.wet());
    }

    @Test
    void wetFieldReportsContinuousDepth() {
        ResolvedWaterField field = new ResolvedWaterField(
                ResolvedWaterOwner.LAKE,
                60.25D,
                63.0D,
                0.0D,
                12.0D,
                0.0D,
                0.15D);

        assertEquals(2.75D, field.depth(), 1.0E-9D);
    }

    @Test
    void dryFactoryPreservesBankMask() {
        ResolvedWaterField field = ResolvedWaterField.dry(71.5D, 0.42D);

        assertEquals(ResolvedWaterOwner.DRY, field.owner());
        assertEquals(71.5D, field.bedHeight(), 1.0E-9D);
        assertEquals(0.42D, field.mask(), 1.0E-9D);
        assertEquals(0.0D, field.depth(), 1.0E-9D);
        assertTrue(Double.isNaN(field.waterSurfaceHeight()));
    }

    @Test
    void wetOwnerRejectsWaterAtOrBelowBed() {
        assertThrows(IllegalArgumentException.class, () -> new ResolvedWaterField(
                ResolvedWaterOwner.RIVER,
                63.0D,
                63.01D,
                0.0D,
                8.0D,
                1.0D,
                0.0D));
    }
}
