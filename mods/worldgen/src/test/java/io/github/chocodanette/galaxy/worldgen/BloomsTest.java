package io.github.chocodanette.galaxy.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

class BloomsTest {
    private static final long SEED = 123456789L;

    /** Every planet of the first galaxies. */
    private static List<Planet> planets(int galaxies) {
        return planets(1, galaxies);
    }

    /** Every planet of galaxies first to last (the last one stays current). */
    private static List<Planet> planets(int first, int last) {
        List<Planet> planets = new ArrayList<>();
        for (int number = first; number <= last; number++) {
            GalaxyCycle.setCurrentForTests(number, number);
            GalaxyLayout.allCells(SEED).forEach(planets::addAll);
        }
        return planets;
    }

    @Test
    void linksGoBothWaysBetweenNearbyPlanets() {
        int links = 0;
        for (Planet planet : planets(1)) {
            List<Planet> partners = Blooms.links(SEED, planet);
            assertTrue(partners.size() <= Blooms.LEAVES);
            for (Planet partner : partners) {
                links++;
                assertTrue(Blooms.links(SEED, partner).contains(planet), "a link only one way");
                double gap = Blooms.gap(planet, partner);
                assertTrue(gap >= Blooms.MIN_GAP && gap <= Blooms.MAX_GAP, "gap of " + gap);
            }
        }
        assertTrue(links > 20, "only " + links + " links");
    }

    @Test
    void everyLinkedLeafLandsOnALeafThatThrowsYouBack() {
        int linked = 0;
        int blooms = 0;
        for (Planet planet : planets(1)) {
            Optional<Blooms.Bloom> bloom = Blooms.bloom(SEED, planet);
            if (bloom.isEmpty()) {
                continue;
            }
            blooms++;
            assertEquals(Blooms.LEAVES, bloom.get().leaves().size());
            for (Blooms.Leaf leaf : bloom.get().leaves()) {
                if (!leaf.linked()) {
                    continue;
                }
                linked++;
                Blooms.Leaf target = Blooms.target(SEED, leaf).orElseThrow();
                assertTrue(target.linked());
                assertEquals(planet, target.partner(), "the partner leaf throws you somewhere else");
                Blooms.Leaf back = Blooms.target(SEED, target).orElseThrow();
                assertEquals(leaf.pad(), back.pad(), "the way back lands on another leaf");
                assertEquals(leaf, Blooms.leafAt(SEED, leaf.pad()).orElseThrow());
            }
        }
        assertTrue(blooms > 10, "only " + blooms + " blooms");
        assertTrue(linked > 20, "only " + linked + " linked leaves");
    }

    @Test
    void bloomsStandOnTheGroundWithOpenSkyOverEachPad() {
        for (Planet planet : planets(1)) {
            Optional<Blooms.Bloom> bloom = Blooms.bloom(SEED, planet);
            if (bloom.isEmpty()) {
                continue;
            }
            List<BlockPos> parts = bloom.get().blocks().stream().map(Blooms.Block::pos).toList();
            Vec3 c = planet.center();
            for (BlockPos pos : parts) {
                double far = Math.max(Math.abs(pos.getX() + 0.5 - c.x),
                        Math.max(Math.abs(pos.getY() + 0.5 - c.y), Math.abs(pos.getZ() + 0.5 - c.z)));
                assertTrue(far <= planet.reach(), "bloom reaches past the planet's reach");
            }
            for (Blooms.Leaf leaf : bloom.get().leaves()) {
                assertNull(planet.materialAt(leaf.pad().getX(), leaf.pad().getY(), leaf.pad().getZ()), "pad inside the ground");
                assertTrue(parts.contains(leaf.pad()));
                // Nothing of the bloom above a pad (you fly straight up off it).
                for (int h = 1; h <= 8; h++) {
                    assertTrue(!parts.contains(leaf.pad().relative(leaf.up(), h)), "bloom block above a pad");
                }
            }
            // The stem stands on the ground.
            BlockPos firstStem = bloom.get().blocks().stream()
                    .filter(b -> b.part() == Blooms.Part.STEM).findFirst().orElseThrow().pos();
            BlockPos ground = firstStem.relative(bloom.get().up().getOpposite());
            assertTrue(planet.materialAt(ground.getX(), ground.getY(), ground.getZ()) != null, "stem floats");
        }
    }

    @Test
    void theArcLeavesUpwardAndLandsOnTheTarget() {
        Vec3 start = new Vec3(0.5, 100, 0.5);
        Vec3 end = new Vec3(80.5, 120, 10.5);
        Vec3 east = new Vec3(-1, 0, 0);
        BloomPath path = BloomPath.between(start, new Vec3(0, 1, 0), end, east);
        assertEquals(0.0, path.at(0).distanceTo(start), 1.0E-9);
        assertEquals(0.0, path.at(1).distanceTo(end), 1.0E-9);
        assertEquals(0.0, path.atTick(path.ticks()).distanceTo(end), 1.0E-9);
        assertTrue(path.at(0.05).subtract(start).normalize().y > 0.8, "should leave straight up");
        Vec3 arrive = end.subtract(path.at(0.95)).normalize();
        assertTrue(arrive.dot(east) < -0.8, "should land coming down along the target's up");
        assertTrue(path.ticks() >= BloomPath.MIN_TICKS && path.ticks() <= BloomPath.MAX_TICKS);
        // Steady progress: never jumps more than a few blocks in a tick.
        for (int t = 1; t <= path.ticks(); t++) {
            assertTrue(path.atTick(t).distanceTo(path.atTick(t - 1)) < 4.0);
        }
    }

    @Test
    void homewardBloomsAreRareNeverOnTheArrivalPlanetAndThrowYouHome() {
        int big = 0;
        int homeward = 0;
        for (int number = 1; number <= 3; number++) {
          GalaxyCycle.setCurrentForTests(number, number);
          Planet arrival = GalaxyLayout.arrivalPlanet(SEED);
          for (Planet planet : planets(number, number)) {
            if (!Blooms.canGrowBloom(planet)) {
                continue;
            }
            big++;
            if (!Blooms.isHomeward(SEED, planet)) {
                continue;
            }
            homeward++;
            assertTrue(!arrival.center().equals(planet.center()), "homeward on the arrival planet");
            assertTrue(Blooms.links(SEED, planet).isEmpty(), "a Homeward Bloom planet with Launch Bloom links");
            Optional<Blooms.Bloom> bloom = Blooms.bloom(SEED, planet);
            if (bloom.isEmpty()) {
                continue; // no room for it on this planet
            }
            assertTrue(bloom.get().homeward());
            long homes = bloom.get().leaves().stream().filter(Blooms.Leaf::home).count();
            assertEquals(1, homes, "one leaf throws you home");
            Blooms.Leaf home = bloom.get().leaves().stream().filter(Blooms.Leaf::home).findFirst().orElseThrow();
            assertTrue(bloom.get().blocks().stream().anyMatch(b -> b.pos().equals(home.pad()) && b.part() == Blooms.Part.HOME_PAD));
            assertEquals(home, Blooms.leafAt(SEED, home.pad()).orElseThrow());
            BloomPath path = BloomRides.pathFrom(SEED, home.pad()).orElseThrow();
            Vec3 rise = path.end().subtract(path.start());
            assertEquals(BloomRides.HOME_RISE, rise.length(), 1.0E-6);
            assertTrue(rise.normalize().dot(home.up().getUnitVec3()) > 0.999, "the way home goes straight up");
          }
        }
        assertTrue(homeward > 0, "no Homeward Bloom in 3 galaxies");
        // About 1 in 15 big planets: well under the share that grows a normal Launch Bloom.
        assertTrue(homeward < big / 6, homeward + " homeward out of " + big);
    }
}
