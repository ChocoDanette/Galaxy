package io.github.chocodanette.galaxy.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import io.github.chocodanette.galaxy.ship.crew.Crew;
import io.github.chocodanette.galaxy.ship.crew.CrewData;
import io.github.chocodanette.galaxy.ship.crew.CrewRole;

class StationPlotsTest {
    @Test
    void theCommonStationIsOnNoPlot() {
        assertEquals(Crew.NO_PLOT, StationPlots.plotAt(StationLocation.ORIGIN));
        assertEquals(Crew.NO_PLOT, StationPlots.plotAt(new BlockPos(900, 100, 0)));
        assertEquals(Crew.NO_PLOT, StationPlots.plotAt(new BlockPos(-2000, 100, 0)));
    }

    @Test
    void eachPlotFindsItself() {
        for (int plot = 1; plot <= 50; plot++) {
            BlockPos c = StationPlots.center(plot);
            assertEquals(plot, StationPlots.plotAt(c));
            assertEquals(plot, StationPlots.plotAt(c.offset(300, -50, -300)));
            assertEquals(plot, StationPlots.plotAt(BlockPos.containing(StationPlots.spawn(plot))));
        }
    }

    @Test
    void buildingStaysInsideTheArea() {
        Crew crew = new Crew(1, "Test", List.of(), 3, 6);
        BlockPos c = StationPlots.center(3);
        assertTrue(StationPlots.inBuildArea(crew, c));
        assertTrue(StationPlots.inBuildArea(crew, c.offset(6, 0, -6)));
        assertFalse(StationPlots.inBuildArea(crew, c.offset(7, 0, 0)));
        assertFalse(StationPlots.inBuildArea(crew, c.below(StationPlots.BUILD_BELOW + 1)));
        assertFalse(StationPlots.inBuildArea(crew, c.above(StationPlots.BUILD_ABOVE + 1)));
        // The area ends well above the depth where falling sends you into the galaxy.
        assertTrue(StationPlots.BUILD_BELOW < StationLocation.DIVE_DEPTH);
        assertFalse(StationPlots.inBuildArea(new Crew(2, "No station", List.of(), Crew.NO_PLOT, 6), c));
    }

    @Test
    void aPlayerIsInOneCrewAtATime() {
        CrewData data = new CrewData();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Crew crew = data.create(alice, "Alice", "Comets");
        data.join(crew, bob, "Bob", CrewRole.GUEST);
        assertEquals(crew, data.crewOf(bob).orElseThrow());
        assertEquals(CrewRole.CAPTAIN, crew.role(alice).orElseThrow());
        assertFalse(crew.role(bob).orElseThrow().canBuild());
        int first = data.claimPlot(crew, 6);
        Crew other = data.create(UUID.randomUUID(), "Carol", "Nebula");
        assertTrue(data.claimPlot(other, 6) > first, "plots are never reused");
        data.leave(crew, bob);
        assertTrue(data.crewOf(bob).isEmpty());
        data.leave(crew, alice);
        assertTrue(data.crewNamed("Comets").isEmpty(), "an empty crew is gone");
        assertTrue(data.crewOnPlot(first).isEmpty());
    }
}
