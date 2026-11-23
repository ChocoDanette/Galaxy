package io.github.chocodanette.galaxy.gravity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class GravityFrameTest {
    private static double angleDegrees(Vec3 a, Vec3 b) {
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.normalize().dot(b.normalize())))));
    }

    @Test
    void startsUpright() {
        GravityFrame frame = new GravityFrame();
        assertFalse(frame.isTilted());
        assertEquals(1.0, frame.up().y, 1.0E-6);
    }

    @Test
    void turnsGraduallyTowardTarget() {
        GravityFrame frame = new GravityFrame();
        Vec3 east = new Vec3(1, 0, 0);
        frame.tick(east);
        assertTrue(frame.isTilted());
        assertEquals(GravityFrame.MAX_TURN_DEGREES_PER_TICK, angleDegrees(new Vec3(0, 1, 0), frame.up()), 0.01);
        for (int i = 0; i < 20; i++) {
            frame.tick(east);
        }
        assertEquals(0.0, angleDegrees(east, frame.up()), 0.01);
    }

    @Test
    void localAndWorldRoundTrip() {
        GravityFrame frame = new GravityFrame();
        for (int i = 0; i < 30; i++) {
            frame.tick(new Vec3(1, 1, 0).normalize());
        }
        Vec3 world = new Vec3(0.3, -0.7, 2.0);
        Vec3 back = frame.toWorld(frame.toLocal(world));
        assertEquals(world.x, back.x, 1.0E-5);
        assertEquals(world.y, back.y, 1.0E-5);
        assertEquals(world.z, back.z, 1.0E-5);
        // Local up maps to the frame's up.
        Vec3 up = frame.toWorld(new Vec3(0, 1, 0));
        assertEquals(0.0, angleDegrees(up, frame.up()), 0.01);
    }

    @Test
    void returnsUprightAwayFromPlanets() {
        GravityFrame frame = new GravityFrame();
        for (int i = 0; i < 30; i++) {
            frame.tick(new Vec3(0, -1, 0));
        }
        for (int i = 0; i < 30; i++) {
            frame.tick(new Vec3(0, 1, 0));
        }
        assertFalse(frame.isTilted());
    }
}
