package io.github.chocodanette.galaxy.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;

class GalaxyCycleTest {
    @AfterEach
    void backToGalaxyOne() {
        GalaxyCycle.forget();
    }

    @Test
    void newGalaxyGetsNextNumberAndAFreshSlot() {
        GalaxyCycle cycle = new GalaxyCycle();
        cycle.publish();
        RandomSource random = RandomSource.create(5);
        cycle.advance(random);
        assertEquals(2, GalaxyCycle.number());
        assertEquals(2, GalaxyCycle.slot());
        assertEquals(java.util.List.of(1), cycle.dirtySlots());
        cycle.advance(random);
        assertEquals(3, GalaxyCycle.slot());
        // Slot 1 is wiped: it can be used again, but only after every other clean one.
        cycle.markClean(1);
        assertEquals(java.util.List.of(2), cycle.dirtySlots());
    }

    @Test
    void lifeIsOneToSixHours() {
        GalaxyCycle cycle = new GalaxyCycle();
        RandomSource random = RandomSource.create(9);
        for (int i = 0; i < 200; i++) {
            cycle.startClock(random);
            assertTrue(cycle.life() >= GalaxyCycle.MIN_LIFE && cycle.life() <= GalaxyCycle.MAX_LIFE, "" + cycle.life());
            assertEquals(cycle.life(), cycle.ticksLeft());
        }
    }

    @Test
    void dirtySlotsAreSkippedWhenWrappingAround() {
        GalaxyCycle cycle = new GalaxyCycle();
        cycle.publish();
        RandomSource random = RandomSource.create(1);
        for (int i = 1; i < Galaxy.MAX_SLOT; i++) {
            cycle.advance(random);
        }
        assertEquals(Galaxy.MAX_SLOT, GalaxyCycle.slot());
        cycle.markClean(4);
        cycle.advance(random);
        assertEquals(4, GalaxyCycle.slot());
    }

    @Test
    void wipeOnlyTouchesWholeRegionsOfTheSlot() {
        int slot = 3;
        int middleRegion = Math.floorDiv(slot * Galaxy.SPACING, 512);
        assertTrue(GalaxyWipe.inSlot(Path.of("r." + middleRegion + ".0.mca"), slot));
        assertTrue(GalaxyWipe.inSlot(Path.of("r." + middleRegion + ".-7.mca"), slot));
        assertFalse(GalaxyWipe.inSlot(Path.of("r." + middleRegion + ".0.mca"), slot + 1));
        int lastRegionOfSlot2 = Math.floorDiv(2 * Galaxy.SPACING + Galaxy.SPACING / 2 - 1, 512);
        // That region straddles the border between slots 2 and 3: kept, to be safe.
        assertFalse(GalaxyWipe.inSlot(Path.of("r." + lastRegionOfSlot2 + ".0.mca"), slot));
        assertFalse(GalaxyWipe.inSlot(Path.of("level.dat"), slot));
    }
}
