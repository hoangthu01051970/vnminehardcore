package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PortalDefinitionTest {
    @Test
    public void containsIncludesAllBoundsAndChecksWorld() {
        PortalDefinition portal = portal();

        assertTrue(portal.contains("world", 10, 64, -3));
        assertTrue(portal.contains("WORLD", 12, 65, -1));
        assertFalse(portal.contains("world", 13, 65, -1));
        assertFalse(portal.contains("other", 11, 64, -2));
    }

    @Test
    public void volumeCountsInclusiveBounds() {
        assertEquals(18L, portal().volume());
    }

    @Test
    public void volumeSaturatesInsteadOfOverflowing() {
        PortalDefinition portal = new PortalDefinition("large-region", "Large", "world",
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            null, null, true);

        assertEquals(Long.MAX_VALUE, portal.volume());
    }

    @Test
    public void validatesIdsAndDestinationCoordinates() {
        assertTrue(PortalDefinition.isValidId("north_gate-2"));
        assertFalse(PortalDefinition.isValidId("A"));
        assertFalse(PortalDefinition.isValidId("invalid id"));
        assertThrows(IllegalArgumentException.class, () ->
            new PortalDefinition.Destination("world", Double.NaN, 64, 0, 0, 0));
    }

    private PortalDefinition portal() {
        return new PortalDefinition("test-gate", "Test Gate", "world",
            10, 64, -3, 12, 65, -1, null, null, true);
    }
}
